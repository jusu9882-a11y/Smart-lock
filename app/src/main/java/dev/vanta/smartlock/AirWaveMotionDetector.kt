package dev.vanta.smartlock

import kotlin.math.sqrt

/**
 * Detects a deliberate back-and-forth phone wave from TYPE_LINEAR_ACCELERATION.
 *
 * The detector does not send anything itself. The chat screen must explicitly
 * arm it for a selected draft/attachment, and must disarm it after one trigger.
 * Requiring two acceleration peaks and a cooldown reduces accidental sends.
 */
internal class AirWaveMotionDetector(
    private val peakThreshold: Float = 2.4f,
    private val minimumPeakGapMs: Long = 140L,
    private val maximumPeakGapMs: Long = 900L,
    private val cooldownMs: Long = 1_500L,
    private val onWave: () -> Unit
) {
    private var aboveThreshold = false
    private var firstPeakAtMs = 0L
    private var lastTriggerAtMs = Long.MIN_VALUE

    fun onLinearAcceleration(x: Float, y: Float, z: Float, timestampNanos: Long) {
        val nowMs = timestampNanos / 1_000_000L
        val magnitude = sqrt(x * x + y * y + z * z)
        val isPeak = magnitude >= peakThreshold

        if (isPeak && !aboveThreshold) {
            aboveThreshold = true
            if (firstPeakAtMs == 0L ||
                nowMs - firstPeakAtMs > maximumPeakGapMs ||
                nowMs - firstPeakAtMs < minimumPeakGapMs
            ) {
                firstPeakAtMs = nowMs
            } else {
                if (nowMs - lastTriggerAtMs >= cooldownMs) {
                    lastTriggerAtMs = nowMs
                    firstPeakAtMs = 0L
                    onWave()
                }
            }
        } else if (!isPeak) {
            aboveThreshold = false
        }
    }

    fun reset() {
        aboveThreshold = false
        firstPeakAtMs = 0L
    }
}
