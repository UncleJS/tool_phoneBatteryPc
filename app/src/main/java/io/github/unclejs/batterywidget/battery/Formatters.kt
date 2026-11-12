// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 UncleJS

package io.github.unclejs.batterywidget.battery

// Pure Kotlin/Java (no android.* imports) so plain JVM unit tests can use it.

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Allowed text sizes (sp) for the widget. */
object SizeLimits {
    const val PCT_MIN = 12
    const val PCT_MAX = 120
    const val PCT_DEFAULT = 48
    const val DETAIL_MIN = 8
    const val DETAIL_MAX = 32
    const val DETAIL_DEFAULT = 12
}

// Fixed 24-hour pattern; Locale.ROOT keeps digits ASCII regardless of device locale.
private val TIMESTAMP_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

/** Formats [epochMs] in [zone] (local time by default) as `yyyy-MM-dd HH:mm:ss`. */
fun formatTimestamp(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(epochMs).atZone(zone))

/** `31.2 °C` (always one decimal, '.' separator). */
fun formatTemperature(tempC: Float): String =
    String.format(Locale.ROOT, "%.1f °C", tempC)

/** `87%`. */
fun formatPercent(levelPct: Int): String = "$levelPct%"

/** `⚡ Fast charging`, `Full`, `Not charging`, ... (see [ChargeSpeed]). */
fun chargingLabel(snapshot: BatterySnapshot): String = snapshot.chargeSpeed.label

/** `AC · 23.4 W`, `USB`, `Wireless` ...; null when nothing is plugged in. */
fun chargingDetail(snapshot: BatterySnapshot): String? {
    if (snapshot.plugType == PlugType.NONE) return null
    val parts = listOfNotNull(
        snapshot.plugType.label,
        snapshot.powerMw?.takeIf { snapshot.chargeSpeed.isCharging }?.let { formatPower(it) },
    )
    return parts.joinToString(" · ").ifEmpty { null }
}

/** `23.4 W` (one decimal, '.' separator). */
fun formatPower(powerMw: Int): String =
    String.format(Locale.ROOT, "%.1f W", powerMw / 1000f)

/** `1 h 23 min`, `23 min`, `< 1 min` (rounded down to whole minutes). */
fun formatDuration(ms: Long): String {
    val totalMin = ms.coerceAtLeast(0L) / 60_000L
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        totalMin < 1 -> "< 1 min"
        h == 0L -> "$m min"
        m == 0L -> "$h h"
        else -> "$h h $m min"
    }
}

/** `Full in 1 h 23 min` (`~` marks the app's own estimate); null when not charging or unknown. */
fun timeToFullLabel(snapshot: BatterySnapshot): String? {
    val ms = snapshot.timeToFullMs ?: return null
    if (!snapshot.chargeSpeed.isCharging) return null
    val prefix = if (snapshot.timeToFullEstimated) "~" else ""
    return "Full in $prefix${formatDuration(ms)}"
}

fun clampPctSize(sp: Int): Int = sp.coerceIn(SizeLimits.PCT_MIN, SizeLimits.PCT_MAX)

fun clampDetailSize(sp: Int): Int = sp.coerceIn(SizeLimits.DETAIL_MIN, SizeLimits.DETAIL_MAX)
