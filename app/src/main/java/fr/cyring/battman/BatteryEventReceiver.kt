package fr.cyring.battman

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat

const val NOTIFICATION_BATTERY_ALERT_ID = 1001

fun showLowBatteryNotification(context: Context, level: Int, id: Int) {
    val channelId = "battman_battery_alerts"
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            context.getString(R.string.battery_alerts_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.battery_alerts_channel_desc)
        }
        notificationManager.createNotificationChannel(channel)
    }

    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent = PendingIntent.getActivity(
        context, 0, intent, PendingIntent.FLAG_IMMUTABLE
    )

    val message = context.getString(R.string.battery_low_alert_text)

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle(context.getString(R.string.battman_alert_title, level))
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()

    try {
        notificationManager.notify(id, notification)
    } catch (e: SecurityException) {
        Toast.makeText(context, "${e.message}", Toast.LENGTH_LONG).show()
    }
}

class BatteryEventReceiver : BroadcastReceiver() {
    companion object {
        private var alertShown = false
        private var alertCount = 0
        var maxAlerts = 3

        fun cancelNotification(context: Context, id: Int) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(id)
            alertShown = false
            alertCount = 0
        }

        fun foregroundMonitor(context: Context, intent: Intent) {
            val trend = BatteryViewModel.updateBatteryLevel(intent)

            if (BatteryViewModel.batteryAlertThreshold > 0
                && BatteryViewModel.batteryPercent <= BatteryViewModel.batteryAlertThreshold
                && trend == BatteryViewModel.BatteryTrend.DECREASING)
            {
                if (alertCount < maxAlerts) {
                    showLowBatteryNotification(context, BatteryViewModel.batteryPercent, NOTIFICATION_BATTERY_ALERT_ID)
                    alertShown = true
                    alertCount++
                }
            } else if (alertShown || BatteryViewModel.batteryPercent > BatteryViewModel.batteryAlertThreshold) {
                cancelNotification(context, NOTIFICATION_BATTERY_ALERT_ID)
            }
        }

        fun backgroundMonitor(context: Context, intent: Intent) {
            val trend = BatteryViewModel.updateBatteryLevel(intent)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            if (BatteryViewModel.batteryAlertThreshold > 0
                && BatteryViewModel.batteryPercent <= BatteryViewModel.batteryAlertThreshold
                && !isCharging)
            {
                if (alertCount < maxAlerts) {
                    showLowBatteryNotification(context, BatteryViewModel.batteryPercent, NOTIFICATION_BATTERY_ALERT_ID)
                    alertShown = true
                    alertCount++
                }
            } else if (alertShown || BatteryViewModel.batteryPercent > BatteryViewModel.batteryAlertThreshold) {
                cancelNotification(context, NOTIFICATION_BATTERY_ALERT_ID)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BATTERY_CHANGED -> {
                foregroundMonitor(context, intent)
            }
            Intent.ACTION_POWER_CONNECTED -> {
                cancelNotification(context, NOTIFICATION_BATTERY_ALERT_ID)
            }
        }
    }
}
