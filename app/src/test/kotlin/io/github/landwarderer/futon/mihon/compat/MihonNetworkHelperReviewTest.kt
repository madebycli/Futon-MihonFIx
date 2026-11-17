package io.github.landwarderer.futon.mihon.compat

import android.os.Looper
import android.util.Log
import io.github.landwarderer.futon.core.network.CloudflareHostCooldown
import io.github.landwarderer.futon.core.network.cloudflare.ClearanceSolver
import io.github.landwarderer.futon.core.network.webview.CloudflareSolveCoordinator
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import java.util.concurrent.atomic.AtomicInteger

class MihonNetworkHelperReviewTest {
    @Test
    fun actualDiagnosticInterceptorDoesNotLogSyntheticSecrets() = mockStatic(Log::class.java).use { logs ->
        val messages = mutableListOf<String>()
        logs.`when`<Int> { Log.d(anyString(), anyString()) }.thenAnswer { messages += it.getArgument<String>(1); 0 }
        logs.`when`<Int> { Log.i(anyString(), anyString()) }.thenAnswer { messages += it.getArgument<String>(1); 0 }
        logs.`when`<Int> { Log.w(anyString(), anyString()) }.thenAnswer { messages += it.getArgument<String>(1); 0 }
        val token = "SYNTHETIC_SECRET_4815"
        val cookies = object : CookieJar {
            override fun loadForRequest(url: HttpUrl) = listOf(Cookie.Builder()
                .name("cf_clearance").value(token).domain(url.host).build())
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
        }
        val helper = MihonNetworkHelper(OkHttpClient(), cookies)
        for (status in listOf(200, 500)) {
            val client = helper.client.newBuilder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("fixture")
                    .header("Set-Cookie", "cf_clearance=$token; Path=/account/$token; HttpOnly")
                    .header("Server", "server-$token")
                    .body("BODY-$token".toResponseBody("application/json; fixture=$token".toMediaType())).build()
            }.build()
            val request = Request.Builder().url("https://user:$token@diagnostics.test/private/$token?q=$token#$token")
                .header("User-Agent", token).header("Referer", "https://diagnostics.test/?q=$token")
                .header("Origin", "https://$token.example.test").header("Authorization", "Bearer $token").build()
            client.newCall(request).execute().close()
        }
        assertTrue(messages.isNotEmpty())
        assertFalse(messages.joinToString("\n"), messages.any { it.contains(token) })
    }

    @Test
    fun actualMihonClientRetriesAChallengeExactlyOnceWithAnInjectedSolver() =
        mockStatic(Log::class.java).use {
            mockStatic(Looper::class.java).use { loopers ->
                loopers.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mock(Looper::class.java))
                val requests = AtomicInteger()
                val solves = AtomicInteger()
                val helper = MihonNetworkHelper(
                    OkHttpClient(), CookieJar.NO_COOKIES,
                    clearanceSolver = ClearanceSolver { solves.incrementAndGet(); true },
                    solveCoordinator = CloudflareSolveCoordinator(CloudflareHostCooldown()),
                )
                val client = helper.client.newBuilder().addInterceptor { chain ->
                    val first = requests.incrementAndGet() == 1
                    Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                        .code(if (first) 403 else 200).message("fixture").header("Server", "cloudflare")
                        .apply { if (first) header("cf-mitigated", "challenge") }
                        .body((if (first) "challenge" else "ok").toResponseBody()).build()
                }.build()
                client.newCall(Request.Builder().url("https://retry.test/").build()).execute().use { response ->
                    assertEquals(200, response.code)
                }
                assertEquals(2, requests.get())
                assertEquals(1, solves.get())
            }
        }
}
