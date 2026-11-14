package org.catosaur.app.playback

/** Detects bass onsets against a one-second adaptive mean and emits a short linear pulse. */
internal class BeatDetector {
    private var average = 0f
    private var initialized = false
    private var previousTimeMs: Long? = null
    private var lastBeatTimeMs: Long? = null
    private var pulse = 0f

    /** Returns a 0..1 pulse for the frame at [timeMs]. Timestamps must be monotonic. */
    fun process(bass: Float, timeMs: Long): Float {
        require(timeMs >= 0L) { "timeMs must be non-negative" }
        val current = bass.coerceIn(0f, 1f)
        val elapsedMs = previousTimeMs?.let { (timeMs - it).coerceAtLeast(0L) } ?: 0L
        val threshold = maxOf(average * THRESHOLD_RATIO, average + THRESHOLD_DELTA)
        val outsideRefractory = lastBeatTimeMs?.let { timeMs - it >= REFRACTORY_MS } ?: true
        val detected = initialized && outsideRefractory && current > threshold

        pulse = (pulse - elapsedMs / PULSE_DECAY_MS.toFloat()).coerceAtLeast(0f)
        if (detected) {
            pulse = 1f
            lastBeatTimeMs = timeMs
        }

        if (!initialized) {
            average = current
            initialized = true
        } else {
            val alpha = elapsedMs / (AVERAGE_WINDOW_MS + elapsedMs)
            average += (current - average) * alpha
        }
        previousTimeMs = timeMs
        return pulse
    }

    fun reset() {
        average = 0f
        initialized = false
        previousTimeMs = null
        lastBeatTimeMs = null
        pulse = 0f
    }

    private companion object {
        const val AVERAGE_WINDOW_MS = 1000f
        const val THRESHOLD_RATIO = 1.5f
        const val THRESHOLD_DELTA = 0.12f
        const val REFRACTORY_MS = 250L
        const val PULSE_DECAY_MS = 200L
    }
}
