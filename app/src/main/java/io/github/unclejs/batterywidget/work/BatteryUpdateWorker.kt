// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 UncleJS

package io.github.unclejs.batterywidget.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.unclejs.batterywidget.widget.WidgetUpdater
import kotlinx.coroutines.CancellationException

/** Refreshes all widgets. When started as the charging trigger it re-arms that trigger. */
class BatteryUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        try {
            WidgetUpdater.refreshAll(applicationContext)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Widget refresh failed", e)
        }
        if (inputData.getBoolean(Scheduler.KEY_CHARGING_TRIGGER, false)) {
            Scheduler.rearmChargingTrigger(applicationContext)
        }
        // Always succeed: an appended follow-up job only runs after a successful one.
        return Result.success()
    }

    private companion object {
        const val TAG = "BatteryUpdateWorker"
    }
}
