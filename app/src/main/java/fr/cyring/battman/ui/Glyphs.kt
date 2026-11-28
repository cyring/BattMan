package fr.cyring.battman.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em

object IG {
    const val ZAP = "_ZAP_"
    const val ELECTRIC_PLUG = "_ELECTRIC_PLUG_"
    const val BATTERY = "_BATTERY_"
    const val DISPLAY = "_DISPLAY_"
    const val ALARM_CLOCK = "_ALARM_CLOCK_"
    const val ARROW_DOWN = "_ARROW_DOWN_"
    const val ARROW_UP = "_ARROW_UP_"
    const val FOLD = "_FOLD_"
    @Suppress("unused")
    const val UNFOLD = "_UNFOLD_"
    const val CALC = "_CALC_"
    const val INFO = "_INFO_"
    const val WAIT = "_WAIT_"
    const val GEAR = "_GEAR_"
    const val TORCH = "_TORCH_"
    const val MIC = "_MIC_"
    const val GPS = "_GPS_"
    const val BT = "_BT_"
    const val AIRPLANE = "_AIRPLANE_"
    const val WIFI = "_WIFI_"
    const val CELLULAR = "_CELLULAR_"
    const val INTERNET = "_INTERNET_"
    const val AI = "_AI_"
}

fun glyphVectorFor(glyphId: String): ImageVector = when (glyphId) {
    IG.ZAP -> Icons.Default.Bolt
    IG.ELECTRIC_PLUG -> Icons.Default.Power
    IG.BATTERY -> Icons.Default.BatteryChargingFull
    IG.DISPLAY -> Icons.Default.BrightnessHigh
    IG.ALARM_CLOCK -> Icons.Default.Alarm
    IG.ARROW_DOWN -> Icons.Default.ArrowDownward
    IG.ARROW_UP -> Icons.Default.ArrowUpward
    IG.CALC -> Icons.Default.Calculate
    IG.INFO -> Icons.Default.Info
    IG.WAIT -> Icons.Default.Timelapse
    IG.GEAR -> Icons.Default.Settings
    IG.TORCH -> Icons.Default.FlashlightOn
    IG.MIC -> Icons.Default.Mic
    IG.GPS -> Icons.Default.GpsFixed
    IG.BT -> Icons.Default.Bluetooth
    IG.AIRPLANE -> Icons.Default.AirplanemodeActive
    IG.WIFI -> Icons.Default.Wifi
    IG.CELLULAR -> Icons.Default.SignalCellular4Bar
    IG.INTERNET -> Icons.Default.Public
    IG.AI -> Icons.Default.AutoAwesome
    else -> Icons.Default.Info
}

val defaultInlineGlyphIds = listOf(
    IG.ARROW_DOWN,
    IG.ARROW_UP,
    IG.CALC,
    IG.INFO,
    IG.WAIT,
    IG.TORCH,
    IG.MIC,
    IG.GPS,
    IG.BT,
    IG.AIRPLANE,
    IG.WIFI,
    IG.CELLULAR,
    IG.INTERNET
)

data class InlineLabel(
    val text: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent> = emptyMap()
)

fun AnnotatedString.Builder.appendInlineIcon(
    id: String,
    alternateText: String = " "
) {
    appendInlineContent(id, alternateText)
}

@Composable
fun rememberSingleInlineIcon(
    id: String,
    icon: ImageVector,
    size: TextUnit = 1.em,
    rotation: Float = 0f,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null
): Map<String, InlineTextContent> = remember(id, icon, size, rotation, tint, contentDescription) {
    mapOf(
        id to InlineTextContent(
            placeholder = Placeholder(
                width = size,
                height = size,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(rotation)
            )
        }
    )
}

@Composable
fun buildSingleGlyphLabel(
    glyphId: String,
    altId: String = " ",
    icon: ImageVector,
    size: TextUnit = 1.em,
    rotation: Float = 0f,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null
): InlineLabel {
    val text = buildAnnotatedString {
        appendInlineIcon(glyphId, alternateText = altId)
    }
    val inlineContent = rememberSingleInlineIcon(
        id = glyphId,
        icon = icon,
        size = size,
        rotation = rotation,
        tint = tint,
        contentDescription = contentDescription
    )
    return InlineLabel(text = text, inlineContent = inlineContent)
}

@Composable
fun buildCollapsibleLabel(
    label: String,
    hasExpr: Boolean,
    isExpanded: Boolean,
    icon: ImageVector = Icons.Default.KeyboardArrowDown,
    tint: Color = LocalContentColor.current,
    size: TextUnit = 1.em
): InlineLabel {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded && hasExpr) 180f else 0f,
        label = "collapsibleLabelRotation"
    )

    val text = buildAnnotatedString {
        if (hasExpr) {
            appendInlineIcon(IG.FOLD, alternateText = " ")
        } else {
            append("  ")
        }
        append(label)
    }

    val inlineContent = if (hasExpr) {
        rememberSingleInlineIcon(
            id = IG.FOLD,
            icon = icon,
            size = size,
            rotation = rotation,
            tint = tint,
            contentDescription = null
        )
    } else {
        emptyMap()
    }

    return InlineLabel(text = text, inlineContent = inlineContent)
}

@Composable
fun rememberInlineGlyphContent(
    glyphIds: List<String> = defaultInlineGlyphIds,
    size: TextUnit = 1.em,
    tint: Color = LocalContentColor.current
): Map<String, InlineTextContent> {
    val entries = glyphIds.map { id ->
        val vector = glyphVectorFor(id)
        id to InlineTextContent(
            placeholder = Placeholder(
                width = size,
                height = size,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
            )
        ) {
            Icon(
                imageVector = vector,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
    return remember(glyphIds, size, tint) { entries.toMap() }
}