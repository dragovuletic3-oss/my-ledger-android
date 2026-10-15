package com.myledger.app

/** A recognition session stays busy until a final result or error, even after audio ends. */
class VoiceSession {
    private enum class Phase { IDLE, LISTENING, PROCESSING }
    private var phase = Phase.IDLE

    fun begin(): Boolean {
        if (phase != Phase.IDLE) return false
        phase = Phase.LISTENING
        return true
    }

    fun processing() {
        if (phase == Phase.LISTENING) phase = Phase.PROCESSING
    }

    fun finish() { phase = Phase.IDLE }
}
