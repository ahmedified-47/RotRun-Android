package com.ahmed.rotrun.logic

/**
 * "Online" for a random Friend Cameo.
 * This is the device's active network, not a Supabase request.
 * The block overlay never calls this with a network fetch.
 */
object DeviceOnline {
    fun connected(hasNetwork: Boolean, internet: Boolean, validated: Boolean): Boolean =
        hasNetwork && internet && validated
}
