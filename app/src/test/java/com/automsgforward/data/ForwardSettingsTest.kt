package com.automsgforward.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardSettingsTest {

    private val valid = ForwardSettings(
        enabled = true,
        gmailAddress = "me@gmail.com",
        appPassword = "abcdefghijklmnop",
    )

    @Test
    fun `blank recipient falls back to gmail address`() {
        assertEquals("me@gmail.com", valid.effectiveRecipient)
        assertEquals("other@example.com", valid.copy(recipient = "other@example.com").effectiveRecipient)
    }

    @Test
    fun `configured only with valid addresses and a password`() {
        assertTrue(valid.isConfigured)
        assertFalse(valid.copy(appPassword = "").isConfigured)
        assertFalse(valid.copy(gmailAddress = "not-an-email").isConfigured)
        assertFalse(valid.copy(recipient = "bad@").isConfigured)
    }

    @Test
    fun `app password spaces are stripped`() {
        assertEquals("abcdefghijklmnop", normalizeAppPassword(" abcd efgh ijkl mnop "))
    }
}
