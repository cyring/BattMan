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
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

data class BatteryReportRow(
    val label: String,
    val value: String
)

class BatteryViewModel(application: Application) : AndroidViewModel(application) {
    private val powerManager = application.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val prefs = application.getSharedPreferences("BattMan", Context.MODE_PRIVATE)

    class TimeTracker(
        var screenOffStartedAt: Long = 0L,
        var lastScreenOffDurationMs: Long = -1L
    )

    val timeTracker = TimeTracker()

    companion object {
        var batteryPercent by mutableIntStateOf(100)

        fun updateBatteryLevel(level: Int, scale: Int) {
            batteryPercent = if (scale > 0 && level >= 0) {
                level * 100 / scale
            } else {
                -1
            }
        }
    }

    var autoRefresh by mutableStateOf(prefs.getBoolean("autoRefresh", true))
    var refreshTime by mutableLongStateOf(prefs.getLong("refreshTime", 3000L))

    var report by mutableStateOf<List<BatteryReportRow>>(emptyList())
    var sysInfo by mutableStateOf("")

    private var refreshJob: Job? = null

    init {
        refreshData()
        sysInfo = readSystemInfo()
        startAutoRefreshLoop()
    }

    fun refreshData() {
        report = readBatteryInfo(getApplication(), powerManager, timeTracker)
    }

    fun updateAutoRefresh(enabled: Boolean, time: Long) {
        autoRefresh = enabled
        refreshTime = time
        prefs.edit()
            .putBoolean("autoRefresh", enabled)
            .putLong("refreshTime", time)
            .apply()
        startAutoRefreshLoop()
    }

    private fun startAutoRefreshLoop() {
        refreshJob?.cancel()
        if (autoRefresh) {
            refreshJob = viewModelScope.launch {
                while (true) {
                    delay(refreshTime.milliseconds)
                    refreshData()
                }
            }
        }
    }

    fun onScreenPaused() {
        if (!powerManager.isInteractive && timeTracker.screenOffStartedAt == 0L) {
            timeTracker.screenOffStartedAt = System.currentTimeMillis()
        }
    }

    fun onScreenResumed() {
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

        val cycle = extra(BatteryManager.EXTRA_CYCLE_COUNT)
        val level = extra(BatteryManager.EXTRA_LEVEL)
        val scale = extra(BatteryManager.EXTRA_SCALE, 100)

        updateBatteryLevel(level, scale)

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

        fun timeToHM(ms: Long): String {
            val totalMinutes = (ms + 59_999L) / 60_000L
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
            return when {
                hours > 0 && minutes > 0 ->
                    context.getString(R.string.duration_format, hoursText, minutesText)

                hours > 0 -> hoursText
                else -> minutesText
            }
        }

        fun timeToHMSMS(ms: Long?): String {
            val durationMs = ms ?: 0
            val totalSeconds = durationMs / 1_000
            val milliseconds = (durationMs % 1_000).toInt()
            val seconds = (totalSeconds % 60).toInt()
            val totalMinutes = totalSeconds / 60
            val minutes = (totalMinutes % 60).toInt()
            val hours = (totalMinutes / 60).toInt()

            return when {
                hours > 0 -> context.getString(R.string.duration_h_m_s_format, hours, minutes, seconds)
                minutes > 0 -> context.getString(R.string.duration_m_s_format, minutes, seconds)
                else -> context.getString(R.string.duration_ms_format, seconds, milliseconds)
            }
        }

        val milliseconds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.computeChargeTimeRemaining()
        } else {
            -1L
        }
        val timeRemaining = if (milliseconds < 0L) {
            context.getString(R.string.unavailable)
        } else {
            timeToHM(milliseconds)
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

            return DisplayInfo(
                brightness = getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1),
                adaptive = getInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
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
                screenTimeoutMs = Settings.System.getLong(resolver, Settings.System.SCREEN_OFF_TIMEOUT, -1L),
                screenSaverEnabled = try {
                    Settings.Secure.getInt(
                        context.contentResolver,
                        "screensaver_enabled"
                    ) != 0
                } catch (_: Settings.SettingNotFoundException) {
                    null
                }
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
            else -> timeToHMSMS(display.screenTimeoutMs)
        }
        val screenSaver = if (display.screenSaverEnabled == true) context.getString(R.string.activated) else context.getString(R.string.deactivated)

        val lastScreenOffDurationMs: String = if (timeTracker.lastScreenOffDurationMs == -1L) {
            "<${context.getString(R.string.waiting)}>"
        } else {
            timeToHMSMS(timeTracker.lastScreenOffDurationMs)
        }

        fun row(labelId: Int, value: String) =
            BatteryReportRow(context.getString(labelId), value)

        return listOf(
            row(R.string.level, "$batteryPercent%"),
            row(R.string.cycle, cycle.toString()),
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
