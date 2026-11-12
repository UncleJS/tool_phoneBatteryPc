// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 UncleJS

package io.github.unclejs.batterywidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import io.github.unclejs.batterywidget.work.Scheduler

/**
 * AppWidgetProvider for [BatteryWidget]. Keeps background work scheduled while at least one
 * widget exists. The optional live service is controlled only by the app setting: it is
 * neither started here (background FGS starts are restricted) nor stopped on removal.
 */
class BatteryWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = BatteryWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Scheduler.schedule(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // KEEP policies make this a no-op when the work is already scheduled.
        Scheduler.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Scheduler.cancel(context)
    }
}
