package com.ahmed.rotrun

import com.ahmed.rotrun.data.iconStreak
import com.ahmed.rotrun.data.themeStreak
import com.ahmed.rotrun.ui.Looks
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakRewardsTest {
    @Test
    fun iconsAndWorldsMatchTheIosCatalog() {
        assertEquals(listOf("Default", "Grassy", "Spark", "Zen", "Scholar", "Cosmic"), Looks.icons.map { it.first })
        assertEquals(listOf(null, null, 3, 7, 14, 30), Looks.icons.map { it.second })
        assertEquals(listOf("Default", "Grassy", "Sunrise", "Zen", "Twilight", "Cosmic"), Looks.all.map { it.id })
        assertEquals(0, iconStreak("Default"))
        assertEquals(0, iconStreak("Grassy"))
        assertEquals(3, iconStreak("Spark"))
        assertEquals(7, iconStreak("Zen"))
        assertEquals(14, iconStreak("Scholar"))
        assertEquals(30, iconStreak("Cosmic"))
        assertEquals(0, themeStreak("Default"))
        assertEquals(0, themeStreak("Grassy"))
        assertEquals(3, themeStreak("Sunrise"))
        assertEquals(7, themeStreak("Zen"))
        assertEquals(14, themeStreak("Twilight"))
        assertEquals(30, themeStreak("Cosmic"))
        assertEquals(
            "Sunrise Home look, Spark app icon, and a bolt streak badge.",
            Looks.rewards.first { it.title == "Spark" }.summary,
        )
    }
}
