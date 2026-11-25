package fr.cyring.battman.ui.components

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import fr.cyring.battman.Controller
import fr.cyring.battman.R

data class SelectionCell (
    val resource: Int,
    val value: Int
)

open class BasicDialog(
    open val titleRes: Int,
    open val icon: ImageVector? = null
) {
    @Composable
    open fun CancelButton(
        fontConfig: CentralFont,
        onDismiss: () -> Unit,
        color: Color = Color.Unspecified
    ) {
        TextButton(
            contentPadding = PaddingValues(horizontal = 6.dp),
            onClick = onDismiss
        ) {
            Text(
                text = stringResource(R.string.cancel),
                style = fontConfig.dialogStyle.button,
                color = color
            )
        }
    }
    @Composable
    open fun Render(
        fontConfig: CentralFont,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = stringResource(titleRes), style = fontConfig.dialogStyle.title) },
            confirmButton = { CancelButton(fontConfig, onDismiss) }
        )
    }
}

open class SelectionDialogContent(
    override val titleRes: Int,
    val cells: List<SelectionCell>,
    val selectedVal: Int,
    val descriptionRes: Int? = null,
    val onDismissRequest: () -> Unit,
    val onItemSelected: (Int) -> Unit,
    override val icon: ImageVector? = null
) : BasicDialog(titleRes, icon) {
    @Composable
    override fun Render(fontConfig: CentralFont, onDismiss: () -> Unit) {
        val windowSize = LocalWindowInfo.current.containerSize
        val isLandscape = windowSize.width > windowSize.height
        val widthFraction = if (isLandscape) 0.55f else 0.85f
        val scrollState = rememberScrollState()

        val dismissAction: () -> Unit = {
            onDismissRequest()
            onDismiss()
        }

        Dialog(
            onDismissRequest = dismissAction,
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
                                val yOffset =
                                    (scrollProgress * travelHeight).coerceIn(0f, travelHeight)

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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        icon?.let {
                            Icon(
                                imageVector = it,
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
                                    dismissAction()
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
                        CancelButton(fontConfig = fontConfig, onDismiss = dismissAction)
                    }
                }
            }
        }
    }
}

class SelectionDialog(
    override val titleRes: Int,
    cells: List<SelectionCell>,
    selectedVal: Int,
    descriptionRes: Int? = null,
    onDismissRequest: () -> Unit,
    onItemSelected: (Int) -> Unit,
    override val icon: ImageVector? = null
) : SelectionDialogContent(
    titleRes = titleRes,
    cells = cells,
    selectedVal = selectedVal,
    descriptionRes = descriptionRes,
    onDismissRequest = onDismissRequest,
    onItemSelected = onItemSelected,
    icon = icon
){
    @Composable
    override fun CancelButton(
        fontConfig: CentralFont,
        onDismiss: () -> Unit,
        color: Color
    ) {
        FilledTonalButton(onClick = onDismiss) {
            Text(
                text = stringResource(R.string.cancel),
                style = fontConfig.dialogStyle.button,
                color = color
            )
        }
    }
}

open class QuitDialog(
    override val titleRes: Int,
    val messageRes: Int,
    val activity: Activity?,
    override val icon: ImageVector? = null,
) : BasicDialog(titleRes, icon) {
    @Composable
    open fun QuitButton(fontConfig: CentralFont,
                        onDismiss: () -> Unit,
                        label: String = stringResource(R.string.quit),
                        onQuitDismiss: () -> Unit)
    {
        TextButton(
            contentPadding = PaddingValues(horizontal = 6.dp),
            onClick = {
                onDismiss()
                onQuitDismiss()
            }
        ) {
            Text(
                text = label,
                style = fontConfig.dialogStyle.button
            )
        }
    }
    @Composable
    open fun ExtraButton(fontConfig: CentralFont,
                         onDismiss: () -> Unit,
                         label: String = stringResource(R.string.extra),
                         onExtraDismiss: () -> Unit)
    {
        TextButton(
            contentPadding = PaddingValues(horizontal = 6.dp),
            onClick = {
                onDismiss()
                onExtraDismiss()
            }
        ) {
            Text(
                text = label,
                style = fontConfig.dialogStyle.button
            )
        }
    }
    @Composable
    override fun Render(fontConfig: CentralFont, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = icon?.let {
                {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            title = {
                Text(
                    text = stringResource(titleRes),
                    style = fontConfig.dialogStyle.title
                )
            },
            text = {
                Text(
                    text = stringResource(messageRes),
                    style = fontConfig.dialogStyle.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            dismissButton = {
                QuitButton(fontConfig, onDismiss) {
                    Controller.userShutdown = true
                    activity?.finishAndRemoveTask()
                }
                ExtraButton(fontConfig, onDismiss) {
                    activity?.finish()
                }
            },
            confirmButton = {
                CancelButton(fontConfig, onDismiss)
            }
        )
    }
}