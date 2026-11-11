package fr.cyring.battman

import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import fr.cyring.battman.ui.theme.BattManTheme
import kotlin.time.Duration.Companion.milliseconds

const val FONT_SIZE_SYSTEM: Int = 0
const val FONT_SIZE_SMALL: Int = 1
const val FONT_SIZE_MEDIUM: Int = 2
const val FONT_SIZE_LARGE: Int = 3

const val THEME_SYSTEM: Int = 0
const val THEME_LIGHT: Int = 1
const val THEME_DARK: Int = 2

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
        MAX_TIME_FOCUS.toInt() / 1000,
        MAX_TIME_AWAY.toInt()
)

private val median = 10 + (maxValue[CASE_FOCUS] - 10) / 2

private val stepValuesList = arrayOf(
    intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, median, maxValue[CASE_FOCUS]),
    intArrayOf(0, 15, 30, 60, 90, 120, 240, maxValue[CASE_AWAY])
)

private val unitMultipliers = longArrayOf(1000L, 60_000L)

val waitingStyle: SpanStyle = SpanStyle(
    textDecoration = TextDecoration.None,
    fontStyle = FontStyle.Italic,
    fontWeight = FontWeight.ExtraLight
)

fun String.waitingStyle(): AnnotatedString = buildAnnotatedString {
    withStyle(style = waitingStyle) { append(this@waitingStyle) }
}

val arrowDown: SpanStyle = SpanStyle(color = Color.Yellow)
val arrowUp: SpanStyle = SpanStyle(color = Color.Green)

data class CentralFont (
    val appStyle: AppStyle,
    val reportStyle: TextStyle,
    val sysInfoStyle: TextStyle,
    val menuStyle: TextStyle,
    val captionStyle: TextStyle,
    val dialogStyle: DialogStyle,
    var lineHeight: Dp = 16.dp
) {
    data class AppStyle(
        val title: TextStyle,
        val subTitle: TextStyle
    )
    data class DialogStyle(
        val title: TextStyle,
        val button: TextStyle,
        val description: TextStyle
    )
}

@Composable
fun centralFont(fontSizeMode: Int = FONT_SIZE_SYSTEM): CentralFont {
    val typography = MaterialTheme.typography
    val density = LocalDensity.current

    return remember(fontSizeMode, typography, density) {

        fun mapStyle(
            options: Array<TextStyle>,
            fontFamily: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default
        ): TextStyle {
            val baseStyle = options.getOrElse(fontSizeMode) { options[1] }
            return baseStyle.copy(fontFamily = fontFamily)
        }

        val reportStyle = mapStyle(
            arrayOf(
                typography.bodyMedium, // FONT_SIZE_SYSTEM (0)
                typography.bodySmall,  // FONT_SIZE_SMALL (1)
                typography.bodyMedium, // FONT_SIZE_MEDIUM (2)
                typography.bodyLarge   // FONT_SIZE_LARGE (3)
            ),
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )

        val menuStyle = mapStyle(
            arrayOf(
                typography.titleMedium,
                typography.titleSmall,
                typography.titleMedium,
                typography.titleLarge
            ),
            fontFamily = androidx.compose.ui.text.font.FontFamily.Default
        )

        val captionStyle = mapStyle(
            arrayOf(
                typography.titleSmall,
                typography.titleSmall,
                typography.titleMedium,
                typography.titleMedium
            ),
            fontFamily = androidx.compose.ui.text.font.FontFamily.Default
        )

        val dialogTitleStyle = mapStyle(
            arrayOf(
                typography.titleLarge,
                typography.titleSmall,
                typography.titleMedium,
                typography.titleLarge
            ),
            fontFamily = androidx.compose.ui.text.font.FontFamily.Default
        )

        val dialogButtonStyle = mapStyle(
            arrayOf(
                typography.bodyLarge,
                typography.bodySmall,
                typography.bodyMedium,
                typography.bodyLarge
            ),
            fontFamily = androidx.compose.ui.text.font.FontFamily.Default
        )

        val dialogDescStyle = mapStyle(
            arrayOf(
                typography.bodyMedium,
                typography.bodySmall,
                typography.bodySmall,
                typography.bodyMedium
            ),
            fontFamily = androidx.compose.ui.text.font.FontFamily.Default
        )

        CentralFont(
            appStyle = CentralFont.AppStyle(
                title = typography.headlineMedium,
                subTitle = typography.headlineSmall
            ),
            reportStyle = reportStyle,
            sysInfoStyle = typography.bodySmall.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            ),
            menuStyle = menuStyle,
            captionStyle = captionStyle,
            dialogStyle = CentralFont.DialogStyle(
                title = dialogTitleStyle,
                button = dialogButtonStyle,
                description = dialogDescStyle
            ),
            lineHeight = with(density) {
                typography.labelSmall.lineHeight.toDp()
            }
        )
    }
}

