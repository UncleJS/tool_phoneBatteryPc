// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 UncleJS

package io.github.unclejs.batterywidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.layout.Box
import androidx.glance.layout.size
import io.github.unclejs.batterywidget.R
import io.github.unclejs.batterywidget.battery.chargingDetail
import io.github.unclejs.batterywidget.battery.timeToFullLabel
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.unclejs.batterywidget.battery.BatteryReader
import io.github.unclejs.batterywidget.battery.BatterySnapshot
import io.github.unclejs.batterywidget.battery.chargingLabel
import io.github.unclejs.batterywidget.battery.formatPercent
import io.github.unclejs.batterywidget.battery.formatTemperature
import io.github.unclejs.batterywidget.battery.formatTimestamp

/** The home-screen battery widget. Each placed instance has its own Glance preferences state. */
class BatteryWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    // Exact: LocalSize is the real widget size, used to keep the big number inside the box.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val config = WidgetUpdater.configFrom(prefs)
            val dark = prefs[WidgetUpdater.Keys.DARK] ?: true
            // No stored reading yet (freshly placed widget): read the sticky broadcast directly.
            val snapshot = WidgetUpdater.snapshotFrom(prefs) ?: BatteryReader.read(context)
            WidgetContent(config = config, snapshot = snapshot, palette = WidgetPalette.of(dark))
        }
    }
}

@Composable
private fun WidgetContent(config: WidgetConfig, snapshot: BatterySnapshot?, palette: WidgetPalette) {
    val size = LocalSize.current
    val textColor = ColorProvider(palette.text)
    val secondaryColor = ColorProvider(palette.secondaryText)

    // Keep the configured size unless it clearly cannot fit the widget ("100%" is ~2.4 em wide).
    var pctSize = config.pctSizeSp.toFloat()
    if (size.width.value > 0f && size.height.value > 0f) {
        val fit = minOf(size.width.value / 2.6f, size.height.value * 0.7f)
        pctSize = minOf(pctSize, fit).coerceAtLeast(12f)
    }

    val detailStyle = TextStyle(
        color = secondaryColor,
        fontSize = config.detailSizeSp.sp,
        textAlign = TextAlign.Center,
    )

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(palette.background)
            .cornerRadius(16.dp)
            .clickable(actionRunCallback<RefreshAction>()),
    ) {
        WidgetLines(config, snapshot, palette, pctSize, textColor, detailStyle)
        if (config.showRefreshButton) {
            // Full-size box only to place the button in the top-right corner.
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                Image(
                    provider = ImageProvider(R.drawable.ic_refresh),
                    contentDescription = "Refresh now",
                    colorFilter = ColorFilter.tint(secondaryColor),
                    modifier = GlanceModifier
                        .size(36.dp)
                        .padding(8.dp)
                        .clickable(actionRunCallback<RefreshAction>()),
                )
            }
        }
    }
}

@Composable
private fun WidgetLines(
    config: WidgetConfig,
    snapshot: BatterySnapshot?,
    palette: WidgetPalette,
    pctSize: Float,
    textColor: ColorProvider,
    detailStyle: TextStyle,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = snapshot?.let { formatPercent(it.levelPct) } ?: "--%",
            style = TextStyle(
                color = textColor,
                fontSize = pctSize.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        if (snapshot != null) {
            if (config.showCharging) {
                val chargeStyle = TextStyle(
                    color = ColorProvider(palette.chargeColor(snapshot.chargeSpeed)),
                    fontSize = config.detailSizeSp.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(text = chargingLabel(snapshot), style = chargeStyle, maxLines = 1)
                chargingDetail(snapshot)?.let { detail ->
                    Text(text = detail, style = chargeStyle, maxLines = 1)
                }
                if (config.showTimeToFull) {
                    timeToFullLabel(snapshot)?.let { ttf ->
                        Text(text = ttf, style = chargeStyle, maxLines = 1)
                    }
                }
            }
            val temp = snapshot.tempC
            if (config.showTemp && temp != null) {
                Spacer(GlanceModifier.height(2.dp))
                Text(text = formatTemperature(temp), style = detailStyle, maxLines = 1)
            }
            if (config.showTime && snapshot.readAtEpochMs > 0L) {
                Spacer(GlanceModifier.height(2.dp))
                Text(text = formatTimestamp(snapshot.readAtEpochMs), style = detailStyle, maxLines = 1)
            }
        }
    }
}
