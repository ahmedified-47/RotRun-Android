package com.ahmed.rotrun

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.ahmed.rotrun.data.AppList
import com.ahmed.rotrun.data.Blocker
import com.ahmed.rotrun.data.RotRunGraph
import com.ahmed.rotrun.data.TrackedApp
import com.ahmed.rotrun.data.UsageReader
import java.io.File
import java.io.FileOutputStream
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives a real device or emulator. Screenshots land in the app files dir
 * under rot5/ so they can be pulled after the run.
 */
@RunWith(AndroidJUnit4::class)
class ShieldProofTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val target = instrumentation.targetContext

    @Before
    fun grantWatching() {
        shell("appops set ${target.packageName} GET_USAGE_STATS allow")
        shell("appops set ${target.packageName} SYSTEM_ALERT_WINDOW allow")
        shell("appops set ${target.packageName} android:system_alert_window allow")
        shell("settings put secure enabled_accessibility_services ${target.packageName}/com.ahmed.rotrun.watch.BlockAccessibilityService")
        shell("settings put secure accessibility_enabled 1")
    }

    @Test
    fun pickerSavesAnAppAndSettingsAreCards() {
        launchHome()
        device.findObject(By.text("Block")).click()
        device.wait(Until.hasObject(By.text("RotRun Apps")), 5_000)
        device.findObject(By.text("RotRun Apps")).click()
        device.wait(Until.hasObject(By.text("Add More Apps")), 5_000)
        device.findObject(By.text("Add More Apps")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Search apps")), 5_000))
        shot("picker")
        val box = device.findObject(By.checkable(true))
        assertNotNull("picker should show a checkbox next to an app icon", box)
        box.click()
        device.findObject(By.text("Save")).click()
        assertTrue(device.wait(Until.hasObject(By.textContains("Apps (")), 8_000))
        val count = device.findObject(By.textContains("Apps ("))
        assertTrue(count.text != "Apps (0)")
        shot("list-after-save")

        device.pressBack()
        device.findObject(By.text("Settings")).click()
        device.wait(Until.hasObject(By.text("App Icon")), 5_000)
        device.findObject(By.text("App Icon")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Grassy")), 5_000))
        assertTrue(device.hasObject(By.text("Spark")))
        shot("app-icon")
        device.findObject(By.text("Grassy")).click()
        val grassy = ComponentName(target, "com.ahmed.rotrun.icon.Grassy")
        val enabled = target.packageManager.getComponentEnabledSetting(grassy)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, enabled)

        device.pressBack()
        device.findObject(By.text("Rewards")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Spark")), 5_000))
        assertTrue(device.hasObject(By.text("Scholar")))
        assertTrue(device.hasObject(By.textContains("day streak")))
        shot("rewards")

        device.pressBack()
        device.findObject(By.text("Support & Feedback")).click()
        device.wait(Until.hasObject(By.text("Feedback / Bug Report")), 5_000)
        device.findObject(By.text("Feedback / Bug Report")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Bug Report")), 5_000))
        assertTrue(device.hasObject(By.text("Email feedback")))
        shot("feedback")
    }

    @Test
    fun shieldFiresForAllDayScheduleAndDailyLimit() {
        val app = pickTarget()
        launchHome()
        prove("all-day", app, Blocker(id = "brick", name = "Brick Mode", mode = "allDay", apps = listOf(app)))
        prove("schedule", app, scheduleBlocker(app))
        prove("daily-limit", app, Blocker(id = "brick", name = "Brick Mode", mode = "dailyLimit", limitMinutes = 0, apps = listOf(app)))
    }

    private fun prove(name: String, app: TrackedApp, blocker: Blocker) {
        val repo = RotRunGraph.repo
        repo.update { current ->
            current.copy(
                onboardingCompleted = true,
                premium = true,
                lifetime = true,
                appLists = listOf(AppList("list.brainmelt", "RotRun Apps", listOf(app))),
                blockers = listOf(blocker),
                focus = null,
                unlockUntil = 0L,
            )
        }
        device.pressHome()
        Thread.sleep(800)
        val launch = target.packageManager.getLaunchIntentForPackage(app.packageName)
            ?: error("No launcher for ${app.packageName}")
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        target.startActivity(launch)
        val shown = device.wait(Until.hasObject(By.text("Ask my brain")), 12_000)
        shot("shield-$name")
        assertTrue("Rot shield did not appear for $name (${app.packageName})", shown)
        device.pressHome()
    }

    private fun scheduleBlocker(app: TrackedApp): Blocker {
        val hour = ZonedDateTime.now().hour
        return Blocker(
            id = "brick",
            name = "Brick Mode",
            mode = "schedule",
            startHour = hour,
            endHour = (hour + 2) % 24,
            apps = listOf(app),
        )
    }

    private fun pickTarget(): TrackedApp {
        launchHome()
        val safety = UsageReader.safetyPackages(target)
        val apps = UsageReader.installedApps(target)
        return apps.firstOrNull { it.packageName !in safety && target.packageManager.getLaunchIntentForPackage(it.packageName) != null }
            ?: error("No launchable app outside the safety set. Saw ${apps.map { it.label }}")
    }

    private fun launchHome() {
        val intent = Intent(target, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        target.startActivity(intent)
        val ready = waitUntil { RotRunGraph.ready() }
        assertTrue("repository never started", ready)
        RotRunGraph.repo.update {
            it.copy(
                onboardingCompleted = true,
                premium = true,
                lifetime = true,
                yearly70Dismissed = true,
                streak = 30,
                appIcon = "Default",
            )
        }
        assertTrue(device.wait(Until.hasObject(By.text("Settings")), 15_000))
        shot("home")
    }

    private fun shot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val dir = File(target.filesDir, "rot5").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).close()
    }

    private fun waitUntil(timeoutMs: Long = 10_000, probe: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (probe()) return true
            Thread.sleep(100)
        }
        return probe()
    }
}
