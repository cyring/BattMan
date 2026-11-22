package fr.cyring.battman.ui.components

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ResetTv
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.cyring.battman.BuildConfig
import fr.cyring.battman.C
import fr.cyring.battman.CentralFont
import fr.cyring.battman.Controller
import fr.cyring.battman.R
import fr.cyring.battman.battery.BatteryMonitor
import fr.cyring.battman.centralFont
import fr.cyring.battman.currentLocale
import kotlinx.coroutines.launch
import kotlin.collections.indexOf
import kotlin.collections.indexOfLast
import kotlin.collections.lastIndex
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

private val TIME_FORMATTERS: Array<(Int) -> String> = arrayOf(
    { sec -> "$sec s" },
    { min ->
        val h = min / 60
        val m = min % 60
        when {
            h > 0 && m > 0 -> "$h h $m min"
            h > 0 -> "$h h"
            else -> "$m min"
        }
    }
)

private val icons = listOf(
    Icons.Default.Autorenew,
    Icons.Default.Schedule
)

private val maxValue = intArrayOf(
    C.MAX_TIME_FOCUS.toInt() / 1000,
    C.MAX_TIME_AWAY.toInt()
)

private val median = 10 + (maxValue[C.CASE_FOCUS] - 10) / 2

private val stepValuesList = arrayOf(
    intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, median, maxValue[C.CASE_FOCUS]),
    intArrayOf(0, 15, 30, 60, 90, 120, 240, maxValue[C.CASE_AWAY])
)

private val unitMultipliers = longArrayOf(1000L, 60_000L)

fun openThemeDialog(
    themeMode: Int,
    onItemSelected: (Int) -> Unit
): SelectionDialogPayload {
    return SelectionDialogPayload(
        titleRes = R.string.theme,
        cells = listOf(
            SelectionCell(R.string.theme_light, C.THEME_LIGHT),
            SelectionCell(R.string.theme_dark, C.THEME_DARK),
            SelectionCell(R.string.theme_system, C.THEME_SYSTEM)
        ),
        selectedVal = themeMode,
        descriptionRes = R.string.theme_desc,
        icon = Icons.Default.Palette,
        onItemSelected = onItemSelected
    )
}

