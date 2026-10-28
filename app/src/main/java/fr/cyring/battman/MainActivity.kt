package fr.cyring.battman

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.res.Resources
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.System.getInt
import android.text.method.Touch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.clickable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.DisplayMode
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import fr.cyring.battman.BuildConfig
import fr.cyring.battman.R
import fr.cyring.battman.ui.theme.BattManTheme
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

public var batteryPercent = 100

private data class BatteryReportRow(
    val label: String,
    val value: String
)

private var lineHeight: Dp = 16.dp

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val prefs = remember {
                getSharedPreferences("BattMan", Context.MODE_PRIVATE)
            }

            var autoRefresh by remember {
                mutableStateOf(prefs.getBoolean("autoRefresh", true))
            }

            var refreshTime by remember {
                mutableLongStateOf(prefs.getLong("refreshTime", 3000L))
            }

            var report by remember {
                mutableStateOf(readBatteryInfo(this@MainActivity))
            }

            LaunchedEffect(autoRefresh, refreshTime) {
                while (autoRefresh) {
                    delay(refreshTime.milliseconds)
                    report = readBatteryInfo(this@MainActivity)
                }
            }

            var sysInfo by remember {
                mutableStateOf(readSystemInfo(this@MainActivity))
            }

            BattManTheme {
                val reportTextStyle = MaterialTheme.typography.labelSmall
                lineHeight = with(LocalDensity.current) {
                    reportTextStyle.lineHeight.toDp()
                }

                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val coroutineScope = rememberCoroutineScope()

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
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

                            Text(
                                text = stringResource(R.string.auto_refresh_settings),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                            )
                            HorizontalDivider()

                            // Auto Off row with Switch
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        autoRefresh = false
                                        prefs.edit().putBoolean("autoRefresh", false).apply()
                                    }
                                    .padding(horizontal = 24.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.auto_off),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Switch(
                                    checked = !autoRefresh,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            autoRefresh = false
                                            prefs.edit().putBoolean("autoRefresh", false).apply()
                                        }
                                    }
                                )
                            }

                            // 1 second row with Switch
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        autoRefresh = true
                                        refreshTime = 1000L
                                        prefs.edit().putBoolean("autoRefresh", true).putLong("refreshTime", 1000L).apply()
                                    }
                                    .padding(horizontal = 24.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.auto_1_sec),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Switch(
                                    checked = autoRefresh && refreshTime == 1000L,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            autoRefresh = true
                                            refreshTime = 1000L
                                            prefs.edit().putBoolean("autoRefresh", true).putLong("refreshTime", 1000L).apply()
                                        }
                                    }
                                )
                            }

                            // 3 seconds row with Switch
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        autoRefresh = true
                                        refreshTime = 3000L
                                        prefs.edit().putBoolean("autoRefresh", true).putLong("refreshTime", 3000L).apply()
                                    }
                                    .padding(horizontal = 24.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.auto_3_sec),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Switch(
                                    checked = autoRefresh && refreshTime == 3000L,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            autoRefresh = true
                                            refreshTime = 3000L
                                            prefs.edit().putBoolean("autoRefresh", true).putLong("refreshTime", 3000L).apply()
                                        }
                                    }
                                )
                            }

                            // 6 seconds row with Switch
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        autoRefresh = true
                                        refreshTime = 6000L
                                        prefs.edit().putBoolean("autoRefresh", true).putLong("refreshTime", 6000L).apply()
                                    }
                                    .padding(horizontal = 24.dp, vertical = 8.dp),

                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.auto_6_sec),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Switch(
                                    checked = autoRefresh && refreshTime == 6000L,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            autoRefresh = true
                                            refreshTime = 6000L
                                            prefs.edit().putBoolean("autoRefresh", true).putLong("refreshTime", 6000L).apply()
                                        }
                                    }
                                )
                            }
                            HorizontalDivider()
                            Spacer(modifier = Modifier.fillMaxHeight(0.7f))
                            HorizontalDivider()
                            Text(
                                "Version ${BuildConfig.VERSION_NAME}\n© 2026 CYRIL COURTIAT",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                            )
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
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = stringResource(R.string.subtitle),
                                        maxLines = 1,
                                        softWrap = false,
                                        autoSize = TextAutoSize.StepBased(
                                            maxFontSize = MaterialTheme.typography.headlineSmall.fontSize
                                        ),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Spacer(modifier = Modifier.padding(24.dp))
                            }
                            Spacer(modifier = Modifier.height(lineHeight * 0.25f))

                            var isRefreshing by remember { mutableStateOf(false) }

                            PullToRefreshBox(
                                isRefreshing = isRefreshing,
                                onRefresh = {
                                    isRefreshing = true
                                    coroutineScope.launch {
                                        report = readBatteryInfo(this@MainActivity)
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
                                        color = if (batteryPercent > 10) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.Red
                                        }
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(lineHeight)
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
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(lineHeight * 0.75f))

                                    BatteryReport(rows = report)
                                }
                            }
                            Spacer(modifier = Modifier.height(lineHeight * 0.75f))

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
                            Spacer(modifier = Modifier.height(lineHeight * 0.75f))

                            Text(
                                text = sysInfo,
                                softWrap = false,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                ),
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
private fun BatteryReport(rows: List<BatteryReportRow>) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        rows.forEach { row ->

            if (row.label.isEmpty() && row.value.isEmpty()) {
                Spacer(modifier = Modifier.height(lineHeight))
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = row.label,
                        modifier = Modifier.weight(1.5f),
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    )

                    Text(
                        text = row.value,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    )
                }
            }
        }
    }
}

