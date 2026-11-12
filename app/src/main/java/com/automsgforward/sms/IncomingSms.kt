package com.automsgforward.sms

/** A single PDU-level fragment as delivered by the telephony stack. */
data class SmsPart(
    val sender: String,
    val body: String,
    val timestampMillis: Long,
)

/** A complete, reassembled text message from one sender. */
data class IncomingSms(
    val sender: String,
    val body: String,
    val timestampMillis: Long,
)

/**
 * Long messages arrive as several parts in a single broadcast. Join consecutive
 * parts from the same sender, keeping the earliest timestamp.
 */
fun assembleMessages(parts: List<SmsPart>): List<IncomingSms> =
    parts
        .groupBy { it.sender }
        .map { (sender, senderParts) ->
            IncomingSms(
                sender = sender,
                body = senderParts.joinToString(separator = "") { it.body },
                timestampMillis = senderParts.minOf { it.timestampMillis },
            )
        }
