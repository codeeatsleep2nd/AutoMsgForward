package com.automsgforward.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountStatusTest {

    private val account = ForwardSettings(
        enabled = true,
        gmailAddress = "me@gmail.com",
        appPassword = "abcdefghijklmnop",
        recipient = "work@example.com",
    )

    private fun checked(settings: ForwardSettings, ok: Boolean) =
        LoginCheck(accountFingerprint(settings), atMillis = 1_000L, succeeded = ok)

    @Test
    fun `incomplete account is not set up`() {
        assertEquals(AccountStatus.NotSetUp, accountStatus(ForwardSettings(), lastCheck = null))
        assertEquals(AccountStatus.NotSetUp, accountStatus(account.copy(appPassword = ""), lastCheck = null))
    }

    @Test
    fun `disabled forwarding is paused even when the login works`() {
        val status = accountStatus(account.copy(enabled = false), checked(account, ok = true))
        assertEquals(AccountStatus.Paused("me@gmail.com", "work@example.com"), status)
    }

    @Test
    fun `account never checked is unverified`() {
        assertEquals(
            AccountStatus.Unverified("me@gmail.com", "work@example.com"),
            accountStatus(account, lastCheck = null),
        )
    }

    @Test
    fun `successful check marks the account verified`() {
        assertEquals(
            AccountStatus.Verified("me@gmail.com", "work@example.com", verifiedAtMillis = 1_000L),
            accountStatus(account, checked(account, ok = true)),
        )
    }

    @Test
    fun `rejected login is reported`() {
        assertEquals(
            AccountStatus.LoginFailed("me@gmail.com", "work@example.com"),
            accountStatus(account, checked(account, ok = false)),
        )
    }

    @Test
    fun `check for a different password does not apply`() {
        val oldCheck = checked(account.copy(appPassword = "zzzzzzzzzzzzzzzz"), ok = true)
        assertEquals(
            AccountStatus.Unverified("me@gmail.com", "work@example.com"),
            accountStatus(account, oldCheck),
        )
    }

    @Test
    fun `blank recipient is shown as the gmail address`() {
        val status = accountStatus(account.copy(recipient = ""), lastCheck = null)
        assertEquals(AccountStatus.Unverified("me@gmail.com", "me@gmail.com"), status)
    }

    @Test
    fun `fingerprint ignores case, spacing and recipient but not the password`() {
        val same = account.copy(gmailAddress = " Me@Gmail.com ", appPassword = "abcd efgh ijkl mnop", recipient = "")
        assertEquals(accountFingerprint(account), accountFingerprint(same))
        assertNotEquals(accountFingerprint(account), accountFingerprint(account.copy(appPassword = "x")))
        assertFalse(accountFingerprint(account).contains("abcdefghijklmnop"))
    }

    @Test
    fun `unsaved changes compare account fields after normalising`() {
        assertFalse(hasUnsavedChanges(account, account.copy(gmailAddress = " me@gmail.com", appPassword = "abcd efgh ijkl mnop")))
        assertFalse(hasUnsavedChanges(account, account.copy(enabled = false)))
        assertTrue(hasUnsavedChanges(account, account.copy(recipient = "")))
        assertTrue(hasUnsavedChanges(account, account.copy(appPassword = "newpassword12345")))
    }
}
