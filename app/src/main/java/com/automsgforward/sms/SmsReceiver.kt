package com.automsgforward.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.automsgforward.data.SettingsRepository
import com.automsgforward.mail.ForwardWorker
import com.automsgforward.service.KeepAliveService

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val settings = SettingsRepository(context).load()
        if (!settings.enabled) return

        val parts = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }
            .onFailure { Log.e(TAG, "Unable to parse SMS intent", it) }
            .getOrNull()
            .orEmpty()
            .filterNotNull()
            .map { sms ->
                SmsPart(
                    sender = sms.displayOriginatingAddress ?: sms.originatingAddress ?: UNKNOWN_SENDER,
                    body = sms.displayMessageBody ?: sms.messageBody.orEmpty(),
                    timestampMillis = sms.timestampMillis,
                )
            }

        assembleMessages(parts).forEach { ForwardWorker.enqueue(context, it) }
        // Revive the keep-alive service if the OS killed it; harmless if already running.
        KeepAliveService.sync(context)
    }

    private companion object {
        const val TAG = "SmsReceiver"
        const val UNKNOWN_SENDER = "Unknown"
    }
}
