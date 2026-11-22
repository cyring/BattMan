package fr.cyring.battman.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import fr.cyring.battman.Net
import fr.cyring.battman.R

class NetworkMonitor(context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    var isNetworkConnected: Net by mutableStateOf(Net.UNAVAILABLE)
        private set

    var viaWiFiGateway by mutableStateOf(false)
        private set

    var viaCellularGateway by mutableStateOf(false)
        private set

    var isInternetValidated by mutableStateOf(false)
        private set

    private fun clearCapabilities() {
        viaWiFiGateway = false
        viaCellularGateway = false
        isInternetValidated = false
    }

    private fun setConnectedState(network: Network?, caps: NetworkCapabilities? = null) {
        if (network == null) {
            isNetworkConnected = Net.DISCONNECTED
            clearCapabilities()
        } else {
            isNetworkConnected = Net.CONNECTED

            val resolvedCaps = caps ?: try {
                connectivityManager.getNetworkCapabilities(network)
            } catch (_: Exception) {
                null
            }

            viaWiFiGateway = try {
                resolvedCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            } catch (_: Exception) {
                false
            }
            viaCellularGateway = try {
                resolvedCaps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            } catch (_: Exception) {
                false
            }
            isInternetValidated = try {
                resolvedCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            } catch (_: Exception) {
                false
            }
        }
    }

    private inner class NetworkCallbackHolder (
        var registered: Boolean = false,
        val function: ConnectivityManager.NetworkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                setConnectedState(network)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                setConnectedState(network, networkCapabilities)
            }

            override fun onLost(network: Network) {
                setConnectedState(null)
            }
        }
    )

    private val callback: NetworkCallbackHolder = NetworkCallbackHolder()

    fun startListening() {
        val currentNetwork = try {
            connectivityManager.activeNetwork
        } catch (_: SecurityException) {
            isNetworkConnected = Net.UNAVAILABLE
            clearCapabilities()
            null
        } catch (_: Exception) {
            null
        }
        if (isNetworkConnected != Net.UNAVAILABLE) {
            setConnectedState(currentNetwork)
        }
        if (callback.registered == false) {
            try {
                connectivityManager.registerDefaultNetworkCallback(callback.function)
                callback.registered = true
            } catch (_: SecurityException) {
                callback.registered = false
                isNetworkConnected = Net.UNAVAILABLE
                clearCapabilities()
            } catch (_: Exception) {
                callback.registered = false
            }
        }
    }

    fun stopListening() {
        if (callback.registered == true) {
            try {
                connectivityManager.unregisterNetworkCallback(callback.function)
                callback.registered = false
            } catch (_: IllegalArgumentException) {
                callback.registered = false
            } catch (_: Exception) {
                // NOP
            }
        }
    }

    fun buildInternetStatus(context: Context): AnnotatedString {
        val internet = if (isInternetValidated) {
            buildAnnotatedString {
                append(context.getString(R.string._internet_))
                append(' ')
                append(context.getString(R.string.yes))
            }
        } else {
            AnnotatedString(context.getString(R.string.no))
        }
        return internet
    }

    fun buildGatewayStatus(context: Context, viaLinkState: Boolean, iconId: Int?): AnnotatedString {
        val gateway = if (viaLinkState) {
            buildAnnotatedString {
                if (iconId != null) {
                    append(context.getString(iconId))
                    append(' ')
                }
                append(context.getString(R.string.activated))
            }
        } else {
            AnnotatedString(context.getString(R.string.deactivated))
        }
        return gateway
    }

    fun buildWiFiStatus(context: Context): AnnotatedString {
        return when (isNetworkConnected) {
            Net.CONNECTED -> buildGatewayStatus(
                context,
                viaWiFiGateway,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { R.string._wifi_ } else { null }
            )
            Net.DISCONNECTED -> AnnotatedString(context.getString(R.string.none))
            else -> AnnotatedString(context.getString(R.string.unavailable))
        }
    }

    fun buildCellularStatus(context: Context): AnnotatedString {
        return when (isNetworkConnected) {
            Net.CONNECTED -> buildGatewayStatus(
                context,
                viaCellularGateway,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { R.string._cellular_ } else { null }
            )
            Net.DISCONNECTED -> AnnotatedString(context.getString(R.string.none))
            else -> AnnotatedString(context.getString(R.string.unavailable))
        }
    }
}