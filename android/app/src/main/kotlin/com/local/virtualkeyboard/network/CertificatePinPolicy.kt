package com.local.virtualkeyboard.network

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

internal object CertificatePinPolicy {
    fun accepts(expected: String, actual: String): Boolean {
        if (expected.isEmpty()) return true
        val normalizedExpected = expected.uppercase(Locale.US).toByteArray(StandardCharsets.US_ASCII)
        val normalizedActual = actual.uppercase(Locale.US).toByteArray(StandardCharsets.US_ASCII)
        return MessageDigest.isEqual(normalizedExpected, normalizedActual)
    }
}
