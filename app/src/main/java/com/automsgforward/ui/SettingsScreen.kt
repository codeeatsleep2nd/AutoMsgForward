package com.automsgforward.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.automsgforward.data.ForwardSettings
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
) {
    val saved = remember { repository.load() }
    var form by remember { mutableStateOf(saved) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var isSendingTest by remember { mutableStateOf(false) }
    val status by remember { repository.statusFlow() }
        .collectAsStateWithLifecycle(initialValue = "")
    val scope = rememberCoroutineScope()

    val draft = form.copy(appPassword = normalizeAppPassword(form.appPassword))

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

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Forwarding enabled", modifier = Modifier.weight(1f))
                Switch(
                    checked = form.enabled,
                    onCheckedChange = { form = form.copy(enabled = it) },
                )
            }

            OutlinedTextField(
                value = form.gmailAddress,
                onValueChange = { form = form.copy(gmailAddress = it) },
                label = { Text("Your Gmail address") },
                singleLine = true,
                isError = form.gmailAddress.isNotBlank() && !isValidEmail(form.gmailAddress),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.appPassword,
                onValueChange = { form = form.copy(appPassword = it) },
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
                onValueChange = { form = form.copy(recipient = it) },
                label = { Text("Forward to (optional)") },
                supportingText = { Text("Leave blank to send to your own Gmail address.") },
                singleLine = true,
                isError = form.recipient.isNotBlank() && !isValidEmail(form.recipient),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = draft.isConfigured || !draft.enabled,
                    onClick = {
                        repository.save(draft)
                        feedback = "Settings saved."
                    },
                ) { Text("Save") }
                OutlinedButton(
                    enabled = draft.isConfigured && !isSendingTest,
                    onClick = {
                        isSendingTest = true
                        feedback = "Sending test email…"
                        scope.launch {
                            feedback = sendTestEmail(draft)
                            isSendingTest = false
                        }
                    },
                ) { Text(if (isSendingTest) "Sending…" else "Send test email") }
            }

            feedback?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.padding(top = 8.dp))
            Text("Last activity", style = MaterialTheme.typography.titleSmall)
            Text(status, style = MaterialTheme.typography.bodySmall)
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
                "Grant \"Receive SMS\" so the app can see new text messages.",
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Button(onClick = onRequest) { Text("Grant permission") }
        }
    }
}

private suspend fun sendTestEmail(settings: ForwardSettings): String = withContext(Dispatchers.IO) {
    try {
        GmailSender(settings.gmailAddress.trim(), settings.appPassword).send(
            to = settings.effectiveRecipient.trim(),
            content = EmailContent(
                subject = "SMS → Gmail test",
                body = "Your SMS forwarder is configured correctly.",
            ),
        )
        "Test email sent to ${settings.effectiveRecipient.trim()}."
    } catch (e: AuthenticationFailedException) {
        "Gmail rejected the login. Check the address and app password."
    } catch (e: Exception) {
        "Test failed: ${e.message ?: e.javaClass.simpleName}"
    }
}
