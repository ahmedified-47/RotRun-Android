package com.ahmed.rotrun.logic

/**
 * Friend Cameo is one of the Ask-my-brain styles when a real friend is cached and the device is online.
 * It is also the last resort at 0 health, and that path is the only one that pays a bailout.
 */
object CameoOffer {
    const val confirmQuestion = "You're at 0 XP. Do you want to continue and open the app?"

    data class FriendLine(val sentence: String, val speaker: String)

    data class WalkAway(val payout: Int, val goHome: Boolean)

    fun motivationCount(): Int = 3

    /** First name only. "Sara J." and "Sam" both stay a name the friend would answer to. */
    fun firstName(displayName: String): String {
        val trimmed = displayName.trim()
        if (trimmed.isEmpty() || trimmed.equals("A friend", ignoreCase = true)) return "A friend"
        return trimmed.substringBefore(' ').trim().trimEnd('.').ifBlank { "A friend" }
    }

    /**
     * The friend's voice, from today's Daily XP.
     * One line when the friend is ahead, one when the scores are level, one when the user is ahead.
     * The iOS checkout did not have a longer list.
     */
    fun friendLine(displayName: String, friendXp: Int, yourXp: Int): FriendLine {
        val name = firstName(displayName)
        val friend = friendXp.coerceAtLeast(0)
        val yours = yourXp.coerceAtLeast(0)
        val sentence = when {
            friend > yours -> "It's $name. I'm ${friend - yours} XP ahead of you today — close this and catch me."
            friend == yours -> "We're level at $friend XP. Don't let this app be the reason I pass you."
            else -> "You're ${yours - friend} XP ahead of me. Don't throw it away on this app."
        }
        return FriendLine(sentence, name)
    }

    enum class Confirm { Open, WalkAway }

    val standardModes = setOf("standard", "breathing", "mirror", "quick")

    fun isStandardStyle(mode: String?): Boolean = mode in standardModes

    /**
     * Standard Dialogue, Breathing, Mirror Mode, and Quick Check.
     * They are unusable when every toggle is off, so the standard flow cannot run.
     * Brain health does not lock them. A toggle that is on still runs at 0 health.
     */
    fun standardStylesUnusable(
        standard: Boolean,
        breathing: Boolean,
        mirror: Boolean,
        quick: Boolean,
    ): Boolean = !standard && !breathing && !mirror && !quick

    /**
     * Show Cameo before the blocked app opens.
     * Health is exactly 0, a real accepted friend is cached, and either the user just
     * chose to continue out of a standard intervention, or all four of those styles are disabled.
     * This is not "the four are locked at 0 health". They are not locked.
     */
    fun standardInterventionFailed(
        health: Int?,
        continuedFromStandard: Boolean,
        standard: Boolean,
        breathing: Boolean,
        mirror: Boolean,
        quick: Boolean,
        hasFriend: Boolean,
    ): Boolean {
        if (health != 0 || !hasFriend) return false
        if (continuedFromStandard) return true
        return standardStylesUnusable(standard, breathing, mirror, quick)
    }

    /** The continue button of a standard style, before the app opens. */
    fun interceptStandardContinue(health: Int?, hasFriend: Boolean): Boolean =
        health == 0 && hasFriend

    /** Random Ask-my-brain pool. Cameo is in it only for a real friend while the device is online. */
    fun cameoInRandomPool(hasFriend: Boolean, online: Boolean): Boolean = hasFriend && online

    fun pool(
        standard: Boolean,
        breathing: Boolean,
        mirror: Boolean,
        quick: Boolean,
        cameo: Boolean = false,
    ): List<String> =
        buildList {
            if (standard) add("standard")
            if (breathing) add("breathing")
            if (mirror) add("mirror")
            if (quick) add("quick")
            if (cameo) add("cameo")
        }

    /** Health exactly 0, all four styles disabled, and a real accepted friend. Not a health lock. */
    fun lastResort(
        health: Int?,
        standard: Boolean,
        breathing: Boolean,
        mirror: Boolean,
        quick: Boolean,
        hasFriend: Boolean,
    ): Boolean = standardInterventionFailed(
        health,
        continuedFromStandard = false,
        standard,
        breathing,
        mirror,
        quick,
        hasFriend,
    )

    fun mode(
        health: Int?,
        standard: Boolean,
        breathing: Boolean,
        mirror: Boolean,
        quick: Boolean,
        hasFriend: Boolean,
        online: Boolean = false,
    ): String {
        if (lastResort(health, standard, breathing, mirror, quick, hasFriend)) return "cameo"
        return pool(standard, breathing, mirror, quick, cameoInRandomPool(hasFriend, online)).randomOrNull() ?: "standard"
    }

    /**
     * Declining Cameo always leaves for Home.
     * The bailout is paid only for the 0-health last resort, and only the first time today.
     */
    fun walkAway(lastResortConfirm: Boolean, reward: Int, alreadyPaid: Boolean): WalkAway {
        val payout = if (lastResortConfirm && !alreadyPaid) reward.coerceAtLeast(0) else 0
        return WalkAway(payout, goHome = true)
    }

    /** Opening the blocked app is only the confirm's Open choice. Walk-away never opens it. */
    fun opensApp(choice: Confirm): Boolean = choice == Confirm.Open

    /**
     * Walk-away pays [reward] the first time today. Opening pays nothing.
     * Nothing is paid above 0 health or without a real friend.
     * A standard style that is still enabled does not pay until the user continues out of it.
     */
    fun confirmPayout(
        choice: Confirm,
        health: Int?,
        standard: Boolean,
        breathing: Boolean,
        mirror: Boolean,
        quick: Boolean,
        hasFriend: Boolean,
        reward: Int,
        alreadyPaid: Boolean,
        continuedFromStandard: Boolean = false,
    ): Int {
        if (choice != Confirm.WalkAway || alreadyPaid) return 0
        if (!standardInterventionFailed(health, continuedFromStandard, standard, breathing, mirror, quick, hasFriend)) return 0
        return reward.coerceAtLeast(0)
    }
}
