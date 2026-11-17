package fr.cyring.battman.battery

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.SharedPreferences
import android.text.format.DateFormat
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.core.text.util.LocalePreferences
import fr.cyring.battman.C
import fr.cyring.battman.M
import fr.cyring.battman.R
import fr.cyring.battman.waitingStyle
import fr.cyring.battman.ReportRow
import fr.cyring.battman.Diagnostics
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Duration.Companion.milliseconds

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
    FOCUS(C.PREF_FOCUS, C.PREF_TIME_FOCUS, C.MIN_TIME_FOCUS, C.MAX_TIME_FOCUS, C.DEFAULT_TIME_FOCUS, C.DEFAULT_AUTO_FOCUS),
    AWAY(C.PREF_AWAY, C.PREF_TIME_AWAY, C.MIN_TIME_AWAY * 60_000L, C.MAX_TIME_AWAY * 60_000L, C.DEFAULT_TIME_AWAY * 60_000L, C.DEFAULT_AUTO_AWAY)
}

class BatteryViewModel(application: Application) : AndroidViewModel(application) {
    private val powerManager = application.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val prefs = application.getSharedPreferences("BattMan", Context.MODE_PRIVATE)

    var tongue: Array<Locale> = arrayOf(Locale.ENGLISH, Locale.ENGLISH)
    data class TimeTracker(
        var screenOffStartedAt: Long = 0L,
        var lastScreenOffDurationMs: Long = -1L
    )

    val timeTracker = TimeTracker()