fun openFontDialog(
    fontSizeMode: Int,
    onItemSelected: (Int) -> Unit
): SelectionDialogPayload {
    return SelectionDialogPayload(
        titleRes = R.string.accessibility,
        cells = listOf(
            SelectionCell(R.string.accessibility_small, C.FONT_SIZE_SMALL),
            SelectionCell(R.string.accessibility_medium, C.FONT_SIZE_MEDIUM),
            SelectionCell(R.string.accessibility_large, C.FONT_SIZE_LARGE),
            SelectionCell(R.string.theme_system, C.FONT_SIZE_SYSTEM)
        ),
        selectedVal = fontSizeMode,
        descriptionRes = R.string.accessibility_desc,
        icon = Icons.Default.Accessibility,
        onItemSelected = onItemSelected
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationDrawer(
    controller: Controller,
    drawerState: DrawerState,
    batteryMonitor: BatteryMonitor,
    onSelectDialog: (SelectionDialogPayload?) -> Unit,
    content: @Composable () -> Unit
) {
    val isDebuggable = BuildConfig.DEBUG
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val fontConfig: CentralFont = centralFont(controller.fontSizeMode)
    val refreshTime = controller.refreshTime
    val autoRefresh = controller.autoRefresh
    val themeMode = controller.themeMode

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(320.dp)
            ) {
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = maxHeight)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                coroutineScope.launch { drawerState.close() }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Menu,
                                                contentDescription = "MenuClose",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = stringResource(R.string.app_name),
                                                style = MaterialTheme.typography.headlineSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    // Monitoring settings
                                    val titles = listOf(
                                        stringResource(R.string.auto_refresh_settings),
                                        stringResource(R.string.agent_scheduler_settings)
                                    )
                                    val helpTexts = listOf(
                                        stringResource(R.string.auto_refresh_help_text),
                                        stringResource(R.string.agent_scheduler_help_text)
                                    )

                                    listOf(C.CASE_FOCUS, C.CASE_AWAY).forEach { caseIdx ->
                                        val steps = stepValuesList[caseIdx]
                                        val multiplier = unitMultipliers[caseIdx]
                                        val currentMs = refreshTime[caseIdx]
                                        val isAuto = autoRefresh[caseIdx]

                                        val currentUnitValue = if (!isAuto) 0 else (currentMs / multiplier).toInt()
                                        val currentIndex = remember(currentUnitValue) {
                                            val idx = steps.indexOf(currentUnitValue)
                                            if (idx >= 0) idx else steps.indexOfLast { it <= currentUnitValue }.coerceAtLeast(0)
                                        }

                                        val valueText = if (!isAuto || steps[currentIndex] == 0) {
                                            stringResource(R.string.off)
                                        } else {
                                            TIME_FORMATTERS[caseIdx](steps[currentIndex])
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(
                                                    start = 24.dp,
                                                    end = 24.dp,
                                                    top = 16.dp,
                                                    bottom = 4.dp
                                                )
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = icons[caseIdx],
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(end = 16.dp)
                                                )
                                                Column {
                                                    Text(
                                                        text = titles[caseIdx],
                                                        style = fontConfig.menuStyle,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = helpTexts[caseIdx],
                                                        style = fontConfig.captionStyle,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            Text(
                                                text = valueText,
                                                style = fontConfig.menuStyle
                                            )
                                        }
                                        Slider(
                                            value = currentIndex.toFloat(),
                                            valueRange = 0f..(steps.size - 1).toFloat(),
                                            steps = if (steps.size > 2) steps.size - 2 else 0,
                                            onValueChange = { newValue ->
                                                val index = newValue.roundToInt().coerceIn(0, steps.lastIndex)
                                                val units = steps[index]
                                                if (units == 0) {
                                                    controller.updateAutoRefreshHandlers[caseIdx](false, currentMs)
                                                } else {
                                                    controller.updateAutoRefreshHandlers[caseIdx](true, units * multiplier)
                                                }
                                            },
                                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp)
                                        )
                                        HorizontalDivider()
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                start = 24.dp,
                                                end = 24.dp,
                                                top = 16.dp,
                                                bottom = 4.dp
                                            )
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.NotificationsActive,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(end = 16.dp)
                                            )
                                            Text(
                                                text = stringResource(R.string.battery_alert_threshold),
                                                style = fontConfig.menuStyle,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = if (BatteryMonitor.batteryAlertThreshold == 0) {
                                                stringResource(R.string.off)
                                            } else {
                                                BatteryMonitor.batteryAlertThreshold.toString() + " %"
                                            },
                                            style = fontConfig.menuStyle
                                        )
                                    }
                                    Slider(
                                        value = BatteryMonitor.batteryAlertThreshold.toFloat(),
                                        valueRange = C.MIN_ALERT_THRESHOLD.toFloat()..C.MAX_ALERT_THRESHOLD.toFloat(),
                                        onValueChange = { newValue ->
                                            batteryMonitor.updateBatteryAlertThreshold(newValue.roundToInt())
                                        },
                                        track = { sliderState ->
                                            SliderDefaults.Track(
                                                sliderState = sliderState,
                                                drawStopIndicator = null
                                            )
                                        },
                                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp)
                                    )
                                    HorizontalDivider()

                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                start = 24.dp,
                                                end = 24.dp,
                                                top = 16.dp,
                                                bottom = 4.dp
                                            )
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Notifications,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(end = 16.dp)
                                            )
                                            Text(
                                                text = stringResource(R.string.max_alerts),
                                                style = fontConfig.menuStyle,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = if (BatteryMonitor.maxAlerts == 0) {
                                                "∞"
                                            } else {
                                                BatteryMonitor.maxAlerts.toString()
                                            },
                                            style = fontConfig.menuStyle
                                        )
                                    }
                                    Slider(
                                        value = BatteryMonitor.maxAlerts.toFloat(),
                                        valueRange = C.MIN_MAX_ALERTS.toFloat()..C.MAX_MAX_ALERTS.toFloat(),
                                        steps = 9,
                                        onValueChange = { newValue ->
                                            batteryMonitor.updateMaxAlerts(newValue.roundToInt())
                                        },
                                        track = { sliderState ->
                                            SliderDefaults.Track(
                                                sliderState = sliderState,
                                                drawStopIndicator = null
                                            )
                                        },
                                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp)
                                    )
                                    HorizontalDivider()

                                    val context = LocalContext.current
                                    val currentLocale = currentLocale(LocalConfiguration.current)
                                    val currentLanguageName = currentLocale.getDisplayLanguage(currentLocale).replaceFirstChar { it.uppercase() }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                    val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                                                        data = Uri.fromParts("package", context.packageName, null)
                                                    }
                                                    context.startActivity(intent)
                                                } else {
                                                    Toast.makeText(context, R.string.unsupported, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            .padding(
                                                horizontal = 24.dp,
                                                vertical = fontConfig.lineHeight
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Language,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(end = 16.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.language),
                                            style = fontConfig.menuStyle,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = currentLanguageName,
                                            style = fontConfig.menuStyle
                                        )
                                    }
                                    HorizontalDivider()

                                    val themeText = when (themeMode) {
                                        C.THEME_LIGHT -> stringResource(R.string.theme_light)
                                        C.THEME_DARK -> stringResource(R.string.theme_dark)
                                        else -> stringResource(R.string.theme_system)
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onSelectDialog(
                                                    openThemeDialog(
                                                        themeMode = themeMode,
                                                        onItemSelected = { newValue ->
                                                            controller.updateThemeMode(newValue)
                                                        }
                                                    )
                                                )
                                            }
                                            .padding(
                                                horizontal = 24.dp,
                                                vertical = fontConfig.lineHeight
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Palette,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(end = 16.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.theme),
                                            style = fontConfig.menuStyle,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = themeText,
                                            style = fontConfig.menuStyle
                                        )
                                    }
                                    HorizontalDivider()

                                    val fontSizeMode = controller.fontSizeMode
                                    val accessibilityText = when (fontSizeMode) {
                                        C.FONT_SIZE_SMALL -> stringResource(R.string.accessibility_small)
                                        C.FONT_SIZE_MEDIUM -> stringResource(R.string.accessibility_medium)
                                        C.FONT_SIZE_LARGE -> stringResource(R.string.accessibility_large)
                                        else -> stringResource(R.string.theme_system)
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onSelectDialog(
                                                    openFontDialog(
                                                        fontSizeMode = controller.fontSizeMode,
                                                        onItemSelected = { newValue ->
                                                            controller.updateFontSizeMode(newValue)
                                                        }
                                                    )
                                                )
                                            }
                                            .padding(
                                                horizontal = 24.dp,
                                                vertical = fontConfig.lineHeight
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Accessibility,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(end = 16.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.accessibility),
                                            style = fontConfig.menuStyle,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = accessibilityText,
                                            style = fontConfig.menuStyle
                                        )
                                    }
                                    //TODO:HorizontalDivider()
                                }

                                Column {
                                    HorizontalDivider()
                                    val activity = LocalActivity.current
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.copyright, BuildConfig.VERSION_NAME, 2026),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(0.dp),
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    controller.updateAutoRefresh(
                                                        C.CASE_FOCUS,
                                                        C.DEFAULT_AUTO_FOCUS,
                                                        C.DEFAULT_TIME_FOCUS
                                                    )
                                                    controller.updateAutoRefresh(
                                                        C.CASE_AWAY,
                                                        C.DEFAULT_AUTO_AWAY,
                                                        C.DEFAULT_TIME_AWAY
                                                    )
                                                    batteryMonitor.updateBatteryAlertThreshold(
                                                        C.BATTERY_ALERT_THRESHOLD
                                                    )
                                                    batteryMonitor.updateMaxAlerts(C.DEFAULT_MAX_ALERTS)
                                                    Toast.makeText(context, R.string.reset, Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ResetTv,
                                                    contentDescription = "Reset",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        Toast.makeText(context, R.string.bye, Toast.LENGTH_SHORT).show()
                                                        kotlinx.coroutines.delay(1.seconds)
                                                        activity?.finishAndRemoveTask()
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PowerSettingsNew,
                                                    contentDescription = "Quit",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            // BEGIN(Firebase:Crashlytics)
                                            if (isDebuggable) {
                                                IconButton(
                                                    onClick = { throw RuntimeException("Test Crash") }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.BugReport,
                                                        contentDescription = "Crash",
                                                        tint = MaterialTheme.colorScheme.primaryFixedDim
                                                    )
                                                }
                                            }
                                            // END(Firebase:Crashlytics)
                                        }
                                    }
                                }
                            }
                        }
                    }
        },
        content = content
    )
}