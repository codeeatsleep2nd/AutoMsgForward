package com.automsgforward.mail

import com.automsgforward.sms.IncomingSms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class EmailContentTest {

    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun `subject includes sender and short body preview`() {
        val sms = IncomingSms("+15550001", "Your code is 123456", 0)

        val email = buildForwardEmail(sms, "Pixel 9", utc)

        assertEquals("SMS from +15550001: Your code is 123456", email.subject)
    }

    @Test
    fun `long or multiline body is collapsed and truncated in subject`() {
        val sms = IncomingSms("Bank", "Line one\n\nline two " + "x".repeat(100), 0)

        val email = buildForwardEmail(sms, "Pixel 9", utc)

        assertTrue(email.subject.startsWith("SMS from Bank: Line one line two "))
        assertTrue(email.subject.endsWith("…"))
    }

    @Test
    fun `body contains metadata and full original text`() {
        val text = "Line one\nline two"
        val sms = IncomingSms("Bank", text, 0)

        val email = buildForwardEmail(sms, "Pixel 9", utc)

        assertTrue(email.body.contains("From: Bank"))
        assertTrue(email.body.contains("Received: 1970-01-01 00:00:00 UTC"))
        assertTrue(email.body.contains("Device: Pixel 9"))
        assertTrue(email.body.endsWith(text))
    }
}
