package com.ahmed.rotrun.logic

/**
 * Friend Cameo is not one of the four intervention styles.
 * It is offered only as a last resort, and only after the confirm step.
 */
object CameoOffer {
    const val confirmQuestion = "You're at 0 XP. Do you want to continue and open the app?"

    data class FriendLine(val sentence: String, val speaker: String)

    private val aheadLines = listOf(
        "Come on, put it down. %s.",
        "Leave it closed. %s.",
        "You can set this one down. %s.",
        "Close it and stay with me. %s.",
        "Put it down for now. %s.",
        "I want you ahead of this app. %s.",
    )

    fun motivationCount(): Int = aheadLines.size

    /** First name only. "Sara J." and "Sam" both stay a name the friend would answer to. */
    fun firstName(displayName: String): String {
        val trimmed = displayName.trim()
        if (trimmed.isEmpty() || trimmed.equals("A friend", ignoreCase = true)) return "A friend"
        return trimmed.substringBefore(' ').trim().trimEnd('.').ifBlank { "A friend" }
    }

    /** Friend XP minus the user's XP. Ahead, behind, and level each say the real gap. */
    fun gapPhrase(friendXp: Int, yourXp: Int): String {
        val gap = friendXp - yourXp
        return when {
            gap > 0 -> "I'm $gap XP ahead of you today"
            gap < 0 -> "You're ${-gap} XP ahead of me today"
            else -> "We're level on XP today"
        }
    }

    /**
     * A short line in the friend's voice. The same friend and gap keep the same line.
     * [seed] is the friend's id hash so the six lines rotate without changing on each redraw.
     */
    fun friendLine(displayName: String, friendXp: Int, yourXp: Int, seed: Int): FriendLine {
        val speaker = firstName(displayName)
        val index = (seed and Int.MAX_VALUE) % aheadLines.size
        return FriendLine(aheadLines[index].format(gapPhrase(friendXp, yourXp)), speaker)
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

    fun pool(standard: Boolean, breathing: Boolean, mirror: Boolean, quick: Boolean): List<String> =
        buildList {
            if (standard) add("standard")
            if (breathing) add("breathing")
            if (mirror) add("mirror")
            if (quick) add("quick")
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
    ): String {
        if (lastResort(health, standard, breathing, mirror, quick, hasFriend)) return "cameo"
        return pool(standard, breathing, mirror, quick).randomOrNull() ?: "standard"
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
