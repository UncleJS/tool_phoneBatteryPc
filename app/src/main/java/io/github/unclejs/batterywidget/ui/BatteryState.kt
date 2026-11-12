// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 UncleJS

package io.github.unclejs.batterywidget.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.unclejs.batterywidget.battery.BatteryReader
import io.github.unclejs.batterywidget.battery.BatterySnapshot

/**
 * Current battery state, kept up to date while the calling composable is on screen.
 * Changing [refreshKey] forces an immediate re-read (used by the refresh button).
 */
@Composable
fun rememberBatterySnapshot(refreshKey: Int = 0): State<BatterySnapshot?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(BatteryReader.read(context)) }
    LaunchedEffect(refreshKey) {
        if (refreshKey != 0) state.value = BatteryReader.read(context)
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                state.value = BatteryReader.fromIntent(c, intent)
            }
        }
        // Protected system broadcast: delivered to non-exported receivers as well.
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}
