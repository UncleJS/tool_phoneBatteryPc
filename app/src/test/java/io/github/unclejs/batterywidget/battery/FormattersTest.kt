// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 UncleJS

package io.github.unclejs.batterywidget.battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset

class FormattersTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val johannesburg: ZoneId = ZoneId.of("Africa/Johannesburg")

    // 2026-11-08T14:05:09Z
    private val afternoonMs = 1_794_146_709_000L

    // 2026-11-08T23:59:59Z
    private val lateEveningMs = 1_794_182_399_000L

    @Test
    fun epochZeroInUtc() {
        assertEquals("1970-01-01 00:00:00", formatTimestamp(0L, utc))
    }

    @Test
    fun formatsIn24HourUtc() {
        assertEquals("2026-11-08 14:05:09", formatTimestamp(afternoonMs, utc))
        assertEquals("2026-11-08 23:59:59", formatTimestamp(lateEveningMs, utc))
    }

    @Test
    fun formatsInJohannesburgLocalTime() {
        assertEquals("2026-11-08 16:05:09", formatTimestamp(afternoonMs, johannesburg))
        // Crosses midnight into the next local day.
        assertEquals("2026-11-09 01:59:59", formatTimestamp(lateEveningMs, johannesburg))
    }

    @Test
    fun timestampMatchesExactPattern() {
        val text = formatTimestamp(afternoonMs, utc)
        assertTrue(text, Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}""").matches(text))
    }

    @Test
    fun temperatureFormatting() {
        assertEquals("31.2 °C", formatTemperature(31.2f))
        assertEquals("30.0 °C", formatTemperature(30f))
        assertEquals("-5.5 °C", formatTemperature(-5.5f))
    }

    @Test
    fun percentFormatting() {
        assertEquals("87%", formatPercent(87))
    }

    @Test
    fun chargingLabels() {
        val base = BatterySnapshot(
            levelPct = 50,
            isCharging = false,
            isFull = false,
            plugType = PlugType.NONE,
            tempC = 25f,
            readAtEpochMs = 0L,
        )
        assertEquals("Not charging", chargingLabel(base))
        assertNull(chargingDetail(base))
        val full = base.copy(isFull = true, plugType = PlugType.AC, chargeSpeed = ChargeSpeed.FULL)
        assertEquals("Full", chargingLabel(full))
        assertEquals("AC", chargingDetail(full))
        // Default speed of a charging snapshot without power figures.
        val charging = BatterySnapshot(
            levelPct = 50, isCharging = true, isFull = false, plugType = PlugType.USB,
            tempC = null, readAtEpochMs = 0L,
        )
        assertEquals("⚡ Charging", chargingLabel(charging))
        assertEquals("USB", chargingDetail(charging))
        val wireless = charging.copy(plugType = PlugType.WIRELESS, chargeSpeed = ChargeSpeed.SLOW, powerMw = 5_000)
        assertEquals("⚡ Slow charging", chargingLabel(wireless))
        assertEquals("Wireless · 5.0 W", chargingDetail(wireless))
        assertEquals("Dock", chargingDetail(charging.copy(plugType = PlugType.DOCK)))
        // Power is hidden when not charging.
        assertEquals("AC", chargingDetail(full.copy(powerMw = 3_000)))
    }

    @Test
    fun durationFormatting() {
        assertEquals("< 1 min", formatDuration(59_000L))
        assertEquals("1 min", formatDuration(60_000L))
        assertEquals("59 min", formatDuration(3_599_000L))
        assertEquals("1 h", formatDuration(3_600_000L))
        assertEquals("1 h 23 min", formatDuration(5_000_000L))
        assertEquals("< 1 min", formatDuration(-5L))
    }

    @Test
    fun timeToFullLabels() {
        val charging = BatterySnapshot(
            levelPct = 50, isCharging = true, isFull = false, plugType = PlugType.AC,
            tempC = null, readAtEpochMs = 0L, chargeSpeed = ChargeSpeed.FAST,
        )
        assertNull(timeToFullLabel(charging))
        assertEquals("Full in 1 h 23 min", timeToFullLabel(charging.copy(timeToFullMs = 5_000_000L)))
        assertEquals(
            "Full in ~30 min",
            timeToFullLabel(charging.copy(timeToFullMs = 1_800_000L, timeToFullEstimated = true)),
        )
        val unplugged = charging.copy(
            isCharging = false, plugType = PlugType.NONE, chargeSpeed = ChargeSpeed.NOT_CHARGING,
            timeToFullMs = 1_800_000L,
        )
        assertNull(timeToFullLabel(unplugged))
    }

    @Test
    fun clampPctSizeRange() {
        assertEquals(12, clampPctSize(0))
        assertEquals(12, clampPctSize(12))
        assertEquals(48, clampPctSize(48))
        assertEquals(120, clampPctSize(120))
        assertEquals(120, clampPctSize(500))
    }

    @Test
    fun clampDetailSizeRange() {
        assertEquals(8, clampDetailSize(-3))
        assertEquals(8, clampDetailSize(8))
        assertEquals(12, clampDetailSize(12))
        assertEquals(32, clampDetailSize(32))
        assertEquals(32, clampDetailSize(33))
    }
}
