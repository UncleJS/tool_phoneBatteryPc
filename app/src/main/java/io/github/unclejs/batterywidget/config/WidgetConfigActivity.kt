// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 UncleJS

package io.github.unclejs.batterywidget.config

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.unclejs.batterywidget.battery.BatterySnapshot
import io.github.unclejs.batterywidget.battery.SizeLimits
import io.github.unclejs.batterywidget.battery.chargingDetail
import io.github.unclejs.batterywidget.battery.chargingLabel
import io.github.unclejs.batterywidget.battery.clampDetailSize
import io.github.unclejs.batterywidget.battery.clampPctSize
import io.github.unclejs.batterywidget.battery.formatPercent
import io.github.unclejs.batterywidget.battery.formatTemperature
import io.github.unclejs.batterywidget.battery.formatTimestamp
import io.github.unclejs.batterywidget.settings.AppSettings
import io.github.unclejs.batterywidget.ui.BatteryTheme
import io.github.unclejs.batterywidget.ui.LicenseFooter
import io.github.unclejs.batterywidget.ui.ThemeToggleAction
import io.github.unclejs.batterywidget.ui.applySystemBars
import io.github.unclejs.batterywidget.ui.rememberBatterySnapshot
import io.github.unclejs.batterywidget.widget.WidgetConfig
import io.github.unclejs.batterywidget.widget.WidgetPalette
import io.github.unclejs.batterywidget.widget.WidgetUpdater
import io.github.unclejs.batterywidget.work.Scheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Configures one widget instance (on placement and via "reconfigure"). */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        applySystemBars(dark = true)
        super.onCreate(savedInstanceState)

        // If the user backs out, the host must see CANCELED.
        setResult(RESULT_CANCELED)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, resultValue)

        val glanceId = try {
            GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
        } catch (e: IllegalArgumentException) {
            Log.w("WidgetConfigActivity", "Unknown widget id $appWidgetId", e)
            finish()
            return
        }

        setContent {
            val darkFlow = remember { AppSettings.darkTheme(applicationContext) }
            val dark by darkFlow.collectAsStateWithLifecycle(initialValue = true)
            LaunchedEffect(dark) { applySystemBars(dark) }
            BatteryTheme(dark = dark) {
                ConfigScreen(
                    glanceId = glanceId,
                    dark = dark,
                    onSaved = {
                        setResult(RESULT_OK, resultValue)
                        finish()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigScreen(
    glanceId: GlanceId,
    dark: Boolean,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
) {
    val appContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val snapshot by rememberBatterySnapshot()

    // Saveable so edits survive rotation; loaded once from the widget's stored state.
    var loaded by rememberSaveable { mutableStateOf(false) }
    var pctSize by rememberSaveable { mutableStateOf(SizeLimits.PCT_DEFAULT) }
    var detailSize by rememberSaveable { mutableStateOf(SizeLimits.DETAIL_DEFAULT) }
    var showCharging by rememberSaveable { mutableStateOf(true) }
    var showTemp by rememberSaveable { mutableStateOf(true) }
    var showTime by rememberSaveable { mutableStateOf(true) }
    var showRefresh by rememberSaveable { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(glanceId) {
        if (!loaded) {
            val stored = WidgetUpdater.loadConfig(appContext, glanceId)
            pctSize = stored.pctSizeSp
            detailSize = stored.detailSizeSp
            showCharging = stored.showCharging
            showTemp = stored.showTemp
            showTime = stored.showTime
            showRefresh = stored.showRefreshButton
            loaded = true
        }
    }

    val config = WidgetConfig(
        pctSizeSp = pctSize,
        detailSizeSp = detailSize,
        showCharging = showCharging,
        showTemp = showTemp,
        showTime = showTime,
        showRefreshButton = showRefresh,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Widget settings") },
                actions = { ThemeToggleAction(dark = dark) },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    ) {
                        OutlinedButton(onClick = onCancel) { Text("Cancel") }
                        Button(
                            onClick = {
                                saving = true
                                scope.launch {
                                    try {
                                        WidgetUpdater.applyConfig(appContext, glanceId, config)
                                        Scheduler.schedule(appContext)
                                        onSaved()
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        Log.w("WidgetConfigActivity", "Saving widget settings failed", e)
                                        saving = false
                                    }
                                }
                            },
                            enabled = loaded && !saving,
                            // Explicit colors keep the label readable in every state.
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text("Save")
                        }
                    }
                    LicenseFooter()
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Preview", style = MaterialTheme.typography.titleMedium)
            WidgetPreview(snapshot = snapshot, config = config, palette = WidgetPalette.of(dark))
            Text(
                "Tap the widget or its ↻ button to refresh it. Large text shrinks " +
                    "automatically if it does not fit the widget.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Percentage size: $pctSize sp", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = pctSize.toFloat(),
                onValueChange = { pctSize = clampPctSize(it.roundToInt()) },
                valueRange = SizeLimits.PCT_MIN.toFloat()..SizeLimits.PCT_MAX.toFloat(),
            )

            Text("Detail text size: $detailSize sp", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = detailSize.toFloat(),
                onValueChange = { detailSize = clampDetailSize(it.roundToInt()) },
                valueRange = SizeLimits.DETAIL_MIN.toFloat()..SizeLimits.DETAIL_MAX.toFloat(),
            )

            SwitchRow("Show charging", showCharging) { showCharging = it }
            SwitchRow("Show temperature", showTemp) { showTemp = it }
            SwitchRow("Show last update", showTime) { showTime = it }
            SwitchRow("Show refresh button", showRefresh) { showRefresh = it }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Mimics the Glance widget layout and colors at the chosen sizes. */
@Composable
private fun WidgetPreview(snapshot: BatterySnapshot?, config: WidgetConfig, palette: WidgetPalette) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(palette.background)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = snapshot?.let { formatPercent(it.levelPct) } ?: "--%",
                color = palette.text,
                fontSize = config.pctSizeSp.sp,
                lineHeight = (config.pctSizeSp * 1.15f).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            if (snapshot != null) {
                val chargeColor = palette.chargeColor(snapshot.chargeSpeed)
                val detailLines = buildList {
                    if (config.showCharging) {
                        add(chargingLabel(snapshot) to chargeColor)
                        chargingDetail(snapshot)?.let { add(it to chargeColor) }
                    }
                    val temp = snapshot.tempC
                    if (config.showTemp && temp != null) add(formatTemperature(temp) to palette.secondaryText)
                    if (config.showTime) add(formatTimestamp(snapshot.readAtEpochMs) to palette.secondaryText)
                }
                for ((line, color) in detailLines) {
                    Text(
                        text = line,
                        color = color,
                        fontSize = config.detailSizeSp.sp,
                        lineHeight = (config.detailSizeSp * 1.3f).sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
        if (config.showRefreshButton) {
            Text(
                text = "↻",
                color = palette.secondaryText,
                fontSize = 20.sp,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}