data class SelectionCell (
    val resource: Int,
    val value: Int
)

data class SelectionDialogPayload(
    val titleRes: Int,
    val cells: List<SelectionCell>,
    val selectedVal: Int,
    val descriptionRes: Int? = null,
    val icon: ImageVector? = null,
    val onItemSelected: (Int) -> Unit
)

@Composable
fun SelectionDialog(
    fontConfig: CentralFont,
    titleRes: Int,
    cells: List<SelectionCell>,
    selectedVal: Int,
    descriptionRes: Int? = null,
    onDismissRequest: () -> Unit,
    onItemSelected: (Int) -> Unit,
    icon: ImageVector? = null
) {
    val windowSize = LocalWindowInfo.current.containerSize
    val isLandscape = windowSize.width > windowSize.height
    val widthFraction = if (isLandscape) 0.55f else 0.85f
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(dismissOnBackPress = true, usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(widthFraction)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(scrollState)
                    .drawWithContent {

                        drawContent()

                        if (scrollState.maxValue > 0) {
                            val scrollStateMaxValue: Float = scrollState.maxValue.toFloat()
                            val scrollStateValue: Float = scrollState.value.toFloat()

                            val totalContentHeight = scrollStateMaxValue + size.height
                            val scrollThumbHeight = 128f
                            val thumbSize = Size(12f, scrollThumbHeight)
                            val scrollProgress = scrollStateValue / scrollStateMaxValue
                            val travelHeight = size.height - scrollThumbHeight
                            val yOffset = (scrollProgress * travelHeight).coerceIn(0f, travelHeight)

                            drawRect(
                                color = Color.Gray.copy(alpha = 0.15f),
                                size = Size(12f, totalContentHeight),
                                topLeft = Offset(size.width + 24f, 0f)
                            )
                            drawRect(
                                color = Color.Gray.copy(alpha = 0.45f),
                                size = thumbSize,
                                topLeft = Offset(size.width + 24f, yOffset)
                            )
                        }
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                    Text(
                        text = stringResource(titleRes),
                        style = fontConfig.dialogStyle.title
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                cells.forEach { cell ->
                    val isSelected = cell.value == selectedVal
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onItemSelected(cell.value)
                                onDismissRequest()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = stringResource(cell.resource),
                            style = fontConfig.dialogStyle.button
                        )
                    }
                }

                if (descriptionRes != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(descriptionRes),
                        style = fontConfig.dialogStyle.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalButton(onClick = onDismissRequest) {
                        Text(
                            text = stringResource(R.string.cancel),
                            style = fontConfig.dialogStyle.button
                        )
                    }
                }
            }
        }
    }
}

fun openThemeDialog(
    themeMode: Int,
    onItemSelected: (Int) -> Unit
): SelectionDialogPayload {
    return SelectionDialogPayload(
        titleRes = R.string.theme,
        cells = listOf(
            SelectionCell(R.string.theme_light, THEME_LIGHT),
            SelectionCell(R.string.theme_dark, THEME_DARK),
            SelectionCell(R.string.theme_system, THEME_SYSTEM)
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
            SelectionCell(R.string.accessibility_small, FONT_SIZE_SMALL),
            SelectionCell(R.string.accessibility_medium, FONT_SIZE_MEDIUM),
            SelectionCell(R.string.accessibility_large, FONT_SIZE_LARGE),
            SelectionCell(R.string.theme_system, FONT_SIZE_SYSTEM)
        ),
        selectedVal = fontSizeMode,
        descriptionRes = R.string.accessibility_desc,
        icon = Icons.Default.Accessibility,
        onItemSelected = onItemSelected
    )
}

class MainActivity : ComponentActivity() {
    private val viewModel: BatteryViewModel by viewModels()
    private val batteryEventReceiver = BatteryEventReceiver()

