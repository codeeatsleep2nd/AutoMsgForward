package com.automsgforward.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.automsgforward.data.SettingsRepository
import com.automsgforward.service.KeepAliveService

class MainActivity : ComponentActivity() {

    private val hasSmsPermission = mutableStateOf(false)
    private val isBatteryUnrestricted = mutableStateOf(true)

    private val requestSmsPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshState()
            KeepAliveService.sync(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = SettingsRepository(this)
        setContent {
            AppTheme {
                SettingsScreen(
                    repository = repository,
                    hasSmsPermission = hasSmsPermission.value,
                    onRequestSmsPermission = { requestSmsPermission.launch(RUNTIME_PERMISSIONS) },
                    isBatteryUnrestricted = isBatteryUnrestricted.value,
                    onRequestBatteryExemption = ::requestBatteryExemption,
                    onOpenAppSettings = ::openAppSettings,
                    onSettingsSaved = { KeepAliveService.sync(this) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
        KeepAliveService.sync(this)
    }

    private fun refreshState() {
        hasSmsPermission.value = SMS_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        isBatteryUnrestricted.value =
            getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
    }

    @Suppress("BatteryLife") // Sideloaded app whose core function needs background delivery.
    private fun requestBatteryExemption() {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName"),
        )
        runCatching { startActivity(intent) }.onFailure { openAppSettings() }
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
        )
    }

    private companion object {
        val SMS_PERMISSIONS = listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)

        /** SMS access, plus the notification that shows the keep-alive service is running. */
        val RUNTIME_PERMISSIONS: Array<String> =
            (SMS_PERMISSIONS + listOfNotNull(
                Manifest.permission.POST_NOTIFICATIONS.takeIf {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                },
            )).toTypedArray()
    }
}
