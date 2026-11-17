package fr.cyring.battman.display

import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.provider.Settings.System.getInt
import android.view.Display
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import fr.cyring.battman.R
import fr.cyring.battman.waitingStyle
import kotlin.time.Duration.Companion.milliseconds

data class DisplayInfo(
    val brightness: Int,
    val adaptive: Boolean,
    val darkState: Boolean,
    val nightState: Boolean?,
    val touchKind: Int,
    val refreshRate: Float,
    val screenTimeoutMs: Long,
    val screenSaverEnabled: Boolean?
)

object Display {
    fun collect(ctx: Context): DisplayInfo {
        val displayManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ctx.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        } else {
            null
        }

        val resolver = ctx.contentResolver
        val sysConf = ctx.resources.configuration

        val brightness = try {
            getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1)
        } catch (_: Exception) {
            -1
        }

        val adaptive = try {
            getInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } catch (_: Exception) {
            false
        }

        val screenTimeoutMs = try {
            Settings.System.getLong(resolver, Settings.System.SCREEN_OFF_TIMEOUT, -1L)
        } catch (_: Exception) {
            -1L
        }

        val screenSaverEnabled = try {
            Settings.Secure.getInt(
                ctx.contentResolver,
                "screensaver_enabled"
            ) != 0
        } catch (_: Exception) {
            null
        }

        return DisplayInfo(
            brightness = brightness,
            adaptive = adaptive,
            darkState = (sysConf.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES,
            nightState = try {
                Settings.Secure.getInt(
                    ctx.contentResolver,
                    "night_display_activated",
                    0
                ) == 1
            } catch (_: Exception) {
                null
            },
            touchKind = sysConf.touchscreen,
            refreshRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
                    display?.refreshRate ?: 0f
                } catch (_: Exception) {
                    0f
                }
            } else {
                0f
            },
            screenTimeoutMs = screenTimeoutMs,
            screenSaverEnabled = screenSaverEnabled
        )
    }

    fun buildBrightness(ctx: Context, display: DisplayInfo): AnnotatedString = AnnotatedString(
        if (display.brightness >= 0) {
            display.brightness.toString()
        } else {
            ctx.getString(
                R.string.unknown
            )
        } + "/255"
    )

    fun buildAdaptiveMode(ctx: Context, display: DisplayInfo): AnnotatedString =
        AnnotatedString(ctx.getString(if (display.adaptive) R.string.yes else R.string.no))

    fun buildDarkTheme(ctx: Context, display: DisplayInfo): AnnotatedString =
        AnnotatedString(ctx.getString(if (display.darkState) R.string.yes else R.string.no))

    fun buildNightLight(ctx: Context, display: DisplayInfo): AnnotatedString =
        AnnotatedString(ctx.getString(if (display.nightState == true) R.string.activated else R.string.deactivated))

    fun buildTouchScreen(ctx: Context, display: DisplayInfo) = AnnotatedString(
        ctx.getString(
            when (display.touchKind) {
                Configuration.TOUCHSCREEN_FINGER -> R.string.finger
                Configuration.TOUCHSCREEN_NOTOUCH -> R.string.notouch
                Configuration.TOUCHSCREEN_UNDEFINED -> R.string.undefined
                else -> R.string.unknown
            }
        )
    )

    fun buildSmoothDisplay(ctx: Context, display: DisplayInfo) = AnnotatedString(
        if (display.refreshRate == 0f) ctx.getString(
            R.string.unsupported
        ) else "${display.refreshRate.toInt()} Hz"
    )

    fun buildScreenTimeout(ctx: Context, display: DisplayInfo) = AnnotatedString(
        when {
        display.screenTimeoutMs < 0L -> ctx.getString(R.string.unknown)
        display.screenTimeoutMs == Int.MAX_VALUE.toLong() -> ctx.getString(R.string.never)
        else -> display.screenTimeoutMs.milliseconds.toComponents { _, h, m, s, ns ->
            if (h > 0) ctx.getString(
                R.string.duration_h_m_s_str_format, h, m, s
            ) else if (m > 0) ctx.getString(
                R.string.duration_m_s_str_format,
                m,
                s
            ) else ctx.getString(
                R.string.duration_ms_str_format, s, ns / 1_000_000L
            )
        }
    })

    fun buildScreenSaver(ctx: Context, display: DisplayInfo) =
        AnnotatedString(ctx.getString(if (display.screenSaverEnabled == true) R.string.activated else R.string.deactivated))

    fun buildLastScreenOffDuration(ctx: Context, ms: Long): AnnotatedString = if (ms == -1L) {
        buildAnnotatedString {
            append(ctx.getString(R.string._wait_) + ' ')
            append(ctx.getString(R.string.waiting).waitingStyle())
        }
    } else {
        val durationString = ms.milliseconds.toComponents { _, h, m, s, ns ->
            if (h > 0) ctx.getString(
                R.string.duration_h_m_s_str_format, h, m, s
            ) else if (m > 0) ctx.getString(
                R.string.duration_m_s_str_format,
                m,
                s
            ) else ctx.getString(
                R.string.duration_ms_str_format, s, ns / 1_000_000L
            )
        }
        buildAnnotatedString {
            append(ctx.getString(R.string._calc_) + ' ')
            append(durationString)
        }
    }
}