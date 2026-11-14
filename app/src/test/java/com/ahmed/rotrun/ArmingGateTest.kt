package com.ahmed.rotrun

import com.ahmed.rotrun.logic.AccessibilityDisclosure
import com.ahmed.rotrun.watch.GatePrompt
import com.ahmed.rotrun.logic.CoverRevoke
import com.ahmed.rotrun.logic.BlockArming
import com.ahmed.rotrun.logic.NotificationPrompt
import com.ahmed.rotrun.logic.OverlayInsetTypes
import org.junit.Assert.assertEquals
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArmingGateTest {
    @Test
    fun imeIsNeverPassedToGetInsetsIgnoringVisibility() {
        val status = 1
        val navigation = 1 shl 1
        val caption = 1 shl 2
        val systemBars = status or navigation or caption
        val cutout = 1 shl 7
        val ime = OverlayInsetTypes.ime()
        val mask = OverlayInsetTypes.ignoringVisibility(systemBars or ime, cutout or ime)
        assertEquals(0, mask and ime)
        assertEquals(systemBars or cutout, mask)
        assertFalse((mask and OverlayInsetTypes.IME) != 0)
        assertTrue(mask != 0)
    }

    @Test
    fun accessibilityDisclosureMustBeAcceptedBeforeSettings() {
        val copy = "RotRun uses Accessibility to detect when you open apps you chose to block and to show a blocking screen; no personal data is collected or shared via this service."
        assertEquals(copy, AccessibilityDisclosure.COPY)
        assertEquals(AccessibilityDisclosure.SETTINGS, AccessibilityDisclosure.afterChoice(accepted = true))
        assertEquals(AccessibilityDisclosure.DISARMED, AccessibilityDisclosure.afterChoice(accepted = false))
        val root = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val strings = File(root, "res/values/resources.xml").readText()
        val service = File(root, "res/xml/block_accessibility.xml").readText()
        assertTrue(strings.contains(copy))
        assertTrue(service.contains("android:isAccessibilityTool=\"false\""))
        assertFalse(service.contains("android:isAccessibilityTool=\"true\""))
        assertFalse(service.contains("flagReportViewIds"))
        assertTrue(service.contains("flagRetrieveInteractiveWindows"))
        val readsViewIds = File(root, "java").walk().filter { it.extension == "kt" }.any { file ->
            file.readText().contains("viewIdResourceName")
        }
        assertFalse(readsViewIds)
    }

    @Test
    fun restoringArmingLetsTheNextRevokeBringRotRunForward() {
        GatePrompt.latch(true)
        assertFalse(GatePrompt.observe(true))
        assertFalse(GatePrompt.observe(false))
        assertTrue(GatePrompt.observe(true))
        assertFalse(GatePrompt.observe(true))
        GatePrompt.latch(false)
    }

    @Test
    fun revokingAccessibilityOrOverlayDoesNotUncover() {
        assertEquals(CoverRevoke.STAY, CoverRevoke.response(accessibilityOn = true, overlayOn = true))
        assertEquals(CoverRevoke.COVER, CoverRevoke.response(accessibilityOn = true, overlayOn = false))
        assertEquals(CoverRevoke.COVER, CoverRevoke.response(accessibilityOn = false, overlayOn = true))
        assertEquals(CoverRevoke.GATE, CoverRevoke.response(accessibilityOn = false, overlayOn = false))
    }

    @Test
    fun aPermanentNotificationDenialOpensSettings() {
        assertEquals(NotificationPrompt.REQUEST, NotificationPrompt.next(granted = false, shouldShowRationale = false, alreadyAsked = false))
        assertEquals(NotificationPrompt.REQUEST, NotificationPrompt.next(granted = false, shouldShowRationale = true, alreadyAsked = true))
        assertEquals(NotificationPrompt.SETTINGS, NotificationPrompt.next(granted = false, shouldShowRationale = false, alreadyAsked = true))
        assertNull(NotificationPrompt.next(granted = true, shouldShowRationale = false, alreadyAsked = true))
        assertEquals(NotificationPrompt.SETTINGS, NotificationPrompt.afterDenial(shouldShowRationale = false))
        assertEquals(NotificationPrompt.REQUEST, NotificationPrompt.afterDenial(shouldShowRationale = true))
    }

    @Test
    fun savedBlockersDoNotArmUntilEveryGrantIsPresent() {
        assertFalse(BlockArming.enforced(armed = false, blocked = true))
        assertTrue(BlockArming.enforced(armed = true, blocked = true))
        assertFalse(BlockArming.enforced(armed = true, blocked = false))
        assertFalse(BlockArming.shouldWatch(armed = false, focusRunning = true, blockerHasApps = true, listHasApps = true))
        assertTrue(BlockArming.shouldWatch(armed = true, focusRunning = false, blockerHasApps = true, listHasApps = false))
        assertTrue(BlockArming.shouldWatch(armed = true, focusRunning = false, blockerHasApps = false, listHasApps = true))
        assertFalse(BlockArming.shouldWatch(armed = true, focusRunning = false, blockerHasApps = false, listHasApps = false))
        assertEquals("Blocking paused: grant Notifications", BlockArming.pausedLine("Notifications"))
    }
}
