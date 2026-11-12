// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 UncleJS

package io.github.unclejs.batterywidget.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlin.math.roundToInt

/**
 * Reads the battery state from the sticky ACTION_BATTERY_CHANGED broadcast.
 *
 * [parse] is pure (no Android calls) and is what the unit tests exercise. The
 * constants below mirror android.os.BatteryManager so [parse] does not touch it.
 */
object BatteryReader {

    // android.os.BatteryManager.BATTERY_STATUS_*
    const val STATUS_UNKNOWN = 1
    const val STATUS_CHARGING = 2
    const val STATUS_DISCHARGING = 3
    const val STATUS_NOT_CHARGING = 4
    const val STATUS_FULL = 5

    // android.os.BatteryManager.BATTERY_PLUGGED_* (bit mask)
    const val PLUGGED_AC = 1
    const val PLUGGED_USB = 2
    const val PLUGGED_WIRELESS = 4
    const val PLUGGED_DOCK = 8

    /** Value used when EXTRA_TEMPERATURE is missing. */
    const val TEMP_MISSING = Int.MIN_VALUE

    // Hidden BatteryManager extras (EXTRA_MAX_CHARGING_*); present on most devices.
    private const val EXTRA_MAX_CHARGING_CURRENT = "max_charging_current"
    private const val EXTRA_MAX_CHARGING_VOLTAGE = "max_charging_voltage"

    /** Returns the current battery state, or null if the sticky broadcast is unavailable. */
    fun read(context: Context, nowMs: Long = System.currentTimeMillis()): BatterySnapshot? {
        // A null receiver only fetches the current sticky intent; nothing is registered.
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        return fromIntent(context, intent, nowMs)
    }

    /** Converts an ACTION_BATTERY_CHANGED intent into a snapshot. */
    fun fromIntent(context: Context, intent: Intent, nowMs: Long = System.currentTimeMillis()): BatterySnapshot =
        parse(
            level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
            scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
            status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, STATUS_UNKNOWN),
            plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
            tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, TEMP_MISSING),
            nowMs = nowMs,
            voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1),
            maxChargingCurrentUa = intent.getIntExtra(EXTRA_MAX_CHARGING_CURRENT, -1),
            maxChargingVoltageUv = intent.getIntExtra(EXTRA_MAX_CHARGING_VOLTAGE, -1),
            currentNowUa = intProperty(context, BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            chargeTimeRemainingMs = chargeTimeRemainingMs(context),
            chargeCounterUah = intProperty(context, BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
        )

    /** A BatteryManager int property (µA / µAh), or Int.MIN_VALUE if unsupported. */
    private fun intProperty(context: Context, id: Int): Int =
        try {
            context.getSystemService(BatteryManager::class.java)
                ?.getIntProperty(id)
                ?: Int.MIN_VALUE
        } catch (e: RuntimeException) {
            Int.MIN_VALUE
        }

    /** Android's own time-until-full estimate in ms, or -1 if unknown. */
    private fun chargeTimeRemainingMs(context: Context): Long =
        try {
            context.getSystemService(BatteryManager::class.java)
                ?.computeChargeTimeRemaining()
                ?: -1L
        } catch (e: RuntimeException) {
            -1L
        }

    /**
     * Pure conversion of the raw broadcast extras.
     *
     * - A missing/invalid scale (0 or negative) is treated as 100.
     * - A missing level (negative) becomes 0; the result is clamped to 0..100.
     * - [tempTenths] is tenths of a degree Celsius; [TEMP_MISSING] means "not reported".
     */
    fun parse(
        level: Int,
        scale: Int,
        status: Int,
        plugged: Int,
        tempTenths: Int,
        nowMs: Long,
        voltageMv: Int = -1,
        maxChargingCurrentUa: Int = -1,
        maxChargingVoltageUv: Int = -1,
        currentNowUa: Int = Int.MIN_VALUE,
        chargeTimeRemainingMs: Long = -1L,
        chargeCounterUah: Int = Int.MIN_VALUE,
    ): BatterySnapshot {
        val safeScale = if (scale > 0) scale else 100
        val pct = if (level < 0) 0 else (level * 100f / safeScale).roundToInt().coerceIn(0, 100)
        val plugType = plugTypeOf(plugged)
        val charging = status == STATUS_CHARGING
        val rated = ChargeSpeedRules.ratedPowerMw(maxChargingCurrentUa, maxChargingVoltageUv)
        val measured = ChargeSpeedRules.measuredPowerMw(currentNowUa, voltageMv)
        val speed = ChargeSpeedRules.classify(status, plugType, rated, measured)
        // Prefer Android's estimate; fall back to a rough one from charge counter and current.
        val platformTime = if (speed.isCharging) ChargeTimeRules.fromPlatform(chargeTimeRemainingMs) else null
        val estimatedTime = if (speed.isCharging && platformTime == null) {
            ChargeTimeRules.estimateMs(chargeCounterUah, pct, currentNowUa)
        } else {
            null
        }
        return BatterySnapshot(
            levelPct = pct,
            isCharging = charging,
            isFull = status == STATUS_FULL,
            plugType = plugType,
            tempC = if (tempTenths == TEMP_MISSING) null else tempTenths / 10f,
            readAtEpochMs = nowMs,
            chargeSpeed = speed,
            // Show what actually flows in; fall back to the charger rating.
            powerMw = if (charging) measured ?: rated else null,
            timeToFullMs = platformTime ?: estimatedTime,
            timeToFullEstimated = platformTime == null && estimatedTime != null,
        )
    }

    /** Maps the EXTRA_PLUGGED bit mask to a single [PlugType] (first match wins). */
    fun plugTypeOf(plugged: Int): PlugType = when {
        plugged <= 0 -> PlugType.NONE
        (plugged and PLUGGED_AC) != 0 -> PlugType.AC
        (plugged and PLUGGED_USB) != 0 -> PlugType.USB
        (plugged and PLUGGED_WIRELESS) != 0 -> PlugType.WIRELESS
        (plugged and PLUGGED_DOCK) != 0 -> PlugType.DOCK
        else -> PlugType.OTHER
    }
}
