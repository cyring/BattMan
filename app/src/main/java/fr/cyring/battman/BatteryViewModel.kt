package fr.cyring.battman

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.provider.Settings.System.getInt
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.SharedPreferences
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.core.text.util.LocalePreferences
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

const val BATTERY_ALERT_THRESHOLD = 10
const val MIN_ALERT_THRESHOLD = 0
const val MAX_ALERT_THRESHOLD = 100

const val DEFAULT_MAX_ALERTS = 3
const val MIN_MAX_ALERTS = 0
const val MAX_MAX_ALERTS = 10

data class BatteryReportRow(
    val label: String,
    val value: AnnotatedString
)

const val CASE_FOCUS = 0
const val CASE_AWAY = 1

const val PREF_FOCUS = "focusRefresh"
const val PREF_AWAY = "awayRefresh"

const val PREF_TIME_FOCUS = "focusRefreshTime"
const val PREF_TIME_AWAY = "awayRefreshTime"

const val MIN_TIME_FOCUS = 0L
const val MAX_TIME_FOCUS = 25000L
const val DEFAULT_TIME_FOCUS = 3000L

const val MIN_TIME_AWAY = 0L
const val MAX_TIME_AWAY = 360L
const val DEFAULT_TIME_AWAY = 15L

const val DEFAULT_AUTO_FOCUS = true
const val DEFAULT_AUTO_AWAY = false

fun SharedPreferences.getLongInBounds(key: String, min: Long, max: Long, default: Long): Long {
    val value = getLong(key, default)
    return if (value in min..max) value else default
}

fun SharedPreferences.getIntInBounds(key: String, min: Int, max: Int, default: Int): Int {
    val value = getInt(key, default)
    return if (value in min..max) value else default
}

enum class RefreshCase(
    val prefKey: String,
    val prefTimeKey: String,
    val minTimeMs: Long,
    val maxTimeMs: Long,
    val defaultTimeMs: Long,
    val defaultAuto: Boolean = true
) {
    FOCUS(PREF_FOCUS, PREF_TIME_FOCUS, MIN_TIME_FOCUS, MAX_TIME_FOCUS, DEFAULT_TIME_FOCUS, DEFAULT_AUTO_FOCUS),
    AWAY(PREF_AWAY, PREF_TIME_AWAY, MIN_TIME_AWAY * 60_000L, MAX_TIME_AWAY * 60_000L, DEFAULT_TIME_AWAY * 60_000L, DEFAULT_AUTO_AWAY)
}

object BatteryTrendMask {
    // Section 1 (Bits 0-3): Trend (Discrete 1-bit flags)
    const val TREND_INCREASING = 1 shl 0        // Bit 0  (0b00000000000000000000000000000001)
    const val TREND_DECREASING = 1 shl 1        // Bit 1  (0b00000000000000000000000000000010)
    const val TREND_UNCHANGED  = 1 shl 2        // Bit 2  (0b00000000000000000000000000000100)

    // Section 2 (Bits 4-8): Battery Charge Status (Discrete 1-bit flags)
    const val STATE_UNKNOWN      = 1 shl 4       // Bit 4  (0b00000000000000000000000000010000)
    const val STATE_CHARGING     = 1 shl 5       // Bit 5  (0b00000000000000000000000000100000)
    const val STATE_DISCHARGING  = 1 shl 6       // Bit 6  (0b00000000000000000000000001000000)
    const val STATE_NOT_CHARGING = 1 shl 7       // Bit 7  (0b00000000000000000000000010000000)
    const val STATE_FULL         = 1 shl 8       // Bit 8  (0b00000000000000000000000100000000)
    const val MASK_CHARGE        = STATE_UNKNOWN or STATE_CHARGING or STATE_DISCHARGING or STATE_NOT_CHARGING or STATE_FULL

    // Section 3 (Bits 9-10): App/Screen Lifecycle (Discrete 1-bit flags)
    const val STATE_RESUMED = 1 shl 9           // Bit 9  (0b00000000000000000000001000000000)
}

