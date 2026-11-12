// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 UncleJS

package io.github.unclejs.batterywidget.live

// Pure Kotlin (no android.* imports) so it can be unit tested on the JVM.

import io.github.unclejs.batterywidget.battery.BatterySnapshot
import kotlin.math.abs

/**
 * Decides when the live service pushes a new reading to the widgets.
 * ACTION_BATTERY_CHANGED fires often (voltage, current, temperature), so most
 * broadcasts are ignored to save battery:
 *
 * - level, charging, full, plug type or charge speed tier changed: push at once;
 * - temperature moved by at least [TEMP_DELTA_C] or charging power by at least
 *   [POWER_DELTA_MW]: push, but at most once per [MIN_INTERVAL_MS];
 * - anything else: skip.
 */
object LiveThrottle {
    const val MIN_INTERVAL_MS = 60_000L
    const val TEMP_DELTA_C = 0.5f
    const val POWER_DELTA_MW = 1_000

    // Float subtraction of tenths (e.g. 31.7 - 31.2) can land just below 0.5.
    private const val EPSILON = 1e-3f

    fun shouldPush(last: BatterySnapshot?, next: BatterySnapshot, lastPushMs: Long, nowMs: Long): Boolean {
        if (last == null) return true
        if (last.levelPct != next.levelPct ||
            last.isCharging != next.isCharging ||
            last.isFull != next.isFull ||
            last.plugType != next.plugType ||
            last.chargeSpeed != next.chargeSpeed
        ) {
            return true
        }
        val a = last.tempC
        val b = next.tempC
        val tempChanged = when {
            a == null && b == null -> false
            a == null || b == null -> true
            else -> abs(a - b) >= TEMP_DELTA_C - EPSILON
        }
        val p = last.powerMw
        val q = next.powerMw
        val powerChanged = when {
            p == null && q == null -> false
            p == null || q == null -> true
            else -> abs(p - q) >= POWER_DELTA_MW
        }
        return (tempChanged || powerChanged) && nowMs - lastPushMs >= MIN_INTERVAL_MS
    }
}
