// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 UncleJS

package io.github.unclejs.batterywidget.widget

import androidx.compose.ui.graphics.Color
import io.github.unclejs.batterywidget.battery.ChargeSpeed

/** Widget colors, shared by the Glance widget, the in-app preview and the legend. */
data class WidgetPalette(
    val background: Color,
    val text: Color,
    val secondaryText: Color,
    /** Colour of the charging line for each [ChargeSpeed]. */
    val charge: Map<ChargeSpeed, Color>,
) {
    fun chargeColor(speed: ChargeSpeed): Color = charge[speed] ?: text

    companion object {
        val Dark = WidgetPalette(
            background = Color(0xFF121212),
            text = Color(0xFFF2F2F2),
            secondaryText = Color(0xFFB8B8B8),
            charge = mapOf(
                ChargeSpeed.NOT_CHARGING to Color(0xFFFFFFFF),         // white
                ChargeSpeed.PLUGGED_NOT_CHARGING to Color(0xFF90A4AE), // blue grey
                ChargeSpeed.FULL to Color(0xFF69F0AE),                 // green
                ChargeSpeed.CHARGING to Color(0xFFFFF59D),             // pale yellow
                ChargeSpeed.SLOW to Color(0xFFFFA726),                 // orange
                ChargeSpeed.NORMAL to Color(0xFFFFEE58),               // yellow
                ChargeSpeed.FAST to Color(0xFF4FC3F7),                 // light blue
                ChargeSpeed.SUPER_FAST to Color(0xFFB388FF),           // violet
                ChargeSpeed.ULTRA_FAST to Color(0xFFFF4081),           // pink
            ),
        )
        // Darker shades of the same hues so they stay readable on white.
        val Light = WidgetPalette(
            background = Color(0xFFFFFFFF),
            text = Color(0xFF1B1B1B),
            secondaryText = Color(0xFF5A5A5A),
            charge = mapOf(
                ChargeSpeed.NOT_CHARGING to Color(0xFF1B1B1B),
                ChargeSpeed.PLUGGED_NOT_CHARGING to Color(0xFF546E7A),
                ChargeSpeed.FULL to Color(0xFF2E7D32),
                ChargeSpeed.CHARGING to Color(0xFF8D6E00),
                ChargeSpeed.SLOW to Color(0xFFE65100),
                ChargeSpeed.NORMAL to Color(0xFF9E7C00),
                ChargeSpeed.FAST to Color(0xFF0277BD),
                ChargeSpeed.SUPER_FAST to Color(0xFF5E35B1),
                ChargeSpeed.ULTRA_FAST to Color(0xFFC51162),
            ),
        )

        fun of(dark: Boolean): WidgetPalette = if (dark) Dark else Light
    }
}
