package com.ahmed.rotrun

import com.ahmed.rotrun.icon.LauncherIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconsTest {
    @Test
    fun exactlyOneAliasIsEnabledAndTheSavedChoiceIsThatAlias() {
        assertEquals("Default", LauncherIcons.persistedChoice(null))
        assertEquals("Default", LauncherIcons.persistedChoice(""))
        assertEquals("Default", LauncherIcons.persistedChoice("Sunrise"))
        assertEquals("Zen", LauncherIcons.persistedChoice("Zen"))
        LauncherIcons.order.forEach { saved ->
            val choice = LauncherIcons.persistedChoice(saved)
            val changes = LauncherIcons.changes(choice)
            val enabled = changes.filter { it.second }.map { it.first }
            assertEquals(listOf(choice), enabled)
            assertEquals(choice, changes.first().first)
            assertTrue(changes.first().second)
            assertEquals(LauncherIcons.order.toSet(), changes.map { it.first }.toSet())
            assertTrue(changes.drop(1).none { it.second })
            assertEquals(enabled, LauncherIcons.changes(LauncherIcons.persistedChoice(saved)).filter { it.second }.map { it.first })
        }
        val restored = LauncherIcons.changes(LauncherIcons.persistedChoice("Cosmic"))
        assertEquals("Cosmic" to true, restored.first())
        assertFalse(LauncherIcons.isLauncherAlias("com.ahmed.rotrun.MainActivity"))
        assertFalse(LauncherIcons.isLauncherAlias("com.ahmed.rotrun.InterventionActivity"))
        assertFalse(LauncherIcons.isLauncherAlias("com.ahmed.rotrun.widget.BrainWidgetProvider"))
        assertTrue(LauncherIcons.isLauncherAlias("com.ahmed.rotrun.icon.Default"))
        assertTrue(LauncherIcons.isLauncherAlias("com.ahmed.rotrun.icon.Spark"))
        assertFalse(LauncherIcons.isLauncherAlias("com.ahmed.rotrun.icon.Sunrise"))
        assertTrue(LauncherIcons.refreshNote.contains("minute"))
    }
}
