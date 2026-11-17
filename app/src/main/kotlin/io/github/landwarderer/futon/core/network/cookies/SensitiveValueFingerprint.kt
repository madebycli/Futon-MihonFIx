// Ported from Kototoro (Kototoro-app/Kototoro, Apache-2.0) at f32242d649471627d8906a523511bc69217897eb:
// app/src/main/kotlin/org/skepsun/kototoro/core/network/cookies/SensitiveValueFingerprint.kt
package io.github.landwarderer.futon.core.network.cookies

import java.security.MessageDigest

/** A short, non-reversible description of a secret (cookie, user agent) that is safe to write to a log. */
internal fun sensitiveValueFingerprint(value: String?): String {
    if (value.isNullOrEmpty()) return "<empty>"
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .take(FINGERPRINT_BYTES)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    return "sha256=$digest,length=${value.length}"
}

private const val FINGERPRINT_BYTES = 6
