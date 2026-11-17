// Adapted from Kototoro KotoNetworkHelper at f32242d649471627d8906a523511bc69217897eb
// (earlier adaptation: f4f37a5b7290da05c10b9325912f2a37ebeff0f9).
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package io.github.landwarderer.futon.mihon.compat

import android.os.Looper
import android.util.Log
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import io.github.landwarderer.futon.core.exceptions.CloudFlareBlockedException
import io.github.landwarderer.futon.core.exceptions.CloudFlareProtectedException
import io.github.landwarderer.futon.core.exceptions.InteractiveActionRequiredException
import io.github.landwarderer.futon.core.network.CloudFlareInterceptor as FutonCloudFlareInterceptor
import io.github.landwarderer.futon.core.network.cloudflare.solveClearanceAndRetry
import io.github.landwarderer.futon.core.network.cookies.sensitiveValueFingerprint
import io.github.landwarderer.futon.core.network.webview.CloudflareSolveCoordinator
import io.github.landwarderer.futon.core.network.webview.WebViewClearanceSolver
import io.github.landwarderer.futon.core.network.webview.WebViewExecutor
import io.github.landwarderer.futon.mihon.model.toMangaSource
import io.github.landwarderer.futon.mihon.parsers.model.ContentSource
import io.github.landwarderer.futon.mihon.parsers.network.CloudFlareHelper
import io.github.landwarderer.futon.mihon.parsers.network.UserAgents
import kotlinx.coroutines.runBlocking
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.brotli.BrotliInterceptor
import okhttp3.zstd.Zstd
import okio.Buffer
import org.koitharu.kotatsu.parsers.model.MangaSource
import java.util.concurrent.ConcurrentHashMap

