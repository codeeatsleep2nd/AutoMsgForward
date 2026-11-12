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

    fun statusFlow(): Flow<String> = callbackFlow {
        trySend(prefs.getString(KEY_STATUS, NO_STATUS) ?: NO_STATUS)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == KEY_STATUS) trySend(p.getString(KEY_STATUS, NO_STATUS) ?: NO_STATUS)
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
        const val NO_STATUS = "Nothing forwarded yet"
    }
}
