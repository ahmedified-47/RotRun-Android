package com.ahmed.rotrun

import com.ahmed.rotrun.data.UsageSlice
import com.ahmed.rotrun.data.dedupedUsageSlices
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
    fun missingBucketStartFallsBackToLastUse() {
        val usedToday = todayStart + 60_000L
        val slice = UsageSlice("com.example.app", 0L, usedToday, 8 * 1000L)
        assertEquals(today, usageBucketDay(slice, zone))
        assertEquals(8, visibleSecondsOnDay(listOf(slice), "com.example.app", today, zone))
    }
}
