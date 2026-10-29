package com.ahmed.rotrun.logic

/** Checkbox and row taps share this so a tick cannot be swallowed. */
object PickerSelection {
    data class Toggle(val chosen: Set<String>, val capped: Boolean)

    fun toggle(chosen: Set<String>, packageName: String, exceedsCap: (Set<String>) -> Boolean): Toggle {
        if (packageName in chosen) return Toggle(chosen - packageName, capped = false)
        val next = chosen + packageName
        return if (exceedsCap(next)) Toggle(chosen, capped = true) else Toggle(next, capped = false)
    }
}
