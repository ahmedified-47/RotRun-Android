package com.ahmed.rotrun.logic

/**
 * Friend Cameo is one of the Ask-my-brain styles when a real friend is cached and the device is online.
 * It is also the last resort at 0 health, and that path is the only one that pays a bailout.
 */
object CameoOffer {
    const val confirmQuestion = "You're at 0 XP. Do you want to continue and open the app?"

    data class FriendLine(val sentence: String, val speaker: String)

    data class WalkAway(val payout: Int, val goHome: Boolean)

    fun motivationCount(): Int = aheadLines("A friend", 1).size

    /** First name only. "Sara J." and "Sam" both stay a name the friend would answer to. */
    fun firstName(displayName: String): String {
        val trimmed = displayName.trim()
        if (trimmed.isEmpty() || trimmed.equals("A friend", ignoreCase = true)) return "A friend"
        return trimmed.substringBefore(' ').trim().trimEnd('.').ifBlank { "A friend" }
    }

    /** Friend is ahead. The first line is the written one. Later lines are extra Android variants. */
    fun aheadLines(name: String, gap: Int): List<String> = listOf(
        "It's $name. I'm $gap XP ahead of you today — close this and catch me.",
        "It's $name. Close this. I'm $gap XP ahead of you today, so come catch me.",
        "$name here. I'm $gap XP ahead of you today — close this and catch me.",
    )

    /** Scores are level. The first line is the written one. Later lines are extra Android variants. */
    fun tiedLines(xp: Int): List<String> = listOf(
        "We're level at $xp XP. Don't let this app be the reason I pass you.",
        "We're level at $xp XP. Close this before I pass you.",
        "Tied at $xp XP. Close this — don't let this app be the reason I pass you.",
    )

    /** User is ahead. The first line is the written one. Later lines are extra Android variants. */
    fun userAheadLines(gap: Int): List<String> = listOf(
        "You're $gap XP ahead of me. Don't throw it away on this app.",
        "You're $gap XP ahead of me. Close this and don't throw that lead away.",
        "You've got a $gap XP lead on me. Don't throw it away on this app — close this.",
    )

    /** The lines for this Daily XP comparison. The first entry is the written line. */
    fun linePool(displayName: String, friendXp: Int, yourXp: Int): List<String> {
        val name = firstName(displayName)
        val friend = friendXp.coerceAtLeast(0)
        val yours = yourXp.coerceAtLeast(0)
        return when {
            friend > yours -> aheadLines(name, friend - yours)
            friend == yours -> tiedLines(friend)
            else -> userAheadLines(yours - friend)
        }
    }

    /**
     * The friend's voice, from today's Daily XP.
     * Each showing picks one line at random from the comparison that matches the real gap.
     * The pick is not locked to a seed for the day.
     */
    fun friendLine(displayName: String, friendXp: Int, yourXp: Int): FriendLine {
        val options = linePool(displayName, friendXp, yourXp)
        return FriendLine(options.random(), firstName(displayName))
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
        val options = pool(standard, breathing, mirror, quick, cameoInRandomPool(hasFriend, online))
        // All four standard styles are off. A real friend means Cameo, even when the
        // phone is offline, so the empty pool does not fall through to a disabled style.
        // Health 0 already returned above. That path keeps the confirm and the bailout.
        if (options.isEmpty() && hasFriend) return "cameo"
        return options.randomOrNull() ?: "standard"
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
