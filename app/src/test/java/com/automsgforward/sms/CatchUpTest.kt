package com.automsgforward.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CatchUpTest {

    private fun row(id: Long, received: Long, body: String = "msg $id", sent: Long = received - 500) =
        InboxRow(sender = "+15550001", body = body, sentMillis = sent, receivedMillis = received)

    @Test
    fun `forward key ignores sub-second timestamp differences`() {
        val fromBroadcast = IncomingSms("+15550001", "Hi", 1_700_000_000_000)
        val fromInbox = IncomingSms("+15550001", "Hi", 1_700_000_000_999)

        assertEquals(forwardKey(fromBroadcast), forwardKey(fromInbox))
    }

    @Test
    fun `forward key differs for different bodies`() {
        assertNotEquals(
            forwardKey(IncomingSms("+15550001", "A", 1_000)),
            forwardKey(IncomingSms("+15550001", "B", 1_000)),
        )
    }

    @Test
    fun `selects only rows received after the watermark, oldest first`() {
        val rows = listOf(row(3, received = 3_000), row(1, received = 1_000), row(2, received = 2_000))

        val plan = planCatchUp(rows, watermarkMillis = 1_000, alreadyForwarded = emptySet())

        assertEquals(listOf("msg 2", "msg 3"), plan.toForward.map { it.body })
        assertEquals(3_000, plan.newWatermarkMillis)
    }

    @Test
    fun `skips messages that were already forwarded but still advances watermark`() {
        val rows = listOf(row(2, received = 2_000))
        val done = setOf(forwardKey(rows.single().toIncomingSms()))

        val plan = planCatchUp(rows, watermarkMillis = 1_000, alreadyForwarded = done)

        assertEquals(emptyList<IncomingSms>(), plan.toForward)
        assertEquals(2_000, plan.newWatermarkMillis)
    }

    @Test
    fun `keeps watermark when nothing is new`() {
        val plan = planCatchUp(emptyList(), watermarkMillis = 5_000, alreadyForwarded = emptySet())

        assertEquals(emptyList<IncomingSms>(), plan.toForward)
        assertEquals(5_000, plan.newWatermarkMillis)
    }

    @Test
    fun `inbox row uses sent time so it matches the broadcast timestamp`() {
        assertEquals(1_500, row(1, received = 2_000, sent = 1_500).toIncomingSms().timestampMillis)
        assertEquals(2_000, row(1, received = 2_000, sent = 0).toIncomingSms().timestampMillis)
    }
}
