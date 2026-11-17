// Adapted from Kototoro (Kototoro-app/Kototoro, Apache-2.0) at f32242d649471627d8906a523511bc69217897eb:
// core-cloudflare/src/test/kotlin/org/skepsun/kototoro/core/network/cloudflare/ClearanceSolvingTest.kt
// MockWebServer is replaced by a terminal interceptor, so the test needs no new dependency.
package io.github.landwarderer.futon.core.network.cloudflare

import io.github.landwarderer.futon.core.network.CloudflareHostCooldown
import io.github.landwarderer.futon.core.network.webview.CloudFlarePageState
import io.github.landwarderer.futon.core.network.webview.CloudflareSolveCoordinator
import kotlinx.coroutines.awaitCancellation
import okhttp3.Headers.Companion.headersOf
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ClearanceSolvingTest {

    /** What the "server" answers, in order; every request is counted. */
    private class FakeServer(private val answers: MutableList<Pair<Int, String>>) : Interceptor {
        val requests = AtomicInteger()

        override fun intercept(chain: Interceptor.Chain): Response {
            requests.incrementAndGet()
            val (code, body) = answers.removeAt(0)
            return Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("test")
                .header("Server", if (code == 403) "cloudflare" else "nginx")
                .body(body.toResponseBody())
                .build()
        }
    }

    private val url = "https://site.test/"

    private fun challenge() = 403 to "challenge"

    @Test
    fun mihonTriggerIsACloudflare403Or503OrTheExplicitChallengeMarker() {
        assertTrue(isMihonCloudflareChallenge(403, "cloudflare", null))
        assertTrue(isMihonCloudflareChallenge(503, "Cloudflare-Nginx", null))
        assertTrue(isMihonCloudflareChallenge(200, null, "Challenge"))
        assertFalse(isMihonCloudflareChallenge(403, "nginx", null))
        assertFalse(isMihonCloudflareChallenge(404, "cloudflare", null))
        assertFalse(isMihonCloudflareChallenge(200, "cloudflare", null))
    }

    @Test
    fun browserHeadersDropUnsafeNamesCookiesAndDuplicateValues() {
        val headers = headersOf(
            "User-Agent", "UA", "Referer", "https://a/", "Host", "a", "Content-Length", "3",
            "Proxy-Authorization", "x", "Connection", "Upgrade", "Cookie", "a=b", "Accept", "1", "Accept", "2",
        ).safeForBrowser()
        assertEquals(mapOf("User-Agent" to "UA", "Referer" to "https://a/", "Accept" to "1"), headers)
        assertTrue(isBrowserRequestHeaderSafe("Connection", "keep-alive"))
    }

    @Test
    fun eventRulesMatchMihonsWebViewSolver() {
        val page = "https://site.test/page"
        ClearanceSolveTracker(page).run {
            assertEquals(ClearanceSolveDecision.WAIT, onMainFrameHttpError(503))
            assertTrue(challengeFound)
            // The challenge page finishing is not a failure; the JS keeps running.
            assertEquals(ClearanceSolveDecision.WAIT, onPageFinished(page, hasNewClearance = false))
            assertEquals(ClearanceSolveDecision.SOLVED, onPageFinished(page, hasNewClearance = true))
        }
        ClearanceSolveTracker(page).run {
            assertEquals(ClearanceSolveDecision.FAILED, onPageFinished(page, hasNewClearance = false))
        }
        ClearanceSolveTracker(page).run {
            assertEquals(ClearanceSolveDecision.WAIT, onPageFinished("https://site.test/redirect", false))
            assertEquals(ClearanceSolveDecision.FAILED, onMainFrameHttpError(500))
        }
    }

    @Test
    fun polledPageStatesFailFastWhereAHiddenBrowserCannotPass() {
        ClearanceSolveTracker("u").run {
            assertEquals(ClearanceSolveDecision.WAIT, onPageState(CloudFlarePageState.LOADING, false, 0))
            assertEquals(ClearanceSolveDecision.WAIT, onPageState(CloudFlarePageState.MANAGED_CHALLENGE, false, 0))
            assertEquals(ClearanceSolveDecision.WAIT, onPageState(CloudFlarePageState.NORMAL, false, 0))
            assertEquals(ClearanceSolveDecision.SOLVED, onPageState(CloudFlarePageState.NORMAL, true, 0))
        }
        assertEquals(
            ClearanceSolveDecision.FAILED,
            ClearanceSolveTracker("u").onPageState(CloudFlarePageState.NORMAL, false, 0),
        )
        assertEquals(
            ClearanceSolveDecision.FAILED,
            ClearanceSolveTracker("u").onPageState(CloudFlarePageState.HARD_BLOCK, false, 0),
        )
        ClearanceSolveTracker("u").run {
            val grace = ClearanceSolveTracker.INTERACTIVE_GRACE_MS
            // Turnstile may show its widget briefly and pass by itself; only a lasting checkbox needs a human.
            assertEquals(
                ClearanceSolveDecision.WAIT,
                onPageState(CloudFlarePageState.INTERACTIVE_CHALLENGE, false, 1000),
            )
            assertEquals(ClearanceSolveDecision.WAIT, onPageState(CloudFlarePageState.MANAGED_CHALLENGE, false, 2000))
            assertEquals(
                ClearanceSolveDecision.WAIT,
                onPageState(CloudFlarePageState.INTERACTIVE_CHALLENGE, false, 3000),
            )
            assertEquals(
                ClearanceSolveDecision.WAIT,
                onPageState(CloudFlarePageState.INTERACTIVE_CHALLENGE, false, 3000 + grace - 1),
            )
            assertEquals(
                ClearanceSolveDecision.FAILED,
                onPageState(CloudFlarePageState.INTERACTIVE_CHALLENGE, false, 3000 + grace),
            )
        }
    }

    private fun clientWith(
        server: FakeServer,
        solver: ClearanceSolver,
        coordinator: CloudflareSolveCoordinator? = CloudflareSolveCoordinator(CloudflareHostCooldown()),
    ) = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (!response.isMihonCloudflareChallenge()) return@addInterceptor response
            response.close()
            chain.solveClearanceAndRetry(request, solver, coordinator)
                ?: response.newBuilder().header("X-Fallback", "unsolved").build()
        }
        .addInterceptor(server)
        .build()

    @Test
    fun solvedChallengeRetriesTheRequestOnce() {
        val server = FakeServer(mutableListOf(challenge(), 200 to "ok"))
        val solves = AtomicInteger()
        val client = clientWith(server, ClearanceSolver { solves.incrementAndGet(); true })
        client.newCall(Request.Builder().url(url).build()).execute().use {
            assertEquals(200, it.code)
            assertEquals("ok", it.body.string())
        }
        assertEquals(1, solves.get())
        assertEquals(2, server.requests.get())
    }

    @Test
    fun failedSolveReturnsNullToTheCallerAndCoolsTheHost() {
        val cooldown = CloudflareHostCooldown()
        val coordinator = CloudflareSolveCoordinator(cooldown)
        val solves = AtomicInteger()
        val server = FakeServer(mutableListOf(challenge(), challenge()))
        val client = clientWith(server, ClearanceSolver { solves.incrementAndGet(); false }, coordinator)
        repeat(2) {
            client.newCall(Request.Builder().url(url).build()).execute().use {
                assertEquals(403, it.code)
                assertEquals("unsolved", it.header("X-Fallback"))
            }
        }
        // The second request hits the cooldown and starts no browser.
        assertEquals(1, solves.get())
        assertTrue(cooldown.isInCooldown("site.test"))
    }

    @Test(timeout = 10_000)
    fun aCancelledCallerStopsWaitingForTheSolve() {
        val cancelled = AtomicBoolean()
        val result = AtomicInteger(-1)
        val server = FakeServer(mutableListOf(challenge()))
        val interceptor = Interceptor { chain ->
            val response = chain.proceed(chain.request())
            response.close()
            val thread = Thread { Thread.sleep(200); cancelled.set(true) }.apply { start() }
            val retried = chain.solveClearanceAndRetry(
                chain.request(),
                ClearanceSolver { awaitCancellation() },
                CloudflareSolveCoordinator(CloudflareHostCooldown()),
            ) { cancelled.get() }
            thread.join()
            result.set(if (retried == null) 0 else 1)
            response
        }
        OkHttpClient.Builder().addInterceptor(interceptor).addInterceptor(server).build()
            .newCall(Request.Builder().url(url).build()).execute().close()
        assertEquals(0, result.get())
        assertEquals(1, server.requests.get())
    }

    @Test
    fun withoutACoordinatorTheSolverRunsDirectly() {
        val server = FakeServer(mutableListOf(challenge(), 200 to "ok"))
        val client = clientWith(server, ClearanceSolver { true }, coordinator = null)
        client.newCall(Request.Builder().url(url).build()).execute().use {
            assertEquals(200, it.code)
        }
    }

    @Test
    fun oneShotRequestBodiesAreNotAutomaticallyReplayed() {
        val server = FakeServer(mutableListOf(challenge(), 200 to "must not be sent"))
        val solves = AtomicInteger()
        val body = object : okhttp3.RequestBody() {
            override fun contentType(): okhttp3.MediaType? = null
            override fun isOneShot() = true
            override fun writeTo(sink: okio.BufferedSink) { sink.writeUtf8("one-shot") }
        }
        val client = clientWith(server, ClearanceSolver { solves.incrementAndGet(); true })
        client.newCall(Request.Builder().url(url).post(body).build()).execute().close()
        assertEquals(1, server.requests.get())
        assertEquals(0, solves.get())
    }
}
