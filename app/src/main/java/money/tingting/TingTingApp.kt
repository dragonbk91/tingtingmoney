package money.tingting

import android.app.Application
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import money.tingting.data.AppDatabase
import android.util.Log
import java.util.Calendar

class TingTingApp : Application() {
    companion object {
        const val DAILY_REPORT_CHANNEL_ID = "daily_report_channel"
        var instance: TingTingApp? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        val db = AppDatabase.getDatabase(this)
        Utils.VoiceScriptCustomizer.initialize(db)
        
        // Initialize TTS at app startup
        Utils.TextToSpeechHelper.initialize(this) { success ->
            if (!success) {
                Log.e("TingTingMoney", "Failed to initialize TTS at app startup")
            }
        }
        
        startService(Intent(this, NotificationListener::class.java))

        // Create notification channel for daily reports
        createDailyReportChannel()
        
        // Schedule daily report
        scheduleDailyReport()
    }

    private fun createDailyReportChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DAILY_REPORT_CHANNEL_ID,
                "Báo cáo doanh thu",
                NotificationManager.IMPORTANCE_HIGH // Changed to HIGH for more visibility
            ).apply {
                description = "Thông báo báo cáo doanh thu hàng ngày"
                enableLights(true)
                enableVibration(true)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun scheduleDailyReport() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, DailyReportReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Set time to 22:00
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            
            // If current time is after 22:00, schedule for next day
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // Use RTC instead of RTC_WAKEUP for less battery impact
        alarmManager.setInexactRepeating(
            AlarmManager.RTC,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }
}
