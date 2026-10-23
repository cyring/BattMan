package com.example.battman

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import com.example.battman.ui.theme.BattManTheme
import java.util.Locale

public var batteryPercent = 100
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
                mutableStateOf(prefs.getLong("refreshTime", 3000L))
            }

            var report by remember {
                mutableStateOf(readBatteryInfo(this@MainActivity))
            }

            LaunchedEffect(autoRefresh, refreshTime) {
                while (autoRefresh) {
                    delay(refreshTime)
                    report = readBatteryInfo(this@MainActivity)
                }
            }

            var sysInfo by remember {
                mutableStateOf(readSystemInfo(this@MainActivity))
            }

            BattManTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = stringResource(R.string.subtitle),
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))

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
                                MaterialTheme.colorScheme.error
                            }
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp)
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(batteryPercent / 100f)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                            )
                        }
                        Spacer(modifier = Modifier.height(32.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = report,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(
                                32.dp,
                                Alignment.CenterHorizontally
                            )
                        ) {
                            Button(
                                onClick = {
                                    report = readBatteryInfo(this@MainActivity)
                                }
                            ) {
                                Text(stringResource(R.string.refresh_now))
                            }

                            var menuExpanded by remember { mutableStateOf(false) }

                            ExposedDropdownMenuBox(
                                expanded = menuExpanded,
                                onExpandedChange = { menuExpanded = it }
                            ) {
                                Button(
                                    onClick = { menuExpanded = true },
                                    modifier = Modifier.menuAnchor(
                                        ExposedDropdownMenuAnchorType.PrimaryNotEditable
                                    )
                                ) {
                                    //val refreshText = (refreshTime / 1000).toString()
                                    Text(
                                        if (autoRefresh) {
                                            stringResource(R.string.auto_interval, refreshTime / 1000)
                                        } else {
                                            stringResource(R.string.auto_off)
                                        }
                                    )
                                }

                                ExposedDropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Auto (1 s)") },
                                        onClick = {
                                            autoRefresh = true
                                            refreshTime = 1000L

                                            prefs.edit()
                                                .putBoolean("autoRefresh", true)
                                                .putLong("refreshTime", refreshTime)
                                                .apply()

                                            menuExpanded = false
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Auto (3 s)") },
                                        onClick = {
                                            autoRefresh = true
                                            refreshTime = 3000L

                                            prefs.edit()
                                                .putBoolean("autoRefresh", true)
                                                .putLong("refreshTime", refreshTime)
                                                .apply()

                                            menuExpanded = false
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Auto (6 s)") },
                                        onClick = {
                                            autoRefresh = true
                                            refreshTime = 6000L

                                            prefs.edit()
                                                .putBoolean("autoRefresh", true)
                                                .putLong("refreshTime", refreshTime)
                                                .apply()

                                            menuExpanded = false
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Auto Off") },
                                        onClick = {
                                            autoRefresh = false

                                            prefs.edit()
                                                .putBoolean("autoRefresh", false)
                                                .apply()

                                            menuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = sysInfo,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

private fun readBatteryInfo(context: Context): String {
    val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

    @Suppress("DEPRECATION")
    val intent = context.registerReceiver(null, filter)
        ?: return "No available information"

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

    val extraCharging = extra(BatteryManager.EXTRA_CHARGING_STATUS)

    val lowBatteryState = extra(BatteryManager.EXTRA_BATTERY_LOW)

    val batteryPlugged = when (extra(BatteryManager.EXTRA_PLUGGED)) {
        0 -> context.getString(R.string.on_battery)
        1 -> context.getString(R.string.ac_charger)
        2 -> context.getString(R.string.usb_power)
        4 -> context.getString(R.string.wireless)
        else -> context.getString(R.string.powered)
    }

    val batteryPresent = extra(BatteryManager.EXTRA_PRESENT)

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
            android.os.PowerManager.THERMAL_STATUS_NONE -> "None"
            android.os.PowerManager.THERMAL_STATUS_LIGHT -> "Light"
            android.os.PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
            android.os.PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
            android.os.PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
            android.os.PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
            android.os.PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
            else -> "Unknown"
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

    return """
        ${context.getString(R.string.level).padEnd(20)}$batteryPercent%
        ${context.getString(R.string.cycle).padEnd(20)}$cycle
        ${context.getString(R.string.state).padEnd(20)}$status
        ${context.getString(R.string.health).padEnd(20)}$health
        ${context.getString(R.string.technology).padEnd(20)}$technology

        ${"Tension".padEnd(20)}$voltage
        ${context.getString(R.string.temperature).padEnd(20)}$tempFormat
        ${context.getString(R.string.energy_counter).padEnd(20)}$energyCounter
        ${context.getString(R.string.charge_counter).padEnd(20)}$chargeCounter µAh
        ${context.getString(R.string.current_now).padEnd(20)}$currentNow µA
        ${context.getString(R.string.current_average).padEnd(20)}$currentAverage µA

        ${context.getString(R.string.battery_plugged).padEnd(20)}$batteryPlugged
        ${context.getString(R.string.battery_saver).padEnd(20)}$batterySaver

        ${context.getString(R.string.thermal_status).padEnd(20)}$thermalStatus
        ${context.getString(R.string.battery_present).padEnd(20)}$batteryPresent
        ${context.getString(R.string.battery_low).padEnd(20)}$lowBatteryState
        ${context.getString(R.string.charging_status).padEnd(20)}$extraCharging
    """.trimIndent()
}

private fun readSystemInfo(context: Context): String {
    return """
        ${Build.MANUFACTURER} ${Build.MODEL} - Android ${Build.VERSION.RELEASE} - API ${Build.VERSION.SDK_INT}
    """.trimIndent()
}
