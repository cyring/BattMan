package fr.cyring.battman

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
        .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
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
        var maxAlerts = DEFAULT_MAX_ALERTS

        fun cancelNotification(context: Context, id: Int) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(id)
            alertShown = false
            alertCount = 0
        }

        fun evaluateBatteryAlert(
            context: Context,
            intent: Intent,
            condition: (mask: Int) -> Boolean
        ) {
            val mask = BatteryViewModel.updateBatteryLevel(intent)

            val isAlertThresholdMet = BatteryViewModel.batteryAlertThreshold > 0
                    && BatteryViewModel.batteryPercent <= BatteryViewModel.batteryAlertThreshold

            if (isAlertThresholdMet && condition(mask)) {
                if (maxAlerts == 0 || alertCount < maxAlerts) {
                    showLowBatteryNotification(context, BatteryViewModel.batteryPercent, NOTIFICATION_BATTERY_ALERT_ID)
                    alertShown = true
                    alertCount++
                }
            } else if (alertShown || BatteryViewModel.batteryPercent > BatteryViewModel.batteryAlertThreshold) {
                cancelNotification(context, NOTIFICATION_BATTERY_ALERT_ID)
            }
        }

        fun frontMonitor(context: Context, intent: Intent) =
            evaluateBatteryAlert(context, intent) { mask ->
                (mask and BatteryTrendMask.TREND_DECREASING) != 0 &&
                        (mask and (BatteryTrendMask.STATE_CHARGING or BatteryTrendMask.STATE_FULL)) == 0
            }

        fun agentMonitor(context: Context, intent: Intent) =
            evaluateBatteryAlert(context, intent) { mask ->
                (mask and (BatteryTrendMask.STATE_DISCHARGING or BatteryTrendMask.STATE_NOT_CHARGING)) != 0
            }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BATTERY_CHANGED -> {
                frontMonitor(context, intent)
            }
            Intent.ACTION_POWER_CONNECTED -> {
                cancelNotification(context, NOTIFICATION_BATTERY_ALERT_ID)
            }
        }
    }
}
