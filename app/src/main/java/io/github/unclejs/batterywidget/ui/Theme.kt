// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 UncleJS

package io.github.unclejs.batterywidget.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.unclejs.batterywidget.settings.AppSettings
import io.github.unclejs.batterywidget.widget.WidgetUpdater
import kotlinx.coroutines.launch

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DDB8C),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF1F5130),
    onPrimaryContainer = Color(0xFF98F8A6),
    secondary = Color(0xFFB7CCB7),
    onSecondary = Color(0xFF233426),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE6E6E6),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE6E6E6),
    surfaceVariant = Color(0xFF2A2F2A),
    onSurfaceVariant = Color(0xFFC0C8BE),
    surfaceContainer = Color(0xFF1E1F1E),
    surfaceContainerHigh = Color(0xFF262826),
    outline = Color(0xFF8A9388),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6D33),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA5F4B0),
    onPrimaryContainer = Color(0xFF00210A),
    secondary = Color(0xFF506350),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFBFDF7),
    onBackground = Color(0xFF1A1C19),
    surface = Color(0xFFFBFDF7),
    onSurface = Color(0xFF1A1C19),
    surfaceVariant = Color(0xFFDDE5DA),
    onSurfaceVariant = Color(0xFF414941),
    surfaceContainer = Color(0xFFEFF2EB),
    surfaceContainerHigh = Color(0xFFE9ECE5),
    outline = Color(0xFF717970),
)

/** App theme. Dark is the default (see AppSettings.darkTheme). */
@Composable
fun BatteryTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}

/** Edge-to-edge with status/navigation bar icons that match the app theme (not the system one). */
fun ComponentActivity.applySystemBars(dark: Boolean) {
    val transparent = android.graphics.Color.TRANSPARENT
    val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
}

/**
 * Top-bar action that flips the app theme. Uses text glyphs (no icon library).
 * The widgets are refreshed afterwards so their colors follow the theme.
 */
@Composable
fun ThemeToggleAction(dark: Boolean) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val label = if (dark) "Switch to light theme" else "Switch to dark theme"
    IconButton(
        onClick = {
            scope.launch {
                AppSettings.setDarkTheme(context, !dark)
                WidgetUpdater.refreshAll(context)
            }
        },
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        Text(
            text = if (dark) "☀" else "☾",
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

const val LICENSE_URL = "https://www.gnu.org/licenses/agpl-3.0.txt"

/** `© 2026 UncleJS — Licensed under AGPL-3.0-or-later`, the license name links to the text. */
@Composable
fun LicenseFooter(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val text = buildAnnotatedString {
        append("© 2026 UncleJS — Licensed under ")
        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
            append("AGPL-3.0-or-later")
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) {
                try {
                    uriHandler.openUri(LICENSE_URL)
                } catch (e: Exception) {
                    // No browser installed: nothing to do.
                }
            }
            .padding(vertical = 8.dp),
    )
}
