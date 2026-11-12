package com.automsgforward.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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

class MainActivity : ComponentActivity() {

    private val hasSmsPermission = mutableStateOf(false)
    private val isBatteryUnrestricted = mutableStateOf(true)

    private val requestSmsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshState()
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
                    onRequestSmsPermission = { requestSmsPermission.launch(Manifest.permission.RECEIVE_SMS) },
                    isBatteryUnrestricted = isBatteryUnrestricted.value,
                    onRequestBatteryExemption = ::requestBatteryExemption,
                    onOpenAppSettings = ::openAppSettings,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun refreshState() {
        hasSmsPermission.value = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECEIVE_SMS,
        ) == PackageManager.PERMISSION_GRANTED
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
}
