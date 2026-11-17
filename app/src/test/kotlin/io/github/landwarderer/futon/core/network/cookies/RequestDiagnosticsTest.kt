package io.github.landwarderer.futon.core.network.cookies

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test

class RequestDiagnosticsTest {
    private val token = "SYNTHETIC_SECRET_4815"

    @Test
    fun urlsNeverExposeCredentialsPathQueryOrFragmentValues() {
        val value = "https://user:$token@example.test/private/$token?q=$token#$token".toHttpUrl().diagnosticIdentity()
        assertTrue(value.contains("example.test"))
        assertTrue(value.contains("sha256="))
        assertFalse(value.contains(token))
        assertFalse(value.contains("/private/"))
        assertFalse(value.contains("user:"))
    }

    @Test
    fun sensitiveHeadersAreOnlyFingerprints() {
        val request = Request.Builder().url("https://example.test")
            .header("User-Agent", token).header("Referer", "https://example.test/?q=$token")
            .header("Origin", "https://$token.example.test").header("Authorization", "Bearer $token")
            .header("Cookie", "session=$token").build()
        assertFalse(request.diagnosticHeaders().contains(token))
        assertTrue(request.diagnosticHeaders().contains("sha256="))
    }

    @Test
    fun setCookieAttributeValuesAreNotConsideredSafeLogData() {
        val result = diagnosticSetCookie("cf_clearance=$token; Path=/account/$token; Domain=$token.test; Secure; HttpOnly")
        assertTrue(result.contains("cf_clearance=sha256="))
        assertTrue(result.contains("path"))
        assertFalse(result.contains(token))
        assertFalse(result.contains("/account/"))
    }
}
