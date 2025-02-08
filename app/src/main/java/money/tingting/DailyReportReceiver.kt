package money.tingting

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import money.tingting.data.AppDatabase
import java.util.Calendar
import java.util.Date
import kotlin.math.abs

class DailyReportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CoroutineScope(Dispatchers.IO).launch {
            // Get today's revenue
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
            val startOfToday = calendar.time
            val now = Date()

            // Get yesterday's revenue
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            val startOfYesterday = calendar.time
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            
            val db = AppDatabase.getDatabase(context)
            
            // Use Flow with collect like in MainActivity
            val todayNotifications = db.notificationHistoryDao()
                .getHistoriesBetweenDates(startOfToday, now)
                .first() // Get first emission from Flow
                
            val yesterdayNotifications = db.notificationHistoryDao()
                .getHistoriesBetweenDates(startOfYesterday, startOfToday)
                .first()

            // Calculate totals using sumOf like in MainActivity
            val todayRevenue = todayNotifications.sumOf { notification -> notification.amount }
            val yesterdayRevenue = yesterdayNotifications.sumOf { notification -> notification.amount }

            // Chỉ hiện thông báo nếu doanh thu khác 0
            if (todayRevenue > 0) {
                // Create notification message
                val message = when {
                    yesterdayRevenue == 0L -> 
                        "Doanh thu hôm nay: ${Utils.formatMoney(todayRevenue)}đ"
                    else -> {
                        val percentChange = ((todayRevenue - yesterdayRevenue).toDouble() / yesterdayRevenue * 100)
                        val trend = if (percentChange > 0) "tăng" else "giảm"
                        val absPercentChange = kotlin.math.abs(percentChange)
                        "Doanh thu hôm nay: ${Utils.formatMoney(todayRevenue)}đ ($trend ${String.format("%.1f", absPercentChange)}% so với hôm qua)"
                    }
                }

                showDailyReport(context, message)
            }
        }
    }

    private fun showDailyReport(context: Context, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, TingTingApp.DAILY_REPORT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Báo cáo doanh thu")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(1001, notification)
    }
}