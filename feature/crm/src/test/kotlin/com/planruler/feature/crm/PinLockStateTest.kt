package com.planruler.feature.crm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinLockStateTest {
    @Test fun `first two failures do not lock`() {
        val now = 1_000L
        val afterOne = PinLockState().afterFailure(now)
        val afterTwo = afterOne.afterFailure(now)
        assertFalse(afterOne.isLocked(now))
        assertFalse(afterTwo.isLocked(now))
    }

    @Test fun `third failure locks for five seconds`() {
        val now = 1_000L
        var state = PinLockState()
        repeat(3) { state = state.afterFailure(now) }
        assertTrue(state.isLocked(now))
        assertEquals(5, state.remainingSeconds(now))
        assertFalse(state.isLocked(now + 5_000L))
    }

    @Test fun `lockout duration doubles with each further failure`() {
        val now = 1_000L
        var state = PinLockState()
        repeat(4) { state = state.afterFailure(now) }
        assertEquals(10, state.remainingSeconds(now))
        state = state.afterFailure(now)
        assertEquals(20, state.remainingSeconds(now))
    }

    @Test fun `lockout duration is capped at five minutes`() {
        val now = 1_000L
        var state = PinLockState()
        repeat(20) { state = state.afterFailure(now) }
        assertEquals(300, state.remainingSeconds(now))
    }

    @Test fun `a successful unlock is expected to reset the caller's tracked state`() {
        // PinLockState itself has no notion of success; CrmScreen removes the map entry
        // instead. This test documents that a fresh state is always unlocked.
        assertFalse(PinLockState().isLocked(System.currentTimeMillis()))
    }
}
