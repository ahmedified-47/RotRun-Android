package com.ahmed.rotrun.data

/** The intervention Ask my brain should be showing. Kept as state so a dropped pulse cannot leave the user on Home. */
data class AskIntervention(
    val packageName: String,
    val label: String,
    val mode: String,
)

object AskInterventionPlan {
    /** iOS picks one enabled style. With none enabled, Standard Dialogue is still the flow, not Home. */
    fun mode(picked: String?): String = picked?.takeIf { it.isNotBlank() } ?: "standard"

    /** A later resume must not roll a new style over the one already on screen. */
    fun alreadyShowing(current: AskIntervention?): Boolean = current != null
}
