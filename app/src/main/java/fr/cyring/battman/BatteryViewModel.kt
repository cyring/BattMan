package fr.cyring.battman

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.res.Resources
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
    val value: String
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
const val DEFAULT_AUTO_AWAY = true

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
    var sysInfo by mutableStateOf("")

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

    fun readBatteryInfo(context: Context, powerManager: PowerManager, timeTracker: TimeTracker): List<BatteryReportRow> {
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

        val count = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            extra(BatteryManager.EXTRA_CYCLE_COUNT)
        } else {
            -1
        }

        updateBatteryLevel(intent)

        val cycle = if (count < 0) context.getString(R.string.unavailable) else count.toString()

        val status = when (batteryTrendMask and BatteryTrendMask.MASK_CHARGE) {
            BatteryTrendMask.STATE_CHARGING -> context.getString(R.string.charging)
            BatteryTrendMask.STATE_DISCHARGING -> context.getString(R.string.discharging)
            BatteryTrendMask.STATE_FULL -> context.getString(R.string.full)
            BatteryTrendMask.STATE_NOT_CHARGING -> context.getString(R.string.not_charging)
            BatteryTrendMask.STATE_UNKNOWN -> context.getString(R.string.unknown)
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

        val technology = if (extraTech == "Unknown") {
            "${Build.MANUFACTURER}"
        } else {
            extraTech
        }

        val batterySaver = if (powerManager.isPowerSaveMode == true) {
            context.getString(R.string.activated)
        } else {
            context.getString(R.string.deactivated)
        }

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

        val thermalStatus = if (Build.VERSION.SDK_INT >= 29) {
            when (powerManager.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> context.getString(R.string.none)
                PowerManager.THERMAL_STATUS_LIGHT -> context.getString(R.string.light)
                PowerManager.THERMAL_STATUS_MODERATE -> context.getString(R.string.moderate)
                PowerManager.THERMAL_STATUS_SEVERE -> context.getString(R.string.severe)
                PowerManager.THERMAL_STATUS_CRITICAL -> context.getString(R.string.critical)
                PowerManager.THERMAL_STATUS_EMERGENCY -> context.getString(R.string.emergency)
                PowerManager.THERMAL_STATUS_SHUTDOWN -> context.getString(R.string.shutdown)
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

        timeRemainingMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.computeChargeTimeRemaining()
        } else {
            -1L
        }
        val timeRemaining = if (timeRemainingMs < 0L) {
            context.getString(R.string.unavailable)
        } else {
            timeRemainingMs.milliseconds.toComponents { _, h, m, _, _ -> if (h > 0) context.getString(R.string.duration_h_m_str_format, h, m) else context.getString(R.string.duration_m_str_format, m) }
        }

        val prediction: String = when {
            predictionMs == -1L -> context.getString(R.string.unsupported)
            predictionMs == -2L -> context.getString(R.string.undefined)
            predictionMs == -3L -> status
            predictionMs == 0L -> context.getString(R.string.unavailable)
            predictionMs > 0L -> predictionMs.milliseconds.toComponents { _, h, m, _, _ -> if (h > 0) context.getString(R.string.duration_h_m_str_format, h, m) else context.getString(R.string.duration_m_str_format, m) }
            else -> context.getString(R.string.unknown)
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

        fun readDisplayInfo(ctx: Context): DisplayInfo {
            val resolver = ctx.contentResolver
            val sysConf = Resources.getSystem().configuration

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
                        val displayManager = ctx.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
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
        val screenTimeout = when {
            display.screenTimeoutMs < 0L -> context.getString(R.string.unknown)
            display.screenTimeoutMs == Int.MAX_VALUE.toLong() ->
                context.getString(R.string.never)
            else -> display.screenTimeoutMs.milliseconds.toComponents { _, h, m, s, ns -> if (h > 0) context.getString(R.string.duration_h_m_s_str_format, h, m, s) else if (m > 0) context.getString(R.string.duration_m_s_str_format, m, s) else context.getString(R.string.duration_ms_str_format, s, ns / 1_000_000L) }
        }
        val screenSaver = if (display.screenSaverEnabled == true) context.getString(R.string.activated) else context.getString(R.string.deactivated)
        val lastScreenOffDurationMs: String = if (timeTracker.lastScreenOffDurationMs == -1L) {
            "<${context.getString(R.string.waiting)}>"
        } else {
            timeTracker.lastScreenOffDurationMs.milliseconds.toComponents { _, h, m, s, ns -> if (h > 0) context.getString(R.string.duration_h_m_s_str_format, h, m, s) else if (m > 0) context.getString(R.string.duration_m_s_str_format, m, s) else context.getString(R.string.duration_ms_str_format, s, ns / 1_000_000L) }
        }

        fun row(labelId: Int, value: String) =
            BatteryReportRow(context.getString(labelId), value)

        return listOf(
            row(R.string.level, "$batteryPercent%"),
            row(R.string.cycle, cycle),
            row(R.string.state, status),
            row(R.string.health, health),
            row(R.string.technology, technology),

            BatteryReportRow("⚡", ""),

            row(R.string.voltage, voltage),
            row(R.string.temperature, tempFormat),
            row(R.string.energy_counter, energyCounter),
            row(R.string.charge_counter, "$chargeCounter µAh"),
            row(R.string.current_now, "$currentNow µA"),
            row(R.string.current_average, "$currentAverage µA"),

            BatteryReportRow("\uD83D\uDD0C", ""),

            row(R.string.battery_plugged, batteryPlugged),
            row(R.string.time_remaining, timeRemaining),
            row(R.string.battery_saver, batterySaver),

            BatteryReportRow("\uD83D\uDD0B", ""),

            row(R.string.thermal_status, thermalStatus),
            row(R.string.battery_present, batteryPresent),
            row(R.string.battery_low, lowBatteryState),
            row(R.string.prediction, prediction),

            BatteryReportRow("\uD83D\uDCF1",""),

            row(R.string.brightness, "${brightnessLevel}/255"),
            row(R.string.adaptive, adaptiveMode),
            row(R.string.mode_night, nightMode),
            row(R.string.touchscreen, touchScreen),
            row(R.string.refresh_rate,smoothDisplay),

            BatteryReportRow("⏰",""),

            row(R.string.screen_timeout, screenTimeout),
            row(R.string.screen_saver, screenSaver),
            row(R.string.screen_off, lastScreenOffDurationMs)
        )
    }
}
