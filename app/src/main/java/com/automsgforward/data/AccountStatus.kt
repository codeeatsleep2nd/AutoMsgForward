package com.automsgforward.data

import java.security.MessageDigest

/** What the settings screen tells the user about the saved Gmail account. */
sealed interface AccountStatus {
    data object NotSetUp : AccountStatus
    data class Paused(val gmail: String, val recipient: String) : AccountStatus
    data class Unverified(val gmail: String, val recipient: String) : AccountStatus
    data class Verified(val gmail: String, val recipient: String, val verifiedAtMillis: Long) : AccountStatus
    data class LoginFailed(val gmail: String, val recipient: String) : AccountStatus
}

/** Outcome of the last Gmail login (a test email or a real forward) for one account. */
data class LoginCheck(val fingerprint: String, val atMillis: Long, val succeeded: Boolean)

fun accountStatus(saved: ForwardSettings, lastCheck: LoginCheck?): AccountStatus {
    if (!saved.isConfigured) return AccountStatus.NotSetUp
    val gmail = saved.gmailAddress.trim()
    val recipient = saved.effectiveRecipient.trim()
    if (!saved.enabled) return AccountStatus.Paused(gmail, recipient)

    val check = lastCheck?.takeIf { it.fingerprint == accountFingerprint(saved) }
    return when {
        check == null -> AccountStatus.Unverified(gmail, recipient)
        check.succeeded -> AccountStatus.Verified(gmail, recipient, check.atMillis)
        else -> AccountStatus.LoginFailed(gmail, recipient)
    }
}

/**
 * Identifies a Gmail login (address + app password) so a check stops applying once either
 * changes. Hashed so the password itself is never stored in the clear.
 */
fun accountFingerprint(settings: ForwardSettings): String {
    val login = settings.gmailAddress.trim().lowercase() + "\n" + normalizeAppPassword(settings.appPassword)
    return MessageDigest.getInstance("SHA-256")
        .digest(login.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

/** True when the form's account fields differ from what is saved. Ignores the on/off switch. */
fun hasUnsavedChanges(saved: ForwardSettings, form: ForwardSettings): Boolean =
    saved.gmailAddress.trim() != form.gmailAddress.trim() ||
        normalizeAppPassword(saved.appPassword) != normalizeAppPassword(form.appPassword) ||
        saved.recipient.trim() != form.recipient.trim()
