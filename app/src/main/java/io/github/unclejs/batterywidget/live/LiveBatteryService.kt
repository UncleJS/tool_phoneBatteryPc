// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 UncleJS

package io.github.unclejs.batterywidget.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.unclejs.batterywidget.MainActivity
import io.github.unclejs.batterywidget.R
import io.github.unclejs.batterywidget.battery.BatteryReader
import io.github.unclejs.batterywidget.battery.BatterySnapshot
import io.github.unclejs.batterywidget.battery.chargingDetail
import io.github.unclejs.batterywidget.battery.chargingLabel
import io.github.unclejs.batterywidget.battery.formatPercent
import io.github.unclejs.batterywidget.battery.formatTemperature
import io.github.unclejs.batterywidget.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Optional foreground service ("specialUse") that listens to ACTION_BATTERY_CHANGED and
 * keeps the widgets live. Updates are throttled by [LiveThrottle].
 */
class LiveBatteryService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var registered = false
    private var lastPushed: BatterySnapshot? = null
    private var lastPushMs = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_BATTERY_CHANGED) return
            onSnapshot(BatteryReader.fromIntent(context, intent))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must reach startForeground quickly after startForegroundService().
        startForeground(
            NOTIFICATION_ID,
            buildNotification(BatteryReader.read(this)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        if (!registered) {
            // ACTION_BATTERY_CHANGED is a protected system broadcast: it is delivered to
            // non-exported receivers too. Registering also delivers the current sticky value.
            registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
            registered = true
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (registered) {
            unregisterReceiver(receiver)
            registered = false
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun onSnapshot(snapshot: BatterySnapshot) {
        val now = System.currentTimeMillis()
        if (!LiveThrottle.shouldPush(lastPushed, snapshot, lastPushMs, now)) return
        lastPushed = snapshot
        lastPushMs = now
        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, buildNotification(snapshot))
        scope.launch {
            try {
                WidgetUpdater.refreshAllWith(applicationContext, snapshot)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "Live widget refresh failed", e)
            }
        }
    }

    private fun buildNotification(snapshot: BatterySnapshot?): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (snapshot != null) "Battery ${formatPercent(snapshot.levelPct)}" else "Battery"
        val text = snapshot?.let { s ->
            listOfNotNull(chargingLabel(s), chargingDetail(s), s.tempC?.let { formatTemperature(it) }).joinToString(" · ")
        } ?: "Keeping the widget up to date"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val TAG = "LiveBatteryService"
        private const val CHANNEL_ID = "live_battery"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, LiveBatteryService::class.java))
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException (background start) extends it.
                Log.w(TAG, "Could not start live service", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LiveBatteryService::class.java))
        }

        private fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(CHANNEL_ID, "Live battery", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Persistent notification while live widget updates are on"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }
}
