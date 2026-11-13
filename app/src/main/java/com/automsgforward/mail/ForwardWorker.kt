package com.automsgforward.mail

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.automsgforward.data.SettingsRepository
import com.automsgforward.sms.IncomingSms
import com.automsgforward.sms.forwardKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.mail.AuthenticationFailedException

class ForwardWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = SettingsRepository(applicationContext)
        val settings = repo.load()
        val sms = inputData.toSms() ?: return Result.failure()
        val key = inputData.getString(KEY_FORWARD_KEY) ?: forwardKey(sms)
        // The broadcast and the inbox catch-up can both enqueue the same text.
        if (key in repo.forwardedKeys()) return Result.success()

        if (!settings.isConfigured) {
            repo.recordStatus("Not forwarded (${sms.sender}): Gmail account not configured")
            return Result.failure()
        }

        val email = buildForwardEmail(sms, deviceName = "${Build.MANUFACTURER} ${Build.MODEL}")
        return try {
            withContext(Dispatchers.IO) {
                GmailSender(settings.gmailAddress, settings.appPassword)
                    .send(settings.effectiveRecipient, email)
            }
            repo.markForwarded(key)
            repo.recordStatus("Forwarded SMS from ${sms.sender}")
            Result.success()
        } catch (e: AuthenticationFailedException) {
            Log.e(TAG, "Gmail rejected credentials", e)
            repo.recordStatus("Failed (${sms.sender}): Gmail rejected the app password")
            Result.failure()
        } catch (e: Exception) {
            Log.w(TAG, "Send failed on attempt ${runAttemptCount + 1}", e)
            if (runAttemptCount + 1 >= MAX_ATTEMPTS) {
                repo.recordStatus("Failed (${sms.sender}) after $MAX_ATTEMPTS attempts: ${e.message}")
                Result.failure()
            } else {
                repo.recordStatus("Retrying (${sms.sender}): ${e.message}")
                Result.retry()
            }
        }
    }

    companion object {
        private const val TAG = "ForwardWorker"
        private const val MAX_ATTEMPTS = 10
        private const val BACKOFF_SECONDS = 30L
        /** WorkManager input Data is capped at 10 KB; keep well under it. */
        private const val MAX_BODY_CHARS = 2500

        private const val KEY_SENDER = "sender"
        private const val KEY_BODY = "body"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_FORWARD_KEY = "forward_key"

        fun enqueue(context: Context, sms: IncomingSms) {
            // Computed from the full body, before truncation, so it matches the inbox copy.
            val key = forwardKey(sms)
            val request = OneTimeWorkRequestBuilder<ForwardWorker>()
                .setInputData(
                    workDataOf(
                        KEY_SENDER to sms.sender,
                        KEY_BODY to sms.body.take(MAX_BODY_CHARS),
                        KEY_TIMESTAMP to sms.timestampMillis,
                        KEY_FORWARD_KEY to key,
                    ),
                )
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()

            // Unique name dedupes a redelivered broadcast, or a catch-up racing the broadcast.
            val uniqueName = "forward-$key"
            // Overwritten by the worker once it runs; until then, explain why nothing was sent.
            SettingsRepository(context)
                .recordStatus("Queued SMS from ${sms.sender} — sends when the phone is online")
            WorkManager.getInstance(context)
                .enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request)
        }

        private fun androidx.work.Data.toSms(): IncomingSms? {
            val sender = getString(KEY_SENDER) ?: return null
            val body = getString(KEY_BODY) ?: return null
            return IncomingSms(sender, body, getLong(KEY_TIMESTAMP, System.currentTimeMillis()))
        }
    }
}
