package com.automsgforward.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.automsgforward.data.SettingsRepository

class MainActivity : ComponentActivity() {

    private val hasSmsPermission = mutableStateOf(false)

    private val requestSmsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasSmsPermission.value = granted
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
                    onRequestSmsPermission = {
                        requestSmsPermission.launch(Manifest.permission.RECEIVE_SMS)
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasSmsPermission.value = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECEIVE_SMS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
