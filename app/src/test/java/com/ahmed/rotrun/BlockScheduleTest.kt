package com.ahmed.rotrun

import com.ahmed.rotrun.data.freshState
import com.ahmed.rotrun.logic.BlockArming
import com.ahmed.rotrun.logic.BlockRules
import com.ahmed.rotrun.logic.BlockSchedule
import com.ahmed.rotrun.logic.Presets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class BlockScheduleTest {
    private val instagram = setOf("com.instagram.android")
    private val weekdays = (1..5).toSet()
    private val allDays = (0..6).toSet()
    private val tuesday = ZonedDateTime.of(2026, 10, 27, 10, 0, 0, 0, ZoneOffset.UTC)
    private val saturday = ZonedDateTime.of(2026, 10, 31, 10, 0, 0, 0, ZoneOffset.UTC)

    @Test
    fun workFocusBlocksWeekdaysFromNineUntilFive() {
        assertTrue(covers("schedule", weekdays, 9, 17, tuesday.withHour(9)))
        assertTrue(covers("schedule", weekdays, 9, 17, tuesday.withHour(16)))
        assertFalse(covers("schedule", weekdays, 9, 17, tuesday.withHour(8)))
        assertFalse(covers("schedule", weekdays, 9, 17, tuesday.withHour(17)))
        assertFalse(covers("schedule", weekdays, 9, 17, saturday.withHour(10)))
    }

    @Test
    fun presetsStartAndEndOnTheirHours() {
        val bedtime = preset("Bedtime")
        assertTrue(covers("schedule", allDays, bedtime.start, bedtime.end, tuesday.withHour(23)))
        assertTrue(covers("schedule", allDays, bedtime.start, bedtime.end, tuesday.withHour(6)))
        assertFalse(covers("schedule", allDays, bedtime.start, bedtime.end, tuesday.withHour(8)))
        assertFalse(covers("schedule", allDays, bedtime.start, bedtime.end, tuesday.withHour(21)))

        val grass = preset("Touch Grass")
        assertTrue(covers("schedule", grass.days.toSet(), grass.start, grass.end, saturday.withHour(10)))
        assertFalse(covers("schedule", grass.days.toSet(), grass.start, grass.end, saturday.withHour(14)))
        assertFalse(covers("schedule", grass.days.toSet(), grass.start, grass.end, tuesday.withHour(11)))

        val evening = preset("Evening Wind Down")
        assertTrue(covers("schedule", allDays, evening.start, evening.end, tuesday.withHour(20)))
        assertFalse(covers("schedule", allDays, evening.start, evening.end, tuesday.withHour(23)))

        val morning = preset("Mindful Morning")
        assertTrue(covers("schedule", allDays, morning.start, morning.end, tuesday.withHour(6)))
        assertFalse(covers("schedule", allDays, morning.start, morning.end, tuesday.withHour(9)))

        val dinner = preset("Dinner Time")
        assertTrue(covers("schedule", dinner.days.toSet(), dinner.start, dinner.end, tuesday.withHour(18)))
        assertFalse(covers("schedule", dinner.days.toSet(), dinner.start, dinner.end, tuesday.withHour(20)))
        assertFalse(covers("schedule", dinner.days.toSet(), dinner.start, dinner.end, saturday.withHour(19)))
    }

    @Test
    fun nextWakeIsTheSoonerOfTheScheduleEdgeAndTheUnlock() {
        val early = tuesday.withHour(8).withMinute(30)
        val work = freshState()
        assertEquals(edge(early, 9), BlockSchedule.nextWakeMillis(work, early, millis(early)))

        val during = tuesday.withHour(10)
        assertEquals(edge(during, 17), BlockSchedule.nextWakeMillis(work, during, millis(during)))

        val sooner = millis(during.plusHours(1))
        val unlocked = work.copy(unlockUntil = sooner)
        assertEquals(sooner, BlockSchedule.nextWakeMillis(unlocked, during, millis(during)))
    }

    @Test
    fun aWorkWindowBlocksTheListedAppUntilTheUnlock() {
        val window = BlockRules.Window(instagram, "schedule", weekdays, 9, 17, 30)
        assertTrue(blocked(window, tuesday.withHour(9)))
        assertFalse(blocked(window, tuesday.withHour(8)))
        val until = millis(tuesday.withHour(11))
        assertFalse(
            BlockRules.isBlocked(
                "com.instagram.android",
                emptySet(),
                emptySet(),
                null,
                until,
                millis(tuesday.withHour(10)),
                tuesday.withHour(10),
                0,
                listOf(window),
            ),
        )
    }

    @Test
    fun armingListsEachMissingGrantAndAllowsOnlyACompleteSet() {
        val missing = BlockArming.steps(
            usage = false,
            accessibilityEnabled = true,
            accessibilityBound = false,
            overlay = false,
            notificationsGranted = false,
            needsNotifications = true,
            batteryUnrestricted = false,
            exactAlarms = false,
        )
        assertEquals(listOf("usage", "accessibility", "overlay", "notifications", "battery", "alarms"), missing.map { it.id })
        assertFalse(BlockArming.canArm(missing))
        val ready = BlockArming.steps(true, true, true, true, true, true, true, true)
        assertTrue(ready.isEmpty())
        assertTrue(BlockArming.canArm(ready))
        val noNotify = BlockArming.steps(true, true, true, true, false, false, true, true)
        assertTrue(noNotify.isEmpty())
    }

    private fun preset(title: String) = Presets.catalog.first { it.title == title }

    private fun covers(mode: String, days: Set<Int>, start: Int, end: Int, now: ZonedDateTime) =
        BlockRules.covers(mode, days, start, end, now)

    private fun blocked(window: BlockRules.Window, now: ZonedDateTime) = BlockRules.isBlocked(
        "com.instagram.android",
        emptySet(),
        emptySet(),
        null,
        0,
        millis(now),
        now,
        0,
        listOf(window),
    )

    private fun edge(now: ZonedDateTime, hour: Int) = millis(now.withHour(hour).withMinute(0).withSecond(5).withNano(0))

    private fun millis(time: ZonedDateTime) = time.toInstant().toEpochMilli()
}
