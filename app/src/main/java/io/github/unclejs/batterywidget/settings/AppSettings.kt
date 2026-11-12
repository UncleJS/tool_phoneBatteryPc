// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 UncleJS

package io.github.unclejs.batterywidget.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

// Single process-wide DataStore instance (the delegate is a singleton per file name).
private val Context.appSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/** App-wide settings (not per widget). */
object AppSettings {
    private val DARK_THEME = booleanPreferencesKey("dark_theme")
    private val LIVE_UPDATES = booleanPreferencesKey("live_updates")

    private fun data(context: Context): Flow<Preferences> =
        context.applicationContext.appSettingsStore.data.catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }

    /** Dark theme is the default. */
    fun darkTheme(context: Context): Flow<Boolean> =
        data(context).map { it[DARK_THEME] ?: true }.distinctUntilChanged()

    /** Live (foreground service) updates are off by default. */
    fun liveUpdates(context: Context): Flow<Boolean> =
        data(context).map { it[LIVE_UPDATES] ?: false }.distinctUntilChanged()

    suspend fun setDarkTheme(context: Context, dark: Boolean) {
        context.applicationContext.appSettingsStore.edit { it[DARK_THEME] = dark }
    }

    suspend fun setLiveUpdates(context: Context, enabled: Boolean) {
        context.applicationContext.appSettingsStore.edit { it[LIVE_UPDATES] = enabled }
    }
}
