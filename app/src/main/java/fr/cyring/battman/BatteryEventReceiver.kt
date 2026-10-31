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

fun showLowBatteryNotification(context: Context, level: Int) {
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

    val message = context.getString(R.string.battery_low_alert_text, level)

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle(context.getString(R.string.battman_alert_title))
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()

    try {
        notificationManager.notify(NOTIFICATION_BATTERY_ALERT_ID, notification)
    } catch (e: SecurityException) {
        Toast.makeText(context, "${e.message}", Toast.LENGTH_LONG).show()
    }
}

class BatteryEventReceiver : BroadcastReceiver() {
    companion object {
        private var alertShown = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        fun cancelNotification(context: Context, id: Int) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(id)
        }
        when (intent.action) {
            Intent.ACTION_BATTERY_CHANGED -> {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val trend = BatteryViewModel.updateBatteryLevel(level, scale)

                if (BatteryViewModel.batteryAlertThreshold > 0
                    && BatteryViewModel.batteryPercent <= BatteryViewModel.batteryAlertThreshold
                    && trend == BatteryViewModel.BatteryTrend.DECREASING)
                {
                    showLowBatteryNotification(context, BatteryViewModel.batteryPercent)
                    alertShown = true
                } else if (alertShown == true) {
                    cancelNotification(context, NOTIFICATION_BATTERY_ALERT_ID)
                    alertShown = false
                }
            }
            Intent.ACTION_POWER_CONNECTED -> {
                cancelNotification(context, NOTIFICATION_BATTERY_ALERT_ID)
                alertShown = false
            }
        }
    }
}
