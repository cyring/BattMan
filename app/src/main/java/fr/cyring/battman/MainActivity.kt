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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.clickable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Slider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import fr.cyring.battman.ui.theme.BattManTheme

class MainActivity : ComponentActivity() {
    private val viewModel: BatteryViewModel by viewModels()
    private var lineHeight: Dp = 16.dp
    private val batteryEventReceiver = BatteryEventReceiver()

    override fun onPause() {
        super.onPause()
        viewModel.onScreenPaused()

        try {
            unregisterReceiver(batteryEventReceiver)
        } catch (e: Exception) {
            Toast.makeText(this@MainActivity, "${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onScreenResumed()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
        }
        registerReceiver(batteryEventReceiver, filter)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {
            val autoRefresh = viewModel.autoRefresh
            val refreshTime = viewModel.refreshTime
            val report = viewModel.report
            val batteryPercent = BatteryViewModel.batteryPercent
            val sysInfo = viewModel.sysInfo

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
                        ModalDrawerSheet(
                            modifier = Modifier.width(320.dp)
                        ) {
                            BoxWithConstraints(
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = maxHeight)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 10.dp),
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
                                                    viewModel.updateAutoRefresh(false, refreshTime)
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
                                                        viewModel.updateAutoRefresh(false, refreshTime)
                                                    }
                                                }
                                            )
                                        }

                                        // 1 second row with Switch
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.updateAutoRefresh(true, 1000L)
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
                                                        viewModel.updateAutoRefresh(true, 1000L)
                                                    }
                                                }
                                            )
                                        }

                                        // 3 seconds row with Switch
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.updateAutoRefresh(true, 3000L)
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
                                                        viewModel.updateAutoRefresh(true, 3000L)
                                                    }
                                                }
                                            )
                                        }

                                        // 6 seconds row with Switch
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.updateAutoRefresh(true, 6000L)
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
                                                        viewModel.updateAutoRefresh(true, 6000L)
                                                    }
                                                }
                                            )
                                        }
                                        HorizontalDivider()

                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 24.dp, vertical = 16.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.battery_alert_threshold),
                                                style = MaterialTheme.typography.titleMedium
                                                )
                                            Text(
                                                text = BatteryViewModel.batteryAlertThreshold.toString() + " %",
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        }
                                        Slider(
                                            value = BatteryViewModel.batteryAlertThreshold.toFloat(),
                                            valueRange = 1f..100f,
                                            onValueChange = { newValue ->
                                                viewModel.updateBatteryAlertThreshold(newValue.roundToInt())
                                            },
                                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                                        )
                                        HorizontalDivider()
                                    }

                                    Column {
                                        HorizontalDivider()
                                        Text(
                                            "Version ${BuildConfig.VERSION_NAME}\n© 2026 CYRIL COURTIAT",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                                        )
                                    }
                                }
                            }
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
                                IconButton(
                                    onClick = {
                                        viewModel.refreshData()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(lineHeight * 0.25f))

                            var isRefreshing by remember { mutableStateOf(false) }

                            PullToRefreshBox(
                                isRefreshing = isRefreshing,
                                onRefresh = {
                                    isRefreshing = true
                                    coroutineScope.launch {
                                        viewModel.refreshData()
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
                                        color = if (batteryPercent > BatteryViewModel.batteryAlertThreshold) {
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

                                    BatteryReport(lineHeight, rows = report)
                                }
                            }
                            Spacer(modifier = Modifier.height(lineHeight * 0.5f))

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
                            Spacer(modifier = Modifier.height(lineHeight * 0.5f))

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
private fun BatteryReport(lineHeight: Dp, rows: List<BatteryReportRow>) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        rows.forEach { row ->
            when {
                // Case 1: ("", "") -> Blank line separator
                row.label.isEmpty() && row.value.isEmpty() -> {
                    Spacer(modifier = Modifier.height(lineHeight))
                }
                // Case 2: ("@", "") -> Unique pictogram only
                row.value.isEmpty() -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = row.label,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
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
}
