package de.radicalfishgames.crosscode

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

class StickShaperTest {
    private fun angle(v: Pair<Double, Double>) = Math.toDegrees(atan2(v.second, v.first))

    private fun length(v: Pair<Double, Double>) = hypot(v.first, v.second)

    /** What the Thor reports for a push at [degrees] and [distance] (1 = against the gate). */
    private fun thor(
        degrees: Double,
        distance: Double,
    ): Pair<Double, Double> {
        val rad = Math.toRadians(degrees)
        val g = StickShaper.GATE_RADIUS * distance
        return Pair(min(1.0, g * cos(rad)).coerceAtLeast(-1.0), min(1.0, g * sin(rad)).coerceAtLeast(-1.0))
    }

    @Test
    fun insideDeadZoneIsCentred() {
        assertEquals(0.0 to 0.0, StickShaper.shape(0.05, -0.05))
    }

    @Test
    fun smallOffAxisPushKeepsItsDirection() {
        // A per-axis 0.1 dead zone turned this into a flat horizontal push.
        val out = StickShaper.shape(0.4, 0.09)
        assertEquals(angle(0.4 to 0.09), angle(out), 0.001)
    }

    @Test
    fun lengthStartsFromZeroPastDeadZone() {
        assertEquals(0.0, length(StickShaper.shape(StickShaper.DEAD_ZONE, 0.0)), 1e-9)
        assertEquals(0.5, length(StickShaper.shape(StickShaper.DEAD_ZONE + 0.5 * (1 - StickShaper.DEAD_ZONE), 0.0)), 1e-9)
    }

    @Test
    fun squareCornerIsCappedToOne() {
        val out = StickShaper.shape(0.94, -0.94)
        assertEquals(1.0, length(out), 1e-9)
        assertEquals(-45.0, angle(out), 1e-6)
    }

    @Test
    fun fullPushNearAnAxisReadsTheTrueAngle() {
        for (degrees in listOf(5.0, 20.0, 30.0, 60.0, 80.0, 110.0, 160.0, -20.0, -100.0)) {
            val out = StickShaper.shape(thor(degrees, 1.0).first, thor(degrees, 1.0).second)
            assertEquals("angle at $degrees", degrees, angle(out), 0.01)
            assertEquals(1.0, length(out), 1e-9)
        }
    }

    @Test
    fun nonThorControllerAtAxisIsUnchanged() {
        val out = StickShaper.shape(1.0, 0.0)
        assertEquals(0.0, angle(out), 1e-9)
        assertEquals(1.0, length(out), 1e-9)
    }
}
