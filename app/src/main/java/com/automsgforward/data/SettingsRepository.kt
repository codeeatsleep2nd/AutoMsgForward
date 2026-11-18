package com.automsgforward.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.text.DateFormat
import java.util.Date

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): ForwardSettings = ForwardSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        gmailAddress = prefs.getString(KEY_GMAIL, "").orEmpty(),
        appPassword = prefs.getString(KEY_PASSWORD, null)?.let(::decryptOrEmpty).orEmpty(),
        recipient = prefs.getString(KEY_RECIPIENT, "").orEmpty(),
    )

    fun save(settings: ForwardSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putString(KEY_GMAIL, settings.gmailAddress.trim())
            .putString(KEY_PASSWORD, SecretCipher.encrypt(normalizeAppPassword(settings.appPassword)))
            .putString(KEY_RECIPIENT, settings.recipient.trim())
            .apply()
    }

    fun recordStatus(message: String) {
        val stamped = "${DateFormat.getDateTimeInstance().format(Date())} — $message"
        prefs.edit().putString(KEY_STATUS, stamped).apply()
    }

    /** Inbox `date` of the newest message the catch-up sweep has looked at; null until first sweep. */
    fun catchUpWatermark(): Long? =
        if (prefs.contains(KEY_WATERMARK)) prefs.getLong(KEY_WATERMARK, 0) else null

    fun setCatchUpWatermark(millis: Long) {
        prefs.edit().putLong(KEY_WATERMARK, millis).apply()
    }

    /** Keys (see [com.automsgforward.sms.forwardKey]) of recently forwarded messages. */
    fun forwardedKeys(): Set<String> =
        prefs.getString(KEY_FORWARDED, null)?.split(KEY_SEPARATOR)?.filter { it.isNotEmpty() }?.toSet()
            ?: emptySet()

    @Synchronized
    fun markForwarded(key: String) {
        val current = prefs.getString(KEY_FORWARDED, null)?.split(KEY_SEPARATOR).orEmpty()
        val updated = (current.filter { it.isNotEmpty() && it != key } + key).takeLast(MAX_FORWARDED_KEYS)
        prefs.edit().putString(KEY_FORWARDED, updated.joinToString(KEY_SEPARATOR)).commit()
    }

    fun statusFlow(): Flow<String> =
        observe(setOf(KEY_STATUS)) { prefs.getString(KEY_STATUS, NO_STATUS) ?: NO_STATUS }

    /** Remember whether Gmail accepted [settings]' login, for the account status card. */
    fun recordLoginCheck(settings: ForwardSettings, succeeded: Boolean) {
        prefs.edit()
            .putString(KEY_CHECK_FINGERPRINT, accountFingerprint(settings))
            .putLong(KEY_CHECK_AT, System.currentTimeMillis())
            .putBoolean(KEY_CHECK_OK, succeeded)
            .apply()
    }

    fun lastLoginCheck(): LoginCheck? {
        val fingerprint = prefs.getString(KEY_CHECK_FINGERPRINT, null) ?: return null
        return LoginCheck(fingerprint, prefs.getLong(KEY_CHECK_AT, 0), prefs.getBoolean(KEY_CHECK_OK, false))
    }

    fun lastLoginCheckFlow(): Flow<LoginCheck?> =
        observe(setOf(KEY_CHECK_FINGERPRINT, KEY_CHECK_AT, KEY_CHECK_OK)) { lastLoginCheck() }

    /** Emits [read] now and again whenever one of [keys] changes. */
    private fun <T> observe(keys: Set<String>, read: () -> T): Flow<T> = callbackFlow {
        trySend(read())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in keys) trySend(read())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun decryptOrEmpty(blob: String): String =
        SecretCipher.decrypt(blob) ?: run {
            Log.w(TAG, "Stored app password could not be decrypted; please re-enter it")
            ""
        }

    private companion object {
        const val TAG = "SettingsRepository"
        const val PREFS_NAME = "forward_settings"
        const val KEY_ENABLED = "enabled"
        const val KEY_GMAIL = "gmail_address"
        const val KEY_PASSWORD = "app_password_enc"
        const val KEY_RECIPIENT = "recipient"
        const val KEY_STATUS = "last_status"
        const val KEY_WATERMARK = "catch_up_watermark"
        const val KEY_FORWARDED = "forwarded_keys"
        const val KEY_SEPARATOR = "\n"
        const val MAX_FORWARDED_KEYS = 300
        const val KEY_CHECK_FINGERPRINT = "login_check_fingerprint"
        const val KEY_CHECK_AT = "login_check_at"
        const val KEY_CHECK_OK = "login_check_ok"
        const val NO_STATUS = "Nothing forwarded yet"
    }
}
