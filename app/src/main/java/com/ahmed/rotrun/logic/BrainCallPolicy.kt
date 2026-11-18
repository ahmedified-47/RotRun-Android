package com.ahmed.rotrun.logic

/** FaceTime-style brain call. Accept stays in the call. Decline leaves without unlocking. */
object BrainCallPolicy {
    const val MODE = "mirror"

    fun isCall(mode: String): Boolean = mode == MODE

    /**
     * Decline and "Nah, you're right" close the chat. They do not unlock the blocked app,
     * and they do not turn RotRun's own screen into the shield.
     */
    fun afterDecline(): String = "dismiss"

    /** iOS Accept opens the mirror question. It does not start a Rot voice session. */
    fun answerStartsVoice(): Boolean = false

    /**
     * Android 14 and ColorOS only deliver a full-screen intent when the app is allowed to use one.
     * Post it once. A second post is what relaunches the screen.
     */
    fun postFullScreenIntent(api: Int, allowed: Boolean, alreadyPosted: Boolean): Boolean =
        !alreadyPosted && (api < 34 || allowed)
}
