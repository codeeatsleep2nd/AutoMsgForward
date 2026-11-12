package com.automsgforward.data

data class ForwardSettings(
    val enabled: Boolean = false,
    val gmailAddress: String = "",
    val appPassword: String = "",
    /** Destination address; blank means "send to the Gmail account itself". */
    val recipient: String = "",
) {
    val effectiveRecipient: String get() = recipient.ifBlank { gmailAddress }

    val isConfigured: Boolean
        get() = isValidEmail(gmailAddress) &&
            appPassword.isNotBlank() &&
            isValidEmail(effectiveRecipient)
}

private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

fun isValidEmail(value: String): Boolean = EMAIL_REGEX.matches(value.trim())

/** Google displays app passwords as "abcd efgh ijkl mnop"; SMTP wants them without spaces. */
fun normalizeAppPassword(raw: String): String = raw.filterNot { it.isWhitespace() }
