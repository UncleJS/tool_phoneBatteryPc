// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 UncleJS

package io.github.unclejs.batterywidget.battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeSpeedTest {

    private fun charging(
        plugged: Int = BatteryReader.PLUGGED_AC,
        voltageMv: Int = 4_000,
        currentNowUa: Int = Int.MIN_VALUE,
        maxCurrentUa: Int = -1,
        maxVoltageUv: Int = -1,
        status: Int = BatteryReader.STATUS_CHARGING,
    ) = BatteryReader.parse(
        level = 50, scale = 100, status = status, plugged = plugged, tempTenths = 300, nowMs = 0L,
        voltageMv = voltageMv, maxChargingCurrentUa = maxCurrentUa,
        maxChargingVoltageUv = maxVoltageUv, currentNowUa = currentNowUa,
    )

    @Test
    fun ratedPower_fromExtras() {
        // 3 A at 9 V = 27 W.
        assertEquals(27_000, ChargeSpeedRules.ratedPowerMw(3_000_000, 9_000_000))
        // No voltage reported: assume 5 V -> 2 A = 10 W.
        assertEquals(10_000, ChargeSpeedRules.ratedPowerMw(2_000_000, -1))
        assertNull(ChargeSpeedRules.ratedPowerMw(-1, 9_000_000))
        assertNull(ChargeSpeedRules.ratedPowerMw(0, 0))
    }

    @Test
    fun measuredPower_usesMagnitudeOfCurrent() {
        // 2.5 A at 4 V = 10 W; sign depends on the device.
        assertEquals(10_000, ChargeSpeedRules.measuredPowerMw(2_500_000, 4_000))
        assertEquals(10_000, ChargeSpeedRules.measuredPowerMw(-2_500_000, 4_000))
        assertNull(ChargeSpeedRules.measuredPowerMw(Int.MIN_VALUE, 4_000))
        assertNull(ChargeSpeedRules.measuredPowerMw(0, 4_000))
        assertNull(ChargeSpeedRules.measuredPowerMw(2_500_000, -1))
        // Tiny readings are noise.
        assertNull(ChargeSpeedRules.measuredPowerMw(10_000, 4_000))
    }

    @Test
    fun tiers_boundaries() {
        assertEquals(ChargeSpeed.CHARGING, ChargeSpeedRules.tierFor(null))
        assertEquals(ChargeSpeed.SLOW, ChargeSpeedRules.tierFor(7_499))
        assertEquals(ChargeSpeed.NORMAL, ChargeSpeedRules.tierFor(7_500))
        assertEquals(ChargeSpeed.NORMAL, ChargeSpeedRules.tierFor(14_999))
        assertEquals(ChargeSpeed.FAST, ChargeSpeedRules.tierFor(15_000))
        assertEquals(ChargeSpeed.SUPER_FAST, ChargeSpeedRules.tierFor(30_000))
        assertEquals(ChargeSpeed.ULTRA_FAST, ChargeSpeedRules.tierFor(60_000))
        assertEquals(ChargeSpeed.ULTRA_FAST, ChargeSpeedRules.tierFor(120_000))
    }

    @Test
    fun classify_usesHigherOfRatedAndMeasured() {
        // Rated 27 W, measured only 4 W (nearly full) -> still fast.
        val nearlyFull = charging(currentNowUa = 1_000_000, maxCurrentUa = 3_000_000, maxVoltageUv = 9_000_000)
        assertEquals(ChargeSpeed.FAST, nearlyFull.chargeSpeed)
        // Shown power is the measured one.
        assertEquals(4_000, nearlyFull.powerMw)

        // Rated under-reported (5 V 0.5 A), measured 20 W -> fast.
        val underReported = charging(currentNowUa = 5_000_000, maxCurrentUa = 500_000)
        assertEquals(ChargeSpeed.FAST, underReported.chargeSpeed)

        // Nothing reported -> plain "Charging", no power.
        val unknown = charging()
        assertEquals(ChargeSpeed.CHARGING, unknown.chargeSpeed)
        assertNull(unknown.powerMw)

        // Only rated reported: power falls back to the rating.
        assertEquals(10_000, charging(maxCurrentUa = 2_000_000).powerMw)
    }

    @Test
    fun classify_nonChargingStates() {
        assertEquals(
            ChargeSpeed.NOT_CHARGING,
            charging(plugged = 0, status = BatteryReader.STATUS_DISCHARGING).chargeSpeed,
        )
        assertEquals(
            ChargeSpeed.PLUGGED_NOT_CHARGING,
            charging(status = BatteryReader.STATUS_NOT_CHARGING).chargeSpeed,
        )
        assertEquals(
            ChargeSpeed.PLUGGED_NOT_CHARGING,
            charging(status = BatteryReader.STATUS_DISCHARGING).chargeSpeed,
        )
        assertEquals(ChargeSpeed.FULL, charging(status = BatteryReader.STATUS_FULL).chargeSpeed)
        // "Full" status after unplugging is just on battery.
        assertEquals(
            ChargeSpeed.NOT_CHARGING,
            charging(plugged = 0, status = BatteryReader.STATUS_FULL).chargeSpeed,
        )
        // Power is only reported while charging.
        assertNull(charging(status = BatteryReader.STATUS_FULL, currentNowUa = 2_000_000).powerMw)
    }

    @Test
    fun isCharging_flag() {
        assertTrue(ChargeSpeed.SLOW.isCharging)
        assertTrue(ChargeSpeed.ULTRA_FAST.isCharging)
        assertTrue(ChargeSpeed.CHARGING.isCharging)
        assertFalse(ChargeSpeed.FULL.isCharging)
        assertFalse(ChargeSpeed.NOT_CHARGING.isCharging)
        assertFalse(ChargeSpeed.PLUGGED_NOT_CHARGING.isCharging)
    }

    @Test
    fun labels() {
        val fast = charging(currentNowUa = 5_000_000, voltageMv = 4_200)
        assertEquals("⚡ Fast charging", chargingLabel(fast))
        assertEquals("AC · 21.0 W", chargingDetail(fast))
        assertEquals("USB", chargingDetail(charging(plugged = BatteryReader.PLUGGED_USB)))
        assertEquals("Not charging", chargingLabel(charging(plugged = 0, status = BatteryReader.STATUS_DISCHARGING)))
        assertNull(chargingDetail(charging(plugged = 0, status = BatteryReader.STATUS_DISCHARGING)))
        assertEquals("23.4 W", formatPower(23_400))
    }
}
