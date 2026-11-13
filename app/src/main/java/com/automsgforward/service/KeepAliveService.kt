package com.automsgforward.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.automsgforward.R
import com.automsgforward.data.SettingsRepository
import com.automsgforward.sms.InboxCatchUp
import com.automsgforward.ui.MainActivity
import kotlin.concurrent.thread

/**
 * Keeps the app's process alive while forwarding is enabled.
 *
 * Some ROMs (ColorOS, MIUI, …) only deliver the SMS broadcast to apps whose process
 * already exists, and refuse to start a stopped app for it. A process that is merely
 * frozen still receives the broadcast, so staying alive is what matters. The service
 * does no work itself.
 */
class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!SettingsRepository(this).load().enabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        thread(name = "inbox-catch-up") { InboxCatchUp.run(applicationContext) }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Forwarding status", NotificationManager.IMPORTANCE_MIN)
                    .apply { setShowBadge(false) },
            )
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Forwarding texts to Gmail")
            .setContentText("Keeps running so texts are forwarded while the phone is locked.")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val TAG = "KeepAliveService"
        private const val CHANNEL_ID = "keep_alive"
        private const val NOTIFICATION_ID = 1

        /** Start (or stop) the service to match the saved "Forwarding enabled" setting. */
        fun sync(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            if (!SettingsRepository(context).load().enabled) {
                context.stopService(intent)
                return
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { Log.w(TAG, "Could not start keep-alive service", it) }
        }
    }
}
