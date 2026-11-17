package fr.cyring.battman.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.cyring.battman.CentralFont
import fr.cyring.battman.R

data class SelectionCell (
    val resource: Int,
    val value: Int
)

data class SelectionDialogPayload(
    val titleRes: Int,
    val cells: List<SelectionCell>,
    val selectedVal: Int,
    val descriptionRes: Int? = null,
    val icon: ImageVector? = null,
    val onItemSelected: (Int) -> Unit
)

@Composable
fun SelectionDialog(
    fontConfig: CentralFont,
    titleRes: Int,
    cells: List<SelectionCell>,
    selectedVal: Int,
    descriptionRes: Int? = null,
    onDismissRequest: () -> Unit,
    onItemSelected: (Int) -> Unit,
    icon: ImageVector? = null
) {
    val windowSize = LocalWindowInfo.current.containerSize
    val isLandscape = windowSize.width > windowSize.height
    val widthFraction = if (isLandscape) 0.55f else 0.85f
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(dismissOnBackPress = true, usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(widthFraction)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(scrollState)
                    .drawWithContent {

                        drawContent()

                        if (scrollState.maxValue > 0) {
                            val scrollStateMaxValue: Float = scrollState.maxValue.toFloat()
                            val scrollStateValue: Float = scrollState.value.toFloat()

                            val totalContentHeight = scrollStateMaxValue + size.height
                            val scrollThumbHeight = 128f
                            val thumbSize = Size(12f, scrollThumbHeight)
                            val scrollProgress = scrollStateValue / scrollStateMaxValue
                            val travelHeight = size.height - scrollThumbHeight
                            val yOffset = (scrollProgress * travelHeight).coerceIn(0f, travelHeight)

                            drawRect(
                                color = Color.Gray.copy(alpha = 0.15f),
                                size = Size(12f, totalContentHeight),
                                topLeft = Offset(size.width + 24f, 0f)
                            )
                            drawRect(
                                color = Color.Gray.copy(alpha = 0.45f),
                                size = thumbSize,
                                topLeft = Offset(size.width + 24f, yOffset)
                            )
                        }
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                    Text(
                        text = stringResource(titleRes),
                        style = fontConfig.dialogStyle.title
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                cells.forEach { cell ->
                    val isSelected = cell.value == selectedVal
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onItemSelected(cell.value)
                                onDismissRequest()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = stringResource(cell.resource),
                            style = fontConfig.dialogStyle.button
                        )
                    }
                }

                if (descriptionRes != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(descriptionRes),
                        style = fontConfig.dialogStyle.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalButton(onClick = onDismissRequest) {
                        Text(
                            text = stringResource(R.string.cancel),
                            style = fontConfig.dialogStyle.button
                        )
                    }
                }
            }
        }
    }
}
