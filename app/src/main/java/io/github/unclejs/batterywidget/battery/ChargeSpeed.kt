// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 UncleJS

package io.github.unclejs.batterywidget.battery

// Pure Kotlin (no android.* imports) so plain JVM unit tests can use it.

import kotlin.math.abs

/**
 * Turns the raw charger figures into a [ChargeSpeed] tier.
 *
 * Two power figures are used, both in milliwatts:
 * - **rated**: what the charger advertises, from the (hidden but widely present)
 *   `max_charging_current` (µA) and `max_charging_voltage` (µV) broadcast extras.
 *   This is what the lock screen uses for "Charging rapidly". It stays stable as
 *   the battery fills up.
 * - **measured**: actual battery current × battery voltage. More honest, but it
 *   drops towards the end of a charge.
 *
 * The tier uses the larger of the two, so a fast charger is still "fast" at 90 %
 * and a device that under-reports its rated power is still classified by what it
 * actually draws.
 */
object ChargeSpeedRules {
    /** Tier boundaries in milliwatts (lower bound of each tier). */
    const val NORMAL_MW = 7_500
    const val FAST_MW = 15_000
    const val SUPER_FAST_MW = 30_000
    const val ULTRA_FAST_MW = 60_000

    /** Lock-screen default when the charger reports a current but no voltage. */
    const val DEFAULT_CHARGING_VOLTAGE_UV = 5_000_000

    /** Below this, a power figure is treated as noise / not reported. */
    const val MIN_VALID_MW = 100

    /** Above this, a figure is treated as bogus (no phone charges at 500 W). */
    const val MAX_VALID_MW = 500_000

    /** Rated charger power from the broadcast extras, or null if not reported. */
    fun ratedPowerMw(maxChargingCurrentUa: Int, maxChargingVoltageUv: Int): Int? {
        if (maxChargingCurrentUa <= 0) return null
        val volts = if (maxChargingVoltageUv > 0) maxChargingVoltageUv else DEFAULT_CHARGING_VOLTAGE_UV
        // µA/1000 = mA, µV/1000 = mV, mA*mV/1000 = mW.
        val mw = (maxChargingCurrentUa / 1000L) * (volts / 1000L) / 1000L
        return mw.takeIf { it in MIN_VALID_MW..MAX_VALID_MW }?.toInt()
    }

    /**
     * Measured charging power from BATTERY_PROPERTY_CURRENT_NOW (µA; the sign
     * differs between devices, so the magnitude is used) and EXTRA_VOLTAGE (mV).
     */
    fun measuredPowerMw(currentNowUa: Int, voltageMv: Int): Int? {
        if (currentNowUa == Int.MIN_VALUE || currentNowUa == 0 || voltageMv <= 0) return null
        val mw = abs(currentNowUa.toLong()) * voltageMv / 1_000_000L
        return mw.takeIf { it in MIN_VALID_MW..MAX_VALID_MW }?.toInt()
    }

    /** Tier for a charging device drawing [powerMw] (null = unknown). */
    fun tierFor(powerMw: Int?): ChargeSpeed = when {
        powerMw == null -> ChargeSpeed.CHARGING
        powerMw >= ULTRA_FAST_MW -> ChargeSpeed.ULTRA_FAST
        powerMw >= SUPER_FAST_MW -> ChargeSpeed.SUPER_FAST
        powerMw >= FAST_MW -> ChargeSpeed.FAST
        powerMw >= NORMAL_MW -> ChargeSpeed.NORMAL
        else -> ChargeSpeed.SLOW
    }

    /** Full classification from the battery status, plug state and power figures. */
    fun classify(
        status: Int,
        plugType: PlugType,
        ratedMw: Int?,
        measuredMw: Int?,
    ): ChargeSpeed {
        val plugged = plugType != PlugType.NONE
        return when {
            status == BatteryReader.STATUS_CHARGING -> tierFor(listOfNotNull(ratedMw, measuredMw).maxOrNull())
            status == BatteryReader.STATUS_FULL && plugged -> ChargeSpeed.FULL
            plugged -> ChargeSpeed.PLUGGED_NOT_CHARGING
            else -> ChargeSpeed.NOT_CHARGING
        }
    }
}
