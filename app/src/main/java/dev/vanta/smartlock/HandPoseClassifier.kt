package dev.vanta.smartlock

import kotlin.math.hypot

internal enum class HandPose {
    FIST,
    OPEN_HAND,
    UNKNOWN
}

internal data class HandPoint(val x: Float, val y: Float)

internal object HandPoseClassifier {
    private val fingers = listOf(
        Triple(5, 6, 8),
        Triple(9, 10, 12),
        Triple(13, 14, 16),
        Triple(17, 18, 20)
    )

    fun classify(points: List<HandPoint>): HandPose {
        if (points.size < 21) return HandPose.UNKNOWN
        var extended = 0
        var curled = 0
        for ((mcpIndex, pipIndex, tipIndex) in fingers) {
            val mcp = points[mcpIndex]
            val pip = points[pipIndex]
            val tip = points[tipIndex]
            val pipReach = distance(pip, mcp)
            val tipReach = distance(tip, mcp)
            if (pipReach < 0.015f) continue
            if (tipReach > pipReach * 1.18f) extended++
            if (tipReach < pipReach * 1.02f) curled++
        }
        return when {
            extended == 4 -> HandPose.OPEN_HAND
            curled == 4 -> HandPose.FIST
            else -> HandPose.UNKNOWN
        }
    }

    private fun distance(a: HandPoint, b: HandPoint): Float =
        hypot(a.x - b.x, a.y - b.y)
}
