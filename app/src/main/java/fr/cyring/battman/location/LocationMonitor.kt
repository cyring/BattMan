package fr.cyring.battman.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.core.content.ContextCompat
import fr.cyring.battman.R
import fr.cyring.battman.ui.IG
import fr.cyring.battman.ui.appendInlineIcon

class LocationMonitor(private val context: Context) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    var isGpsEnabled by mutableStateOf(false)
        private set

    private val gpsReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                updateGpsState()
            }
        }
    }

    fun startListening() {
        updateGpsState()
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        ContextCompat.registerReceiver(
            context,
            gpsReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun stopListening() {
        try {
            context.unregisterReceiver(gpsReceiver)
        } catch (_: Exception) {
            // TODO
        }
    }

    private fun updateGpsState() {
        isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    fun buildLocationStatus(context: Context): AnnotatedString {
        val gps = if (isGpsEnabled) {
            buildAnnotatedString {
                appendInlineIcon(IG.GPS, context.getString(R.string._gps_))
                append(' ')
                append(context.getString(R.string.activated))
            }
        } else {
            AnnotatedString(context.getString(R.string.deactivated))
        }
        return gps
    }
}