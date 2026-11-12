// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 UncleJS

package io.github.unclejs.batterywidget.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Background refresh scheduling.
 *
 * 1. A unique periodic job every 15 minutes (the WorkManager minimum).
 * 2. A unique one-time job constrained to "device is charging", so plugging in the phone
 *    refreshes the widget soon after. It re-enqueues itself after each run.
 *
 * Tradeoff of the charging job: WorkManager has no "on plug-in" edge trigger, only a
 * "while charging" constraint. Re-enqueueing immediately would make the job run again and
 * again for as long as the phone stays plugged in. So the follow-up job gets a 15-minute
 * initial delay: while plugged in it costs at most one extra refresh per 15 minutes, but a
 * plug-in that happens within 15 minutes of the previous charging run is only picked up
 * when that delay has passed (or by the periodic job / a tap / live mode).
 */
object Scheduler {
    const val PERIODIC_WORK = "battery_widget_periodic"
    const val CHARGING_WORK = "battery_widget_charging"
    const val KEY_CHARGING_TRIGGER = "charging_trigger"

    private const val PERIOD_MINUTES = 15L
    private const val CHARGING_REARM_DELAY_MINUTES = 15L

    /** Ensures both jobs exist. Safe to call repeatedly (KEEP). */
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context.applicationContext)
        val periodic = PeriodicWorkRequestBuilder<BatteryUpdateWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, periodic)
        wm.enqueueUniqueWork(CHARGING_WORK, ExistingWorkPolicy.KEEP, chargingRequest(delayMinutes = 0L))
    }

    /**
     * Called by the charging job itself when it finishes. APPEND_OR_REPLACE (not REPLACE)
     * because REPLACE would cancel the job that is currently running, i.e. the caller.
     */
    fun rearmChargingTrigger(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            CHARGING_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            chargingRequest(delayMinutes = CHARGING_REARM_DELAY_MINUTES),
        )
    }

    /** Removes all background jobs (last widget removed). */
    fun cancel(context: Context) {
        val wm = WorkManager.getInstance(context.applicationContext)
        wm.cancelUniqueWork(PERIODIC_WORK)
        wm.cancelUniqueWork(CHARGING_WORK)
    }

    private fun chargingRequest(delayMinutes: Long): OneTimeWorkRequest {
        val builder = OneTimeWorkRequestBuilder<BatteryUpdateWorker>()
            .setConstraints(Constraints.Builder().setRequiresCharging(true).build())
            .setInputData(Data.Builder().putBoolean(KEY_CHARGING_TRIGGER, true).build())
        if (delayMinutes > 0L) builder.setInitialDelay(delayMinutes, TimeUnit.MINUTES)
        return builder.build()
    }
}
