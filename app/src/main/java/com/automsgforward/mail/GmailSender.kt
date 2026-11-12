package com.automsgforward.mail

import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * Sends mail through Gmail's SMTP relay using an App Password
 * (https://myaccount.google.com/apppasswords). Blocking — call off the main thread.
 */
class GmailSender(
    private val account: String,
    private val appPassword: String,
) {

    /** @throws javax.mail.AuthenticationFailedException when credentials are rejected. */
    fun send(to: String, content: EmailContent) {
        val session = Session.getInstance(smtpProperties(), object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(account, appPassword)
        })

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(account))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, true))
            setSubject(content.subject, CHARSET)
            setText(content.body, CHARSET)
        }
        Transport.send(message)
    }

    private fun smtpProperties() = Properties().apply {
        put("mail.smtp.host", SMTP_HOST)
        put("mail.smtp.port", SMTP_PORT)
        put("mail.smtp.auth", "true")
        put("mail.smtp.starttls.enable", "true")
        put("mail.smtp.starttls.required", "true")
        put("mail.smtp.ssl.checkserveridentity", "true")
        put("mail.smtp.connectiontimeout", TIMEOUT_MS)
        put("mail.smtp.timeout", TIMEOUT_MS)
        put("mail.smtp.writetimeout", TIMEOUT_MS)
    }

    private companion object {
        const val SMTP_HOST = "smtp.gmail.com"
        const val SMTP_PORT = "587"
        const val TIMEOUT_MS = "20000"
        const val CHARSET = "UTF-8"
    }
}
