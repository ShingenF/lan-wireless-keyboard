package com.local.virtualkeyboard.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CertificatePinPolicyTest {
    @Test
    fun `first pairing accepts the certificate for challenge verification`() {
        assertTrue(CertificatePinPolicy.accepts(expected = "", actual = "A1B2"))
    }

    @Test
    fun `saved pin accepts the same certificate case-insensitively`() {
        assertTrue(CertificatePinPolicy.accepts(expected = "a1b2", actual = "A1B2"))
    }

    @Test
    fun `saved pin rejects a changed certificate`() {
        assertFalse(CertificatePinPolicy.accepts(expected = "A1B2", actual = "FFFF"))
    }
}
