package com.guiderun.server.domain

import com.guiderun.server.common.RunRequestAction.*
import com.guiderun.server.common.RunRequestStatus.*
import com.guiderun.server.common.TriggeredRole.*
import com.guiderun.server.exception.AppException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RunRequestStateMachineTest {

    private val sm = RunRequestStateMachine()

    // ── 合法转移 ──────────────────────────────────────────────────────────

    @Test fun `MATCHING ACCEPT VOLUNTEER → ACCEPTED`() =
        assertEquals(ACCEPTED, sm.validate(MATCHING, ACCEPT, VOLUNTEER))

    @Test fun `ACCEPTED DEPART VOLUNTEER → EN_ROUTE`() =
        assertEquals(EN_ROUTE, sm.validate(ACCEPTED, DEPART, VOLUNTEER))

    @Test fun `EN_ROUTE CONFIRM_MET BLIND → MET`() =
        assertEquals(MET, sm.validate(EN_ROUTE, CONFIRM_MET, BLIND))

    @Test fun `EN_ROUTE CONFIRM_MET VOLUNTEER → MET`() =
        assertEquals(MET, sm.validate(EN_ROUTE, CONFIRM_MET, VOLUNTEER))

    @Test fun `MET START_RUN VOLUNTEER → RUNNING`() =
        assertEquals(RUNNING, sm.validate(MET, START_RUN, VOLUNTEER))

    @Test fun `RUNNING END_RUN VOLUNTEER → FINISHED`() =
        assertEquals(FINISHED, sm.validate(RUNNING, END_RUN, VOLUNTEER))

    @Test fun `RUNNING END_RUN BLIND → FINISHED`() =
        assertEquals(FINISHED, sm.validate(RUNNING, END_RUN, BLIND))

    @Test fun `MATCHING CANCEL BLIND → ABORTED`() =
        assertEquals(ABORTED, sm.validate(MATCHING, CANCEL, BLIND))

    @Test fun `EN_ROUTE CANCEL VOLUNTEER → ABORTED`() =
        assertEquals(ABORTED, sm.validate(EN_ROUTE, CANCEL, VOLUNTEER))

    @Test fun `RUNNING EMERGENCY BLIND → ABORTED`() =
        assertEquals(ABORTED, sm.validate(RUNNING, EMERGENCY, BLIND))

    @Test fun `FINISHED REVIEW_COMPLETE SYSTEM → CLOSED`() =
        assertEquals(CLOSED, sm.validate(FINISHED, REVIEW_COMPLETE, SYSTEM))

    // ── ABANDON 计数分支 ──────────────────────────────────────────────────

    @Test fun `ABANDON count 0 → MATCHING`() =
        assertEquals(MATCHING, sm.validate(ACCEPTED, ABANDON, VOLUNTEER, abandonCount = 0))

    @Test fun `ABANDON count 2 → MATCHING`() =
        assertEquals(MATCHING, sm.validate(ACCEPTED, ABANDON, VOLUNTEER, abandonCount = 2))

    @Test fun `ABANDON count 3 → ABORTED`() =
        assertEquals(ABORTED, sm.validate(ACCEPTED, ABANDON, VOLUNTEER, abandonCount = 3))

    @Test fun `ABANDON count 5 → ABORTED`() =
        assertEquals(ABORTED, sm.validate(ACCEPTED, ABANDON, VOLUNTEER, abandonCount = 5))

    // ── 非法转移 ──────────────────────────────────────────────────────────

    @Test fun `RUNNING ACCEPT VOLUNTEER → 抛异常`() {
        val ex = assertThrows<AppException> { sm.validate(RUNNING, ACCEPT, VOLUNTEER) }
        assertEquals("INVALID_STATE_TRANSITION", ex.errorCode)
    }

    @Test fun `BLIND 不能 ABANDON`() {
        assertThrows<AppException> { sm.validate(ACCEPTED, ABANDON, BLIND) }
    }

    @Test fun `VOLUNTEER 不能在 MATCHING 阶段 CANCEL`() {
        assertThrows<AppException> { sm.validate(MATCHING, CANCEL, VOLUNTEER) }
    }

    @Test fun `ACCEPTED 阶段不能 START_RUN`() {
        assertThrows<AppException> { sm.validate(ACCEPTED, START_RUN, VOLUNTEER) }
    }

    @Test fun `CLOSED 终态不接受任何转移`() {
        assertThrows<AppException> { sm.validate(CLOSED, CANCEL, BLIND) }
    }

    @Test fun `ABORTED 终态不接受任何转移`() {
        assertThrows<AppException> { sm.validate(ABORTED, CANCEL, BLIND) }
    }

    @Test fun `MET END_RUN VOLUNTEER → 抛异常`() {
        assertThrows<AppException> { sm.validate(MET, END_RUN, VOLUNTEER) }
    }
}
