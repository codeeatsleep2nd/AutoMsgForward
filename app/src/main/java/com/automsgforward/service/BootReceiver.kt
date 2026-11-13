package com.automsgforward.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts the keep-alive service after a reboot or an app update. Without it the app's
 * process stays dead after the phone restarts (ColorOS reboots phones silently overnight),
 * and texts are no longer delivered to it.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in HANDLED_ACTIONS) KeepAliveService.sync(context)
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
