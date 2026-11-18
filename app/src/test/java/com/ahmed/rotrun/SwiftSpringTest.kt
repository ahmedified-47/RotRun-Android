package com.ahmed.rotrun

import com.ahmed.rotrun.logic.SwiftSpring
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

class SwiftSpringTest {
    @Test
    fun bounceZeroIsCriticallyDamped() {
        assertEquals(1f, SwiftSpring.dampingRatio(0f), 0.001f)
        val expected = (2.0 * PI / 0.35).let { (it * it).toFloat() }
        assertEquals(expected, SwiftSpring.stiffness(0.35f), 0.1f)
    }

    @Test
    fun chunkyPressAndSnappy() {
        assertEquals(0.75f, SwiftSpring.dampingRatio(0.25f), 0.001f)
        assertEquals(SwiftSpring.stiffness(0.2f), SwiftSpring.stiffness(0.2f), 0f)
        assertEquals(1f, SwiftSpring.dampingRatio(0f), 0.001f)
    }
}
