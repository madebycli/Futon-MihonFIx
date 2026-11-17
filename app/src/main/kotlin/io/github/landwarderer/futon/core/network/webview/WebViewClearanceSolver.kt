// Ported and adapted from Kototoro (Kototoro-app/Kototoro, Apache-2.0) at
// f32242d649471627d8906a523511bc69217897eb: app/.../core/network/webview/WebViewClearanceSolver.kt
// (earlier adaptation: f4f37a5b7290da05c10b9325912f2a37ebeff0f9).
package io.github.landwarderer.futon.core.network.webview

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.core.util.Predicate
import io.github.landwarderer.futon.core.network.cloudflare.CLEARANCE_COOKIE_NAMES
import io.github.landwarderer.futon.core.network.cloudflare.CLEARANCE_SOLVE_TIMEOUT_MS
import io.github.landwarderer.futon.core.network.cloudflare.ClearanceSolveDecision
import io.github.landwarderer.futon.core.network.cloudflare.ClearanceSolveTracker
import io.github.landwarderer.futon.core.network.cloudflare.ClearanceSolver
import io.github.landwarderer.futon.core.network.cloudflare.safeForBrowser
import io.github.landwarderer.futon.core.network.cookies.MutableCookieJar
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.CookieJar
import okhttp3.Request
import org.koitharu.kotatsu.parsers.network.CloudFlareHelper
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Fresh off-screen Cloudflare solver used by the Mihon extension network stack (Mihon's own approach).
 *
 * The request URL and its browser-safe headers are loaded in a new WebView that uses the same user agent as
 * OkHttp and shares Futon's Android cookie store. Cloudflare runs its challenge in that WebView; once a new
 * `cf_clearance` is written the caller retries the original request. No Activity is opened by this class.
 *
 * [solve] is cancellable: when the last caller waiting for a host goes away, the coroutine is cancelled, which
 * stops and destroys the WebView. The WebView is created, stopped and destroyed on the main thread.
 */
class WebViewClearanceSolver(
    context: Context,
    private val cookieJar: CookieJar,
) : ClearanceSolver {
    private val appContext = context.applicationContext
    private val executor = ContextCompat.getMainExecutor(appContext)

    /**
     * Solves the challenge of [request] in an off-screen WebView. Returns true only if a `cf_clearance` that
     * differs from the one before the solve is stored. The old clearance is removed first, so a new cookie is the
     * success signal. A timeout ([CLEARANCE_SOLVE_TIMEOUT_MS]) or cancellation returns false and destroys the WebView.
     */
    override suspend fun solve(request: Request): Boolean {
        val url = request.url.toString()
        val oldClearance = CloudFlareHelper.getClearanceCookie(cookieJar, url)
        removeClearance(request)
        val headers = request.headers.safeForBrowser()

        return withTimeoutOrNull(CLEARANCE_SOLVE_TIMEOUT_MS) {
            var session: SolveSession? = null
            try {
                suspendCancellableCoroutine { continuation ->
                    val created = SolveSession(url, oldClearance, request, headers) { result ->
                        if (continuation.isActive) continuation.resume(result)
                    }
                    session = created
                    continuation.invokeOnCancellation { created.destroy() }
                    created.start()
                }
            } finally {
                session?.destroy()
            }
        } ?: false
    }

    private fun removeClearance(request: Request) {
        (cookieJar as? MutableCookieJar)?.removeCookies(
            request.url,
            Predicate { it.name in CLEARANCE_COOKIE_NAMES },
        )
    }

    private fun createWebView(request: Request): WebView = WebView(appContext).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        // Same user agent as the request: Cloudflare binds its cookie to it.
        request.header("User-Agent")?.let { settings.userAgentString = it }
    }

    /**
     * Owns one off-screen WebView solve: create and load on the main thread, settle exactly once, destroy at most
     * once. [destroy] is safe to call from any thread, before or after the WebView was created.
     */
    private inner class SolveSession(
        private val url: String,
        private val oldClearance: String?,
        private val request: Request,
        private val headers: Map<String, String>,
        private val onSettled: (Boolean) -> Unit,
    ) {
        @Volatile
        private var webView: WebView? = null

        /** Mihon's solve rules, shared with the other Cloudflare solvers. */
        private val tracker = ClearanceSolveTracker(url)

        private val settled = AtomicBoolean(false)
        private val destroyed = AtomicBoolean(false)

        fun start() {
            executor.execute {
                // Cancelled before the WebView was created.
                if (destroyed.get()) return@execute
                val created = try {
                    createWebView(request)
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "createWebView failed host=${request.url.host} type=${e.javaClass.simpleName}")
                    settle(false)
                    return@execute
                }
                webView = created
                if (destroyed.get()) {
                    // destroy() raced with the creation.
                    destroyWebView(created)
                    return@execute
                }
                created.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, finishedUrl: String) {
                        decide(tracker.onPageFinished(finishedUrl, hasNewClearance()))
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?,
                    ) {
                        if (request?.isForMainFrame == true) {
                            // 403/503 is the challenge page: wait for its JS. Any other error cannot be solved here.
                            decide(tracker.onMainFrameHttpError(errorResponse?.statusCode ?: 0))
                        }
                    }
                }
                created.loadUrl(url, headers)
            }
        }

        private fun hasNewClearance(): Boolean {
            val current = CloudFlareHelper.getClearanceCookie(cookieJar, url)
            return current != null && current != oldClearance
        }

        fun destroy() {
            if (!destroyed.compareAndSet(false, true)) return
            executor.execute { webView?.let(::destroyWebView) }
        }

        private fun decide(decision: ClearanceSolveDecision) {
            when (decision) {
                ClearanceSolveDecision.SOLVED -> settle(true)
                ClearanceSolveDecision.FAILED -> settle(false)
                ClearanceSolveDecision.WAIT -> Unit
            }
        }

        private fun settle(result: Boolean) {
            if (settled.compareAndSet(false, true)) onSettled(result)
        }
    }

    private fun destroyWebView(webView: WebView) {
        runCatching {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
    }

    private companion object {
        const val TAG = "WebViewClearanceSolver"
    }
}
