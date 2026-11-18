package fr.cyring.battman.camera

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import fr.cyring.battman.R

class FlashlightMonitor(context: Context) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val mainHandler = Handler(Looper.getMainLooper())

    var isTorchOn by mutableStateOf(false)
        private set

    var torchUnavailable by mutableStateOf(false)
        private set

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            isTorchOn = enabled
            torchUnavailable = false
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            torchUnavailable = true
        }
    }

    fun startListening() {
        try {
            cameraManager.registerTorchCallback(torchCallback, mainHandler)
        } catch (_: Exception) {}
    }

    fun stopListening() {
        try {
            cameraManager.unregisterTorchCallback(torchCallback)
        } catch (_: Exception) {}
    }

    fun buildTorchStatus(ctx: Context): AnnotatedString {
        return when {
            torchUnavailable -> AnnotatedString(ctx.getString(R.string.unavailable))
            isTorchOn -> buildAnnotatedString {
                append(ctx.getString(R.string._torch_))
                append(' ')
                append(ctx.getString(R.string.activated))
            }
            else -> AnnotatedString(ctx.getString(R.string.deactivated))
        }
    }
}