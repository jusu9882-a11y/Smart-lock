package dev.vanta.smartlock

import org.junit.Assert.assertEquals
import org.junit.Test

class HandPoseClassifierTest {
    @Test
    fun identifiesFourExtendedFingersAsOpenHand() {
        assertEquals(HandPose.OPEN_HAND, HandPoseClassifier.classify(points(extended = true)))
    }

    @Test
    fun identifiesFourCurledFingersAsFist() {
        assertEquals(HandPose.FIST, HandPoseClassifier.classify(points(extended = false)))
    }

    @Test
    fun incompleteLandmarkSetIsUnknown() {
        assertEquals(HandPose.UNKNOWN, HandPoseClassifier.classify(emptyList()))
    }

    private fun points(extended: Boolean): List<HandPoint> {
        val result = MutableList(21) { HandPoint(0.5f, 0.5f) }
        val fingers = listOf(Triple(5, 6, 8), Triple(9, 10, 12), Triple(13, 14, 16), Triple(17, 18, 20))
        fingers.forEachIndexed { index, (mcp, pip, tip) ->
            val x = 0.25f + index * 0.16f
            result[mcp] = HandPoint(x, 0.65f)
            result[pip] = HandPoint(x, 0.45f)
            result[tip] = if (extended) HandPoint(x, 0.20f) else HandPoint(x + 0.015f, 0.64f)
        }
        return result
    }
}