/** Mihon/Keiyoushi network host with the same default-client ABI and Cloudflare semantics. */
class MihonNetworkHelper(
    baseClient: OkHttpClient,
    val cookieJar: CookieJar,
    private val webViewExecutor: WebViewExecutor? = null,
    private val clearanceSolver: WebViewClearanceSolver? = null,
    private val solveCoordinator: CloudflareSolveCoordinator? = null,
    private val defaultUserAgent: String = UserAgents.CHROME_MOBILE,
) : NetworkHelper() {

    private val zstdRuntimeDependency = Zstd

    override val client: OkHttpClient = run {
        val builder = baseClient.newBuilder().apply {
            interceptors().clear()
            networkInterceptors().clear()
            cookieJar(cookieJar)
        }

        // Keep the exact three Mihon/Keiyoushi default interceptors first.
        builder.addInterceptor(UncaughtExceptionInterceptor())
        builder.addInterceptor(UserAgentInterceptor(::defaultUserAgentProvider))
        builder.addInterceptor(
            CloudflareInterceptor(
                delegate = Interceptor { chain -> chain.proceed(chain.request()) },
            ),
        )

        baseClient.interceptors.forEach { interceptor ->
            if (isCompatibleInterceptor(interceptor) && !isDefaultMihonInterceptor(interceptor)) {
                builder.addInterceptor(interceptor)
            }
        }
        baseClient.networkInterceptors.forEach { interceptor ->
            if (isCompatibleInterceptor(interceptor)) builder.addNetworkInterceptor(interceptor)
        }

        // Real Cloudflare handling. A genuine challenge is solved by Kototoro's current strategy: a fresh off-screen
        // WebView, shared per host, followed by one retry of the source request. This adapter also recovers the
        // request's source context and keeps the user agent Cloudflare accepted for the host.
        builder.addInterceptor { chain ->
            val originalRequest = chain.request()
                .withSourceRequestContext()
                .withCloudflareUserAgent()
            // Request headers belong to the extension: no browser-like headers are added here (Kototoro's
            // enrichApiRequestHeadersIfNeeded is deliberately not ported, see MihonNetworkHelperTest).
            val request = originalRequest
            val response = chain.proceed(request)
            val challengeUrl = request.toChallengeUrl()
            val protection = CloudFlareHelper.checkResponseForProtection(response)
            rememberAcceptedCloudflareUserAgent(request, response, protection)
            if (protection != CloudFlareHelper.PROTECTION_NOT_DETECTED) {
                Log.w(
                    TAG,
                    "Protection detected: type=${protectionLabel(protection)}, host=${request.url.host}, " +
                        "code=${response.code}, server=${response.header("server")}, " +
                        "cf-ray=${response.header("cf-ray")}, cf-mitigated=${response.header("cf-mitigated")}, " +
                        "url=${request.url}",
                )
            }

            when (protection) {
                CloudFlareHelper.PROTECTION_BLOCKED -> response.closeThrowing(
                    CloudFlareBlockedException(
                        url = challengeUrl,
                        source = request.requestSource(),
                    ),
                )

                CloudFlareHelper.PROTECTION_CAPTCHA -> {
                    val host = request.url.host.lowercase()
                    val previousClearance = getClearanceCookie(request)
                    val retryResponse = solveAndRetry(chain, request, response, challengeUrl)
                    if (retryResponse != null) {
                        val retryClearance = getClearanceCookie(request)
                        Log.i(
                            TAG,
                            "Cloudflare offscreen solve completed host=$host, " +
                                "clearanceChanged=${retryClearance != null && retryClearance != previousClearance}; " +
                                "source request retried",
                        )
                        val retryProtection = CloudFlareHelper.checkResponseForProtection(retryResponse)
                        when (retryProtection) {
                            CloudFlareHelper.PROTECTION_NOT_DETECTED -> {
                                recentChallengeAttempts.remove(host)
                                rememberAcceptedCloudflareUserAgent(request, retryResponse, retryProtection)
                                Log.i(TAG, "Cloudflare retry succeeded host=$host status=${retryResponse.code}")
                                return@addInterceptor retryResponse
                            }

                            CloudFlareHelper.PROTECTION_BLOCKED -> retryResponse.closeThrowing(
                                CloudFlareBlockedException(
                                    url = challengeUrl,
                                    source = request.requestSource(),
                                ),
                            )

                            else -> {
                                Log.w(TAG, "Cloudflare retry still protected host=$host status=${retryResponse.code}")
                                throwUnresolvedChallenge(retryResponse, request, challengeUrl, host)
                            }
                        }
                    }

                    throwUnresolvedChallenge(response, request, challengeUrl, host)
                }

                else -> response
            }
        }

        // Diagnostics deliberately run after the challenge interceptor so device logs show the
        // extension-visible request/response and distinguish a CF challenge from API errors such
        // as Comix's JSON {"message":"Invalid token."} response.
        builder.addInterceptor { chain ->
            val request = chain.request()
            val requestCookies = cookieJar.loadForRequest(request.url)
            val cfClearanceCookie = requestCookies.firstOrNull { it.name == "cf_clearance" }?.value
            val cookieNames = requestCookies.joinToString(",") { it.name }
            Log.d(
                TAG,
                "RequestMeta: host=${request.url.host}, ua=${maskUserAgent(request.header("User-Agent"))}, " +
                    "uaFingerprint=${sensitiveValueFingerprint(request.header("User-Agent"))}, " +
                    "referer=${request.header("Referer")}, origin=${request.header("Origin")}, " +
                    "hasCfClearance=${cfClearanceCookie != null}, " +
                    "cfClearanceFingerprint=${sensitiveValueFingerprint(cfClearanceCookie)}, cookies=[$cookieNames]",
            )
            Log.d(TAG, "Request: ${request.method} ${request.url}")

            val response = chain.proceed(request)
            logCloudflareSetCookies(response)
            val responseCode = response.code
            val contentType = response.header("Content-Type")
            Log.d(
                TAG,
                "Response: $responseCode, Content-Type: $contentType, cf-ray=${response.header("cf-ray")}, cf-mitigated=${response.header("cf-mitigated")}, server=${response.header("server")}, URL: ${request.url}",
            )

            if (!response.isSuccessful) {
                val source = response.body.source()
                source.request(200)
                val buffer = source.buffer.clone()
                val preview = buffer.readUtf8(minOf(200, buffer.size))
                Log.w(TAG, "Non-successful response ($responseCode) preview: $preview")
            }
            response
        }

        builder.build()
    }

    private fun isCompatibleInterceptor(interceptor: Interceptor): Boolean =
        interceptor !== BrotliInterceptor &&
            interceptor.javaClass.simpleName != "BrotliInterceptor" &&
            interceptor.javaClass.simpleName != "GZipInterceptor" &&
            interceptor.javaClass.simpleName != "IgnoreGzipInterceptor" &&
            interceptor !is FutonCloudFlareInterceptor

    private fun isDefaultMihonInterceptor(interceptor: Interceptor): Boolean =
        interceptor.javaClass.simpleName in MIHON_COMPAT_INTERCEPTOR_NAMES

    @Deprecated("The regular client handles Cloudflare by default")
    override val cloudflareClient: OkHttpClient = client.newBuilder()
        .addNetworkInterceptor(BrotliInterceptor)
        .build()

    override fun defaultUserAgentProvider(): String = defaultUserAgent

    private fun Response.closeThrowing(error: Throwable): Nothing {
        try {
            close()
        } catch (e: Exception) {
            error.addSuppressed(e)
        }
        throw error
    }

    private fun Request.toChallengeUrl(): String {
        val referer = header("Referer")?.toHttpUrlOrNull()
        if (referer != null && referer.host == url.host) {
            return referer.newBuilder().query(null).fragment(null).build().toString()
        }
        return url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString()
    }

    private fun getClearanceCookie(request: Request): String? =
        cookieJar.loadForRequest(request.url).firstOrNull { it.name == "cf_clearance" }?.value

    /**
     * Solves the challenge once per host in an off-screen WebView and retries [request]. Returns the retried
     * response, or null when it could not be solved (the challenged [response] is closed in every case).
     */
    private fun solveAndRetry(
        chain: Interceptor.Chain,
        request: Request,
        response: Response,
        challengeUrl: String,
    ): Response? {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "Cloudflare automatic solve skipped on main thread")
            return null
        }

        val solver = clearanceSolver
        if (solver != null) {
            response.close()
            return runCatching { chain.solveClearanceAndRetry(request, solver, solveCoordinator) }
                .onFailure { Log.w(TAG, "Cloudflare offscreen solver failed for ${request.url.host}", it) }
                .getOrNull()
        }

        // Compatibility-only fallback for tests or old construction sites. Production Mihon
        // wiring always supplies the dedicated solver above.
        val executor = webViewExecutor ?: return null
        response.close()
        val exception = CloudFlareProtectedException(
            url = challengeUrl,
            source = null,
            headers = request.headers,
            method = request.method,
            body = request.replayableUtf8Body(),
            contentType = request.header("Content-Type"),
            originalUrl = request.url.toString(),
        )
        val resolved = runCatching {
            runBlocking { executor.tryResolveCaptcha(exception, LEGACY_RESOLVE_TIMEOUT_MS) }
        }.onFailure {
            Log.w(TAG, "Legacy Cloudflare resolver failed for ${request.url.host}", it)
        }.getOrDefault(false)
        return if (resolved) chain.proceed(request) else null
    }

    private fun Request.requestSource(): MangaSource? =
        tag(SourceRequestContext::class.java)?.source?.toMangaSource()
            ?: (tag(ContentSource::class.java) as MangaSource?)

    /** Recovers the source tag for requests an extension built without it (nested/legacy calls). */
    private fun Request.withSourceRequestContext(): Request {
        tag(SourceRequestContext::class.java)?.let { return this }
        val legacySource = tag(ContentSource::class.java)
            ?: MihonRequestContext.currentSource()
            ?: MihonRequestContext.sourceForHost(url.host)
            ?: return this
        Log.d(TAG, "Recovered source request context: host=${url.host}, source=${legacySource.name}")
        return newBuilder()
            .tag(ContentSource::class.java, legacySource)
            .tag(SourceRequestContext::class.java, SourceRequestContext.from(legacySource))
            .build()
    }

    /** A host that accepted a user agent with its clearance keeps getting that one, as Cloudflare binds the two. */
    private fun Request.withCloudflareUserAgent(): Request {
        val currentUserAgent = header("User-Agent")?.takeIf { it.isNotBlank() }
        val pinnedUserAgent = if (hasCloudflareClearance()) {
            acceptedCloudflareUserAgents[url.host.lowercase()]
        } else {
            null
        }
        val targetUserAgent = pinnedUserAgent ?: currentUserAgent ?: defaultUserAgentProvider()
        if (currentUserAgent == targetUserAgent) return this
        if (!pinnedUserAgent.isNullOrBlank() && !currentUserAgent.isNullOrBlank()) {
            Log.d(
                TAG,
                "Using accepted Cloudflare UA for host=${url.host}: " +
                    "from=${maskUserAgent(currentUserAgent)} to=${maskUserAgent(targetUserAgent)}",
            )
        }
        return newBuilder().header("User-Agent", targetUserAgent).build()
    }

    private fun Request.hasCloudflareClearance(): Boolean =
        cookieJar.loadForRequest(url).any { it.name == "cf_clearance" }

    private fun rememberAcceptedCloudflareUserAgent(request: Request, response: Response, protection: Int) {
        if (protection != CloudFlareHelper.PROTECTION_NOT_DETECTED || !response.isSuccessful) return
        if (!request.hasCloudflareClearance()) return
        val userAgent = request.header("User-Agent")?.takeIf { it.isNotBlank() } ?: return
        val host = request.url.host.lowercase()
        val previous = acceptedCloudflareUserAgents.put(host, userAgent)
        if (previous != userAgent) {
            Log.d(TAG, "Remembered accepted Cloudflare UA for host=$host: ${maskUserAgent(userAgent)}")
        }
    }

    private fun Request.replayableUtf8Body(): String? {
        val requestBody = body ?: return null
        if (requestBody.isDuplex() || requestBody.isOneShot() || requestBody.contentLength() > MAX_REPLAY_BODY_BYTES) {
            return null
        }
        return runCatching { Buffer().use { buffer -> requestBody.writeTo(buffer); buffer.readUtf8() } }.getOrNull()
    }

    private fun maskUserAgent(value: String?): String = value
        ?.replace(Regex("""Chrome/\d+(\.\d+)*"""), "Chrome/*")
        ?.take(140)
        ?: "<none>"

    private fun logCloudflareSetCookies(response: Response) {
        val headers = response.headers("Set-Cookie").filter { it.startsWith("cf_clearance=", ignoreCase = true) }
        if (headers.isEmpty()) return
        Log.i(
            TAG,
            "Set-Cookie cf_clearance: status=${response.code}, url=${response.request.url}, " +
                "cf-ray=${response.header("cf-ray")}, headers=${headers.joinToString(" | ", transform = ::summarizeSetCookie)}",
        )
    }

    private fun summarizeSetCookie(header: String): String = header
        .split(";")
        .mapIndexedNotNull { index, part ->
            val trimmed = part.trim()
            when {
                trimmed.isBlank() -> null
                index == 0 -> "${trimmed.substringBefore("=")}=${sensitiveValueFingerprint(trimmed.substringAfter("=", ""))}"
                trimmed.substringBefore("=").lowercase() in SAFE_COOKIE_ATTRIBUTES -> trimmed
                else -> null
            }
        }
        .joinToString(";")

    private fun throwUnresolvedChallenge(
        response: Response,
        request: Request,
        challengeUrl: String,
        host: String,
    ): Nothing {
        val clearance = getClearanceCookie(request)
        if (shouldSkipInteractiveAction(host, clearance)) {
            Log.w(TAG, "Skip interactive action for host=$host: repeated challenge with same cf_clearance")
            response.closeThrowing(
                CloudFlareBlockedException(
                    url = challengeUrl,
                    source = request.requestSource(),
                ),
            )
        }

        val source = request.tag(SourceRequestContext::class.java)?.source ?: request.tag(ContentSource::class.java)
        if (source == null) {
            Log.w(TAG, "Cloudflare challenge unresolved for host=$host and request has no ContentSource tag")
            response.closeThrowing(
                CloudFlareProtectedException(
                    url = challengeUrl,
                    source = null,
                    headers = request.headers,
                    method = request.method,
                    body = request.replayableUtf8Body(),
                    contentType = request.header("Content-Type"),
                    originalUrl = request.url.toString(),
                ),
            )
        }

        // Only after the dedicated background solver failed do we preserve Futon's existing
        // manual fallback contract for callers that can deliberately present interactive UI.
        response.closeThrowing(
            InteractiveActionRequiredException(
                source = source.toMangaSource(),
                url = challengeUrl,
            ),
        )
    }

    private fun shouldSkipInteractiveAction(host: String, clearance: String?): Boolean {
        if (clearance.isNullOrBlank()) return false
        val now = System.currentTimeMillis()
        val last = recentChallengeAttempts[host]
        if (last == null || now - last.timestampMs > INTERACTIVE_RETRY_WINDOW_MS || last.clearance != clearance) {
            recentChallengeAttempts[host] = ChallengeAttempt(clearance, now, 1)
            return false
        }
        val nextCount = last.count + 1
        recentChallengeAttempts[host] = last.copy(timestampMs = now, count = nextCount)
        return nextCount >= 2
    }

    private data class ChallengeAttempt(
        val clearance: String,
        val timestampMs: Long,
        val count: Int,
    )

    companion object {
        private const val TAG = "MihonNetwork"
        private const val LEGACY_RESOLVE_TIMEOUT_MS = 20_000L
        private const val INTERACTIVE_RETRY_WINDOW_MS = 10 * 60 * 1000L
        private const val MAX_REPLAY_BODY_BYTES = 2L * 1024L * 1024L
        private val SAFE_COOKIE_ATTRIBUTES = setOf("domain", "path", "max-age", "expires", "samesite", "secure", "httponly")
        private val acceptedCloudflareUserAgents = ConcurrentHashMap<String, String>()

        private fun protectionLabel(protection: Int): String = when (protection) {
            CloudFlareHelper.PROTECTION_CAPTCHA -> "captcha"
            CloudFlareHelper.PROTECTION_BLOCKED -> "blocked"
            else -> "none"
        }
        private val recentChallengeAttempts = ConcurrentHashMap<String, ChallengeAttempt>()
        private val MIHON_COMPAT_INTERCEPTOR_NAMES = setOf(
            "UncaughtExceptionInterceptor",
            "UserAgentInterceptor",
            "CloudflareInterceptor",
        )
    }
}
