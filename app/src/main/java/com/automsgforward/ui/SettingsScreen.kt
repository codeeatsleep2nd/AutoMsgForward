package com.automsgforward.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.automsgforward.data.ForwardSettings
import com.automsgforward.data.accountStatus
import com.automsgforward.data.hasUnsavedChanges
import com.automsgforward.data.SettingsRepository
import com.automsgforward.data.isValidEmail
import com.automsgforward.data.normalizeAppPassword
import com.automsgforward.mail.EmailContent
import com.automsgforward.mail.GmailSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.mail.AuthenticationFailedException

@Composable
fun SettingsScreen(
    repository: SettingsRepository,
    hasSmsPermission: Boolean,
    onRequestSmsPermission: () -> Unit,
    isBatteryUnrestricted: Boolean,
    onRequestBatteryExemption: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onSettingsSaved: () -> Unit,
) {
    var saved by remember { mutableStateOf(repository.load()) }
    var form by remember { mutableStateOf(saved) }
    var isEditing by remember { mutableStateOf(!saved.isConfigured) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var isSendingTest by remember { mutableStateOf(false) }
    val status by remember { repository.statusFlow() }
        .collectAsStateWithLifecycle(initialValue = "")
    val lastCheck by remember { repository.lastLoginCheckFlow() }
        .collectAsStateWithLifecycle(initialValue = repository.lastLoginCheck())
    val scope = rememberCoroutineScope()

    fun persist(settings: ForwardSettings) {
        repository.save(settings)
        saved = repository.load()
        onSettingsSaved()
    }

    fun sendTest(settings: ForwardSettings) {
        isSendingTest = true
        feedback = "Sending test email…"
        scope.launch {
            val result = sendTestEmail(settings)
            result.loginAccepted?.let { repository.recordLoginCheck(settings, it) }
            // A rejected login of the saved account already turns its card red; don't repeat it.
            val cardShowsIt = result.loginAccepted == false && settings == saved
            feedback = result.message.takeUnless { cardShowsIt }
            isSendingTest = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("SMS → Gmail", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Every incoming text message is emailed to your Gmail inbox.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!hasSmsPermission) {
                PermissionCard(onRequestSmsPermission)
            }
            if (!isBatteryUnrestricted) {
                BackgroundCard(onRequestBatteryExemption, onOpenAppSettings)
            }

            AccountCard(
                status = accountStatus(saved, lastCheck),
                isSendingTest = isSendingTest,
                onChange = { isEditing = true; feedback = null }.takeUnless { isEditing },
                onTest = { sendTest(saved) }.takeUnless { isEditing },
            )

            ForwardingSwitch(
                checked = saved.enabled,
                canEnable = saved.isConfigured,
                onCheckedChange = { persist(saved.copy(enabled = it)) },
            )

            if (isEditing) {
                AccountForm(
                    form = form,
                    onFormChange = { form = it },
                    hasUnsavedChanges = hasUnsavedChanges(saved, form),
                    isSendingTest = isSendingTest,
                    onSave = {
                        // Saving the first account turns forwarding on; that's why it was entered.
                        persist(
                            form.copy(
                                appPassword = normalizeAppPassword(form.appPassword),
                                enabled = saved.enabled || !saved.isConfigured,
                            ),
                        )
                        form = saved
                        isEditing = false
                        feedback = "Account saved."
                    },
                    onCancel = { form = saved; isEditing = false; feedback = null }
                        .takeIf { saved.isConfigured },
                    onTest = { sendTest(form.copy(appPassword = normalizeAppPassword(form.appPassword))) },
                )
            }

            feedback?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.padding(top = 8.dp))
            Text("Last activity", style = MaterialTheme.typography.titleSmall)
            Text(status, style = MaterialTheme.typography.bodySmall)

            if (isBatteryUnrestricted && hasAggressiveBackgroundLimits()) {
                AutoLaunchTip(onOpenAppSettings)
            }
        }
    }
}

