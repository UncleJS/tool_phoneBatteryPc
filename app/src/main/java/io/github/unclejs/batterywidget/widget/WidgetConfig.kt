// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 UncleJS

package io.github.unclejs.batterywidget.widget

import io.github.unclejs.batterywidget.battery.SizeLimits
import io.github.unclejs.batterywidget.battery.clampDetailSize
import io.github.unclejs.batterywidget.battery.clampPctSize

/** Per-widget display settings. */
data class WidgetConfig(
    val pctSizeSp: Int = SizeLimits.PCT_DEFAULT,
    val detailSizeSp: Int = SizeLimits.DETAIL_DEFAULT,
    val showCharging: Boolean = true,
    val showTemp: Boolean = true,
    val showTime: Boolean = true,
    val showRefreshButton: Boolean = true,
) {
    fun clamped(): WidgetConfig = copy(
        pctSizeSp = clampPctSize(pctSizeSp),
        detailSizeSp = clampDetailSize(detailSizeSp),
    )
}
