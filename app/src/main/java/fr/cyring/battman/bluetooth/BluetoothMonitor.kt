package fr.cyring.battman.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.core.content.ContextCompat
import fr.cyring.battman.R
import fr.cyring.battman.ui.IG
import fr.cyring.battman.ui.appendInlineIcon

class BluetoothMonitor(private val context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter?
        get() = try {
            bluetoothManager?.adapter
        } catch (_: SecurityException) {
            null
        }

    var isBluetoothEnabled by mutableStateOf(false)
        private set

    private fun updateBluetoothState() {
        isBluetoothEnabled = try {
            bluetoothAdapter?.isEnabled == true
        } catch (_: SecurityException) {
            false
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                updateBluetoothState()
            }
        }
    }

    fun startListening() {
        updateBluetoothState()
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(
            context,
            bluetoothReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun stopListening() {
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (_: Exception) {
            // TODO
        }
    }

    fun buildBluetoothStatus(context: Context): AnnotatedString {
        val bt = if (isBluetoothEnabled) {
            buildAnnotatedString {
                appendInlineIcon(IG.BT, context.getString(R.string._bt_))
                append(' ')
                append(context.getString(R.string.activated))
            }
        } else {
            AnnotatedString(context.getString(R.string.deactivated))
        }
        return bt
    }
}