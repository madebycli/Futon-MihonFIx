package io.github.landwarderer.futon.core.network.cookies

import okhttp3.HttpUrl
import okhttp3.Request

/** Diagnostics never include URL paths, credentials, query values, fragments or raw sensitive headers. */
internal fun HttpUrl.diagnosticIdentity(): String =
    "$scheme://$host:$port urlFingerprint=${sensitiveValueFingerprint(toString())}"

internal fun Request.diagnosticHeaders(): String =
    "ua=${sensitiveValueFingerprint(header("User-Agent"))}, " +
        "referer=${sensitiveValueFingerprint(header("Referer"))}, " +
        "origin=${sensitiveValueFingerprint(header("Origin"))}"

/** Cookie values and attribute values may both contain credentials; only names and fingerprints are logged. */
internal fun diagnosticSetCookie(header: String): String {
    val pieces = header.split(';')
    val name = pieces.first().substringBefore('=').trim()
    val value = pieces.first().substringAfter('=', "")
    val attributes = pieces.drop(1).map { it.substringBefore('=').trim().lowercase() }
        .filter { it in setOf("domain", "path", "max-age", "expires", "samesite", "secure", "httponly") }
    return "$name=${sensitiveValueFingerprint(value)} attributes=${attributes.joinToString(",")}"
}
