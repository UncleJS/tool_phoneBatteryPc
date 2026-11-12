// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 UncleJS

package io.github.unclejs.batterywidget.battery

// Pure Kotlin (no android.* imports) so it can be used from plain JVM unit tests.

/** Power source the device is plugged into. [label] is shown in the UI, null means "no label". */
enum class PlugType(val label: String?) {
    NONE(null),
    AC("AC"),
    USB("USB"),
    WIRELESS("Wireless"),
    DOCK("Dock"),
    OTHER(null);

    companion object {
        /** Lenient lookup used when reading a stored name back from widget state. */
        fun fromName(name: String?): PlugType =
            entries.firstOrNull { it.name == name } ?: NONE
    }
}

/**
 * What the charger is doing, used for the label and colour of the charging line.
 * The charging tiers are based on charging power (see [ChargeSpeedRules]).
 */
enum class ChargeSpeed(val label: String) {
    /** Running on battery. */
    NOT_CHARGING("Not charging"),
    /** Plugged in but the battery is not charging (charge limit, too hot, weak charger). */
    PLUGGED_NOT_CHARGING("Plugged in, not charging"),
    FULL("Full"),
    /** Charging, but the device reports no usable power figures. */
    CHARGING("⚡ Charging"),
    SLOW("⚡ Slow charging"),
    NORMAL("⚡ Normal charging"),
    FAST("⚡ Fast charging"),
    SUPER_FAST("⚡ Super fast charging"),
    ULTRA_FAST("⚡ Ultra fast charging");

    val isCharging: Boolean
        get() = this == CHARGING || this == SLOW || this == NORMAL ||
            this == FAST || this == SUPER_FAST || this == ULTRA_FAST

    companion object {
        /** Lenient lookup used when reading a stored name back from widget state. */
        fun fromName(name: String?): ChargeSpeed? = entries.firstOrNull { it.name == name }
    }
}

/** One reading of the battery state. */
data class BatterySnapshot(
    /** 0..100. */
    val levelPct: Int,
    val isCharging: Boolean,
    val isFull: Boolean,
    val plugType: PlugType,
    /** Degrees Celsius, or null when the device does not report a temperature. */
    val tempC: Float?,
    val readAtEpochMs: Long,
    /** Charger state / speed tier. */
    val chargeSpeed: ChargeSpeed = when {
        isCharging -> ChargeSpeed.CHARGING
        isFull -> ChargeSpeed.FULL
        else -> ChargeSpeed.NOT_CHARGING
    },
    /** Power currently flowing into the battery in milliwatts, or null if unknown. */
    val powerMw: Int? = null,
    /** Time until full in milliseconds while charging, or null if unknown / not charging. */
    val timeToFullMs: Long? = null,
    /** True when [timeToFullMs] is the app's own rough estimate rather than Android's. */
    val timeToFullEstimated: Boolean = false,
)
