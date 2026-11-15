package com.ahmed.rotrun.logic

/**
 * Friend Cameo is not one of the four intervention styles.
 * It is offered only as a last resort, and only after the confirm step.
 */
object CameoOffer {
    const val confirmQuestion = "You're at 0 XP. Do you want to continue and open the app?"

    enum class Confirm { Open, WalkAway }

    /**
     * Standard Dialogue, Breathing, Mirror Mode, and Quick Check.
     * They are unusable when every toggle is off, so the standard flow cannot run.
     * Brain health does not lock a style: a toggle that is on is still available at 0 health.
     */
    fun standardStylesUnusable(
        standard: Boolean,
        breathing: Boolean,
        mirror: Boolean,
        quick: Boolean,
    ): Boolean = !standard && !breathing && !mirror && !quick

    fun pool(standard: Boolean, breathing: Boolean, mirror: Boolean, quick: Boolean): List<String> =
        buildList {
            if (standard) add("standard")
            if (breathing) add("breathing")
            if (mirror) add("mirror")
            if (quick) add("quick")
        }

    /** Health exactly 0, none of the four styles usable, and a real accepted friend. */
    fun lastResort(
        health: Int?,
        standard: Boolean,
        breathing: Boolean,
        mirror: Boolean,
        quick: Boolean,
        hasFriend: Boolean,
    ): Boolean = health == 0 &&
        standardStylesUnusable(standard, breathing, mirror, quick) &&
        hasFriend

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
     * Nothing is paid above 0 health, without a real friend, or while a standard style can still run.
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
    ): Int {
        if (choice != Confirm.WalkAway || alreadyPaid) return 0
        if (!lastResort(health, standard, breathing, mirror, quick, hasFriend)) return 0
        return reward.coerceAtLeast(0)
    }
}
