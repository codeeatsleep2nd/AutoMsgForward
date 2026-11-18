package com.automsgforward.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.automsgforward.data.AccountStatus
import java.text.DateFormat
import java.util.Date

/** Text and colors for one [AccountStatus]. */
private data class CardLook(
    val title: String,
    val detail: String?,
    val container: Color,
    val content: Color,
)

@Composable
private fun AccountStatus.look(): CardLook {
    val colors = MaterialTheme.colorScheme
    return when (this) {
        AccountStatus.NotSetUp -> CardLook(
            title = "Not set up",
            detail = "Enter your Gmail address and app password below, then tap Save.",
            container = colors.surfaceVariant,
            content = colors.onSurfaceVariant,
        )
        is AccountStatus.Paused -> CardLook(
            title = "Forwarding is off",
            detail = "Turn on Forwarding to start emailing new texts.",
            container = colors.surfaceVariant,
            content = colors.onSurfaceVariant,
        )
        is AccountStatus.Unverified -> CardLook(
            title = "Saved, not verified yet",
            detail = "Send a test email to check that Gmail accepts this account.",
            container = colors.tertiaryContainer,
            content = colors.onTertiaryContainer,
        )
        is AccountStatus.Verified -> CardLook(
            title = "✓  Connected",
            detail = "Verified ${formatTime(verifiedAtMillis)}",
            container = colors.primaryContainer,
            content = colors.onPrimaryContainer,
        )
        is AccountStatus.LoginFailed -> CardLook(
            title = "✕  Gmail rejected the login",
            detail = "Enter a new app password under Change. Texts are not being forwarded.",
            container = colors.errorContainer,
            content = colors.onErrorContainer,
        )
    }
}

/** The saved account and where its emails go, or null when nothing is saved. */
private fun AccountStatus.route(): Pair<String, String>? {
    val (gmail, recipient) = when (this) {
        AccountStatus.NotSetUp -> return null
        is AccountStatus.Paused -> gmail to recipient
        is AccountStatus.Unverified -> gmail to recipient
        is AccountStatus.Verified -> gmail to recipient
        is AccountStatus.LoginFailed -> gmail to recipient
    }
    val destination =
        if (gmail.equals(recipient, ignoreCase = true)) "Emails go to this inbox" else "Forwards to $recipient"
    return gmail to destination
}

private fun formatTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

/**
 * Shows the saved Gmail account and whether it works. [onChange] and [onTest] are null
 * while the account form is open, which hides the buttons.
 */
@Composable
fun AccountCard(
    status: AccountStatus,
    isSendingTest: Boolean,
    onChange: (() -> Unit)?,
    onTest: (() -> Unit)?,
) {
    val look = status.look()
    Card(
        colors = CardDefaults.cardColors(containerColor = look.container, contentColor = look.content),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                look.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            status.route()?.let { (gmail, destination) ->
                Text(gmail, style = MaterialTheme.typography.bodyLarge)
                Text(destination, style = MaterialTheme.typography.bodyMedium)
            }
            look.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (status != AccountStatus.NotSetUp && (onChange != null || onTest != null)) {
                AccountCardButtons(status, isSendingTest, onChange, onTest)
            }
        }
    }
}

@Composable
private fun AccountCardButtons(
    status: AccountStatus,
    isSendingTest: Boolean,
    onChange: (() -> Unit)?,
    onTest: (() -> Unit)?,
) {
    // The action that fixes the current state gets the filled button.
    val changeIsPrimary = status is AccountStatus.LoginFailed
    val testIsPrimary = status is AccountStatus.Unverified
    val outlinedColors = ButtonDefaults.outlinedButtonColors(contentColor = LocalContentColor.current)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
        onChange?.let {
            if (changeIsPrimary) Button(onClick = it) { Text("Change") }
            else OutlinedButton(onClick = it, colors = outlinedColors) { Text("Change") }
        }
        onTest?.let {
            val label = if (isSendingTest) "Sending…" else "Send test email"
            if (testIsPrimary) Button(onClick = it, enabled = !isSendingTest) { Text(label) }
            else OutlinedButton(onClick = it, enabled = !isSendingTest, colors = outlinedColors) { Text(label) }
        }
    }
}
