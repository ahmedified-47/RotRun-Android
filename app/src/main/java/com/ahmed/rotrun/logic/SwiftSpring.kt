package com.ahmed.rotrun.logic

import kotlin.math.PI

/**
 * SwiftUI `spring(duration:bounce:)` and `.snappy(duration:extraBounce:)`.
 * Bounce 0 is critically damped. Positive bounce lowers the damping ratio.
 * `.snappy` extraBounce 0 is the same as bounce 0 at that duration.
 */
object SwiftSpring {
    fun dampingRatio(bounce: Float): Float = (1f - bounce).coerceAtLeast(0.05f)

    fun stiffness(durationSec: Float): Float {
        val omega = 2.0 * PI / durationSec.coerceAtLeast(0.01f)
        return (omega * omega).toFloat()
    }
}
