package fr.cyring.battman

import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import fr.cyring.battman.CentralColor.AlertBattery
import fr.cyring.battman.battery.BatteryEventReceiver
import fr.cyring.battman.battery.BatteryMonitor
import fr.cyring.battman.ui.components.BasicDialog
import fr.cyring.battman.ui.components.BatteryHeader
import fr.cyring.battman.ui.components.NavigationDrawer
import fr.cyring.battman.ui.theme.BattManTheme
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import java.util.Locale

const val REPORT_LEFT: Int = 0
const val REPORT_RIGHT: Int = 1

object M {
    val waitingStyle: SpanStyle = SpanStyle(
        textDecoration = TextDecoration.None,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.ExtraLight
    )

    val arrowDown: SpanStyle = SpanStyle(color = Color.Yellow)
    val arrowUp: SpanStyle = SpanStyle(color = Color.Green)
    val labelOff: SpanStyle = SpanStyle(color = Color.LightGray)
}

fun String.waitingStyle(): AnnotatedString = buildAnnotatedString {
    withStyle(style = M.waitingStyle) { append(this@waitingStyle) }
}

data class CentralColor (
    val nominal: AlertBattery,
    val issue: AlertBattery
) {
    data class AlertBattery (
        val level: Color,
        val sign: Color
    )
}

@Composable
fun centralColor(): CentralColor {
    return CentralColor(
        nominal = AlertBattery(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary
        ),
        issue = AlertBattery(
            MaterialTheme.colorScheme.inversePrimary,
            MaterialTheme.colorScheme.inversePrimary
        )
    )
}

