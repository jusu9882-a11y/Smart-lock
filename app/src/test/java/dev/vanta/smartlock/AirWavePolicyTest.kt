package dev.vanta.smartlock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AirWavePolicyTest {
    @Test
    fun motionSensorCanProvideAirWaveInput() {
        assertTrue(AirWavePolicy.hasGestureInput(hasMotionSensor = true, handGesturesActive = false))
    }

    @Test
    fun cameraGesturesAreAValidFallbackWithoutMotionSensor() {
        assertTrue(AirWavePolicy.hasGestureInput(hasMotionSensor = false, handGesturesActive = true))
    }

    @Test
    fun armingIsUnavailableWhenNeitherInputIsActive() {
        assertFalse(AirWavePolicy.hasGestureInput(hasMotionSensor = false, handGesturesActive = false))
    }
}
