package com.myledger.app

import org.junit.Assert.*
import org.junit.Test

class VoiceSessionTest {
    @Test fun repeatedTapCannotStartAnotherSession() {
        val session = VoiceSession()
        assertTrue(session.begin())
        assertFalse(session.begin())
    }

    @Test fun endOfAudioMustWaitForFinalResult() {
        val session = VoiceSession()
        assertTrue(session.begin())
        session.processing()
        assertFalse(session.begin())
        session.finish()
        assertTrue(session.begin())
    }

    @Test fun failureOrDisposalAllowsRetry() {
        val session = VoiceSession()
        session.begin()
        session.finish()
        session.finish()
        assertTrue(session.begin())
    }

    @Test fun lateEndOfAudioDoesNotBlockIdleSession() {
        val session = VoiceSession()
        session.processing()
        assertTrue(session.begin())
    }
}
