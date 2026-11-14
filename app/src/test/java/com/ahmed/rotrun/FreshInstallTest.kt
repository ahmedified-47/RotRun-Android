package com.ahmed.rotrun

import com.ahmed.rotrun.logic.AccessPrompt
import com.ahmed.rotrun.logic.InstallStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FreshInstallTest {
    @Test
    fun emptyPrefsShowOnboardingAndARestoredFlagWouldSkipIt() {
        assertTrue(InstallStart.showsOnboarding(null))
        assertTrue(InstallStart.showsOnboarding(""))
        assertTrue(InstallStart.showsOnboarding("   "))
        assertFalse(InstallStart.showsOnboarding("""{"onboardingCompleted":true,"premium":true,"productId":"com.ahmed.rotrun.subscription.monthly"}"""))
        assertTrue(InstallStart.showsOnboarding("""{"onboardingCompleted":false}"""))
    }

    @Test
    fun backupDoesNotRestoreOnboardingGrantOrSession() {
        val rules = readProjectFile("app/src/main/res/xml/backup_rules.xml") +
            readProjectFile("app/src/main/res/xml/data_extraction_rules.xml")
        val manifest = readProjectFile("app/src/main/AndroidManifest.xml")
        listOf("rotrun.session.xml", "rotrun.xml", "rotrun.flags.xml").forEach { name ->
            assertTrue(rules.contains("path=\"$name\""))
        }
        assertTrue(rules.contains("<cloud-backup>"))
        assertTrue(rules.contains("<device-transfer>"))
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
    }

    @Test
    fun missingShieldPermissionIsAPromptAndBothOffCannotDraw() {
        assertTrue(AccessPrompt.shouldPrompt(usage = false, accessibility = true, overlay = true))
        assertTrue(AccessPrompt.shouldPrompt(usage = true, accessibility = false, overlay = true))
        assertTrue(AccessPrompt.shouldPrompt(usage = true, accessibility = true, overlay = false))
        assertFalse(AccessPrompt.shouldPrompt(usage = true, accessibility = true, overlay = true))
        assertFalse(AccessPrompt.shieldCanDraw(accessibility = false, overlay = false))
        assertTrue(AccessPrompt.shieldCanDraw(accessibility = true, overlay = false))
        assertTrue(AccessPrompt.shieldCanDraw(accessibility = false, overlay = true))
        val bothOff = AccessPrompt.message(usage = false, accessibility = false, overlay = false, batteryUnrestricted = false)
        assertTrue(bothOff.contains("not blocked"))
        assertTrue(bothOff.contains("Battery"))
        assertEquals("usage", AccessPrompt.firstMissing(usage = false, accessibility = false, overlay = false))
        assertEquals("accessibility", AccessPrompt.firstMissing(usage = true, accessibility = false, overlay = false))
        assertEquals("overlay", AccessPrompt.firstMissing(usage = true, accessibility = true, overlay = false))
        assertEquals(null, AccessPrompt.firstMissing(usage = true, accessibility = true, overlay = true))
    }

    private fun readProjectFile(relative: String): String {
        val bare = relative.removePrefix("app/")
        val candidates = listOf(File(relative), File(bare), File("app").resolve(bare))
        val file = candidates.firstOrNull { it.isFile } ?: error("missing $relative")
        return file.readText()
    }
}
