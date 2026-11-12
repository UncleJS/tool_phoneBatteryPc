// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 UncleJS

package io.github.unclejs.batterywidget.battery

// Pure Kotlin (no android.* imports) so it can be unit tested on the JVM.

/** Rules for the "time until full" figure. */
object ChargeTimeRules {
    const val MIN_MS = 60_000L
    const val MAX_MS = 24 * 60 * 60_000L
    private const val MS_PER_HOUR = 3_600_000.0

    /** BatteryManager.computeChargeTimeRemaining(): -1 (or any negative) means unknown. */
    fun fromPlatform(ms: Long): Long? = ms.takeIf { it >= 0L && it <= MAX_MS }

    /**
     * Linear estimate from the charge counter (µAh) and current (µA):
     * full ≈ counter × 100 / level, time = (full − counter) / |current|.
     * Returns null when the inputs are unusable or the result is outside [MIN_MS]..[MAX_MS].
     * Charging slows near 100 %, so this tends to be optimistic.
     */
    fun estimateMs(chargeCounterUah: Int, levelPct: Int, currentNowUa: Int): Long? {
        if (chargeCounterUah <= 0 || levelPct !in 1..99) return null
        if (currentNowUa == Int.MIN_VALUE || currentNowUa == 0) return null
        // Sign conventions differ between devices; callers only use this while charging.
        val current = kotlin.math.abs(currentNowUa.toDouble())
        val full = chargeCounterUah.toDouble() * 100.0 / levelPct
        val remaining = full - chargeCounterUah
        if (remaining <= 0.0) return null
        val ms = (remaining / current * MS_PER_HOUR).toLong()
        return ms.takeIf { it in MIN_MS..MAX_MS }
    }
}
