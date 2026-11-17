// Ported from Kototoro (Kototoro-app/Kototoro, Apache-2.0) at f32242d649471627d8906a523511bc69217897eb:
// app/src/main/kotlin/org/skepsun/kototoro/core/network/UserAgentProvider.kt
package io.github.landwarderer.futon.core.network

import android.content.Context
import android.webkit.WebSettings
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug

/**
 * The device WebView's own user agent, without the markers that identify an embedded WebView. Cloudflare binds its
 * clearance to the user agent and checks it against the browser fingerprint, so the Mihon network stack uses the
 * same string the off-screen WebView solver presents.
 */
object UserAgentProvider {
    @Volatile
    private var cachedUserAgent: String? = null

    @JvmStatic
    fun get(context: Context): String {
        cachedUserAgent?.let { return it }
        val rawUA = try {
            WebSettings.getDefaultUserAgent(context)
        } catch (e: Exception) {
            e.printStackTraceDebug()
            // Fallback Chrome User Agent if WebView is not ready/available
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.203 Mobile Safari/537.36"
        }
        val sanitized = sanitize(rawUA)
        cachedUserAgent = sanitized
        return sanitized
    }

    @JvmStatic
    fun sanitize(userAgent: String): String = userAgent
        .replace(Regex("; wv\\b", RegexOption.IGNORE_CASE), "")
        .replace(Regex("Version/\\d+\\.\\d+\\s?", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s*\\[[a-zA-Z0-9._]+\\]\\s*$", RegexOption.IGNORE_CASE), "")
        .trim()
}
