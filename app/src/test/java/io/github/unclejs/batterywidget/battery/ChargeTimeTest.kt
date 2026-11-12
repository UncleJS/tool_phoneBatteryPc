// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 UncleJS

package io.github.unclejs.batterywidget.battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChargeTimeTest {

    private val hourMs = 3_600_000L

    @Test
    fun platformValue() {
        assertNull(ChargeTimeRules.fromPlatform(-1L))
        assertEquals(0L, ChargeTimeRules.fromPlatform(0L))
        assertEquals(5_400_000L, ChargeTimeRules.fromPlatform(5_400_000L))
        assertNull(ChargeTimeRules.fromPlatform(25 * hourMs))
    }

    @Test
    fun linearEstimate() {
        // 2 Ah at 50 % → 2 Ah to go at 1 A → 2 h.
        assertEquals(2 * hourMs, ChargeTimeRules.estimateMs(2_000_000, 50, 1_000_000))
        // Negative current (device sign convention) is treated the same.
        assertEquals(2 * hourMs, ChargeTimeRules.estimateMs(2_000_000, 50, -1_000_000))
        // 4 Ah at 80 % → 1 Ah to go at 2 A → 30 min.
        assertEquals(hourMs / 2, ChargeTimeRules.estimateMs(4_000_000, 80, 2_000_000))
    }

    @Test
    fun estimateRejectsUnusableInputs() {
        assertNull(ChargeTimeRules.estimateMs(2_000_000, 0, 1_000_000))
        assertNull(ChargeTimeRules.estimateMs(2_000_000, 100, 1_000_000))
        assertNull(ChargeTimeRules.estimateMs(Int.MIN_VALUE, 50, 1_000_000))
        assertNull(ChargeTimeRules.estimateMs(0, 50, 1_000_000))
        assertNull(ChargeTimeRules.estimateMs(2_000_000, 50, 0))
        assertNull(ChargeTimeRules.estimateMs(2_000_000, 50, Int.MIN_VALUE))
        // 2 Ah to go at 10 mA → 200 h: out of range.
        assertNull(ChargeTimeRules.estimateMs(2_000_000, 50, 10_000))
    }
}
