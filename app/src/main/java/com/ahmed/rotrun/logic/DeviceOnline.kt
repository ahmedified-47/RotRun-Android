package com.ahmed.rotrun.logic

/**
 * "Online" for a random Friend Cameo.
 * This is the device's active network, not a Supabase request.
 * The block overlay never calls this with a network fetch.
 *
 * iOS uses `NWPath.status == .satisfied`. Android's captive-portal bit
 * (`validated`) is stricter than that and was keeping Cameo out of the pool
 * on a connection that can already reach the internet.
 */
object DeviceOnline {
    fun connected(
        hasNetwork: Boolean,
        internet: Boolean,
        @Suppress("UNUSED_PARAMETER") validated: Boolean,
    ): Boolean = hasNetwork && internet
}