    companion object {
        var timeRemainingMs by mutableLongStateOf(-1L)
        var predictionMs by mutableLongStateOf(-1L)
        var batteryAlertThreshold by mutableIntStateOf(C.BATTERY_ALERT_THRESHOLD)
        var maxAlerts by mutableIntStateOf(C.DEFAULT_MAX_ALERTS)
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

    var report by mutableStateOf<List<ReportRow>>(emptyList())

    private var refreshJob: Job? = null

    init {
        batteryAlertThreshold = prefs.getIntInBounds("batteryAlertThreshold", C.MIN_ALERT_THRESHOLD, C.MAX_ALERT_THRESHOLD, C.BATTERY_ALERT_THRESHOLD)
        maxAlerts = prefs.getIntInBounds("maxAlerts", C.MIN_MAX_ALERTS, C.MAX_MAX_ALERTS, C.DEFAULT_MAX_ALERTS)
        BatteryEventReceiver.maxAlerts = maxAlerts
        tongue[C.CASE_FOCUS] = Locale.getDefault()
        tongue[C.CASE_AWAY] = tongue[C.CASE_FOCUS]
        buildData()

        // Reactive observer: automatically manages the foreground loop when CASE_FOCUS settings change
        viewModelScope.launch {
            snapshotFlow { autoRefresh[C.CASE_FOCUS] to refreshTime[C.CASE_FOCUS] }
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

    fun buildData() {
        report = Diagnostics.buildReport(getApplication(), powerManager, timeTracker, this)
    }
    fun refreshData() {
        val context: Context = getApplication()
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        @Suppress("DEPRECATION")
        val intent = context.registerReceiver(null, filter)
        if (intent != null) Diagnostics.updateReport(context, powerManager, timeTracker, intent, this)
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
        { enabled, time -> updateAutoRefresh(C.CASE_FOCUS, enabled, time) },
        { enabled, time -> updateAutoRefresh(C.CASE_AWAY, enabled, time) }
    )

    var themeMode by mutableIntStateOf(prefs.getInt("themeMode", C.THEME_SYSTEM))

    fun updateThemeMode(mode: Int) {
        themeMode = mode
        prefs.edit().putInt("themeMode", mode).apply()
    }

    var fontSizeMode by mutableIntStateOf(prefs.getInt("fontSizeMode", C.FONT_SIZE_SYSTEM))

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
        val context: Context = getApplication()
        if (!powerManager.isInteractive && timeTracker.screenOffStartedAt == 0L) {
            timeTracker.screenOffStartedAt = System.currentTimeMillis()
        }
        val awayMinutes = refreshTime[C.CASE_AWAY] / 60_000L
        if (autoRefresh[C.CASE_AWAY] && awayMinutes > 0 && batteryAlertThreshold > 0) {
            BatteryAlertWorker.schedule(context, awayMinutes)
        } else {
            BatteryAlertWorker.cancel(context)
        }
        tongue[C.CASE_AWAY] = tongue[C.CASE_FOCUS]
    }

    fun onScreenResumed() {
        val context: Context = getApplication()
        BatteryAlertWorker.cancel(context)
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent != null) {
            updateBatteryLevel(intent, isResumedEvent = true)
        }
        if (powerManager.isInteractive && timeTracker.screenOffStartedAt > 0L) {
            timeTracker.lastScreenOffDurationMs = System.currentTimeMillis() - timeTracker.screenOffStartedAt
            timeTracker.screenOffStartedAt = 0L
        }
        tongue[C.CASE_FOCUS] = Locale.getDefault()
        if (tongue[C.CASE_FOCUS] != tongue[C.CASE_AWAY]) {
            buildData()
            tongue[C.CASE_AWAY] = tongue[C.CASE_FOCUS]
        } else {
            refreshData()
        }
    }

    private fun formatTargetTime(context: Context, durationMs: Long, iconId: Int, iconStyle: SpanStyle): AnnotatedString {
        if (durationMs <= 0L) {
            return buildAnnotatedString {
                append(context.getString(R.string._wait_) + ' ')
                append(context.getString(R.string.waiting).waitingStyle())
            }
        }
        val targetMs = System.currentTimeMillis() + durationMs
        val timeString = DateFormat.getTimeFormat(context).format(Date(targetMs))

        val now = System.currentTimeMillis()
        val tz = TimeZone.getDefault()
        val nowDays = (now + tz.getOffset(now)) / C.MILLIS_PER_DAY
        val targetDays = (targetMs + tz.getOffset(targetMs)) / C.MILLIS_PER_DAY
        val dayString = when (val diffDays = (targetDays - nowDays).toInt()) {
            0 -> context.getString(R.string.today)
            1 -> context.getString(R.string.tomorrow)
            else -> context.getString(R.string.in_days, diffDays)
        }

        return buildAnnotatedString {
            withStyle(iconStyle) {
                append(context.getString(iconId))
            }
            append(dayString)
            append(' ')
            append(timeString)
        }
    }

    fun buildCycle(ctx: Context, count: Int) = AnnotatedString(if (count < 0) ctx.getString(R.string.unavailable) else count.toString())

    fun buildStatus(ctx: Context) = AnnotatedString(ctx.getString(when (batteryTrendMask and BatteryTrendMask.MASK_CHARGE) {
        BatteryTrendMask.STATE_CHARGING -> R.string.charging
        BatteryTrendMask.STATE_DISCHARGING -> R.string.discharging
        BatteryTrendMask.STATE_FULL -> R.string.full
        BatteryTrendMask.STATE_NOT_CHARGING -> R.string.not_charging
        BatteryTrendMask.STATE_UNKNOWN -> R.string.unknown
        else -> R.string.undefined
    }))

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

    fun buildTechnology(tech: String) = AnnotatedString(if (tech == "Unknown") {
        Build.MANUFACTURER
    } else {
        tech
    })

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

    fun buildVoltage(ctx: Context, voltageMv: Int) = AnnotatedString(
        if (voltageMv >= 0) {
            "%.3f V".format(tongue[C.CASE_FOCUS], voltageMv / 1000.0)
        } else {
            ctx.getString(R.string.unavailable)
        }
    )

    fun buildTemperature(ctx: Context, temp: Int) = AnnotatedString(
        if (temp >= 0) {
            val celsius = temp / 10.0
            val unit = LocalePreferences.getTemperatureUnit()
            if (unit == LocalePreferences.TemperatureUnit.FAHRENHEIT) {
                val fahrenheit = (celsius * 1.8) + 32.0
                ctx.getString(R.string.fahrenheit_format).format(tongue[C.CASE_FOCUS], fahrenheit)
            } else {
                ctx.getString(R.string.celsius_format).format(tongue[C.CASE_FOCUS], celsius)
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
            val firstPair = buildAnnotatedString { append(ctx.getString(R.string._wait_) + ' '); append(waiting) }
            val secondPair = buildAnnotatedString { append(ctx.getString(R.string._wait_) + ' '); append(waiting) }
            return firstPair to secondPair
        } else {
            val remaining = AnnotatedString(ms.milliseconds.toComponents { d, h, m, _, _ -> if (d > 0) ctx.getString(
                R.string.duration_d_h_str_format, d, h) else if (h > 0) ctx.getString(R.string.duration_h_m_str_format, h, m) else ctx.getString(
                R.string.duration_m_str_format, m) })
            return remaining to formatTargetTime(ctx, ms, R.string._arrow_up_, M.arrowUp)
        }
    }

    fun buildPrediction(ctx: Context, ms: Long, default: AnnotatedString): Pair<AnnotatedString, AnnotatedString> {
        if (ms <= 0L) {
            val issue = when (ms) {
                -1L -> AnnotatedString(ctx.getString(R.string.unsupported))
                -2L -> AnnotatedString(ctx.getString(R.string.undefined))
                -3L -> default
                0L -> AnnotatedString(ctx.getString(R.string.unavailable))
                else -> AnnotatedString(ctx.getString(R.string.unknown))
            }
            val waiting = ctx.getString(R.string.waiting).waitingStyle()
            return issue to buildAnnotatedString { append(ctx.getString(R.string._wait_) + ' '); append(waiting) }
        } else {
            val duration = AnnotatedString(ms.milliseconds.toComponents { d, h, m, _, _ -> if (d > 0) ctx.getString(
                R.string.duration_d_h_str_format, d, h) else if (h > 0) ctx.getString(R.string.duration_h_m_str_format, h, m) else ctx.getString(
                R.string.duration_m_str_format, m) })
            return duration to formatTargetTime(ctx, ms, R.string._arrow_down_, M.arrowDown)
        }
    }
}
