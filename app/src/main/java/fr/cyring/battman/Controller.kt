package fr.cyring.battman

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.cyring.battman.battery.BatteryAlertWorker
import fr.cyring.battman.battery.BatteryMonitor
import fr.cyring.battman.battery.BatteryMonitor.Companion.batteryAlertThreshold
import fr.cyring.battman.battery.BatteryMonitor.Companion.updateBatteryLevel
import fr.cyring.battman.camera.FlashlightMonitor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

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

class Controller(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("BattMan", Context.MODE_PRIVATE)
    private val powerManager = application.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val batteryMonitor = BatteryMonitor(application)

    var tongue: Array<Locale> = arrayOf(Locale.ENGLISH, Locale.ENGLISH)
    data class TimeTracker(
        var screenOffStartedAt: Long = 0L,
        var lastScreenOffDurationMs: Long = -1L
    )

    val timeTracker = TimeTracker()

    val flashlightMonitor = FlashlightMonitor(application)

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

    fun init() {
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
        report = Diagnostics.buildReport(getApplication(), powerManager, timeTracker, batteryMonitor, flashlightMonitor)
    }
    fun refreshData() {
        val context: Context = getApplication()
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        @Suppress("DEPRECATION")
        val intent = context.registerReceiver(null, filter)
        if (intent != null) Diagnostics.updateReport(context, powerManager, timeTracker, intent, batteryMonitor, flashlightMonitor)
    }

    fun updateAutoRefresh(idx: Int, enabled: Boolean, time: Long) {
        val config = RefreshCase.entries[idx]
        autoRefresh[idx] = enabled
        refreshTime[idx] = time
        prefs.edit {
            putBoolean(config.prefKey, enabled)
            putLong(config.prefTimeKey, time)
        }
    }

    // Function pointer callbacks table (C-style function pointer array)
    val updateAutoRefreshHandlers: Array<(Boolean, Long) -> Unit> = arrayOf(
        { enabled, time -> updateAutoRefresh(C.CASE_FOCUS, enabled, time) },
        { enabled, time -> updateAutoRefresh(C.CASE_AWAY, enabled, time) }
    )

    var themeMode by mutableIntStateOf(prefs.getInt("themeMode", C.THEME_SYSTEM))

    fun updateThemeMode(mode: Int) {
        themeMode = mode
        prefs.edit { putInt("themeMode", mode) }
    }

    var fontSizeMode by mutableIntStateOf(prefs.getInt("fontSizeMode", C.FONT_SIZE_SYSTEM))

    fun updateFontSizeMode(mode: Int) {
        fontSizeMode = mode
        prefs.edit { putInt("fontSizeMode", mode) }
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
            timeTracker.lastScreenOffDurationMs =
                System.currentTimeMillis() - timeTracker.screenOffStartedAt
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

    fun startTorchMonitoring() {
        flashlightMonitor.startListening()
    }

    fun stopTorchMonitoring() {
        flashlightMonitor.stopListening()
    }
}