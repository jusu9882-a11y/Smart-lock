package dev.vanta.smartlock

/**
 * Shared AirWave capability checks, kept pure so fallback behavior is regression-tested.
 */
internal object AirWavePolicy {
    fun hasGestureInput(hasMotionSensor: Boolean, handGesturesActive: Boolean): Boolean =
        hasMotionSensor || handGesturesActive
}
