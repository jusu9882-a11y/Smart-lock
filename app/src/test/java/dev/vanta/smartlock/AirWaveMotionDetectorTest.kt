package dev.vanta.smartlock

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AirWaveMotionDetectorTest {
    @Test
    fun twoSeparatedAccelerationPeaksTriggerOnce() {
        val triggers = AtomicInteger(0)
        val detector = AirWaveMotionDetector(onWave = { triggers.incrementAndGet() })

        detector.onLinearAcceleration(3f, 0f, 0f, 100_000_000L)
        detector.onLinearAcceleration(0f, 0f, 0f, 200_000_000L)
        detector.onLinearAcceleration(3f, 0f, 0f, 350_000_000L)

        assertEquals(1, triggers.get())
    }

    @Test
    fun peaksTooCloseTogetherDoNotTrigger() {
        val triggers = AtomicInteger(0)
        val detector = AirWaveMotionDetector(onWave = { triggers.incrementAndGet() })

        detector.onLinearAcceleration(3f, 0f, 0f, 100_000_000L)
        detector.onLinearAcceleration(0f, 0f, 0f, 150_000_000L)
        detector.onLinearAcceleration(3f, 0f, 0f, 200_000_000L)

        assertEquals(0, triggers.get())
    }

    @Test
    fun peaksTooFarApartDoNotTrigger() {
        val triggers = AtomicInteger(0)
        val detector = AirWaveMotionDetector(onWave = { triggers.incrementAndGet() })

        detector.onLinearAcceleration(3f, 0f, 0f, 100_000_000L)
        detector.onLinearAcceleration(0f, 0f, 0f, 150_000_000L)
        detector.onLinearAcceleration(3f, 0f, 0f, 1_200_000_000L)

        assertEquals(0, triggers.get())
    }

    @Test
    fun valuesBelowThresholdDoNotTrigger() {
        val triggers = AtomicInteger(0)
        val detector = AirWaveMotionDetector(onWave = { triggers.incrementAndGet() })

        detector.onLinearAcceleration(1f, 0f, 0f, 100_000_000L)
        detector.onLinearAcceleration(0f, 0f, 0f, 200_000_000L)
        detector.onLinearAcceleration(1f, 0f, 0f, 350_000_000L)

        assertEquals(0, triggers.get())
    }
}
