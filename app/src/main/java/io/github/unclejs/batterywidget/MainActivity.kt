// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 UncleJS

package io.github.unclejs.batterywidget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import io.github.unclejs.batterywidget.battery.ChargeSpeed
import io.github.unclejs.batterywidget.battery.ChargeSpeedRules
import io.github.unclejs.batterywidget.battery.chargingDetail
import io.github.unclejs.batterywidget.battery.chargingLabel
import io.github.unclejs.batterywidget.widget.WidgetPalette
import io.github.unclejs.batterywidget.widget.WidgetUpdater
import io.github.unclejs.batterywidget.battery.formatPercent
import io.github.unclejs.batterywidget.battery.formatTemperature
import io.github.unclejs.batterywidget.battery.formatTimestamp
import io.github.unclejs.batterywidget.live.LiveBatteryService
import io.github.unclejs.batterywidget.settings.AppSettings
import io.github.unclejs.batterywidget.ui.BatteryTheme
import io.github.unclejs.batterywidget.ui.LicenseFooter
import io.github.unclejs.batterywidget.ui.ThemeToggleAction
import io.github.unclejs.batterywidget.ui.applySystemBars
import io.github.unclejs.batterywidget.ui.rememberBatterySnapshot
import io.github.unclejs.batterywidget.widget.BatteryWidget
import io.github.unclejs.batterywidget.widget.BatteryWidgetReceiver
import io.github.unclejs.batterywidget.work.Scheduler
import kotlinx.coroutines.launch

/** Legend rows: tier and the power range it covers. */
private val CHARGE_LEGEND: List<Pair<ChargeSpeed, String>> = listOf(
    ChargeSpeed.NOT_CHARGING to "on battery",
    ChargeSpeed.PLUGGED_NOT_CHARGING to "paused / limit",
    ChargeSpeed.SLOW to "< ${ChargeSpeedRules.NORMAL_MW / 1000.0} W",
    ChargeSpeed.NORMAL to "${ChargeSpeedRules.NORMAL_MW / 1000.0}–${ChargeSpeedRules.FAST_MW / 1000} W",
    ChargeSpeed.FAST to "${ChargeSpeedRules.FAST_MW / 1000}–${ChargeSpeedRules.SUPER_FAST_MW / 1000} W",
    ChargeSpeed.SUPER_FAST to "${ChargeSpeedRules.SUPER_FAST_MW / 1000}–${ChargeSpeedRules.ULTRA_FAST_MW / 1000} W",
    ChargeSpeed.ULTRA_FAST to "≥ ${ChargeSpeedRules.ULTRA_FAST_MW / 1000} W",
    ChargeSpeed.CHARGING to "speed unknown",
    ChargeSpeed.FULL to "100 %, plugged in",
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        applySystemBars(dark = true)
        super.onCreate(savedInstanceState)
        setContent {
            val darkFlow = remember { AppSettings.darkTheme(applicationContext) }
            val dark by darkFlow.collectAsStateWithLifecycle(initialValue = true)
            LaunchedEffect(dark) { applySystemBars(dark) }
            BatteryTheme(dark = dark) {
                MainScreen(dark = dark)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(dark: Boolean) {
    val appContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var refreshKey by remember { mutableIntStateOf(0) }
    val snapshot by rememberBatterySnapshot(refreshKey)
    val palette = WidgetPalette.of(dark)

    val liveFlow = remember { AppSettings.liveUpdates(appContext) }
    val live by liveFlow.collectAsStateWithLifecycle(initialValue = false)
    var notificationsDenied by remember { mutableStateOf(false) }

    val enableLive: () -> Unit = {
        scope.launch { AppSettings.setLiveUpdates(appContext, true) }
        LiveBatteryService.start(appContext)
    }
    val disableLive: () -> Unit = {
        scope.launch { AppSettings.setLiveUpdates(appContext, false) }
        LiveBatteryService.stop(appContext)
    }
    // The foreground service runs without the permission too; the notification is just hidden.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsDenied = !granted
        enableLive()
    }

    // Restart the live service if it was enabled but got stopped (e.g. force stop).
    LaunchedEffect(live) {
        if (live) LiveBatteryService.start(appContext)
    }
    // Make sure background work exists when widgets are already placed.
    LaunchedEffect(Unit) {
        val ids = GlanceAppWidgetManager(appContext).getGlanceIds(BatteryWidget::class.java)
        if (ids.isNotEmpty()) Scheduler.schedule(appContext)
    }

    val appWidgetManager = remember { AppWidgetManager.getInstance(appContext) }
    val canPin = remember { appWidgetManager.isRequestPinAppWidgetSupported }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Battery Widget") },
                actions = {
                    IconButton(
                        onClick = {
                            refreshKey++
                            scope.launch {
                                WidgetUpdater.refreshAll(appContext)
                                Toast.makeText(appContext, "Battery data refreshed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.semantics { contentDescription = "Refresh now" },
                    ) {
                        Text("↻", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    ThemeToggleAction(dark = dark)
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                LicenseFooter(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val s = snapshot
                    Text(
                        text = s?.let { formatPercent(it.levelPct) } ?: "--%",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (s != null) {
                        val chargeColor = palette.chargeColor(s.chargeSpeed)
                        Text(chargingLabel(s), style = MaterialTheme.typography.bodyLarge, color = chargeColor)
                        chargingDetail(s)?.let {
                            Text(it, style = MaterialTheme.typography.bodyLarge, color = chargeColor)
                        }
                        s.tempC?.let { Text(formatTemperature(it), style = MaterialTheme.typography.bodyLarge) }
                        Text(
                            "Last read: ${formatTimestamp(s.readAtEpochMs)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text("Battery information is not available.")
                    }
                }
            }

            Text("Charging colours", style = MaterialTheme.typography.titleMedium)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = palette.background,
                    contentColor = palette.text,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for ((speed, range) in CHARGE_LEGEND) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                speed.label,
                                color = palette.chargeColor(speed),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text(range, color = palette.secondaryText, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Text(
                "Speed is based on charging power: the charger's rating or what the battery " +
                    "actually draws, whichever is higher. Some phones don't report these, " +
                    "then it just shows \"Charging\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Add the widget", style = MaterialTheme.typography.titleMedium)
            Text("Long-press home screen → Widgets → Battery Widget")
            if (canPin) {
                Button(
                    onClick = {
                        appWidgetManager.requestPinAppWidget(
                            ComponentName(appContext, BatteryWidgetReceiver::class.java),
                            null,
                            null,
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text("Add widget to home screen")
                }
            }

            Text("Updates", style = MaterialTheme.typography.titleMedium)
            Text(
                "The widget refreshes every 15 minutes, shortly after you plug in a charger, " +
                    "and whenever you tap it or its ↻ button (↻ above refreshes everything " +
                    "immediately too). Long-press the widget to change its text sizes " +
                    "and which lines it shows.",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Live updates (persistent notification)", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Keeps a small service running so the widget follows every change " +
                            "in level and charging state. Uses a little more battery.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = live,
                    onCheckedChange = { on ->
                        if (on) {
                            val granted = ContextCompat.checkSelfPermission(
                                appContext,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                notificationsDenied = false
                                enableLive()
                            } else {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } else {
                            disableLive()
                        }
                    },
                )
            }
            if (notificationsDenied && live) {
                Text(
                    "Notifications are off, so the live-update notification is hidden. " +
                        "Live updates still work.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
