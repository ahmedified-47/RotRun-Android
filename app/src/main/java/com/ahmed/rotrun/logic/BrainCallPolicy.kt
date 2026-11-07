package com.ahmed.rotrun.logic

/** FaceTime-style brain call. Accept stays in the call. Decline goes back to the block screen. */
object BrainCallPolicy {
    const val MODE = "mirror"
    const val BLOCK = "shield"

    fun isCall(mode: String): Boolean = mode == MODE

    /** Decline and the question's "nah" return to the block screen. They do not open a second one. */
    fun afterDecline(): String = BLOCK

    /** iOS Accept opens the mirror question. It does not start a Rot voice session. */
    fun answerStartsVoice(): Boolean = false

    /**
     * Android 14 and ColorOS only deliver a full-screen intent when the app is allowed to use one.
     * Post it once. A second post is what relaunches the screen.
     */
    fun postFullScreenIntent(api: Int, allowed: Boolean, alreadyPosted: Boolean): Boolean =
        !alreadyPosted && (api < 34 || allowed)
}
