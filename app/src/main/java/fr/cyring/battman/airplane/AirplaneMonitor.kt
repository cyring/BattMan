package fr.cyring.battman.airplane

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import fr.cyring.battman.R
import fr.cyring.battman.ui.IG
import fr.cyring.battman.ui.appendInlineIcon

class AirplaneMonitor(context: Context) {
    private val contentResolver = context.contentResolver
    private val airplaneModeUri: Uri = Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON)

    var isAirplaneModeEnabled by mutableStateOf(false)
        private set

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            updateAirplaneModeState()
        }
    }

    fun startListening() {
        updateAirplaneModeState()
        contentResolver.registerContentObserver(airplaneModeUri, false, observer)
    }

    fun stopListening() {
        contentResolver.unregisterContentObserver(observer)
    }

    private fun updateAirplaneModeState() {
        isAirplaneModeEnabled = Settings.Global.getInt(
            contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0
        ) == 1
    }

    fun buildAirplaneStatus(context: Context): AnnotatedString {
        val airplane = if (isAirplaneModeEnabled) {
            buildAnnotatedString {
                appendInlineIcon(IG.AIRPLANE, context.getString(R.string._airplane_))
                append(' ')
                append(context.getString(R.string.activated))
            }
        } else {
            AnnotatedString(context.getString(R.string.deactivated))
        }
        return airplane
    }
}