@Composable
private fun ForwardingSwitch(checked: Boolean, canEnable: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Forwarding")
            if (!canEnable) {
                Text(
                    "Save a Gmail account first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, enabled = canEnable || checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AccountForm(
    form: ForwardSettings,
    onFormChange: (ForwardSettings) -> Unit,
    hasUnsavedChanges: Boolean,
    isSendingTest: Boolean,
    onSave: () -> Unit,
    onCancel: (() -> Unit)?,
    onTest: () -> Unit,
) {
    val canSend = form.copy(appPassword = normalizeAppPassword(form.appPassword)).isConfigured
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = form.gmailAddress,
            onValueChange = { onFormChange(form.copy(gmailAddress = it)) },
            label = { Text("Your Gmail address") },
            singleLine = true,
            isError = form.gmailAddress.isNotBlank() && !isValidEmail(form.gmailAddress),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.appPassword,
            onValueChange = { onFormChange(form.copy(appPassword = it)) },
            label = { Text("Gmail app password") },
            supportingText = {
                Text("Create one at myaccount.google.com/apppasswords (requires 2-Step Verification).")
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.recipient,
            onValueChange = { onFormChange(form.copy(recipient = it)) },
            label = { Text("Forward to (optional)") },
            supportingText = { Text("Leave blank to send to your own Gmail address.") },
            singleLine = true,
            isError = form.recipient.isNotBlank() && !isValidEmail(form.recipient),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )

        if (hasUnsavedChanges) {
            Text(
                "Unsaved changes",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(enabled = canSend && hasUnsavedChanges, onClick = onSave) { Text("Save") }
            OutlinedButton(enabled = canSend && !isSendingTest, onClick = onTest) {
                Text(if (isSendingTest) "Sending…" else "Send test email")
            }
            onCancel?.let { TextButton(onClick = it) { Text("Cancel") } }
        }
    }
}

@Composable
private fun PermissionCard(onRequest: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "SMS permission required",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                "Allow SMS access so the app can see new texts, and forward any that arrived " +
                    "while it wasn't running (for example right after the phone restarted).",
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Button(onClick = onRequest) { Text("Grant permission") }
        }
    }
}

@Composable
private fun BackgroundCard(onAllow: () -> Unit, onOpenAppSettings: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Allow running in background",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                "Otherwise your phone may pause the app while the screen is locked, and texts " +
                    "are only forwarded once you open it.",
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            BackgroundSteps(color = MaterialTheme.colorScheme.onTertiaryContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onAllow) { Text("Allow") }
                OutlinedButton(onClick = onOpenAppSettings) { Text("App settings") }
            }
        }
    }
}

/** Brands whose ROMs block SMS broadcasts to apps unless "Auto launch" is allowed. */
private val AGGRESSIVE_OEMS = setOf("oneplus", "oppo", "realme", "xiaomi", "redmi", "poco", "vivo", "iqoo", "huawei", "honor")

private fun hasAggressiveBackgroundLimits(): Boolean =
    Build.MANUFACTURER.lowercase() in AGGRESSIVE_OEMS || Build.BRAND.lowercase() in AGGRESSIVE_OEMS

private val BACKGROUND_STEPS = listOf(
    "Tap Allow below and confirm, so battery saving doesn't pause the app.",
    "Tap App settings → Battery usage → turn on \"Allow background activity\" and \"Allow auto launch\" " +
        "(on Xiaomi: \"Autostart\"; on vivo/Huawei: \"Auto start\" / \"App launch\").",
    "Keep Wi-Fi or mobile data on. Texts that arrive offline are sent once the phone is back online.",
    "Don't swipe this app away from the recent apps screen; lock it there with the 🔒 instead.",
)

@Composable
private fun BackgroundSteps(color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BACKGROUND_STEPS.forEachIndexed { index, step ->
            Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium, color = color)
        }
    }
}

@Composable
private fun AutoLaunchTip(onOpenAppSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Keep forwarding working while locked", style = MaterialTheme.typography.titleSmall)
        Text(
            "${Build.MANUFACTURER} phones also block background apps. Make sure that:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "• App settings → Battery usage → \"Allow background activity\" and \"Allow auto launch\" are on\n" +
                "• Wi-Fi or mobile data is on, so texts can be sent\n" +
                "• The \"Forwarding texts to Gmail\" notification isn't blocked\n" +
                "• This app is not swiped away from recent apps (lock it with the 🔒)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onOpenAppSettings) { Text("Open app settings") }
    }
}

/** [loginAccepted] is null when the attempt says nothing about the login (e.g. offline). */
private data class TestResult(val message: String, val loginAccepted: Boolean?)

private suspend fun sendTestEmail(settings: ForwardSettings): TestResult = withContext(Dispatchers.IO) {
    try {
        GmailSender(settings.gmailAddress.trim(), settings.appPassword).send(
            to = settings.effectiveRecipient.trim(),
            content = EmailContent(
                subject = "SMS → Gmail test",
                body = "Your SMS forwarder is configured correctly.",
            ),
        )
        TestResult("Test email sent to ${settings.effectiveRecipient.trim()}.", loginAccepted = true)
    } catch (e: AuthenticationFailedException) {
        TestResult("Gmail rejected the login. Check the address and app password.", loginAccepted = false)
    } catch (e: Exception) {
        TestResult("Test failed: ${e.message ?: e.javaClass.simpleName}", loginAccepted = null)
    }
}
