// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 UncleJS

package io.github.unclejs.batterywidget.battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryReaderTest {

    private fun parse(
        level: Int = 50,
        scale: Int = 100,
        status: Int = BatteryReader.STATUS_DISCHARGING,
        plugged: Int = 0,
        tempTenths: Int = 250,
        nowMs: Long = 1_000L,
    ) = BatteryReader.parse(level, scale, status, plugged, tempTenths, nowMs)

    @Test
    fun levelAndScaleMath() {
        assertEquals(87, parse(level = 87, scale = 100).levelPct)
        assertEquals(100, parse(level = 100, scale = 100).levelPct)
        assertEquals(0, parse(level = 0, scale = 100).levelPct)
        assertEquals(50, parse(level = 128, scale = 256).levelPct)
        assertEquals(33, parse(level = 1, scale = 3).levelPct)
        assertEquals(67, parse(level = 2, scale = 3).levelPct)
    }

    @Test
    fun invalidScaleIsTreatedAsPercent() {
        assertEquals(42, parse(level = 42, scale = 0).levelPct)
        assertEquals(42, parse(level = 42, scale = -1).levelPct)
    }

    @Test
    fun levelIsClampedAndMissingLevelIsZero() {
        assertEquals(100, parse(level = 150, scale = 100).levelPct)
        assertEquals(100, parse(level = 150, scale = -1).levelPct)
        assertEquals(0, parse(level = -1, scale = 100).levelPct)
        assertEquals(0, parse(level = -1, scale = -1).levelPct)
    }

    @Test
    fun statusMapsToChargingAndFull() {
        parse(status = BatteryReader.STATUS_CHARGING).let {
            assertTrue(it.isCharging)
            assertFalse(it.isFull)
        }
        parse(status = BatteryReader.STATUS_FULL).let {
            assertFalse(it.isCharging)
            assertTrue(it.isFull)
        }
        for (status in listOf(
            BatteryReader.STATUS_DISCHARGING,
            BatteryReader.STATUS_NOT_CHARGING,
            BatteryReader.STATUS_UNKNOWN,
        )) {
            val s = parse(status = status)
            assertFalse(s.isCharging)
            assertFalse(s.isFull)
        }
    }

    @Test
    fun plugTypes() {
        assertEquals(PlugType.NONE, parse(plugged = 0).plugType)
        assertEquals(PlugType.AC, parse(plugged = BatteryReader.PLUGGED_AC).plugType)
        assertEquals(PlugType.USB, parse(plugged = BatteryReader.PLUGGED_USB).plugType)
        assertEquals(PlugType.WIRELESS, parse(plugged = BatteryReader.PLUGGED_WIRELESS).plugType)
        assertEquals(PlugType.DOCK, parse(plugged = BatteryReader.PLUGGED_DOCK).plugType)
        assertEquals(PlugType.OTHER, parse(plugged = 64).plugType)
        // Bit mask: AC wins over the others.
        assertEquals(
            PlugType.AC,
            parse(plugged = BatteryReader.PLUGGED_AC or BatteryReader.PLUGGED_DOCK).plugType,
        )
    }

    @Test
    fun temperatureTenths() {
        assertEquals(31.2f, parse(tempTenths = 312).tempC!!, 0.0001f)
        assertEquals(0f, parse(tempTenths = 0).tempC!!, 0.0001f)
        assertEquals(-5.5f, parse(tempTenths = -55).tempC!!, 0.0001f)
    }

    @Test
    fun missingTemperatureIsNull() {
        assertNull(parse(tempTenths = Int.MIN_VALUE).tempC)
        assertNull(parse(tempTenths = BatteryReader.TEMP_MISSING).tempC)
    }

    @Test
    fun readTimeIsPassedThrough() {
        assertEquals(1_794_146_709_000L, parse(nowMs = 1_794_146_709_000L).readAtEpochMs)
    }

    @Test
    fun timeToFullPrefersPlatformThenEstimate() {
        val charging = BatteryReader.STATUS_CHARGING
        val platform = BatteryReader.parse(
            50, 100, charging, BatteryReader.PLUGGED_AC, 250, 0L,
            currentNowUa = 1_000_000, chargeTimeRemainingMs = 1_800_000L, chargeCounterUah = 2_000_000,
        )
        assertEquals(1_800_000L, platform.timeToFullMs)
        assertFalse(platform.timeToFullEstimated)

        val estimate = BatteryReader.parse(
            50, 100, charging, BatteryReader.PLUGGED_AC, 250, 0L,
            currentNowUa = 1_000_000, chargeCounterUah = 2_000_000,
        )
        assertEquals(7_200_000L, estimate.timeToFullMs)
        assertTrue(estimate.timeToFullEstimated)

        val unknown = BatteryReader.parse(50, 100, charging, BatteryReader.PLUGGED_AC, 250, 0L)
        assertNull(unknown.timeToFullMs)
        assertFalse(unknown.timeToFullEstimated)
    }

    @Test
    fun noTimeToFullWhenNotCharging() {
        val s = BatteryReader.parse(
            50, 100, BatteryReader.STATUS_DISCHARGING, 0, 250, 0L,
            currentNowUa = 1_000_000, chargeTimeRemainingMs = 1_800_000L, chargeCounterUah = 2_000_000,
        )
        assertNull(s.timeToFullMs)
    }

    @Test
    fun plugTypeFromName() {
        assertEquals(PlugType.WIRELESS, PlugType.fromName("WIRELESS"))
        assertEquals(PlugType.NONE, PlugType.fromName(null))
        assertEquals(PlugType.NONE, PlugType.fromName("bogus"))
    }
}
