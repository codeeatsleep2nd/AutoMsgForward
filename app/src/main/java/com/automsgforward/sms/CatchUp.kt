package com.automsgforward.sms

/** One row of the system SMS inbox (`content://sms/inbox`). */
data class InboxRow(
    val sender: String,
    val body: String,
    /** `date_sent`: the service-centre timestamp, the same value the broadcast reports. 0 if unknown. */
    val sentMillis: Long,
    /** `date`: when the phone stored the message. Used as the catch-up watermark. */
    val receivedMillis: Long,
)

fun InboxRow.toIncomingSms(): IncomingSms =
    IncomingSms(sender, body, if (sentMillis > 0) sentMillis else receivedMillis)

/**
 * Identity of a message that is stable whether it came from the SMS broadcast or from the
 * inbox, so the two paths never email the same text twice. SMS timestamps have one-second
 * resolution, so milliseconds are dropped.
 */
fun forwardKey(sms: IncomingSms): String =
    "${sms.sender}|${sms.timestampMillis / 1000}|${sms.body.hashCode()}"

data class CatchUpPlan(
    val toForward: List<IncomingSms>,
    val newWatermarkMillis: Long,
)

/**
 * Decide which inbox messages were missed (e.g. the phone rebooted, or the OS refused to
 * start the app for the SMS broadcast) and still need forwarding.
 */
fun planCatchUp(
    rows: List<InboxRow>,
    watermarkMillis: Long,
    alreadyForwarded: Set<String>,
): CatchUpPlan {
    val newRows = rows.filter { it.receivedMillis > watermarkMillis }.sortedBy { it.receivedMillis }
    return CatchUpPlan(
        toForward = newRows.map { it.toIncomingSms() }.filter { forwardKey(it) !in alreadyForwarded },
        newWatermarkMillis = newRows.maxOfOrNull { it.receivedMillis } ?: watermarkMillis,
    )
}
