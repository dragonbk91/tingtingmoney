package money.tingting

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import money.tingting.data.AppDatabase
import money.tingting.data.NotificationHistory
import java.util.Date

class NotificationListener : NotificationListenerService() {
    private lateinit var notificationManager: NotificationManager
    private lateinit var db: AppDatabase
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val channelId = "TingTingMoney"
    private var notificationId = 1
    private var isConnected = false
    private lateinit var settingsViewModel: SettingsViewModel
    private val notificationChannelId = "TINGTING_SERVICE_CHANNEL"

    override fun onCreate() {
        super.onCreate()
        try {
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            db = AppDatabase.getDatabase(this)
            createNotificationChannel()
            startForegroundService()
            
            // Initialize TTS
            Utils.TextToSpeechHelper.initialize(this) { success ->
                Log.d("TingTingMoney", "TTS initialized: $success")
            }
            settingsViewModel = SettingsViewModel(application)
        } catch (e: Exception) {
            Log.e("TingTingMoney", "Error in onCreate: ${e.message}")
        }
    }

    private fun startForegroundService() {
        val notification = createForegroundNotification()
        startForeground(1, notification)
    }

    private fun createForegroundNotification(): Notification {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                notificationChannelId,
                "Tingting Money Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Lắng nghe thông báo chuyển khoản"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, notificationChannelId)
            .setSmallIcon(R.drawable.ic_notification) // Thay đổi thành ic_notification
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.app_logo))
            .setBadgeIconType(R.drawable.app_logo)
            .setContentTitle("TingTing Money")
            .setContentText("Đang lắng nghe thông báo chuyển khoản")
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setNumber(notificationId)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("TingTingMoney", "onStartCommand")
        startForegroundService()
        if (!isConnected) {
            requestRebind(ComponentName(this, NotificationListener::class.java))
        }
        return START_STICKY
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        Log.d("TingTingMoney", "Notification Listener Connected")
        
        // Request appropriate interruption filter
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestInterruptionFilter(NotificationListenerService.INTERRUPTION_FILTER_ALL)
            requestListenerHints(NotificationListenerService.HINT_HOST_DISABLE_EFFECTS)
        }

        // Cancel all existing notifications to start fresh
        cancelAllNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        Log.d("TingTingMoney", "Notification Listener Disconnected")
        
        // Reconnect immediately
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(ComponentName(this, NotificationListener::class.java))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Utils.TextToSpeechHelper.shutdown()
        Log.d("TingTingMoney", "Notification Listener Destroyed")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            if (!isConnected) {
                Log.d("TingTingMoney", "Notification received but listener not connected")
                return
            }

            // Log the package name for debugging
            Log.d("TingTingMoney", "Notification from package: ${sbn.packageName}")

            val notification = sbn.notification
            val extras = notification.extras
            
            val title = extras.getCharSequence("android.title")?.toString() ?: ""
            val text = extras.getCharSequence("android.text")?.toString() ?: ""
            val packageName = sbn.packageName

            // Define list of ignored package names
            val ignoredPackages = listOf(
                "com.whatsapp",
                "com.zing.zalo",
                "com.facebook.orca",
                "org.telegram.messenger"
            )

            // Check if package should be ignored
            if (packageName in ignoredPackages) {
                Log.d("TingTingMoney", "Ignored notification from package: $packageName")
                return
            }

            // Get app name (bank name)
            val packageManager = applicationContext.packageManager
            val appName = try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                Log.d("TingTingMoney", packageManager.getApplicationLabel(appInfo).toString())
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e("TingTingMoney", "Error getting app name: ${e.message}")
                packageName
            }

            // Only process if there's actual content
            if (text.isNotEmpty()) {
                val incomeMoney = Utils.parseIncomeMoney(text)
                Log.d("TingTingMoney", "Notification content - Title: $title, Text: $text")

                // Save to database
                if (incomeMoney > 0) {
                    coroutineScope.launch {
                        val history = NotificationHistory(
                            package_name = packageName,
                            bank = appName,
                            time = Date(sbn.postTime),
                            amount = incomeMoney,
                            title = title,
                            text = text
                        )
                        db.notificationHistoryDao().insert(history)
                        Log.d("TingTingMoney", "Saved notification to database")
                    }

                    if (settingsViewModel.getSpeakEnabled()) {
                        val moneyInWords = Utils.NumberToWordsConverter.convert(incomeMoney)
                        val customScript = Utils.VoiceScriptCustomizer.customize()
                        Log.d("TingTingMoney", "Custom script: $customScript")

                        val textToSpeak = listOf("$moneyInWords đồng", customScript)
                        coroutineScope.launch {
                            Utils.TextToSpeechHelper.speakSequentially(textToSpeak)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TingTingMoney", "Error processing notification: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Handle notification removal here
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "TingTing Money Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }
    }
}