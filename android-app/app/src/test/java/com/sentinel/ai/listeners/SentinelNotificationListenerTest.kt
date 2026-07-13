package com.sentinel.ai.listeners

import org.junit.Assert.assertEquals
import org.junit.Test

class SentinelNotificationListenerTest {

    @Test
    fun `normalizes Indian country code spacing and punctuation`() {
        assertEquals("9876543210", normalizeNumber("+91 98765-43210"))
        assertEquals("9876543210", normalizeNumber("98765 43210"))
        assertEquals("9876543210", normalizeNumber("(+91) 98765.43210"))
    }
}
