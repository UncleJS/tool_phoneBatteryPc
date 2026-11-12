// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 UncleJS

package io.github.unclejs.batterywidget.live

import io.github.unclejs.batterywidget.battery.BatterySnapshot
import io.github.unclejs.batterywidget.battery.PlugType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveThrottleTest {

    private val base = BatterySnapshot(
        levelPct = 50,
        isCharging = false,
        isFull = false,
        plugType = PlugType.NONE,
        tempC = 30.0f,
        readAtEpochMs = 0L,
    )
    private val lastPush = 1_000_000L

    @Test
    fun firstReadingIsAlwaysPushed() {
        assertTrue(LiveThrottle.shouldPush(null, base, 0L, lastPush))
    }

    @Test
    fun significantChangesPushImmediately() {
        val now = lastPush + 1_000L
        assertTrue(LiveThrottle.shouldPush(base, base.copy(levelPct = 49), lastPush, now))
        assertTrue(LiveThrottle.shouldPush(base, base.copy(isCharging = true), lastPush, now))
        assertTrue(LiveThrottle.shouldPush(base, base.copy(isFull = true), lastPush, now))
        assertTrue(LiveThrottle.shouldPush(base, base.copy(plugType = PlugType.USB), lastPush, now))
    }

    @Test
    fun unchangedReadingIsSkipped() {
        assertFalse(LiveThrottle.shouldPush(base, base.copy(readAtEpochMs = 99L), lastPush, lastPush + 600_000L))
    }

    @Test
    fun temperatureChangeIsRateLimited() {
        val warmer = base.copy(tempC = 30.5f)
        assertFalse(LiveThrottle.shouldPush(base, warmer, lastPush, lastPush + 59_999L))
        assertTrue(LiveThrottle.shouldPush(base, warmer, lastPush, lastPush + 60_000L))
    }

    @Test
    fun smallTemperatureChangeIsIgnored() {
        assertFalse(LiveThrottle.shouldPush(base, base.copy(tempC = 30.4f), lastPush, lastPush + 600_000L))
    }

    @Test
    fun tenthsRoundingStillCountsAsHalfDegree() {
        val a = base.copy(tempC = 312 / 10f)
        val b = base.copy(tempC = 317 / 10f)
        assertTrue(LiveThrottle.shouldPush(a, b, lastPush, lastPush + 60_000L))
    }
}
