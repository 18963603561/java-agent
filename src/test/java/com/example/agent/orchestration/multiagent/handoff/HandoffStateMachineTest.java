package com.example.agent.orchestration.multiagent.handoff;

import com.example.agent.common.error.ErrorCodeException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 交接状态机测试。
 */
class HandoffStateMachineTest {

    @Test
    void shouldAllowValidTransitions() {
        HandoffStateMachine stateMachine = new HandoffStateMachine();

        assertEquals(HandoffStatus.WAITING, stateMachine.transition(HandoffStatus.PENDING, HandoffStatus.WAITING));
        assertEquals(HandoffStatus.RUNNING, stateMachine.transition(HandoffStatus.WAITING, HandoffStatus.RUNNING));
        assertEquals(HandoffStatus.SUCCEEDED, stateMachine.transition(HandoffStatus.RUNNING, HandoffStatus.SUCCEEDED));
    }

    @Test
    void shouldRejectInvalidTransitionFromTerminalStatus() {
        HandoffStateMachine stateMachine = new HandoffStateMachine();

        ErrorCodeException exception = assertThrows(ErrorCodeException.class,
                () -> stateMachine.transition(HandoffStatus.SUCCEEDED, HandoffStatus.RUNNING));

        assertEquals("HANDOFF_INVALID_TRANSITION", exception.getErrorCode());
    }

    @Test
    void shouldTreatNullCurrentAsPending() {
        HandoffStateMachine stateMachine = new HandoffStateMachine();

        assertEquals(HandoffStatus.RUNNING, stateMachine.transition(null, HandoffStatus.RUNNING));
    }
}

