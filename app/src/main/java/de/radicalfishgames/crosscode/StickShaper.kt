package de.radicalfishgames.crosscode

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Turns raw stick axes into what the game should see, keeping the direction the player pushed.
 *
 * - Radial dead zone: the dead zone is a circle on the stick's distance from centre, and the
 *   range past it is rescaled to start at 0. A per-axis dead zone snaps a slightly-off-axis
 *   push onto the axis (on the AYN Thor up to 15 degrees of aim error).
 * - Round range: the AYN Thor reports each axis with about 1.3x gain and clamps each at 1, so
 *   the range is a square and a full push near an axis reads several degrees off. When one axis
 *   is pinned, the pinned axis is rebuilt from the stick's circular gate, and the length is
 *   capped at 1.
 */
object StickShaper {
    const val DEAD_ZONE = 0.08

    /** Distance from centre the Thor reports with the stick against its gate (measured 1.29-1.35). */
    const val GATE_RADIUS = 1.3

    private const val PINNED = 0.995

    fun shape(
        rawX: Double,
        rawY: Double,
    ): Pair<Double, Double> {
        var x = rawX
        var y = rawY
        // Rebuild a clamped axis assuming the stick is at the gate; never shrink it below 1.
        if (abs(x) >= PINNED && abs(y) < PINNED) {
            x = Math.copySign(max(1.0, sqrt(max(GATE_RADIUS * GATE_RADIUS - y * y, 0.0))), x)
        } else if (abs(y) >= PINNED && abs(x) < PINNED) {
            y = Math.copySign(max(1.0, sqrt(max(GATE_RADIUS * GATE_RADIUS - x * x, 0.0))), y)
        }
        val r = hypot(x, y)
        if (r < DEAD_ZONE) return 0.0 to 0.0
        val scaled = min(1.0, (r - DEAD_ZONE) / (1.0 - DEAD_ZONE))
        return x / r * scaled to y / r * scaled
    }
}
