package fr.cyring.battman

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import fr.cyring.battman.battery.BatteryViewModel
import fr.cyring.battman.battery.BatteryViewModel.Companion.batteryPercent
import fr.cyring.battman.battery.BatteryViewModel.Companion.predictionMs
import fr.cyring.battman.battery.BatteryViewModel.Companion.timeRemainingMs
import fr.cyring.battman.battery.BatteryViewModel.Companion.updateBatteryLevel
import fr.cyring.battman.battery.BatteryViewModel.TimeTracker
import fr.cyring.battman.camera.FlashlightMonitor
import fr.cyring.battman.display.DisplayInfo
import fr.cyring.battman.display.Display
import java.time.Duration
import kotlin.time.toKotlinDuration

data class ReportRow(
    val label: String,
    val store: AnnotatedString
) {
    var value by mutableStateOf(store)
}

lateinit var deepRow: Array<ReportRow>

object Diagnostics {
    fun buildSystemInfo(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL} - Android ${Build.VERSION.RELEASE} - API ${Build.VERSION.SDK_INT}"
    }

    fun updateReport(
        context: Context,
        powerManager: PowerManager,
        timeTracker: TimeTracker,
        intent: Intent,
        viewModel: BatteryViewModel,
        flashLight: FlashlightMonitor
    ) {
        val batteryManager = context.getSystemService(
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

        val status = viewModel.buildStatus(context)

        val extraTech = intent.getStringExtra(
            BatteryManager.EXTRA_TECHNOLOGY
        ) ?: "Unknown"

        predictionMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val duration: Duration? = powerManager.batteryDischargePrediction
                duration?.toKotlinDuration()?.inWholeMilliseconds ?: -3L
            } catch (_: Exception) {
                -2L
            }
        } else {
            -1L
        }

        timeRemainingMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            batteryManager.computeChargeTimeRemaining()
        } else {
            -1L
        }

        val (timeRemaining, fullyChargedAt) = viewModel.buildRemaining(context, timeRemainingMs)
        val (prediction, totallyEmptyAt) = viewModel.buildPrediction(context, predictionMs, status)

        val display: DisplayInfo = Display.collect(context)

        deepRow[C.CYCLE].value = viewModel.buildCycle(context, count)
        deepRow[C.STATUS].value = viewModel.buildStatus(context)
        deepRow[C.HEALTH].value = viewModel.buildHealth(context, intent)
        deepRow[C.TECH].value = viewModel.buildTechnology(extraTech)
        deepRow[C.VOLTAGE].value = viewModel.buildVoltage(context, extra(BatteryManager.EXTRA_VOLTAGE))
        deepRow[C.TEMP].value = viewModel.buildTemperature(context, extra(BatteryManager.EXTRA_TEMPERATURE))
        deepRow[C.ENERGY].value = viewModel.buildEnergyCounter(context, batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER))
        deepRow[C.CHARGE].value = viewModel.buildChargeCounter(context, batteryManager)
        deepRow[C.CURRENT].value = viewModel.buildCurrentNow(context, batteryManager)
        deepRow[C.AVG].value = viewModel.buildCurrentAverage(context, batteryManager)
        deepRow[C.PLUG].value = viewModel.buildBatteryPlug(context, intent)
        deepRow[C.REMAIN].value = timeRemaining
        deepRow[C.FULL].value = fullyChargedAt
        deepRow[C.BAT_SAV].value = viewModel.buildBatterySaver(context)
        deepRow[C.THERMAL].value = viewModel.buildThermal(context)
        deepRow[C.PRESENT].value = viewModel.buildBatteryPresent(context, intent)
        deepRow[C.BAT_LOW].value = viewModel.buildLowBattery(context, intent)
        deepRow[C.PREDICT].value = prediction
        deepRow[C.EMPTY].value = totallyEmptyAt
        deepRow[C.BRIGHT].value = Display.buildBrightness(context, display)
        deepRow[C.ADAPTIVE].value = Display.buildAdaptiveMode(context, display)
        deepRow[C.DARK_MOD].value = Display.buildDarkTheme(context, display)
        deepRow[C.NIGHT_LT].value = Display.buildNightLight(context, display)
        deepRow[C.TOUCH].value = Display.buildTouchScreen(context, display)
        deepRow[C.SMOOTH].value = Display.buildSmoothDisplay(context, display)
        deepRow[C.TIMEOUT].value = Display.buildScreenTimeout(context, display)
        deepRow[C.SCR_SAV].value = Display.buildScreenSaver(context, display)
        deepRow[C.SCR_OFF].value = Display.buildLastScreenOffDuration(context, timeTracker.lastScreenOffDurationMs)
        deepRow[C.TORCH].value = flashLight.buildTorchStatus(context)
    }

    fun buildReport(
        context: Context,
        powerManager: PowerManager,
        timeTracker: TimeTracker,
        viewModel: BatteryViewModel,
        flashLight: FlashlightMonitor
    ): List<ReportRow> {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

        @Suppress("DEPRECATION")
        val intent = context.registerReceiver(null, filter)
            ?: return listOf(
                ReportRow(
                    context.getString(R.string.unavailable),
                    AnnotatedString("")
                )
            )

        val batteryManager = context.getSystemService(
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

        val status = viewModel.buildStatus(context)
        val energy = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)

        val voltageMv = extra(BatteryManager.EXTRA_VOLTAGE)
        val batteryTemp = extra(BatteryManager.EXTRA_TEMPERATURE)

        val extraTech = intent.getStringExtra(
            BatteryManager.EXTRA_TECHNOLOGY
        ) ?: "Unknown"

        predictionMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val duration: Duration? = powerManager.batteryDischargePrediction
                duration?.toKotlinDuration()?.inWholeMilliseconds ?: -3L
            } catch (_: Exception) {
                -2L
            }
        } else {
            -1L
        }

        timeRemainingMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            batteryManager.computeChargeTimeRemaining()
        } else {
            -1L
        }

        val (timeRemaining, fullyChargedAt) = viewModel.buildRemaining(context, timeRemainingMs)
        val (prediction, totallyEmptyAt) = viewModel.buildPrediction(context, predictionMs, status)

        val display: DisplayInfo = Display.collect(context)

        fun row(labelId: Int, value: AnnotatedString) = ReportRow(context.getString(labelId), value)

        deepRow = arrayOf(
            /* CYCLE    */  row(R.string.cycle, viewModel.buildCycle(context, count)),
            /* STATUS   */  row(R.string.state, viewModel.buildStatus(context)),
            /* HEALTH   */  row(R.string.health, viewModel.buildHealth(context, intent)),
            /* TECH     */  row(R.string.technology, viewModel.buildTechnology(extraTech)),
            /* VOLTAGE  */  row(R.string.voltage, viewModel.buildVoltage(context, voltageMv)),
            /* TEMP     */  row(R.string.temperature, viewModel.buildTemperature(context, batteryTemp)),
            /* ENERGY   */  row(R.string.energy_counter, viewModel.buildEnergyCounter(context, energy)),
            /* CHARGE   */  row(R.string.charge_counter, viewModel.buildChargeCounter(context, batteryManager)),
            /* CURRENT  */  row(R.string.current_now, viewModel.buildCurrentNow(context, batteryManager)),
            /* AVG      */  row(R.string.current_average, viewModel.buildCurrentAverage(context, batteryManager)),
            /* PLUG     */  row(R.string.battery_plugged, viewModel.buildBatteryPlug(context, intent)),
            /* REMAIN   */  row(R.string.time_remaining, timeRemaining),
            /* FULL     */  row(R.string.fully_charged, fullyChargedAt),
            /* BAT_SAV  */  row(R.string.battery_saver, viewModel.buildBatterySaver(context)),
            /* THERMAL  */  row(R.string.thermal_status, viewModel.buildThermal(context)),
            /* PRESENT  */  row(R.string.battery_present, viewModel.buildBatteryPresent(context, intent)),
            /* BAT_LOW  */  row(R.string.battery_low, viewModel.buildLowBattery(context, intent)),
            /* PREDICT  */  row(R.string.prediction, prediction),
            /* EMPTY    */  row(R.string.totally_empty, totallyEmptyAt),
            /* BRIGHT   */  row(R.string.brightness, Display.buildBrightness(context, display)),
            /* ADAPTIVE */  row(R.string.adaptive, Display.buildAdaptiveMode(context, display)),
            /* DARK_MOD */  row(R.string.mode_dark, Display.buildDarkTheme(context, display)),
            /* NIGHT_LT */  row(R.string.night_light, Display.buildNightLight(context, display)),
            /* TOUCH    */  row(R.string.touchscreen, Display.buildTouchScreen(context, display)),
            /* SMOOTH   */  row(R.string.refresh_rate, Display.buildSmoothDisplay(context, display)),
            /* TIMEOUT  */  row(R.string.screen_timeout, Display.buildScreenTimeout(context, display)),
            /* SCR_SAV  */  row(R.string.screen_saver, Display.buildScreenSaver(context, display)),
            /* SCR_OFF  */  row(R.string.screen_off, Display.buildLastScreenOffDuration(context, timeTracker.lastScreenOffDurationMs)),
            /* TORCH    */  row(R.string.torch, flashLight.buildTorchStatus(context))
        )
        return listOf(
            row(R.string.level, AnnotatedString("$batteryPercent%")),
            deepRow[C.CYCLE],
            deepRow[C.STATUS],
            deepRow[C.HEALTH],
            deepRow[C.TECH],

            ReportRow(context.getString(R.string._zap_), AnnotatedString("")),    /* ⚡ */

            deepRow[C.VOLTAGE],
            deepRow[C.TEMP],
            deepRow[C.ENERGY],
            deepRow[C.CHARGE],
            deepRow[C.CURRENT],
            deepRow[C.AVG],

            ReportRow(context.getString(R.string._electric_plug_), AnnotatedString("")),

            deepRow[C.PLUG],
            deepRow[C.REMAIN],
            deepRow[C.FULL],
            deepRow[C.BAT_SAV],

            ReportRow(context.getString(R.string._battery_), AnnotatedString("")),

            deepRow[C.THERMAL],
            deepRow[C.PRESENT],
            deepRow[C.BAT_LOW],
            deepRow[C.PREDICT],
            deepRow[C.EMPTY],

            ReportRow(context.getString(R.string._mobile_phone_), AnnotatedString("")),

            deepRow[C.BRIGHT],
            deepRow[C.ADAPTIVE],
            deepRow[C.DARK_MOD],
            deepRow[C.NIGHT_LT],
            deepRow[C.TOUCH],
            deepRow[C.SMOOTH],

            ReportRow(context.getString(R.string._alarm_clock_), AnnotatedString("")), /* ⏰ */

            deepRow[C.TIMEOUT],
            deepRow[C.SCR_SAV],
            deepRow[C.SCR_OFF],

            ReportRow(context.getString(R.string._torch_), AnnotatedString("")),

            deepRow[C.TORCH]
        )
    }
}