package fr.cyring.battman.audio

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import fr.cyring.battman.R
import fr.cyring.battman.ui.IG
import fr.cyring.battman.ui.appendInlineIcon

class AudioMonitor(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    var isRecordingActive by mutableStateOf(false)
        private set

    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: List<android.media.AudioRecordingConfiguration>) {
            isRecordingActive = configs.isNotEmpty()
        }
    }

    fun startListening() {
        try {
            audioManager.registerAudioRecordingCallback(recordingCallback, Handler(Looper.getMainLooper()))
            val activeConfigs = audioManager.activeRecordingConfigurations
            isRecordingActive = activeConfigs.isNotEmpty()
        } catch (_: Exception) {
            // TODO
        }
    }

    fun stopListening() {
        try {
            audioManager.unregisterAudioRecordingCallback(recordingCallback)
        } catch (_: Exception) {
            // TODO
        }
    }

    fun buildAudioStatus(context: Context): AnnotatedString {
        val microphone = if (isRecordingActive) {
            buildAnnotatedString {
                appendInlineIcon(IG.MIC, context.getString(R.string._mic_))
                append(' ')
                append(context.getString(R.string.activated))
            }
        } else {
            AnnotatedString(context.getString(R.string.deactivated))
        }
        return microphone
    }
}