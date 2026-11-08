package com.ahmed.rotrun

import com.ahmed.rotrun.logic.RewardSceneTint
import com.ahmed.rotrun.ui.Looks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardSceneTintTest {
    @Test
    fun everyOfferedRewardHasItsOwnPalette() {
        assertEquals(RewardSceneTint.offeredIds, Looks.all.map { it.id })
        assertEquals(RewardSceneTint.offeredIds.toSet(), Looks.rewards.map { it.themeId }.toSet())
        RewardSceneTint.offeredIds.forEach { id ->
            assertTrue(RewardSceneTint.explicit(id))
            assertEquals(id, RewardSceneTint.resolve(id).name)
        }
        assertFalse(RewardSceneTint.explicit("Spark"))
        assertEquals("Grassy", RewardSceneTint.resolve("Spark").name)
        assertEquals("Grassy", RewardSceneTint.resolve(null).name)
        assertEquals("Grassy", RewardSceneTint.resolve("").name)
    }

    @Test
    fun surfacesMatchIosAndCardTextStaysReadable() {
        assertPalette(RewardSceneTint.grassy, 0xFF3DB370, 0xFF0E5A28, 0xFF0A3E1C, 0xFF062814, 0xFF0A3E1C, light = true)
        assertPalette(RewardSceneTint.sunrise, 0xFFFFC76B, 0xFFFFD18A, 0xFFF0B15A, 0xFFC4842E, 0xFF7A2808, light = false)
        assertPalette(RewardSceneTint.zen, 0xFF0E6E78, 0xFF0C5560, 0xFF083E46, 0xFF062E34, 0xFF08343C, light = true)
        assertPalette(RewardSceneTint.twilight, 0xFFF0B4F4, 0xFFF6D0F8, 0xFFE7A0EE, 0xFFC46CD0, 0xFF6A1878, light = false)
        assertPalette(RewardSceneTint.cosmic, 0xFF070B24, 0xFF1A2A72, 0xFF101848, 0xFF0A102E, 0xFF050814, light = true)
        assertTrue(RewardSceneTint.standard.system)
        listOf(RewardSceneTint.grassy, RewardSceneTint.sunrise, RewardSceneTint.zen, RewardSceneTint.twilight, RewardSceneTint.cosmic).forEach { scene ->
            val ink = RewardSceneTint.label(scene.lightInk)
            assertTrue(scene.name, RewardSceneTint.contrast(ink, scene.cardTop) >= 4.5)
            assertTrue("${scene.name} bottom", RewardSceneTint.contrast(ink, scene.cardBottom) >= 4.5)
        }
    }

    private fun assertPalette(
        scene: RewardSceneTint.Palette,
        canvas: Long,
        cardTop: Long,
        cardBottom: Long,
        cardLip: Long,
        tabBar: Long,
        light: Boolean,
    ) {
        assertEquals(scene.name, canvas, scene.canvas)
        assertEquals(scene.name, cardTop, scene.cardTop)
        assertEquals(scene.name, cardBottom, scene.cardBottom)
        assertEquals(scene.name, cardLip, scene.cardLip)
        assertEquals(scene.name, tabBar, scene.tabBar)
        assertEquals(scene.name, light, scene.lightInk)
        assertEquals(scene.name, light, scene.lightPercent)
    }
}
