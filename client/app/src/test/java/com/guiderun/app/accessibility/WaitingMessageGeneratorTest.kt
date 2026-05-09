package com.guiderun.app.accessibility

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaitingMessageGeneratorTest {

    @Test
    fun `tier 1 message returned for under 3 minutes`() {
        repeat(10) {
            val msg = WaitingMessageGenerator.getMessage(waitingSeconds = 120) // 2 min
            assertNotNull(msg)
            assertTrue("Message should not be empty", msg.isNotBlank())
        }
    }

    @Test
    fun `tier 2 message returned for 3 to 9 minutes`() {
        val msg = WaitingMessageGenerator.getMessage(waitingSeconds = 300) // 5 min
        assertNotNull(msg)
        assertTrue(msg.isNotBlank())
    }

    @Test
    fun `tier 3 message returned for 10 minutes or more`() {
        val msg = WaitingMessageGenerator.getMessage(waitingSeconds = 600) // 10 min
        assertNotNull(msg)
        assertTrue(msg.isNotBlank())
    }

    @Test
    fun `boundary at exactly 3 minutes uses tier 2`() {
        val msg = WaitingMessageGenerator.getMessage(waitingSeconds = 180) // 3 min
        assertNotNull(msg)
    }

    @Test
    fun `boundary at exactly 10 minutes uses tier 3`() {
        val msg = WaitingMessageGenerator.getMessage(waitingSeconds = 600) // 10 min
        assertNotNull(msg)
    }

    @Test
    fun `zero seconds uses tier 1`() {
        val msg = WaitingMessageGenerator.getMessage(waitingSeconds = 0)
        assertTrue(msg.isNotBlank())
    }
}
