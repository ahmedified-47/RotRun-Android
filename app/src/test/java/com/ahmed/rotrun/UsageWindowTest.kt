package com.ahmed.rotrun

import com.ahmed.rotrun.data.countedSampleMillis
import com.ahmed.rotrun.data.ForegroundMark
import com.ahmed.rotrun.data.UsageSlice
import com.ahmed.rotrun.data.dedupedUsageSlices
import com.ahmed.rotrun.data.foregroundSeconds
import com.ahmed.rotrun.data.usageBucketDay
import com.ahmed.rotrun.data.visibleSecondsOnDay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class UsageWindowTest {
    private val zone: ZoneId = ZoneId.of("Australia/Sydney")
    private val today: LocalDate = LocalDate.of(2026, 11, 1)
    private val todayStart: Long = today.atStartOfDay(zone).toInstant().toEpochMilli()
    private val yesterdayStart: Long = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun olderDailyRecordIsNotTodaysUsage() {
        val slices = listOf(
            UsageSlice("com.example.old", yesterdayStart, todayStart + 5_000, 90 * 60 * 1000L),
            UsageSlice("com.example.old", todayStart, todayStart + 20_000, 15 * 1000L),
        )
        assertEquals(15, visibleSecondsOnDay(slices, "com.example.old", today, zone))
    }

    @Test
    fun splitTodayRecordsAreAdded() {
        val slices = listOf(
            UsageSlice("com.example.app", todayStart, todayStart + 1_000, 40 * 1000L),
            UsageSlice("com.example.app", todayStart + 3_600_000L, todayStart + 4_000_000L, 20 * 1000L),
        )
        assertEquals(60, visibleSecondsOnDay(slices, "com.example.app", today, zone))
    }

    @Test
    fun duplicateBucketKeepsTheLargerSample() {
        val slices = listOf(
            UsageSlice("com.example.app", todayStart, todayStart + 1_000, 10 * 1000L),
            UsageSlice("com.example.app", todayStart, todayStart + 2_000, 25 * 1000L),
        )
        assertEquals(1, dedupedUsageSlices(slices).size)
        assertEquals(25, visibleSecondsOnDay(slices, "com.example.app", today, zone))
    }

    @Test
    fun sampleLongerThanTheWindowIsNotTodaysUsage() {
        val window = 14 * 60 * 60 * 1000L
        val thirtySevenHours = 37L * 60 * 60 * 1000L
        assertEquals(0L, countedSampleMillis(0L, thirtySevenHours, window))
        assertEquals(5 * 60 * 1000L, countedSampleMillis(5 * 60 * 1000L, thirtySevenHours, window))
        assertEquals(90_000L, countedSampleMillis(0L, 90_000L, window))
    }

    @Test
    fun openSessionCountsThroughTheWindowEnd() {
        val start = 1_000L
        val end = 91_000L
        val marks = listOf(ForegroundMark("com.example.chrome", start, resumed = true))
        assertEquals(90L, foregroundSeconds(marks, start, end, "com.ahmed.rotrun")["com.example.chrome"])
    }

    @Test
    fun pauseWithoutResumeCountsFromTheWindowStart() {
        val start = 0L
        val marks = listOf(ForegroundMark("com.example.chrome", 45_000L, resumed = false))
        assertEquals(45L, foregroundSeconds(marks, start, 60_000L, "")["com.example.chrome"])
    }

    @Test
    fun extraPausesDoNotCountTheWindowAgain() {
        val marks = listOf(
            ForegroundMark("com.example.launcher", 10_000L, resumed = false),
            ForegroundMark("com.example.launcher", 20_000L, resumed = false),
        )
        assertEquals(10L, foregroundSeconds(marks, 0L, 60_000L, "")["com.example.launcher"])
    }

    @Test
    fun overlappingActivitiesCountOnce() {
        val marks = listOf(
            ForegroundMark("com.example.launcher", 10_000L, resumed = true),
            ForegroundMark("com.example.launcher", 12_000L, resumed = true),
            ForegroundMark("com.example.launcher", 18_000L, resumed = false),
            ForegroundMark("com.example.launcher", 25_000L, resumed = false),
        )
        assertEquals(8L, foregroundSeconds(marks, 0L, 60_000L, "")["com.example.launcher"])
    }

    @Test
    fun nextAppResumeClosesThePreviousApp() {
        val marks = listOf(
            ForegroundMark("com.example.launcher", 0L, resumed = true),
            ForegroundMark("com.example.chrome", 10_000L, resumed = true),
            ForegroundMark("com.example.chrome", 16_000L, resumed = false),
        )
        val totals = foregroundSeconds(marks, 0L, 60_000L, "")
        assertEquals(10L, totals["com.example.launcher"])
        assertEquals(6L, totals["com.example.chrome"])
    }

    @Test
    fun ownAppResumeClosesThePreviousAppWithoutCountingItself() {
        val marks = listOf(
            ForegroundMark("com.example.launcher", 0L, resumed = true),
            ForegroundMark("com.ahmed.rotrun", 5_000L, resumed = true),
            ForegroundMark("com.example.chrome", 20_000L, resumed = true),
            ForegroundMark("com.example.chrome", 23_000L, resumed = false),
        )
        val totals = foregroundSeconds(marks, 0L, 60_000L, "com.ahmed.rotrun")
        assertEquals(5L, totals["com.example.launcher"])
        assertEquals(3L, totals["com.example.chrome"])
        assertEquals(null, totals["com.ahmed.rotrun"])
    }

    @Test
    fun missingBucketStartFallsBackToLastUse() {
        val usedToday = todayStart + 60_000L
        val slice = UsageSlice("com.example.app", 0L, usedToday, 8 * 1000L)
        assertEquals(today, usageBucketDay(slice, zone))
        assertEquals(8, visibleSecondsOnDay(listOf(slice), "com.example.app", today, zone))
    }
}
