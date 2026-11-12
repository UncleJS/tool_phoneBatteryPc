// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 UncleJS

package io.github.unclejs.batterywidget.live

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import io.github.unclejs.batterywidget.settings.AppSettings
import io.github.unclejs.batterywidget.widget.BatteryWidget
import io.github.unclejs.batterywidget.widget.WidgetUpdater
import io.github.unclejs.batterywidget.work.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** After boot or an app update: reschedule work and restart the live service if enabled. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val hasWidgets = GlanceAppWidgetManager(app).getGlanceIds(BatteryWidget::class.java).isNotEmpty()
                if (hasWidgets) Scheduler.schedule(app)
                // BOOT_COMPLETED / MY_PACKAGE_REPLACED are exempt from background FGS start limits.
                if (AppSettings.liveUpdates(app).first()) LiveBatteryService.start(app)
                if (hasWidgets) WidgetUpdater.refreshAll(app)
            } catch (e: Exception) {
                Log.w("BootReceiver", "Boot handling failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