private fun readBatteryInfo(context: Context): List<BatteryReportRow> {
    val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

    @Suppress("DEPRECATION")
    val intent = context.registerReceiver(null, filter)
        ?: return listOf(
            BatteryReportRow(
                context.getString(R.string.unavailable),
                ""
            )
        )

    val manager = context.getSystemService(
        Context.BATTERY_SERVICE
    ) as BatteryManager

    fun extra(key: String, default: Int = -1) =
        intent.getIntExtra(key, default)

    fun property(id: Int): String {
        val value = manager.getIntProperty(id)
        return if (value == Int.MIN_VALUE) {
            context.getString(R.string.unavailable)
        } else {
            value.toString()
        }
    }

    val cycle = extra(BatteryManager.EXTRA_CYCLE_COUNT)
    val level = extra(BatteryManager.EXTRA_LEVEL)
    val scale = extra(BatteryManager.EXTRA_SCALE, 100)

    batteryPercent = if (scale > 0 && level >= 0) {
        level * 100 / scale
    } else {
        -1
    }

    val status = when (extra(BatteryManager.EXTRA_STATUS)) {
        BatteryManager.BATTERY_STATUS_CHARGING -> context.getString(R.string.charging)
        BatteryManager.BATTERY_STATUS_DISCHARGING -> context.getString(R.string.discharging)
        BatteryManager.BATTERY_STATUS_FULL -> context.getString(R.string.full)
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> context.getString(R.string.not_charging)
        BatteryManager.BATTERY_STATUS_UNKNOWN -> context.getString(R.string.unknown)
        else -> context.getString(R.string.undefined)
    }

    val health = when (extra(BatteryManager.EXTRA_HEALTH)) {
        BatteryManager.BATTERY_HEALTH_GOOD -> context.getString(R.string.good)
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> context.getString(R.string.overheated)
        BatteryManager.BATTERY_HEALTH_DEAD -> context.getString(R.string.dead)
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> context.getString(R.string.over_voltage)
        BatteryManager.BATTERY_HEALTH_COLD -> context.getString(R.string.cold)
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> context.getString(R.string.failure)
        BatteryManager.BATTERY_HEALTH_UNKNOWN -> context.getString(R.string.unknown)
        else -> context.getString(R.string.undefined)
    }

    val energy = manager.getLongProperty(
        BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER
    )

    val voltageMv = extra(BatteryManager.EXTRA_VOLTAGE)
    val temperature = extra(BatteryManager.EXTRA_TEMPERATURE)

    val extraTech = intent.getStringExtra(
        BatteryManager.EXTRA_TECHNOLOGY
    ) ?: "Unknown"

    val lowBatteryState = if (intent.getBooleanExtra(BatteryManager.EXTRA_BATTERY_LOW, false) == true) {
        context.getString(R.string.yes)
    } else {
        context.getString(R.string.no)
    }

    val batteryPlugged = when (extra(BatteryManager.EXTRA_PLUGGED)) {
        0 -> context.getString(R.string.on_battery)
        BatteryManager.BATTERY_PLUGGED_AC -> context.getString(R.string.ac_charger)
        BatteryManager.BATTERY_PLUGGED_USB -> context.getString(R.string.usb_plug)
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> context.getString(R.string.wireless)
        BatteryManager.BATTERY_PLUGGED_DOCK -> context.getString(R.string.dock)
        else -> context.getString(R.string.powered)
    }

    val batteryPresent = if (intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false) == true) {
        context.getString(R.string.yes)
    } else {
        context.getString(R.string.no)
    }

    val technology = if ("$extraTech" == "Unknown") {
        "${Build.MANUFACTURER}"
    } else {
        "$extraTech"
    }

    val powerManager = context.getSystemService(
        Context.POWER_SERVICE
    ) as android.os.PowerManager

    val batterySaver = if (powerManager.isPowerSaveMode == true) {
        context.getString(R.string.activated)
    } else {
        context.getString(R.string.deactivated)
    }

    val thermalStatus = if (Build.VERSION.SDK_INT >= 29) {
        when (powerManager.currentThermalStatus) {
            android.os.PowerManager.THERMAL_STATUS_NONE -> context.getString(R.string.none)
            android.os.PowerManager.THERMAL_STATUS_LIGHT -> context.getString(R.string.light)
            android.os.PowerManager.THERMAL_STATUS_MODERATE -> context.getString(R.string.moderate)
            android.os.PowerManager.THERMAL_STATUS_SEVERE -> context.getString(R.string.severe)
            android.os.PowerManager.THERMAL_STATUS_CRITICAL -> context.getString(R.string.critical)
            android.os.PowerManager.THERMAL_STATUS_EMERGENCY -> context.getString(R.string.emergency)
            android.os.PowerManager.THERMAL_STATUS_SHUTDOWN -> context.getString(R.string.shutdown)
            else -> context.getString(R.string.undefined)
        }
    } else {
        context.getString(R.string.unsupported)
    }

    val voltage = if (voltageMv >= 0) {
        "%.3f V".format(Locale.US, voltageMv / 1000.0)
    } else {
        context.getString(R.string.unavailable)
    }

    val tempFormat = if (temperature >= 0) {
        "%.1f °C".format(Locale.US, temperature / 10.0)
    } else {
        context.getString(R.string.unavailable)
    }

    val energyCounter = if (energy != Long.MIN_VALUE) {
        "$energy nWh"
    } else {
        context.getString(R.string.unavailable)
    }

    val chargeCounter = property(
        BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER
    )
    val currentNow = property(
        BatteryManager.BATTERY_PROPERTY_CURRENT_NOW
    )
    val currentAverage = property(
        BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE
    )

    val milliseconds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        manager.computeChargeTimeRemaining()
    } else {
        -1L
    }
    val timeRemaining = if (milliseconds < 0L) {
        context.getString(R.string.unavailable)
    } else {
        val totalMinutes = (milliseconds + 59_999L) / 60_000L
        val hours = (totalMinutes / 60).toInt()
        val minutes = (totalMinutes % 60).toInt()

        val hoursText = context.resources.getQuantityString(
            R.plurals.hours,
            hours,
            hours
        )
        val minutesText = context.resources.getQuantityString(
            R.plurals.minutes,
            minutes,
            minutes
        )
        when {
            hours > 0 && minutes > 0 ->
                context.getString(R.string.duration_format, hoursText, minutesText)

            hours > 0 -> hoursText
            else -> minutesText
        }
    }

    data class DisplayInfo(
        val brightness: Int,
        val adaptive: Boolean,
        val nightState: Boolean,
        val touchKind: Int,
        val refreshRate: Float
    )

    fun readDisplayInfo(ctx: Context): DisplayInfo {
        val resolver = ctx.contentResolver
        val sysConf = Resources.getSystem().configuration
        return DisplayInfo(
            brightness = getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1),
            adaptive = getInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
            nightState = (sysConf.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES,
            sysConf.touchscreen,
            refreshRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ctx.display.refreshRate?: 0f else 0f
        )
    }

    val display: DisplayInfo = readDisplayInfo(context)

    val brightnessLevel = if (display.brightness >= 0) display.brightness.toString() else context.getString(R.string.unknown)
    val adaptiveMode = context.getString(if (display.adaptive) R.string.yes else R.string.no)
    val nightMode = context.getString(if (display.nightState) R.string.yes else R.string.no)
    val touchScreen = when (display.touchKind) {
        Configuration.TOUCHSCREEN_FINGER -> context.getString(R.string.finger)
        Configuration.TOUCHSCREEN_NOTOUCH -> context.getString(R.string.notouch)
        Configuration.TOUCHSCREEN_UNDEFINED -> context.getString(R.string.undefined)
        else -> context.getString(R.string.unknown)
    }
    val smoothDisplay = if (display.refreshRate == 0f) context.getString(R.string.unsupported) else display.refreshRate.toInt().toString() + " Hz"

    fun row(labelId: Int, value: String) =
        BatteryReportRow(context.getString(labelId), value)

    return listOf(
        row(R.string.level, "$batteryPercent%"),
        row(R.string.cycle, cycle.toString()),
        row(R.string.state, status),
        row(R.string.health, health),
        row(R.string.technology, technology),

        BatteryReportRow("", ""),

        row(R.string.voltage, voltage),
        row(R.string.temperature, tempFormat),
        row(R.string.energy_counter, energyCounter),
        row(R.string.charge_counter, "$chargeCounter µAh"),
        row(R.string.current_now, "$currentNow µA"),
        row(R.string.current_average, "$currentAverage µA"),

        BatteryReportRow("", ""),

        row(R.string.battery_plugged, batteryPlugged),
        row(R.string.battery_saver, batterySaver),
        row(R.string.time_remaining, timeRemaining),

        BatteryReportRow("", ""),

        row(R.string.thermal_status, thermalStatus),
        row(R.string.battery_present, batteryPresent),
        row(R.string.battery_low, lowBatteryState),

        BatteryReportRow("",""),

        row(R.string.brightness, "${brightnessLevel}/255"),
        row(R.string.adaptive, adaptiveMode),
        row(R.string.mode_night, nightMode),
        row(R.string.touchscreen, touchScreen),
        row(R.string.refresh_rate,smoothDisplay)
    )
}

private fun readSystemInfo(context: Context): String {
    return """
        ${Build.MANUFACTURER} ${Build.MODEL} - Android ${Build.VERSION.RELEASE} - API ${Build.VERSION.SDK_INT}
    """.trimIndent()
}
