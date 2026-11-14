package com.ahmed.rotrun.logic

/**
 * WindowInsets.Type.ime() must not be passed to getInsetsIgnoringVisibility.
 * That call throws IllegalArgumentException. IME is read with getInsets.
 * The bit matches android.view.WindowInsets.Type on API 30.
 */
object OverlayInsetTypes {
    const val IME = 1 shl 3

    fun ime(): Int = IME

    /** Mask for getInsetsIgnoringVisibility: system bars and the display cutout, never the IME. */
    fun ignoringVisibility(systemBars: Int, displayCutout: Int): Int =
        (systemBars or displayCutout) and IME.inv()
}
