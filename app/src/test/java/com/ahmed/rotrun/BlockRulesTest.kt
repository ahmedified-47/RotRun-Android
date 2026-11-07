package com.ahmed.rotrun

import com.ahmed.rotrun.data.AppList
import com.ahmed.rotrun.data.OnboardingStep
import com.ahmed.rotrun.data.TrackedApp
import com.ahmed.rotrun.data.freshState
import com.ahmed.rotrun.data.stepForward
import com.ahmed.rotrun.logic.BlockBackup
import com.ahmed.rotrun.logic.BlockRules
import com.ahmed.rotrun.logic.VoiceLimits
import com.ahmed.rotrun.logic.withoutAppList
import com.ahmed.rotrun.logic.InterventionCopy
import com.ahmed.rotrun.logic.Marketing
import com.ahmed.rotrun.logic.PickerSelection
import com.ahmed.rotrun.logic.ScreenTimeSync
import com.ahmed.rotrun.logic.ShieldPalette
import com.ahmed.rotrun.logic.WidgetGlyph
import com.ahmed.rotrun.logic.WidgetGlyphs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class BlockRulesTest {
    private val tuesdayMorning = ZonedDateTime.of(2026, 10, 27, 10, 0, 0, 0, ZoneOffset.UTC)
    private val saturdayMorning = ZonedDateTime.of(2026, 10, 31, 10, 0, 0, 0, ZoneOffset.UTC)
    private val weekdays = (1..5).toSet()
    private val allDays = (0..6).toSet()

    @Test
    fun scheduleBlocksOnlyInsideTheWindow() {
        val window = BlockRules.Window(setOf("com.instagram.android"), "schedule", weekdays, 9, 17, 30)
        assertTrue(blocked("com.instagram.android", listOf(window), tuesdayMorning, 0))
        assertFalse(blocked("com.instagram.android", listOf(window), saturdayMorning, 0))
        assertFalse(blocked("com.instagram.android", listOf(window), tuesdayMorning.withHour(18), 0))
    }

    @Test
    fun overnightScheduleWrapsPastMidnight() {
        val window = BlockRules.Window(setOf("com.instagram.android"), "schedule", allDays, 22, 7, 30)
        assertTrue(blocked("com.instagram.android", listOf(window), tuesdayMorning.withHour(23), 0))
        assertTrue(blocked("com.instagram.android", listOf(window), tuesdayMorning.withHour(6), 0))
        assertFalse(blocked("com.instagram.android", listOf(window), tuesdayMorning.withHour(8), 0))
    }

    @Test
    fun dailyLimitIsPerApp() {
        val window = BlockRules.Window(setOf("com.instagram.android", "com.zhiliaoapp.musically"), "dailyLimit", allDays, 9, 17, 30)
        assertFalse(blocked("com.instagram.android", listOf(window), tuesdayMorning, 29 * 60))
        assertTrue(blocked("com.instagram.android", listOf(window), tuesdayMorning, 30 * 60))
        assertFalse(blocked("com.zhiliaoapp.musically", listOf(window), tuesdayMorning, 10 * 60))
    }

    @Test
    fun focusIgnoresAnExistingUnlockAndSkipsExcludedApps() {
        val focus = BlockRules.FocusGate(allApps = true, brainrot = emptySet())
        assertTrue(
            BlockRules.isBlocked(
                packageName = "com.instagram.android",
                safety = emptySet(),
                excluded = emptySet(),
                focus = focus,
                unlockUntil = Long.MAX_VALUE,
                nowMillis = 0,
                now = tuesdayMorning,
                usageSeconds = 0,
                windows = emptyList(),
            ),
        )
        assertFalse(
            BlockRules.isBlocked(
                packageName = "com.google.android.apps.maps",
                safety = emptySet(),
                excluded = setOf("com.google.android.apps.maps"),
                focus = focus,
                unlockUntil = 0,
                nowMillis = 0,
                now = tuesdayMorning,
                usageSeconds = 0,
                windows = emptyList(),
            ),
        )
    }

    @Test
    fun safetyAppsAreNeverBlocked() {
        val window = BlockRules.Window(setOf("com.android.settings"), "allDay", allDays, 0, 0, 30)
        assertFalse(blocked("com.android.settings", listOf(window), tuesdayMorning, 9_000, safety = setOf("com.android.settings")))
    }

    @Test
    fun freeTierKeepsOneApp() {
        assertEquals(setOf("com.instagram.android"), BlockRules.freeAllowed(listOf("com.instagram.android", "com.zhiliaoapp.musically"), premium = false, paywall = true))
        assertEquals(setOf("com.instagram.android", "com.zhiliaoapp.musically"), BlockRules.freeAllowed(listOf("com.instagram.android", "com.zhiliaoapp.musically"), premium = true, paywall = true))
        assertEquals(setOf("com.instagram.android", "com.zhiliaoapp.musically"), BlockRules.freeAllowed(listOf("com.instagram.android", "com.zhiliaoapp.musically"), premium = false, paywall = false))
        assertTrue(BlockRules.overFreeCap(emptySet(), setOf("a", "b"), emptySet(), premium = false, paywall = true))
        assertFalse(BlockRules.overFreeCap(setOf("a"), setOf("a"), emptySet(), premium = false, paywall = true))
        assertFalse(BlockRules.overFreeCap(emptySet(), setOf("maps"), setOf("maps"), premium = false, paywall = true))
    }

    @Test
    fun shieldTemplateIsStableAndThereAreFifteen() {
        assertEquals(15, InterventionCopy.templateCount())
        val first = InterventionCopy.forPackage("com.instagram.android", "Instagram")
        val again = InterventionCopy.forPackage("com.instagram.android", "Instagram")
        assertEquals(first.id, again.id)
        assertEquals(first.headline, again.headline)
        assertTrue(first.headline.contains("Instagram"))
        val other = InterventionCopy.forPackage("com.twitter.android", "X")
        assertTrue(other.headline.contains("X"))
    }

    @Test
    fun androidBackupDoesNotLookLikeAnIosSelection() {
        val apps = listOf(BlockBackup.AppRef("com.instagram.android", "Instagram"))
        val selection = BlockBackup.selectionData(apps)
        assertTrue(BlockBackup.isAndroidOwned(selection))
        assertFalse(BlockBackup.isAndroidOwned("bplist00opaque-family-controls"))
        assertEquals("android:brick", BlockBackup.clientId("brick"))
        assertNull(BlockBackup.localId("list.brainmelt"))
        val row = BlockBackup.listJson(BlockBackup.ListRow("list.brainmelt", "RotRun Apps", apps, true, 0))
        val parsed = BlockBackup.parseList(row)
        assertEquals(listOf("com.instagram.android"), parsed?.apps?.map { it.packageName })
        val ios = org.json.JSONObject().put("client_id", "list.brainmelt").put("selection_data", "bplist00")
        assertNull(BlockBackup.parseList(ios))
    }

    @Test
    fun screenTimeSkipsReadingsUnderAMinute() {
        val missing = ScreenTimeSync.reading(
            ScreenTimeSync.UsageView(true, 30, 0, 0, false, emptyList(), List(24) { 0f }, List(24) { 0f }, emptyList()),
        )
        assertNull(missing)
        val reading = ScreenTimeSync.reading(
            ScreenTimeSync.UsageView(
                authorized = true,
                todaySeconds = 120,
                pickups = 2,
                avgSeconds = 180,
                ahead = false,
                offenders = listOf("Instagram" to 90, "App" to 120),
                hourlyToday = List(24) { index -> if (index == 10) 2f else 0f },
                hourlyAvg = List(24) { 0f },
                week = listOf("2026-10-29" to 120, "2026-10-28" to 20),
            ),
        )
        assertEquals(2, reading?.todayMinutes)
        assertEquals(listOf("Instagram"), reading?.offenders?.map { it.name })
        assertEquals(1, reading?.days?.size)
        assertEquals(1, reading?.hourly?.size)
    }

    @Test
    fun marketingFiguresStayBehindOneConstant() {
        assertEquals("4.7", Marketing.rating)
        assertEquals("650K+ RotRun users", Marketing.users)
        assertEquals("10,000+ Reviews", Marketing.reviews)
        assertEquals("6h 32m", Marketing.beforeAverage)
        assertEquals("1h 49m", Marketing.afterAverage)
        assertFalse(freshState().upgradeReminders)
    }

    @Test
    fun shieldBackgroundIsDark() {
        val background = ShieldPalette.background("melting")
        val red = (background shr 16) and 0xFF
        val green = (background shr 8) and 0xFF
        val blue = background and 0xFF
        assertTrue(red < 120 && green < 120 && blue < 120)
        assertNotEquals(ShieldPalette.accent("melting"), background)
    }

    @Test
    fun pickerTickSticksAndTheSecondAppHitsTheFreeCap() {
        val cap: (Set<String>) -> Boolean = { it.size > 1 }
        val first = PickerSelection.toggle(emptySet(), "com.google.android.calendar", cap)
        assertEquals(setOf("com.google.android.calendar"), first.chosen)
        assertFalse(first.capped)
        val second = PickerSelection.toggle(first.chosen, "com.android.chrome", cap)
        assertEquals(setOf("com.google.android.calendar"), second.chosen)
        assertTrue(second.capped)
        val cleared = PickerSelection.toggle(first.chosen, "com.google.android.calendar", cap)
        assertTrue(cleared.chosen.isEmpty())
        assertFalse(cleared.capped)
    }

    @Test
    fun deletingAListClearsBrickAndDropsRulesThatUsedIt() {
        val instagram = TrackedApp("com.instagram.android", "Instagram")
        val state = freshState().copy(
            appLists = listOf(
                AppList("list.brainmelt", "RotRun Apps", listOf(instagram)),
                AppList("list.other", "Night", listOf(TrackedApp("com.zhiliaoapp.musically", "TikTok"))),
            ),
            blockers = freshState().blockers.map { blocker ->
                if (blocker.id == "brick") blocker.copy(apps = listOf(instagram)) else blocker
            } + com.ahmed.rotrun.data.Blocker(
                id = "custom",
                name = "Custom",
                mode = "allDay",
                appListId = "list.other",
                apps = listOf(TrackedApp("com.zhiliaoapp.musically", "TikTok")),
            ),
        )
        val next = state.withoutAppList("list.brainmelt")
        assertTrue(next.appLists.none { it.id == "list.brainmelt" })
        assertEquals(emptyList<TrackedApp>(), next.blockers.first { it.id == "brick" }.apps)
        assertTrue(next.blockers.none { it.id == "work" })
        assertEquals("list.other", next.blockers.first { it.id == "custom" }.appListId)
        assertEquals(state, state.withoutAppList("missing"))
    }

    @Test
    fun androidBackupIdsDoNotMatchAnIosBlob() {
        assertTrue(BlockBackup.clientId("list.brainmelt").startsWith("android:"))
        assertFalse(BlockBackup.isAndroidOwned("""{"familyControls":true}"""))
        assertTrue(BlockBackup.isAndroidOwned(BlockBackup.selectionData(emptyList())))
    }

    @Test
    fun voiceStaysInsideTheProCaps() {
        assertEquals(null, VoiceLimits.denial(4, 29))
        assertEquals(VoiceLimits.dailyMessage, VoiceLimits.denial(5, 10))
        assertEquals(VoiceLimits.monthlyMessage, VoiceLimits.denial(1, 30))
        assertEquals(300.0, VoiceLimits.cappedSeconds(null), 0.0)
        assertEquals(300.0, VoiceLimits.cappedSeconds(900.0), 0.0)
        assertEquals(90.0, VoiceLimits.cappedSeconds(90.0), 0.0)
    }

    @Test
    fun onboardingAsksForUsageAccess() {
        val state = freshState().copy(onboardingStep = OnboardingStep.SelectApps.raw)
        assertFalse(OnboardingStep.ScreenTimeDialog.skipped(state))
        assertTrue(OnboardingStep.SeeForYourself.skipped(state))
        assertTrue(OnboardingStep.ConnectScreenTime.skipped(state))
        assertEquals(OnboardingStep.ScreenTimeDialog.raw, stepForward(state).onboardingStep)
    }

    @Test
    fun askBrainChatLeavesThatPackageUnblocked() {
        val window = BlockRules.Window(setOf("com.instagram.android", "com.whatsapp"), "allDay", allDays, 0, 24, 30)
        assertFalse(blocked("com.instagram.android", listOf(window), tuesdayMorning, 0, chatPackage = "com.instagram.android"))
        assertTrue(blocked("com.whatsapp", listOf(window), tuesdayMorning, 0, chatPackage = "com.instagram.android"))
        assertTrue(blocked("com.instagram.android", listOf(window), tuesdayMorning, 0))
        assertFalse(
            BlockRules.isBlocked(
                packageName = "com.instagram.android",
                safety = emptySet(),
                excluded = emptySet(),
                focus = BlockRules.FocusGate(allApps = true, brainrot = emptySet()),
                unlockUntil = 0,
                nowMillis = 1_000,
                now = tuesdayMorning,
                usageSeconds = 0,
                windows = emptyList(),
                chatPackage = "com.instagram.android",
            ),
        )
    }

    @Test
    fun widgetGlyphsFollowTheIosNameRules() {
        assertEquals(WidgetGlyph.Instagram, WidgetGlyphs.forApp("Instagram"))
        assertEquals(WidgetGlyph.Instagram, WidgetGlyphs.forApp("Photos", "com.instagram.android"))
        assertEquals(WidgetGlyph.WhatsApp, WidgetGlyphs.forApp("WhatsApp"))
        assertEquals(WidgetGlyph.Maps, WidgetGlyphs.forApp("Google Maps"))
        assertEquals(WidgetGlyph.AppStore, WidgetGlyphs.forApp("App Store"))
        assertEquals(WidgetGlyph.AppStore, WidgetGlyphs.forApp("Play Store", "com.android.vending"))
        assertEquals(WidgetGlyph.Screenshots, WidgetGlyphs.forApp("Screenshots"))
        assertEquals(WidgetGlyph.Other, WidgetGlyphs.forApp("Calendar"))
    }

    @Test
    fun dailyLimitZeroBlocksAsSoonAsTheAppIsOpened() {
        val window = BlockRules.Window(setOf("com.google.android.calendar"), "dailyLimit", allDays, 0, 24, 0)
        assertTrue(blocked("com.google.android.calendar", listOf(window), tuesdayMorning, 0))
    }

    private fun blocked(
        packageName: String,
        windows: List<BlockRules.Window>,
        now: ZonedDateTime,
        usageSeconds: Long,
        safety: Set<String> = emptySet(),
        chatPackage: String? = null,
    ): Boolean = BlockRules.isBlocked(
        packageName = packageName,
        safety = safety,
        excluded = emptySet(),
        focus = null,
        unlockUntil = 0,
        nowMillis = 1_000,
        now = now,
        usageSeconds = usageSeconds,
        windows = windows,
        chatPackage = chatPackage,
    )
}
