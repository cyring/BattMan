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
import fr.cyring.battman.Controller.TimeTracker
import fr.cyring.battman.ai.AiMonitor
import fr.cyring.battman.airplane.AirplaneMonitor
import fr.cyring.battman.audio.AudioMonitor
import fr.cyring.battman.battery.BatteryMonitor
import fr.cyring.battman.battery.BatteryMonitor.Companion.batteryPercent
import fr.cyring.battman.battery.BatteryMonitor.Companion.predictionMs
import fr.cyring.battman.battery.BatteryMonitor.Companion.timeRemainingMs
import fr.cyring.battman.battery.BatteryMonitor.Companion.updateBatteryLevel
import fr.cyring.battman.bluetooth.BluetoothMonitor
import fr.cyring.battman.camera.FlashlightMonitor
import fr.cyring.battman.display.DisplayInfo
import fr.cyring.battman.display.Display
import fr.cyring.battman.location.LocationMonitor
import fr.cyring.battman.network.NetworkMonitor
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
        batteryMonitor: BatteryMonitor,
        flashLight: FlashlightMonitor,
        audioMonitor: AudioMonitor,
        locationMonitor: LocationMonitor,
        bluetoothMonitor: BluetoothMonitor,
        airplaneMonitor: AirplaneMonitor,
        networkMonitor: NetworkMonitor,
        aiMonitor: AiMonitor
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

        val status = batteryMonitor.buildStatus(context)

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

        val (timeRemaining, fullyChargedAt) = batteryMonitor.buildRemaining(context, timeRemainingMs)
        val (prediction, totallyEmptyAt) = batteryMonitor.buildPrediction(context, predictionMs, status)

        val display: DisplayInfo = Display.collect(context)

        val aiAS = aiMonitor.buildComponent(context, AiCapability.AI_SA_CAP, showVersion = true)
        val aiCore = aiMonitor.buildComponent(context, AiCapability.AI_CORE_CAP, showVersion = true)
        val aiPrompt = aiMonitor.buildComponent(context, AiCapability.AI_PROMPT_CAP)
        val aiNLP = aiMonitor.buildComponent(context, AiCapability.AI_NLP_CAP)
        val aiVLM = aiMonitor.buildComponent(context, AiCapability.AI_VLM_CAP)
        val aiSpeech = aiMonitor.buildComponent(context, AiCapability.AI_SPEECH_CAP)

        deepRow[C.CYCLE].value = batteryMonitor.buildCycle(context, count)
        deepRow[C.STATUS].value = status
        deepRow[C.HEALTH].value = batteryMonitor.buildHealth(context, intent)
        deepRow[C.TECH].value = batteryMonitor.buildTechnology(extraTech)
        deepRow[C.VOLTAGE].value = batteryMonitor.buildVoltage(context, extra(BatteryManager.EXTRA_VOLTAGE))
        deepRow[C.TEMP].value = batteryMonitor.buildTemperature(context, extra(BatteryManager.EXTRA_TEMPERATURE))
        deepRow[C.ENERGY].value = batteryMonitor.buildEnergyCounter(context, batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER))
        deepRow[C.CHARGE].value = batteryMonitor.buildChargeCounter(context, batteryManager)
        deepRow[C.CURRENT].value = batteryMonitor.buildCurrentNow(context, batteryManager)
        deepRow[C.AVG].value = batteryMonitor.buildCurrentAverage(context, batteryManager)
        deepRow[C.PLUG].value = batteryMonitor.buildBatteryPlug(context, intent)
        deepRow[C.REMAIN].value = timeRemaining
        deepRow[C.FULL].value = fullyChargedAt
        deepRow[C.BAT_SAV].value = batteryMonitor.buildBatterySaver(context)
        deepRow[C.THERMAL].value = batteryMonitor.buildThermal(context)
        deepRow[C.PRESENT].value = batteryMonitor.buildBatteryPresent(context, intent)
        deepRow[C.BAT_LOW].value = batteryMonitor.buildLowBattery(context, intent)
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
        deepRow[C.AWAKE].value = Display.buildStayAwake(context, display)
        deepRow[C.TORCH].value = flashLight.buildTorchStatus(context)
        deepRow[C.AUDIO].value = audioMonitor.buildAudioStatus(context)
        deepRow[C.LOCATION].value = locationMonitor.buildLocationStatus(context)
        deepRow[C.BT_MOD].value = bluetoothMonitor.buildBluetoothStatus(context)
        deepRow[C.WIFI].value = networkMonitor.buildWiFiStatus(context)
        deepRow[C.CELLULAR].value = networkMonitor.buildCellularStatus(context)
        deepRow[C.INTERNET].value = networkMonitor.buildInternetStatus(context)
        deepRow[C.AIRPLANE].value = airplaneMonitor.buildAirplaneStatus(context)
        deepRow[C.AI_AS].value = aiAS
        deepRow[C.AI_NPU].value = aiCore
        deepRow[C.AI_INPUT].value = aiPrompt
        deepRow[C.AI_NLP].value = aiNLP
        deepRow[C.AI_VLM].value = aiVLM
        deepRow[C.AI_VOICE].value = aiSpeech
    }

    fun buildReport(
        context: Context,
        powerManager: PowerManager,
        timeTracker: TimeTracker,
        batteryMonitor: BatteryMonitor,
        flashLight: FlashlightMonitor,
        audioMonitor: AudioMonitor,
        locationMonitor: LocationMonitor,
        bluetoothMonitor: BluetoothMonitor,
        airplaneMonitor: AirplaneMonitor,
        networkMonitor: NetworkMonitor,
        aiMonitor: AiMonitor
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

        val status = batteryMonitor.buildStatus(context)
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

        val (timeRemaining, fullyChargedAt) = batteryMonitor.buildRemaining(context, timeRemainingMs)
        val (prediction, totallyEmptyAt) = batteryMonitor.buildPrediction(context, predictionMs, status)

        val display: DisplayInfo = Display.collect(context)

        val aiAS = aiMonitor.buildComponent(context, AiCapability.AI_SA_CAP, showVersion = true)
        val aiCore = aiMonitor.buildComponent(context, AiCapability.AI_CORE_CAP, showVersion = true)
        val aiPrompt = aiMonitor.buildComponent(context, AiCapability.AI_PROMPT_CAP)
        val aiNLP = aiMonitor.buildComponent(context, AiCapability.AI_NLP_CAP)
        val aiVLM = aiMonitor.buildComponent(context, AiCapability.AI_VLM_CAP)
        val aiSpeech = aiMonitor.buildComponent(context, AiCapability.AI_SPEECH_CAP)

        fun row(labelId: Int, value: AnnotatedString) = ReportRow(context.getString(labelId), value)

        deepRow = arrayOf(
            /* CYCLE    */  row(R.string.cycle, batteryMonitor.buildCycle(context, count)),
            /* STATUS   */  row(R.string.state, status),
            /* HEALTH   */  row(R.string.health, batteryMonitor.buildHealth(context, intent)),
            /* TECH     */  row(R.string.technology, batteryMonitor.buildTechnology(extraTech)),
            /* VOLTAGE  */  row(R.string.voltage, batteryMonitor.buildVoltage(context, voltageMv)),
            /* TEMP     */  row(R.string.temperature, batteryMonitor.buildTemperature(context, batteryTemp)),
            /* ENERGY   */  row(R.string.energy_counter, batteryMonitor.buildEnergyCounter(context, energy)),
            /* CHARGE   */  row(R.string.charge_counter, batteryMonitor.buildChargeCounter(context, batteryManager)),
            /* CURRENT  */  row(R.string.current_now, batteryMonitor.buildCurrentNow(context, batteryManager)),
            /* AVG      */  row(R.string.current_average, batteryMonitor.buildCurrentAverage(context, batteryManager)),
            /* PLUG     */  row(R.string.battery_plugged, batteryMonitor.buildBatteryPlug(context, intent)),
            /* REMAIN   */  row(R.string.time_remaining, timeRemaining),
            /* FULL     */  row(R.string.fully_charged, fullyChargedAt),
            /* BAT_SAV  */  row(R.string.battery_saver, batteryMonitor.buildBatterySaver(context)),
            /* THERMAL  */  row(R.string.thermal_status, batteryMonitor.buildThermal(context)),
            /* PRESENT  */  row(R.string.battery_present, batteryMonitor.buildBatteryPresent(context, intent)),
            /* BAT_LOW  */  row(R.string.battery_low, batteryMonitor.buildLowBattery(context, intent)),
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
            /* AWAKE    */  row(R.string.stay_awake, Display.buildStayAwake(context, display)),
            /* TORCH    */  row(R.string.torch, flashLight.buildTorchStatus(context)),
            /* AUDIO    */  row(R.string.microphone, audioMonitor.buildAudioStatus(context)),
            /* LOCATION */  row(R.string.location, locationMonitor.buildLocationStatus(context)),
            /* BT_MOD   */  row(R.string.bluetooth, bluetoothMonitor.buildBluetoothStatus(context)),
            /* WIFI     */  row(R.string.wifi, networkMonitor.buildWiFiStatus(context)),
            /* CELLULAR */  row(R.string.cellular, networkMonitor.buildCellularStatus(context)),
            /* INTERNET */  row(R.string.internet, networkMonitor.buildInternetStatus(context)),
            /* AIRPLANE */  row(R.string.airplane, airplaneMonitor.buildAirplaneStatus(context)),
            /* AI_AS    */  row(R.string.ai_as, aiAS),
            /* AI_NPU   */  row(R.string.ai_core, aiCore),
            /* AI_INPUT */  row(R.string.ai_prompt, aiPrompt),
            /* AI_NLP   */  row(R.string.ai_nlp, aiNLP),
            /* AI_VLM   */  row(R.string.ai_vlm, aiVLM),
            /* AI_VOICE */  row(R.string.ai_speech, aiSpeech)
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

            ReportRow(context.getString(R.string._display_), AnnotatedString("")),

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
            deepRow[C.AWAKE],

            ReportRow(context.getString(R.string._gear_), AnnotatedString("")),

            deepRow[C.TORCH],
            deepRow[C.AUDIO],
            deepRow[C.LOCATION],
            deepRow[C.BT_MOD],
            deepRow[C.WIFI],
            deepRow[C.CELLULAR],
            deepRow[C.INTERNET],
            deepRow[C.AIRPLANE],

            ReportRow(context.getString(R.string._ai_), AnnotatedString("")),

            deepRow[C.AI_AS],
            deepRow[C.AI_NPU],
            deepRow[C.AI_INPUT],
            deepRow[C.AI_NLP],
            deepRow[C.AI_VLM],
            deepRow[C.AI_VOICE]

        )
    }
}