package fr.cyring.battman

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import fr.cyring.battman.BatteryViewModel.Companion.updateBatteryLevel

class BatteryAlertWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    private val privateIntent: Intent? = appContext.registerReceiver(
        null,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    )

    override suspend fun doWork(): Result {
        if (privateIntent != null) {
            BatteryEventReceiver.backgroundMonitor(applicationContext, privateIntent)
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "battman_battery_alert_worker"

        fun schedule(context: Context, intervalMinutes: Long = 15L) {
            if (intervalMinutes <= 0L) {
                cancel(context)
                return
            }

            val localIntent: Intent? = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            if (localIntent != null) {
                updateBatteryLevel(localIntent)
            }

            val workRequest = PeriodicWorkRequestBuilder<BatteryAlertWorker>(
                intervalMinutes, TimeUnit.MINUTES,
                intervalMinutes, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