class BatteryViewModel(application: Application) : AndroidViewModel(application) {
    private val powerManager = application.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val displayManager by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            application.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
        } else {
            null
        }
    }
    private val prefs = application.getSharedPreferences("BattMan", Context.MODE_PRIVATE)

    data class TimeTracker(
        var screenOffStartedAt: Long = 0L,
        var lastScreenOffDurationMs: Long = -1L
    )

    val timeTracker = TimeTracker()

    companion object {
        var timeRemainingMs by mutableLongStateOf(-1L)
        var predictionMs by mutableLongStateOf(-1L)
        var batteryAlertThreshold by mutableIntStateOf(BATTERY_ALERT_THRESHOLD)
        var maxAlerts by mutableIntStateOf(DEFAULT_MAX_ALERTS)
        var batteryPercent by mutableIntStateOf(100)
        var previousPercent = -1

        var batteryTrendMask: Int = BatteryTrendMask.TREND_UNCHANGED
            private set

        fun updateBatteryLevel(intent: Intent, isResumedEvent: Boolean = false): Int {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            batteryPercent = if (scale > 0 && level >= 0) level * 100 / scale else -1

            // Nibble 1: Trend
            val trendBits = when {
                isResumedEvent || (batteryTrendMask and BatteryTrendMask.STATE_RESUMED) != 0 -> {
                    BatteryTrendMask.TREND_DECREASING
                }
                previousPercent != -1 && batteryPercent != -1 -> when {
                    batteryPercent > previousPercent -> BatteryTrendMask.TREND_INCREASING
                    batteryPercent < previousPercent -> BatteryTrendMask.TREND_DECREASING
                    else -> BatteryTrendMask.TREND_UNCHANGED
                }
                else -> BatteryTrendMask.TREND_UNCHANGED
            }
            previousPercent = batteryPercent

            // Nibble 2: Battery Charge Status
            val rawStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val chargeBits = when (rawStatus) {
                BatteryManager.BATTERY_STATUS_CHARGING -> BatteryTrendMask.STATE_CHARGING
                BatteryManager.BATTERY_STATUS_DISCHARGING -> BatteryTrendMask.STATE_DISCHARGING
                BatteryManager.BATTERY_STATUS_FULL -> BatteryTrendMask.STATE_FULL
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> BatteryTrendMask.STATE_NOT_CHARGING
                BatteryManager.BATTERY_STATUS_UNKNOWN -> BatteryTrendMask.STATE_UNKNOWN
                else -> BatteryTrendMask.STATE_UNKNOWN
            }

            // Nibble 3: App Lifecycle State
            val stateBits = if (isResumedEvent) BatteryTrendMask.STATE_RESUMED else 0

            batteryTrendMask = trendBits or chargeBits or stateBits
            return batteryTrendMask
        }
    }

    val autoRefresh = mutableStateListOf(
        prefs.getBoolean(RefreshCase.FOCUS.prefKey, RefreshCase.FOCUS.defaultAuto),
        prefs.getBoolean(RefreshCase.AWAY.prefKey, RefreshCase.AWAY.defaultAuto)
    )

    val refreshTime = mutableStateListOf(
        prefs.getLongInBounds(RefreshCase.FOCUS.prefTimeKey, RefreshCase.FOCUS.minTimeMs, RefreshCase.FOCUS.maxTimeMs, prefs.getLong("refreshTime", RefreshCase.FOCUS.defaultTimeMs)),
        prefs.getLongInBounds(RefreshCase.AWAY.prefTimeKey, RefreshCase.AWAY.minTimeMs, RefreshCase.AWAY.maxTimeMs, RefreshCase.AWAY.defaultTimeMs)
    )

    var report by mutableStateOf<List<BatteryReportRow>>(emptyList())
    var sysInfo: String = ""

    private var refreshJob: Job? = null

    init {
        batteryAlertThreshold = prefs.getIntInBounds("batteryAlertThreshold", MIN_ALERT_THRESHOLD, MAX_ALERT_THRESHOLD, BATTERY_ALERT_THRESHOLD)
        maxAlerts = prefs.getIntInBounds("maxAlerts", MIN_MAX_ALERTS, MAX_MAX_ALERTS, DEFAULT_MAX_ALERTS)
        BatteryEventReceiver.maxAlerts = maxAlerts
        refreshData()
        sysInfo = readSystemInfo()

        // Reactive observer: automatically manages the foreground loop when CASE_FOCUS settings change
        viewModelScope.launch {
            snapshotFlow { autoRefresh[CASE_FOCUS] to refreshTime[CASE_FOCUS] }
                .collect { (enabled, time) ->
                    refreshJob?.cancel()
                    if (enabled) {
                        refreshJob = viewModelScope.launch {
                            while (true) {
                                delay(time.milliseconds)
                                refreshData()
                            }
                        }
                    }
                }
        }
    }

    fun refreshData() {
        report = readBatteryInfo(getApplication(), powerManager, timeTracker)
    }

    fun updateAutoRefresh(idx: Int, enabled: Boolean, time: Long) {
        val config = RefreshCase.entries[idx]
        autoRefresh[idx] = enabled
        refreshTime[idx] = time
        prefs.edit()
            .putBoolean(config.prefKey, enabled)
            .putLong(config.prefTimeKey, time)
            .apply()
    }

    // Function pointer callbacks table (C-style function pointer array)
    val updateAutoRefreshHandlers: Array<(Boolean, Long) -> Unit> = arrayOf(
        { enabled, time -> updateAutoRefresh(CASE_FOCUS, enabled, time) },
        { enabled, time -> updateAutoRefresh(CASE_AWAY, enabled, time) }
    )

    var themeMode by mutableIntStateOf(prefs.getInt("themeMode", THEME_SYSTEM))

    fun updateThemeMode(mode: Int) {
        themeMode = mode
        prefs.edit().putInt("themeMode", mode).apply()
    }

    var fontSizeMode by mutableIntStateOf(prefs.getInt("fontSizeMode", FONT_SIZE_SYSTEM))

    fun updateFontSizeMode(mode: Int) {
        fontSizeMode = mode
        prefs.edit().putInt("fontSizeMode", mode).apply()
    }

    fun updateBatteryAlertThreshold(value: Int) {
        batteryAlertThreshold = value
        prefs.edit().putInt("batteryAlertThreshold", value).apply()
    }

    fun updateMaxAlerts(value: Int) {
        maxAlerts = value
        BatteryEventReceiver.maxAlerts = value
        BatteryEventReceiver.resetAlertCount()
        prefs.edit().putInt("maxAlerts", value).apply()
    }

    fun onScreenPaused() {
        if (!powerManager.isInteractive && timeTracker.screenOffStartedAt == 0L) {
            timeTracker.screenOffStartedAt = System.currentTimeMillis()
        }
        val awayMinutes = refreshTime[CASE_AWAY] / 60_000L
        if (autoRefresh[CASE_AWAY] && awayMinutes > 0 && batteryAlertThreshold > 0) {
            BatteryAlertWorker.schedule(getApplication(), awayMinutes)
        } else {
            BatteryAlertWorker.cancel(getApplication())
        }
    }

    fun onScreenResumed() {
        BatteryAlertWorker.cancel(getApplication())
        val intent = getApplication<Application>().registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent != null) {
            updateBatteryLevel(intent, isResumedEvent = true)
        }
        if (powerManager.isInteractive && timeTracker.screenOffStartedAt > 0L) {
            timeTracker.lastScreenOffDurationMs = System.currentTimeMillis() - timeTracker.screenOffStartedAt
            timeTracker.screenOffStartedAt = 0L
        }
    }

    private fun readSystemInfo(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL} - Android ${Build.VERSION.RELEASE} - API ${Build.VERSION.SDK_INT}"
    }

    data class DisplayInfo(
        val brightness: Int,
        val adaptive: Boolean,
        val nightState: Boolean,
        val touchKind: Int,
        val refreshRate: Float,
        val screenTimeoutMs: Long,
        val screenSaverEnabled: Boolean?
    )

    private fun readDisplayInfo(ctx: Context): DisplayInfo {
        val resolver = ctx.contentResolver
        val sysConf = ctx.resources.configuration

        val brightness = try {
            getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1)
        } catch (_: Exception) {
            -1
        }

        val adaptive = try {
            getInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } catch (_: Exception) {
            false
        }

        val screenTimeoutMs = try {
            Settings.System.getLong(resolver, Settings.System.SCREEN_OFF_TIMEOUT, -1L)
        } catch (_: Exception) {
            -1L
        }

        val screenSaverEnabled = try {
            Settings.Secure.getInt(
                ctx.contentResolver,
                "screensaver_enabled"
            ) != 0
        } catch (_: Exception) {
            null
        }

        return DisplayInfo(
            brightness = brightness,
            adaptive = adaptive,
            nightState = (sysConf.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES,
            touchKind = sysConf.touchscreen,
            refreshRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val display = displayManager?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
                    display?.refreshRate ?: 0f
                } catch (_: Exception) {
                    0f
                }
            } else {
                0f
            },
            screenTimeoutMs = screenTimeoutMs,
            screenSaverEnabled = screenSaverEnabled
        )
    }

    private fun formatTargetTime(context: Context, durationMs: Long, id: Int, style: SpanStyle): AnnotatedString {
        if (durationMs <= 0L) {
            return context.getString(R.string.waiting).waitingStyle()
        }
        val targetMs = System.currentTimeMillis() + durationMs
        val targetDate = java.util.Date(targetMs)
        val timeString = android.text.format.DateFormat.getTimeFormat(context).format(targetDate)

        val nowCal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val targetCal = java.util.Calendar.getInstance().apply {
            timeInMillis = targetMs
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val diffDays = ((targetCal.timeInMillis - nowCal.timeInMillis) / (24 * 60 * 60 * 1000L)).toInt()

        val timeContent = when (diffDays) {
            0 -> timeString
            1 -> context.getString(R.string.tomorrow, timeString)
            else -> context.getString(R.string.in_days, diffDays, timeString)
        }

        return buildAnnotatedString {
            withStyle(style) {
                append(context.getString(id))
            }
            append(timeContent)
        }
    }

    fun buildCycle(ctx: Context, count: Int) = AnnotatedString(if (count < 0) ctx.getString(R.string.unavailable) else count.toString())

    fun buildStatus(ctx: Context) = ctx.getString(when (batteryTrendMask and BatteryTrendMask.MASK_CHARGE) {
        BatteryTrendMask.STATE_CHARGING -> R.string.charging
        BatteryTrendMask.STATE_DISCHARGING -> R.string.discharging
        BatteryTrendMask.STATE_FULL -> R.string.full
        BatteryTrendMask.STATE_NOT_CHARGING -> R.string.not_charging
        BatteryTrendMask.STATE_UNKNOWN -> R.string.unknown
        else -> R.string.undefined
    })

    fun buildHealth(ctx: Context, intent: Intent) = AnnotatedString(ctx.getString(when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
        BatteryManager.BATTERY_HEALTH_GOOD -> R.string.good
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> R.string.overheated
        BatteryManager.BATTERY_HEALTH_DEAD -> R.string.dead
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> R.string.over_voltage
        BatteryManager.BATTERY_HEALTH_COLD -> R.string.cold
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> R.string.failure
        BatteryManager.BATTERY_HEALTH_UNKNOWN -> R.string.unknown
        else -> R.string.undefined
    }))

    fun buildLowBattery(ctx: Context, intent: Intent) = AnnotatedString(ctx.getString(if (intent.getBooleanExtra(BatteryManager.EXTRA_BATTERY_LOW, false) == true) {
        R.string.yes
    } else {
        R.string.no
    }))

    fun buildBatteryPlug(ctx: Context, intent: Intent) = AnnotatedString(ctx.getString(when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
        0 -> R.string.on_battery
        BatteryManager.BATTERY_PLUGGED_AC -> R.string.ac_charger
        BatteryManager.BATTERY_PLUGGED_USB -> R.string.usb_plug
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> R.string.wireless
        BatteryManager.BATTERY_PLUGGED_DOCK -> R.string.dock
        else -> R.string.powered
    }))

    fun buildBatteryPresent(ctx: Context, intent: Intent) = AnnotatedString(ctx.getString(if (intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false) == true) {
        R.string.yes
    } else {
        R.string.no
    }))

    fun buildBatterySaver(ctx: Context) = AnnotatedString(ctx.getString(if (powerManager.isPowerSaveMode == true) {
        R.string.activated
    } else {
        R.string.deactivated
    }))

    fun property(ctx: Context, manager: BatteryManager, id: Int): String {
        val value = manager.getIntProperty(id)
        return if (value == Int.MIN_VALUE) {
            ctx.getString(R.string.unavailable)
        } else {
            value.toString()
        }
    }

    fun buildThermal(ctx: Context) = AnnotatedString(ctx.getString(if (Build.VERSION.SDK_INT >= 29) {
        when (powerManager.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> R.string.none
            PowerManager.THERMAL_STATUS_LIGHT -> R.string.light
            PowerManager.THERMAL_STATUS_MODERATE -> R.string.moderate
            PowerManager.THERMAL_STATUS_SEVERE -> R.string.severe
            PowerManager.THERMAL_STATUS_CRITICAL -> R.string.critical
            PowerManager.THERMAL_STATUS_EMERGENCY -> R.string.emergency
            PowerManager.THERMAL_STATUS_SHUTDOWN -> R.string.shutdown
            else -> R.string.undefined
        }
    } else {
        R.string.unsupported
    }))

    fun buildVoltage(ctx: Context, locale: Locale, voltageMv: Int) = AnnotatedString(
        if (voltageMv >= 0) {
            "%.3f V".format(locale, voltageMv / 1000.0)
        } else {
            ctx.getString(R.string.unavailable)
        }
    )

    fun buildTemperature(ctx: Context, locale: Locale, temp: Int) = AnnotatedString(
        if (temp >= 0) {
            val celsius = temp / 10.0
            val unit = LocalePreferences.getTemperatureUnit()
            if (unit == LocalePreferences.TemperatureUnit.FAHRENHEIT) {
                val fahrenheit = (celsius * 1.8) + 32.0
                ctx.getString(R.string.fahrenheit_format).format(locale, fahrenheit)
            } else {
                ctx.getString(R.string.celsius_format).format(locale, celsius)
            }
        } else {
            ctx.getString(R.string.unavailable)
        }
    )

    fun buildEnergyCounter(ctx: Context, energy: Long) = AnnotatedString(
        if (energy != Long.MIN_VALUE) {
            "$energy nWh"
        } else {
            ctx.getString(R.string.unavailable)
        }
    )

    fun buildChargeCounter(ctx: Context, manager: BatteryManager) = AnnotatedString(property(ctx, manager, BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) + " µAh")

    fun buildCurrentNow(ctx: Context, manager: BatteryManager) = AnnotatedString(property(ctx, manager, BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) + " µA")

    fun buildCurrentAverage(ctx: Context, manager: BatteryManager) = AnnotatedString(property(ctx, manager, BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) + " µA")

    fun buildRemaining(ctx: Context, ms: Long): Pair<AnnotatedString, AnnotatedString> {
        if (ms <= 0L) {
            val waiting = ctx.getString(R.string.waiting).waitingStyle()
            return waiting to waiting
        } else {
            val remaining = AnnotatedString(ms.milliseconds.toComponents { _, h, m, _, _ -> if (h > 0) ctx.getString(R.string.duration_h_m_str_format, h, m) else ctx.getString(R.string.duration_m_str_format, m) })
            return remaining to formatTargetTime(ctx, ms, R.string._arrow_up_, arrowUp)
        }
    }

    fun buildPrediction(ctx: Context, ms: Long, default: String): Pair<AnnotatedString, AnnotatedString> {
        if (ms <= 0L) {
            val issue = AnnotatedString(when (ms) {
                -1L -> ctx.getString(R.string.unsupported)
                -2L -> ctx.getString(R.string.undefined)
                -3L -> default
                0L -> ctx.getString(R.string.unavailable)
                else -> ctx.getString(R.string.unknown)
            })
            val waiting = ctx.getString(R.string.waiting).waitingStyle()
            return issue to waiting
        } else {
            val duration = AnnotatedString(ms.milliseconds.toComponents { _, h, m, _, _ -> if (h > 0) ctx.getString(R.string.duration_h_m_str_format, h, m) else ctx.getString(R.string.duration_m_str_format, m) })
            return duration to formatTargetTime(ctx, ms, R.string._arrow_down_, arrowDown)
        }
    }

    fun buildBrightness(ctx: Context, display: DisplayInfo): String = if (display.brightness >= 0) display.brightness.toString() else ctx.getString(R.string.unknown)

    fun buildAdaptiveMode(ctx: Context, display: DisplayInfo): AnnotatedString = AnnotatedString(ctx.getString(if (display.adaptive) R.string.yes else R.string.no))

    fun buildNightMode(ctx: Context, display: DisplayInfo): AnnotatedString = AnnotatedString(ctx.getString(if (display.nightState) R.string.yes else R.string.no))

    fun buildTouchScreen(ctx: Context, display: DisplayInfo) = AnnotatedString(ctx.getString(when (display.touchKind) {
        Configuration.TOUCHSCREEN_FINGER -> R.string.finger
        Configuration.TOUCHSCREEN_NOTOUCH -> R.string.notouch
        Configuration.TOUCHSCREEN_UNDEFINED -> R.string.undefined
        else -> R.string.unknown
    }))

    fun buildSmoothDisplay(ctx: Context, display: DisplayInfo) = AnnotatedString(if (display.refreshRate == 0f) ctx.getString(R.string.unsupported) else "${display.refreshRate.toInt()} Hz")

    fun buildScreenTimeout(ctx: Context, display: DisplayInfo) = AnnotatedString(when {
        display.screenTimeoutMs < 0L -> ctx.getString(R.string.unknown)
        display.screenTimeoutMs == Int.MAX_VALUE.toLong() -> ctx.getString(R.string.never)
        else -> display.screenTimeoutMs.milliseconds.toComponents { _, h, m, s, ns -> if (h > 0) ctx.getString(R.string.duration_h_m_s_str_format, h, m, s) else if (m > 0) ctx.getString(R.string.duration_m_s_str_format, m, s) else ctx.getString(R.string.duration_ms_str_format, s, ns / 1_000_000L) }
    })

    fun buildScreenSaver(ctx: Context, display: DisplayInfo) = AnnotatedString(ctx.getString(if (display.screenSaverEnabled == true) R.string.activated else R.string.deactivated))

    fun buildLastScreenOffDuration(ctx: Context, ms: Long): AnnotatedString = if (ms == -1L) {
        ctx.getString(R.string.waiting).waitingStyle()
    } else {
        AnnotatedString(ms.milliseconds.toComponents { _, h, m, s, ns -> if (h > 0) ctx.getString(R.string.duration_h_m_s_str_format, h, m, s) else if (m > 0) ctx.getString(R.string.duration_m_s_str_format, m, s) else ctx.getString(R.string.duration_ms_str_format, s, ns / 1_000_000L) })
    }

    fun readBatteryInfo(context: Context, powerManager: PowerManager, timeTracker: TimeTracker): List<BatteryReportRow> {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

        @Suppress("DEPRECATION")
        val intent = context.registerReceiver(null, filter)
            ?: return listOf(
                BatteryReportRow(
                    context.getString(R.string.unavailable),
                    AnnotatedString("")
                )
            )

        val manager = context.getSystemService(
            Context.BATTERY_SERVICE
        ) as BatteryManager

        fun extra(key: String, default: Int = -1) =
            intent.getIntExtra(key, default)

        val count = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            extra(BatteryManager.EXTRA_CYCLE_COUNT)
        } else {
            -1
        }

        updateBatteryLevel(intent)

        val currentLocale = Locale.getDefault()

        val cycle = buildCycle(context, count)
        val status = buildStatus(context)
        val health = buildHealth(context, intent)

        val energy = manager.getLongProperty(
            BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER
        )

        val voltageMv = extra(BatteryManager.EXTRA_VOLTAGE)
        val batteryTemp = extra(BatteryManager.EXTRA_TEMPERATURE)

        val extraTech = intent.getStringExtra(
            BatteryManager.EXTRA_TECHNOLOGY
        ) ?: "Unknown"

        val lowBatteryState = buildLowBattery(context, intent)
        val batteryPlugged = buildBatteryPlug(context, intent)
        val batteryPresent = buildBatteryPresent(context, intent)

        val technology = AnnotatedString(if (extraTech == "Unknown") {
            Build.MANUFACTURER
        } else {
            extraTech
        })

        val batterySaver = buildBatterySaver(context)

        predictionMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val duration: java.time.Duration? = powerManager.batteryDischargePrediction
                duration?.toMillis() ?: -3L
            } catch (_: Exception) {
                -2L
            }
        } else {
             -1L
        }

        val thermalStatus = buildThermal(context)
        val voltage = buildVoltage(context, currentLocale, voltageMv)
        val temperature = buildTemperature(context, currentLocale, batteryTemp)

        val energyCounter = buildEnergyCounter(context, energy)
        val chargeCounter = buildChargeCounter(context, manager)
        val currentNow = buildCurrentNow(context, manager)
        val currentAverage = buildCurrentAverage(context, manager)

        timeRemainingMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.computeChargeTimeRemaining()
        } else {
            -1L
        }

        val (timeRemaining, fullyChargedAt) = buildRemaining(context, timeRemainingMs)
        val (prediction, totallyEmptyAt) = buildPrediction(context, predictionMs, status)

        val display: DisplayInfo = readDisplayInfo(context)

        val brightnessLevel = buildBrightness(context, display)
        val adaptiveMode = buildAdaptiveMode(context, display)
        val nightMode = buildNightMode(context, display)
        val touchScreen = buildTouchScreen(context, display)
        val smoothDisplay = buildSmoothDisplay(context, display)

        val screenTimeout = buildScreenTimeout(context, display)
        val screenSaver = buildScreenSaver(context, display)

        val lastScreenOffDuration = buildLastScreenOffDuration(context, timeTracker.lastScreenOffDurationMs)

        fun row(labelId: Int, value: AnnotatedString) =
            BatteryReportRow(context.getString(labelId), value)

        return listOf(
            row(R.string.level, AnnotatedString("$batteryPercent%")),
            row(R.string.cycle, cycle),
            row(R.string.state, AnnotatedString(status)),
            row(R.string.health, health),
            row(R.string.technology, technology),

            BatteryReportRow(context.getString(R.string._zap_), AnnotatedString("")),    /* ⚡ */

            row(R.string.voltage, voltage),
            row(R.string.temperature, temperature),
            row(R.string.energy_counter, energyCounter),
            row(R.string.charge_counter, chargeCounter),
            row(R.string.current_now, currentNow),
            row(R.string.current_average, currentAverage),

            BatteryReportRow(context.getString(R.string._electric_plug_), AnnotatedString("")),

            row(R.string.battery_plugged, batteryPlugged),
            row(R.string.time_remaining, timeRemaining),
            row(R.string.fully_charged, fullyChargedAt),
            row(R.string.battery_saver, batterySaver),

            BatteryReportRow(context.getString(R.string._battery_), AnnotatedString("")),

            row(R.string.thermal_status, thermalStatus),
            row(R.string.battery_present, batteryPresent),
            row(R.string.battery_low, lowBatteryState),
            row(R.string.prediction, prediction),
            row(R.string.totally_empty, totallyEmptyAt),

            BatteryReportRow(context.getString(R.string._mobile_phone_), AnnotatedString("")),

            row(R.string.brightness, AnnotatedString("${brightnessLevel}/255")),
            row(R.string.adaptive, adaptiveMode),
            row(R.string.mode_night, nightMode),
            row(R.string.touchscreen, touchScreen),
            row(R.string.refresh_rate, smoothDisplay),

            BatteryReportRow(context.getString(R.string._alarm_clock_), AnnotatedString("")), /* ⏰ */

            row(R.string.screen_timeout, screenTimeout),
            row(R.string.screen_saver, screenSaver),
            row(R.string.screen_off, lastScreenOffDuration)
        )
    }
}
