package com.automsgforward.sms

import org.junit.Assert.assertEquals
import org.junit.Test

class AssembleMessagesTest {

    @Test
    fun `joins multipart message from one sender in order`() {
        val parts = listOf(
            SmsPart("+15550001", "Hello ", 2_000),
            SmsPart("+15550001", "world", 1_000),
        )

        val result = assembleMessages(parts)

        assertEquals(listOf(IncomingSms("+15550001", "Hello world", 1_000)), result)
    }

    @Test
    fun `keeps messages from different senders separate`() {
        val parts = listOf(
            SmsPart("A", "one", 1),
            SmsPart("B", "two", 2),
        )

        val result = assembleMessages(parts)

        assertEquals(2, result.size)
        assertEquals("one", result.first { it.sender == "A" }.body)
        assertEquals("two", result.first { it.sender == "B" }.body)
    }

    @Test
    fun `returns empty list for no parts`() {
        assertEquals(emptyList<IncomingSms>(), assembleMessages(emptyList()))
    }
}