@Suppress("ArrayInDataClass")
data class CentralFont (
    val appStyle: AppStyle,
    val reportStyle: TextStyle,
    val minReportFontSize: Array<TextUnit>,
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
fun centralFont(fontSizeMode: Int = C.FONT_SIZE_SYSTEM): CentralFont {
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
                /* FONT_SIZE_SYSTEM */  typography.bodyLarge,
                /* FONT_SIZE_SMALL  */  typography.bodySmall,
                /* FONT_SIZE_MEDIUM */  typography.bodyMedium,
                /* FONT_SIZE_LARGE  */  typography.bodyLarge.copy(fontSize = (typography.bodyLarge.fontSize.value * 1.25f).sp)
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

        val minReportFontSize = arrayOf(
            (reportStyle.fontSize.value * 0.65f).sp, /* REPORT_LEFT  */
            (reportStyle.fontSize.value * 0.60f).sp  /* REPORT_RIGHT */
        )

        CentralFont(
            appStyle = CentralFont.AppStyle(
                title = typography.headlineMedium,
                subTitle = typography.headlineSmall
            ),
            reportStyle = reportStyle,
            minReportFontSize = minReportFontSize,
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

class MainActivity : ComponentActivity() {
    var tongue: Array<Locale> = arrayOf(Locale.ENGLISH, Locale.ENGLISH)

    private val controller: Controller by viewModels()
    private val batteryMonitor by lazy { BatteryMonitor(application, tongue) }
    private val batteryEventReceiver = BatteryEventReceiver()

    override fun onPause() {
        super.onPause()

        tongue[C.CASE_AWAY] = currentLocale(resources.configuration)

        controller.onScreenPaused(this)

        try {
            unregisterReceiver(batteryEventReceiver)
        } catch (e: Exception) {
            Toast.makeText(this@MainActivity, "${e.message}", Toast.LENGTH_LONG).show()
        }

        controller.stopTorchMonitoring()
        controller.stopAudioMonitoring()
        controller.stopGpsMonitoring()
        controller.stopBtMonitoring()
        controller.stopAirplaneMonitoring()
        controller.stopNetworkMonitoring()
    }

    override fun onResume() {
        super.onResume()

        tongue[C.CASE_FOCUS] = currentLocale(resources.configuration)

        controller.onScreenResumed(this)

        tongue[C.CASE_AWAY] = tongue[C.CASE_FOCUS]

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
        }
        registerReceiver(batteryEventReceiver, filter)

        controller.startTorchMonitoring()
        controller.startAudioMonitoring()
        controller.startGpsMonitoring()
        controller.startBtMonitoring()
        controller.startAirplaneMonitoring()
        controller.startNetworkMonitoring()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        tongue[C.CASE_FOCUS] = currentLocale(resources.configuration)
        tongue[C.CASE_AWAY] = tongue[C.CASE_FOCUS]

        controller.init(this, tongue)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {
            val fontConfig: CentralFont = centralFont(controller.fontSizeMode)
            val report = controller.report
            val batteryPercent = BatteryMonitor.batteryPercent
            val sysInfo = Diagnostics.buildSystemInfo()
            val themeMode = controller.themeMode

            val darkTheme = when (themeMode) {
                C.THEME_LIGHT -> false
                C.THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }

            val dialogStack = remember { mutableStateListOf<BasicDialog>() }

            BattManTheme(darkTheme = darkTheme) {
                dialogStack.forEach { dialog ->
                    dialog.Render(
                        fontConfig = fontConfig,
                        onDismiss = { dialogStack.remove(dialog) }
                    )
                }

                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val coroutineScope = rememberCoroutineScope()

                NavigationDrawer(
                    controller = controller,
                    drawerState = drawerState,
                    batteryMonitor = batteryMonitor,
                    onPushDialog = { dialog -> dialogStack.add(dialog) }
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
                            BatteryHeader(
                                this@MainActivity,
                                controller = controller,
                                fontConfig = fontConfig,
                                onOpenDrawer = {
                                    coroutineScope.launch { drawerState.open() }
                                }
                            )

                            Spacer(modifier = Modifier.height(fontConfig.lineHeight * 0.25f))

                            // Management rules
                            val centralColor = centralColor()
                            val levelColor: Color
                            var signColor: Color
                            val signText: AnnotatedString

                            if (batteryPercent > BatteryMonitor.batteryAlertThreshold) {
                                levelColor = centralColor.nominal.level
                                signColor = centralColor.nominal.sign
                            } else {
                                levelColor = centralColor.issue.level
                                signColor = centralColor.issue.sign
                            }
                            if (BatteryMonitor.predictionMs > 0L) {
                                val formattedDuration = BatteryMonitor.predictionMs.milliseconds.toComponents { d, h, m, _, _ -> if (d > 0) stringResource(R.string.duration_d_h_str_format, d, h) else if (h > 0) stringResource(R.string.duration_h_m_str_format, h, m) else stringResource(R.string.duration_m_str_format, m) }
                                signText = buildAnnotatedString {
                                    withStyle(M.arrowDown) { append(stringResource(R.string._arrow_down_) + ' ') } /* ⬇ */
                                    append(formattedDuration)
                                }
                            } else if (BatteryMonitor.timeRemainingMs > 0L) {
                                signColor = MaterialTheme.colorScheme.secondary
                                val formattedDuration = BatteryMonitor.timeRemainingMs.milliseconds.toComponents { d, h, m, _, _ -> if (d > 0) stringResource(R.string.duration_d_h_str_format, d, h) else if (h > 0) stringResource(R.string.duration_h_m_str_format, h, m) else stringResource(R.string.duration_m_str_format, m) }
                                signText = buildAnnotatedString {
                                    withStyle(M.arrowUp) { append(stringResource(R.string._arrow_up_) + ' ') } /* ⬆ */
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

                                if (BatteryMonitor.batteryAlertThreshold > 0) {
                                    val markerSize = fontConfig.lineHeight * 0.4f
                                    val fraction = BatteryMonitor.batteryAlertThreshold / 100f
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
                                        controller.refreshData(this@MainActivity)
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
                                    DiagnosticsReport(fontConfig, rows = report)
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
private fun DiagnosticsReport(fontConfig: CentralFont, rows: List<ReportRow>) {
    SelectionContainer {
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
                                autoSize = TextAutoSize.StepBased(
                                    minFontSize = fontConfig.minReportFontSize[REPORT_LEFT],
                                    maxFontSize = fontConfig.reportStyle.fontSize
                                ),
                                style = fontConfig.reportStyle
                            )

                            Text(
                                text = row.value,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Start,
                                maxLines = 1,
                                softWrap = false,
                                autoSize = TextAutoSize.StepBased(
                                    minFontSize = fontConfig.minReportFontSize[REPORT_RIGHT],
                                    maxFontSize = fontConfig.reportStyle.fontSize
                                ),
                                style = fontConfig.reportStyle
                            )
                        }
                    }
                }
            }
        }
    }
}