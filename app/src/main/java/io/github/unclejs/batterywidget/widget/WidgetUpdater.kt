// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 UncleJS

package io.github.unclejs.batterywidget.widget

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import io.github.unclejs.batterywidget.battery.BatteryReader
import io.github.unclejs.batterywidget.battery.BatterySnapshot
import io.github.unclejs.batterywidget.battery.ChargeSpeed
import io.github.unclejs.batterywidget.battery.PlugType
import io.github.unclejs.batterywidget.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

/** Writes battery readings and settings into each widget's Glance state and redraws it. */
object WidgetUpdater {

    /** Keys of the per-widget Glance state (PreferencesGlanceStateDefinition). */
    object Keys {
        val PCT_SIZE = intPreferencesKey("pctSizeSp")
        val DETAIL_SIZE = intPreferencesKey("detailSizeSp")
        val SHOW_CHARGING = booleanPreferencesKey("showCharging")
        val SHOW_TEMP = booleanPreferencesKey("showTemp")
        val SHOW_TIME = booleanPreferencesKey("showTime")
        val SHOW_REFRESH = booleanPreferencesKey("showRefresh")

        val LEVEL = intPreferencesKey("level")
        val CHARGING = booleanPreferencesKey("charging")
        val FULL = booleanPreferencesKey("full")
        val PLUG = stringPreferencesKey("plug")
        val TEMP_TENTHS = intPreferencesKey("tempTenths")
        val READ_AT = longPreferencesKey("readAtMs")
        val SPEED = stringPreferencesKey("chargeSpeed")
        val POWER_MW = intPreferencesKey("powerMw")

        /** Copy of the app theme so the widget can render without reading app settings. */
        val DARK = booleanPreferencesKey("darkTheme")
    }

    // Serialises updates coming from the worker, the live service and the UI.
    private val mutex = Mutex()

    /** Reads the battery now and refreshes every placed widget. */
    suspend fun refreshAll(context: Context) {
        val app = context.applicationContext
        refreshAllWith(app, BatteryReader.read(app))
    }

    /** Refreshes every placed widget with [snapshot] (null = keep the stored reading). */
    suspend fun refreshAllWith(context: Context, snapshot: BatterySnapshot?) {
        val app = context.applicationContext
        val dark = AppSettings.darkTheme(app).first()
        mutex.withLock {
            val ids = GlanceAppWidgetManager(app).getGlanceIds(BatteryWidget::class.java)
            val widget = BatteryWidget()
            for (id in ids) {
                updateAppWidgetState(app, id) { prefs ->
                    if (snapshot != null) writeSnapshot(prefs, snapshot)
                    prefs[Keys.DARK] = dark
                }
                widget.update(app, id)
            }
        }
    }

    /** Stores [config] for one widget, together with a fresh reading, and redraws it. */
    suspend fun applyConfig(context: Context, glanceId: GlanceId, config: WidgetConfig) {
        val app = context.applicationContext
        val safe = config.clamped()
        val snapshot = BatteryReader.read(app)
        val dark = AppSettings.darkTheme(app).first()
        mutex.withLock {
            updateAppWidgetState(app, glanceId) { prefs ->
                prefs[Keys.PCT_SIZE] = safe.pctSizeSp
                prefs[Keys.DETAIL_SIZE] = safe.detailSizeSp
                prefs[Keys.SHOW_CHARGING] = safe.showCharging
                prefs[Keys.SHOW_TEMP] = safe.showTemp
                prefs[Keys.SHOW_TIME] = safe.showTime
                prefs[Keys.SHOW_REFRESH] = safe.showRefreshButton
                if (snapshot != null) writeSnapshot(prefs, snapshot)
                prefs[Keys.DARK] = dark
            }
            BatteryWidget().update(app, glanceId)
        }
    }

    /** Loads the stored config of one widget (defaults if never configured). */
    suspend fun loadConfig(context: Context, glanceId: GlanceId): WidgetConfig {
        val prefs = getAppWidgetState(context.applicationContext, PreferencesGlanceStateDefinition, glanceId)
        return configFrom(prefs)
    }

    fun configFrom(prefs: Preferences): WidgetConfig {
        val defaults = WidgetConfig()
        return WidgetConfig(
            pctSizeSp = prefs[Keys.PCT_SIZE] ?: defaults.pctSizeSp,
            detailSizeSp = prefs[Keys.DETAIL_SIZE] ?: defaults.detailSizeSp,
            showCharging = prefs[Keys.SHOW_CHARGING] ?: defaults.showCharging,
            showTemp = prefs[Keys.SHOW_TEMP] ?: defaults.showTemp,
            showTime = prefs[Keys.SHOW_TIME] ?: defaults.showTime,
            showRefreshButton = prefs[Keys.SHOW_REFRESH] ?: defaults.showRefreshButton,
        ).clamped()
    }

    /** The last stored reading, or null if the widget has never received one. */
    fun snapshotFrom(prefs: Preferences): BatterySnapshot? {
        val level = prefs[Keys.LEVEL] ?: return null
        val base = BatterySnapshot(
            levelPct = level,
            isCharging = prefs[Keys.CHARGING] ?: false,
            isFull = prefs[Keys.FULL] ?: false,
            plugType = PlugType.fromName(prefs[Keys.PLUG]),
            tempC = prefs[Keys.TEMP_TENTHS]?.let { it / 10f },
            readAtEpochMs = prefs[Keys.READ_AT] ?: 0L,
            powerMw = prefs[Keys.POWER_MW],
        )
        // Older state has no speed: keep the default derived from charging/full.
        val speed = ChargeSpeed.fromName(prefs[Keys.SPEED]) ?: return base
        return base.copy(chargeSpeed = speed)
    }

    private fun writeSnapshot(prefs: MutablePreferences, s: BatterySnapshot) {
        prefs[Keys.LEVEL] = s.levelPct
        prefs[Keys.CHARGING] = s.isCharging
        prefs[Keys.FULL] = s.isFull
        prefs[Keys.PLUG] = s.plugType.name
        val temp = s.tempC
        if (temp != null) {
            prefs[Keys.TEMP_TENTHS] = (temp * 10f).roundToInt()
        } else {
            prefs.remove(Keys.TEMP_TENTHS)
        }
        prefs[Keys.READ_AT] = s.readAtEpochMs
        prefs[Keys.SPEED] = s.chargeSpeed.name
        val power = s.powerMw
        if (power != null) prefs[Keys.POWER_MW] = power else prefs.remove(Keys.POWER_MW)
    }
}