    override fun onPause() {
        super.onPause()
        viewModel.onScreenPaused()

        try {
            unregisterReceiver(batteryEventReceiver)
        } catch (e: Exception) {
            Toast.makeText(this@MainActivity, "${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onScreenResumed()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
        }
        registerReceiver(batteryEventReceiver, filter)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {
            val fontConfig: CentralFont = centralFont(viewModel.fontSizeMode)
            val autoRefresh = viewModel.autoRefresh
            val refreshTime = viewModel.refreshTime
            val report = viewModel.report
            val batteryPercent = BatteryViewModel.batteryPercent
            val sysInfo = viewModel.sysInfo
            val themeMode = viewModel.themeMode

            var activeSelectionDialog by remember { mutableStateOf<SelectionDialogPayload?>(null) } /* Sentinel */

            val darkTheme = when (themeMode) {
                THEME_LIGHT -> false
                THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }

            BattManTheme(darkTheme = darkTheme) {
                activeSelectionDialog?.let { payload ->
                    SelectionDialog(
                        fontConfig = fontConfig,
                        titleRes = payload.titleRes,
                        cells = payload.cells,
                        selectedVal = payload.selectedVal,
                        descriptionRes = payload.descriptionRes,
                        onDismissRequest = { activeSelectionDialog = null /* Sentinel */ },
                        onItemSelected = { newValue ->
                            payload.onItemSelected(newValue)
                            activeSelectionDialog = null /* Sentinel */
                        },
                        icon = payload.icon
                    )
                }

                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val coroutineScope = rememberCoroutineScope()

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

                                        listOf(CASE_FOCUS, CASE_AWAY).forEach { caseIdx ->
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
                                                        viewModel.updateAutoRefreshHandlers[caseIdx](false, currentMs)
                                                    } else {
                                                        viewModel.updateAutoRefreshHandlers[caseIdx](true, units * multiplier)
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
                                                text = if (BatteryViewModel.batteryAlertThreshold == 0) {
                                                    stringResource(R.string.off)
                                                } else {
                                                    BatteryViewModel.batteryAlertThreshold.toString() + " %"
                                                },
                                                style = fontConfig.menuStyle
                                            )
                                        }
                                        Slider(
                                            value = BatteryViewModel.batteryAlertThreshold.toFloat(),
                                            valueRange = MIN_ALERT_THRESHOLD.toFloat()..MAX_ALERT_THRESHOLD.toFloat(),
                                            onValueChange = { newValue ->
                                                viewModel.updateBatteryAlertThreshold(newValue.roundToInt())
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
                                                text = if (BatteryViewModel.maxAlerts == 0) {
                                                    "∞"
                                                } else {
                                                    BatteryViewModel.maxAlerts.toString()
                                                },
                                                style = fontConfig.menuStyle
                                            )
                                        }
                                        Slider(
                                            value = BatteryViewModel.maxAlerts.toFloat(),
                                            valueRange = MIN_MAX_ALERTS.toFloat()..MAX_MAX_ALERTS.toFloat(),
                                            steps = 9,
                                            onValueChange = { newValue ->
                                                viewModel.updateMaxAlerts(newValue.roundToInt())
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
                                        val currentLocale = LocalConfiguration.current.locales[0]
                                        val currentLanguageName = currentLocale.getDisplayLanguage(currentLocale).replaceFirstChar { it.uppercase() }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val intent =
                                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                            Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                                                                data = Uri.fromParts(
                                                                    "package",
                                                                    context.packageName,
                                                                    null
                                                                )
                                                            }
                                                        } else {
                                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                                data = Uri.fromParts(
                                                                    "package",
                                                                    context.packageName,
                                                                    null
                                                                )
                                                            }
                                                        }
                                                    context.startActivity(intent)
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
                                            THEME_LIGHT -> stringResource(R.string.theme_light)
                                            THEME_DARK -> stringResource(R.string.theme_dark)
                                            else -> stringResource(R.string.theme_system)
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    activeSelectionDialog = openThemeDialog(
                                                        themeMode = themeMode,
                                                        onItemSelected = { newValue ->
                                                            viewModel.updateThemeMode(newValue)
                                                        }
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

                                        val fontSizeMode = viewModel.fontSizeMode
                                        val accessibilityText = when (fontSizeMode) {
                                            FONT_SIZE_SMALL -> stringResource(R.string.accessibility_small)
                                            FONT_SIZE_MEDIUM -> stringResource(R.string.accessibility_medium)
                                            FONT_SIZE_LARGE -> stringResource(R.string.accessibility_large)
                                            else -> stringResource(R.string.theme_system)
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    activeSelectionDialog = openFontDialog(
                                                        fontSizeMode = viewModel.fontSizeMode,
                                                        onItemSelected = { newValue ->
                                                            viewModel.updateFontSizeMode(newValue)
                                                        }
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
                                            horizontalArrangement = Arrangement.SpaceBetween,
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
                                            IconButton(
                                                onClick = {
                                                    activity?.finishAndRemoveTask()
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PowerSettingsNew,
                                                    contentDescription = "Quit",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize()
                    ) { innerPadding ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .padding(16.dp, 0.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch { drawerState.open() }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "MenuOpen",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = stringResource(R.string.app_name),
                                        style = fontConfig.appStyle.title,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = stringResource(R.string.subtitle),
                                        maxLines = 1,
                                        softWrap = false,
                                        autoSize = TextAutoSize.StepBased(
                                            maxFontSize = fontConfig.appStyle.subTitle.fontSize
                                        ),
                                        textAlign = TextAlign.Center,
                                        style = fontConfig.appStyle.subTitle,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        viewModel.refreshData()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(fontConfig.lineHeight * 0.25f))

                            // Management rules
                            val levelColor: Color
                            var signColor: Color
                            val signText: AnnotatedString

                            if (batteryPercent > BatteryViewModel.batteryAlertThreshold) {
                                levelColor = MaterialTheme.colorScheme.primary
                                signColor = MaterialTheme.colorScheme.primary
                            } else {
                                levelColor = MaterialTheme.colorScheme.inversePrimary
                                signColor = MaterialTheme.colorScheme.inversePrimary
                            }
                            if (BatteryViewModel.predictionMs > 0L) {
                                val formattedDuration = BatteryViewModel.predictionMs.milliseconds.toComponents { _, h, m, _, _ -> if (h > 0) stringResource(R.string.duration_h_m_str_format, h, m) else stringResource(R.string.duration_m_str_format, m) }
                                signText = buildAnnotatedString {
                                    withStyle(arrowDown) { append(stringResource(R.string._arrow_down_) + " ") } /* ⬇ */
                                    append(formattedDuration)
                                }
                            } else if (BatteryViewModel.timeRemainingMs > 0L) {
                                signColor = MaterialTheme.colorScheme.outline
                                val formattedDuration = BatteryViewModel.timeRemainingMs.milliseconds.toComponents { _, h, m, _, _ -> if (h > 0) stringResource(R.string.duration_h_m_str_format, h, m) else stringResource(R.string.duration_m_str_format, m) }
                                signText = buildAnnotatedString {
                                    withStyle(arrowUp) { append(stringResource(R.string._arrow_up_) + " ") } /* ⬆ */
                                    append(formattedDuration)
                                }
                            } else {
                                signText = AnnotatedString("")
                            }

                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            )
                            {
                                Text(
                                    text = signText,
                                    color = signColor,
                                    style = TextStyle(fontSize = 24.sp),
                                    modifier = Modifier.padding(start = 16.dp, bottom = 16.dp),
                                )
                                Text(
                                    text = buildAnnotatedString {
                                        append(batteryPercent.toString())
                                        withStyle(style = SpanStyle(fontSize = 24.sp)) {
                                            append("%")
                                        }
                                    },
                                    style = MaterialTheme.typography.displayLarge,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(end = 16.dp),
                                    color = levelColor
                                )
                            }

                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(fontConfig.lineHeight)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(batteryPercent / 100f)
                                        .background(
                                            color = levelColor,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                )

                                if (BatteryViewModel.batteryAlertThreshold > 0) {
                                    val markerSize = fontConfig.lineHeight * 0.4f
                                    val fraction = BatteryViewModel.batteryAlertThreshold / 100f
                                    val markerOffset = (maxWidth * fraction) - (markerSize / 2)

                                    Box(
                                        modifier = Modifier
                                            .size(markerSize)
                                            .align(Alignment.CenterStart)
                                            .offset(x = markerOffset)
                                            .background(
                                                color = MaterialTheme.colorScheme.onSurface,
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(fontConfig.lineHeight * 0.75f))

                            var isRefreshing by remember { mutableStateOf(false) }

                            PullToRefreshBox(
                                isRefreshing = isRefreshing,
                                onRefresh = {
                                    isRefreshing = true
                                    coroutineScope.launch {
                                        viewModel.refreshData()
                                        isRefreshing = false
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    BatteryReport(fontConfig, rows = report)
                                }
                            }
                            Spacer(modifier = Modifier.height(fontConfig.lineHeight * 0.5f))

                            // Quarter-width centered separator line
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                HorizontalDivider(
                                    modifier = Modifier.fillMaxWidth(0.25f),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(fontConfig.lineHeight * 0.5f))

                            Text(
                                text = sysInfo,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.outline,
                                style = fontConfig.sysInfoStyle,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryReport(fontConfig: CentralFont, rows: List<BatteryReportRow>) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        rows.forEach { row ->
            when {
                // Case 1: ("", "") -> Blank line separator
                row.label.isEmpty() && row.value.isEmpty() -> {
                    Spacer(modifier = Modifier.height(fontConfig.lineHeight))
                }
                // Case 2: ("@", "") -> Unique pictogram only
                row.value.isEmpty() -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = row.label,
                            style = fontConfig.reportStyle
                        )
                    }
                }
                // Case 3: Standard data row (label & value)
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = row.label,
                            modifier = Modifier.weight(1.5f),
                            maxLines = 1,
                            softWrap = false,
                            style = fontConfig.reportStyle
                        )

                        Text(
                            text = row.value,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Start,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            style = fontConfig.reportStyle
                        )
                    }
                }
            }
        }
    }
}
