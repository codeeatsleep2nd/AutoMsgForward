package com.automsgforward.mail

import com.automsgforward.sms.IncomingSms
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class EmailContent(val subject: String, val body: String)

private const val SUBJECT_PREVIEW_CHARS = 40

fun buildForwardEmail(
    sms: IncomingSms,
    deviceName: String,
    timeZone: TimeZone = TimeZone.getDefault(),
): EmailContent {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz", Locale.US).apply {
        this.timeZone = timeZone
    }
    val received = formatter.format(Date(sms.timestampMillis))
    val preview = sms.body.replace(Regex("\\s+"), " ").trim().let {
        if (it.length > SUBJECT_PREVIEW_CHARS) it.take(SUBJECT_PREVIEW_CHARS).trimEnd() + "…" else it
    }

    return EmailContent(
        subject = "SMS from ${sms.sender}: $preview",
        body = buildString {
            appendLine("From: ${sms.sender}")
            appendLine("Received: $received")
            appendLine("Device: $deviceName")
            appendLine()
            append(sms.body)
        },
    )
}
