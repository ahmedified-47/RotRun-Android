package com.ahmed.rotrun.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.ahmed.rotrun.logic.DeviceOnline

/** Reads the active network. Does not call Supabase. */
fun isDeviceOnline(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = manager.activeNetwork ?: return false
    val caps = manager.getNetworkCapabilities(network) ?: return false
    return DeviceOnline.connected(
        hasNetwork = true,
        internet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
        // Passed through and ignored. A usable network matches iOS NWPath.satisfied.
        validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
    )
}
