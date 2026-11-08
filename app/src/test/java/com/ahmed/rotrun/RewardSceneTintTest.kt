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
    }

    @Test
    fun scenicPalettesStayApartAndReadable() {
        val scenic = RewardSceneTint.offeredIds.map { RewardSceneTint.resolve(it) }.filter { !it.system }
        assertEquals(5, scenic.size)
        scenic.forEach { scene ->
            assertTrue(scene.name, RewardSceneTint.contrast(RewardSceneTint.ink(scene.canvas, scene.lightInk), scene.canvas) >= 4.5)
            assertTrue(scene.name, RewardSceneTint.contrast(RewardSceneTint.ink(scene.cardTop, scene.lightInk), scene.cardTop) >= 4.5)
            assertTrue(scene.name, RewardSceneTint.contrast(RewardSceneTint.ink(scene.cardTop, scene.lightInk), scene.cardBottom) >= 4.5)
            assertTrue(scene.name, RewardSceneTint.contrast(RewardSceneTint.ink(scene.tabBar, preferLight = true), scene.tabBar) >= 4.5)
            assertTrue(scene.name, RewardSceneTint.contrast(RewardSceneTint.ink(scene.sky, preferLight = true), scene.sky) >= 4.5)
            assertTrue(scene.name, RewardSceneTint.contrast(RewardSceneTint.ink(scene.canvas, scene.lightPercent), scene.canvas) >= 4.5)
        }
        for (index in scenic.indices) {
            for (other in index + 1 until scenic.size) {
                val apart = RewardSceneTint.hueDistance(scenic[index].cardTop, scenic[other].cardTop) >= 25.0
                assertTrue("${scenic[index].name} vs ${scenic[other].name}", apart)
            }
        }
    }
}
