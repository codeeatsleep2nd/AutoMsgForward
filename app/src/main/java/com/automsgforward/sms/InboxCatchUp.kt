package com.automsgforward.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import com.automsgforward.data.SettingsRepository
import com.automsgforward.mail.ForwardWorker

/**
 * Forwards texts that reached the inbox while this app could not receive the SMS broadcast:
 * after a reboot, or when the OS (e.g. ColorOS) refused to start the app for the broadcast.
 * Runs whenever the app's process starts, so a missed text is forwarded at the latest the
 * next time the app runs.
 */
object InboxCatchUp {

    private const val TAG = "InboxCatchUp"

    /** Upper bound on rows read per sweep, in case the watermark is far behind. */
    private const val MAX_ROWS = 200

    @Synchronized
    fun run(context: Context) {
        val repo = SettingsRepository(context)
        if (!repo.load().enabled) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val watermark = repo.catchUpWatermark()
        if (watermark == null) {
            // First run: start from now instead of emailing the whole inbox history.
            repo.setCatchUpWatermark(System.currentTimeMillis())
            return
        }

        val rows = runCatching { queryInboxSince(context, watermark) }
            .onFailure { Log.w(TAG, "Unable to read SMS inbox", it) }
            .getOrNull() ?: return

        val plan = planCatchUp(rows, watermark, repo.forwardedKeys())
        plan.toForward.forEach { ForwardWorker.enqueue(context, it) }
        repo.setCatchUpWatermark(plan.newWatermarkMillis)
        if (plan.toForward.isNotEmpty()) {
            Log.i(TAG, "Queued ${plan.toForward.size} missed text(s) for forwarding")
        }
    }

    private fun queryInboxSince(context: Context, watermarkMillis: Long): List<InboxRow> {
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE_SENT,
            Telephony.Sms.DATE,
        )
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            "${Telephony.Sms.DATE} > ?",
            arrayOf(watermarkMillis.toString()),
            "${Telephony.Sms.DATE} ASC",
        ) ?: return emptyList()

        return cursor.use { c ->
            buildList {
                while (size < MAX_ROWS && c.moveToNext()) {
                    add(
                        InboxRow(
                            sender = c.getString(0) ?: "Unknown",
                            body = c.getString(1).orEmpty(),
                            sentMillis = c.getLong(2),
                            receivedMillis = c.getLong(3),
                        ),
                    )
                }
            }
        }
    }
}
