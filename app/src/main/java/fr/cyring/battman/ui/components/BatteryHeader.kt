package fr.cyring.battman.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import fr.cyring.battman.R
import fr.cyring.battman.CentralFont
import fr.cyring.battman.Controller

@Composable
fun BatteryHeader(
    controller: Controller,
    fontConfig: CentralFont,
    onOpenDrawer: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onOpenDrawer
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
                style = fontConfig.appStyle.title,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.subtitle),
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(
                    maxFontSize = fontConfig.appStyle.subTitle.fontSize
                ),
                textAlign = TextAlign.Center,
                style = fontConfig.appStyle.subTitle,
                color = MaterialTheme.colorScheme.outline
            )
        }
        IconButton(
            onClick = {
                controller.refreshData()
            }
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}