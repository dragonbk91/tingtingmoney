package money.tingting

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.content.Context
import android.Manifest
import android.app.ActivityManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import money.tingting.ui.theme.TingTingMoneyTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Switch
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import com.patrykandpatrick.vico.compose.axis.horizontal.bottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.startAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.core.entry.entryModelOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import money.tingting.data.AppDatabase
import money.tingting.data.User
import java.util.Calendar
import money.tingting.data.NotificationHistory
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import kotlin.math.roundToInt
import com.patrykandpatrick.vico.core.axis.vertical.VerticalAxis
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.ui.graphics.vector.ImageVector
import money.tingting.ui.theme.Primary  // Add this import
import money.tingting.ui.theme.GreenChart
import androidx.compose.ui.graphics.toArgb
import com.patrykandpatrick.vico.core.component.shape.LineComponent
import androidx.compose.material3.HorizontalDivider
import androidx.core.app.NotificationManagerCompat
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.ripple.rememberRipple

class MainActivity : BaseActivity() {
    private lateinit var settingsViewModel: SettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        db = AppDatabase.getDatabase(this)
        settingsViewModel = SettingsViewModel(application)

        // Add this: Start service if all permissions are granted
        if (hasRequiredPermissions()) {
            startListenerService()
        }

        setContent {
            TingTingMoneyTheme {
                var userData by remember { mutableStateOf<User?>(null) }
                LaunchedEffect(Unit) {
                    userData = db.userDao().getUser().first()
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        MainScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            onPermissionsRequest = { checkAndRequestAllPermissions() },
                            isSpeakEnabled = settingsViewModel.getSpeakEnabled(),
                            onSpeakEnabledChange = { enabled ->
                                settingsViewModel.setSpeakEnabled(enabled)
                            },
                            user = userData
                        )
                    }
                }
            }
        }
    }

    // Add state hoisting at activity level
    private var _isServiceEnabled = mutableStateOf(false)
    private val PERMISSION_REQUEST_CODE = 123
    private lateinit var db: AppDatabase
    private var hasSkippedPermissions = false // Add this variable

    // Add this method to check if service is running
    private fun isServiceRunning(): Boolean {
        try {
            val componentName = ComponentName(this, NotificationListener::class.java)
            packageManager.getServiceInfo(componentName, PackageManager.GET_META_DATA)
            return true
        } catch (e: PackageManager.NameNotFoundException) {
            return false
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        // Check all required permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }

        if (!isNotificationServiceEnabled()) {
            return false
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                return false
            }
        }
        
        return isServiceRunning()
    }

    private fun checkAndRequestAllPermissions() {
        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // First try requesting permission normally
                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        PERMISSION_REQUEST_CODE
                    )
                } else {
                    // Navigate directly to notification settings
                    try {
                        Intent().apply {
                            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                            startActivity(this)
                        }
                    } catch (e: Exception) {
                        // Fallback to application details if direct notification settings fails
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                            startActivity(this)
                        }
                    }
                }
                return
            }
        }

        // Rest of permission checks
        // Then check notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    PERMISSION_REQUEST_CODE
                )
                return
            }
        }

        // First check if notification listener is enabled
        if (!isNotificationServiceEnabled()) {
            toggleNotificationListenerService()
            startNotificationService()  // Use the new function here
            return
        }

        // Finally check battery optimization
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
                return
            }
        }

        startListenerService()
    }

    // Replace deprecated onRequestPermissionsResult with ActivityResultCallback
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            try {
                Intent().apply {
                    action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    startActivity(this)
                }
            } catch (e: Exception) {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                    startActivity(this)
                }
            }
        }
    }

    private fun startListenerService() {
        try {
            // Toggle notification listener service to ensure it's properly initialized
            toggleNotificationListenerService()
            
            val serviceIntent = Intent(this, NotificationListener::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Log.d("TingTingMoney", "Service started successfully")
        } catch (e: Exception) {
            Log.e("TingTingMoney", "Error starting service: ${e.message}")
            throw e  // Re-throw the exception to be handled by caller
        }
    }

    private fun stopListenerService() {
        try {
            val serviceIntent = Intent(this, NotificationListener::class.java)
            // Disable the notification listener component
            val componentName = ComponentName(this, NotificationListener::class.java)
            packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            
            // Stop the service
            stopService(serviceIntent)
            Log.d("TingTingMoney", "Service stopped successfully")
        } catch (e: Exception) {
            Log.e("TingTingMoney", "Error stopping service: ${e.message}")
        }
    }

    private fun toggleNotificationListenerService() {
        val componentName = ComponentName(this, NotificationListener::class.java)
        val pkgManager = packageManager
        pkgManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        pkgManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    override fun onResume() {
        super.onResume()
        // Update service state
        _isServiceEnabled.value = hasRequiredPermissions()

        // Add this: Start service if all permissions are granted
        if (hasRequiredPermissions()) {
            startListenerService()
        }

        if (!hasSkippedPermissions && !hasRequiredPermissions()) {
            setContent {
                TingTingMoneyTheme {
                    var userData by remember { mutableStateOf<User?>(null) }
                    var showPermissionDialog by remember { mutableStateOf(true) }
                    var isServiceEnabled by remember { mutableStateOf(hasRequiredPermissions()) }

                    Log.d("TingTingMoney", "MainActivity: onResume: isServiceEnabled = $isServiceEnabled")
                    LaunchedEffect(Unit) {
                        userData = db.userDao().getUser().first()
                    }

                    if (showPermissionDialog) {
                        PermissionsDialog(
                            onDismiss = { showPermissionDialog = false },
                            onConfirm = {
                                showPermissionDialog = !hasRequiredPermissions()
                                checkAndRequestAllPermissions()
                                _isServiceEnabled.value = hasRequiredPermissions()
                                Log.d("TingTingMoney", "PermissionDialog onConfirm: service state: ${_isServiceEnabled.value}")
                            },
                            onSkip = {
                                showPermissionDialog = false
                                hasSkippedPermissions = true
                                isServiceEnabled = false
                            }
                        )
                    }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            MainScreen(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding),
                                onPermissionsRequest = { 
                                    checkAndRequestAllPermissions()
                                    _isServiceEnabled.value = hasRequiredPermissions()
                                    Log.d("TingTingMoney", "MainScreen onPermissionsRequest: service state: ${_isServiceEnabled.value}")
                                },
                                isSpeakEnabled = settingsViewModel.getSpeakEnabled(),
                                onSpeakEnabledChange = { enabled ->
                                    settingsViewModel.setSpeakEnabled(enabled)
                                },
                                user = userData
                            )
                        }
                    }
                }
            }
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
    }

    @Composable
    fun MainScreen(
        modifier: Modifier = Modifier,
        onPermissionsRequest: () -> Unit,
        isSpeakEnabled: Boolean,
        onSpeakEnabledChange: (Boolean) -> Unit,
        user: User?
    ) {
        Log.d("TingTingMoney", "MainScreen recomposing with isSpeakEnabled: $isSpeakEnabled")
        
        var showPermissionDialog by remember { mutableStateOf(false) }
        var todayRevenue by remember { mutableStateOf(0L) }
        var hourlyData by remember { mutableStateOf(listOf<Pair<Float, Float>>()) }
        var notificationData by remember { mutableStateOf<List<NotificationHistory>>(emptyList()) }
        val context = LocalContext.current

        Log.d("TingTingMoney", "MainScreen: isSpeakEnabled = $isSpeakEnabled, hasRequiredPermissions = ${hasRequiredPermissions()}")
        
        // Check permissions on first launch
        LaunchedEffect(Unit) {
            if (!hasRequiredPermissions()) {
                Log.d("TingTingMoney", "MainScreen: First launch, showing permission dialog")
                showPermissionDialog = true
            }
        }

        if (showPermissionDialog) {
            PermissionsDialog(
                onDismiss = { showPermissionDialog = false },
                onConfirm = {
                    showPermissionDialog = !hasRequiredPermissions()
                    onPermissionsRequest()
                },
                onSkip = {
                    showPermissionDialog = false
                    hasSkippedPermissions = true
                }
            )
        }

        // Collect notification data for today
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfDay = calendar.time
        
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = calendar.time
        
        // Collect data from database
        LaunchedEffect(Unit) {
            AppDatabase.getDatabase(context).notificationHistoryDao()
                .getHistoriesBetweenDates(startOfDay, endOfDay)
                .collect { notifications ->
                    notificationData = notifications

                    // Group notifications by hour and sum amounts
                    val hourlyAmounts = mutableMapOf<Int, Long>()
                    
                    for (notification in notifications) {
                        val cal = Calendar.getInstance().apply { time = notification.time }
                        val hour = cal.get(Calendar.HOUR_OF_DAY)
                        hourlyAmounts[hour] = (hourlyAmounts[hour] ?: 0L) + notification.amount
                    }

                    // Convert to chart data format
                    hourlyData = (0..23).map { hour ->
                        hour.toFloat() to (hourlyAmounts[hour]?.toFloat()?.div(1000) ?: 0f)
                    }

                    // Calculate total revenue for today
                    todayRevenue = notifications.sumOf { it.amount }
                }
        }

        // Add this state to track current speak enabled status
        var currentSpeakEnabled by remember { mutableStateOf(isSpeakEnabled) }
        
        // Update the value when isSpeakEnabled changes
        LaunchedEffect(isSpeakEnabled) {
            currentSpeakEnabled = isSpeakEnabled
        }

        Column(
            modifier = modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Update TopSection to use currentSpeakEnabled
            TopSection(
                name = user?.name ?: "Unknown User",
                isSpeakEnabled = currentSpeakEnabled,
                onSpeakEnabledChange = { enabled ->
                    currentSpeakEnabled = enabled  // Update local state immediately
                    onSpeakEnabledChange(enabled)  // Update ViewModel state
                }
            )

            // Middle Section with vertical centering
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,  // Add this line
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Doanh thu hôm nay:",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${Utils.formatMoney(todayRevenue)} VND",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                )

                // Today's Hourly Chart
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Biểu đồ doanh thu theo giờ",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        if (hourlyData.isNotEmpty()) {
                            Chart(
                                chart = columnChart(
                                    columns = listOf(
                                        LineComponent(
                                            color = GreenChart.toArgb(),
                                            thicknessDp = 8f
                                        )
                                    )
                                ),
                                model = entryModelOf(*hourlyData.toTypedArray()),
                                startAxis = rememberStartAxis(
                                    itemPlacer = AxisItemPlacer.Vertical.default(maxItemCount = 3),
                                    valueFormatter = { value, _ -> "${value.toInt()}K" }
                                ),
                                bottomAxis = rememberBottomAxis(
                                    valueFormatter = { value, _ -> "${value.toInt()}h" }
                                ),
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Không có dữ liệu",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                // Notification Count Chart
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Số lượng thông báo theo giờ",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Create notification count data for all hours
                        val hourlyNotificationCount = notificationData
                            .groupBy { notification ->
                                val cal = Calendar.getInstance().apply { time = notification.time }
                                cal.get(Calendar.HOUR_OF_DAY)
                            }
                            .mapValues { it.value.size.toFloat() }

                        // Always create data for all 24 hours
                        val notificationCountData = (0..23).map { hour ->
                            hour.toFloat() to (hourlyNotificationCount[hour] ?: 0f)
                        }

                        Chart(
                            chart = columnChart(
                                columns = listOf(
                                    LineComponent(
                                        color = GreenChart.toArgb(),
                                        thicknessDp = 8f
                                    )
                                )
                            ),
                            model = entryModelOf(*notificationCountData.toTypedArray()),
                            startAxis = rememberStartAxis(
                                itemPlacer = AxisItemPlacer.Vertical.default(maxItemCount = 3),
                                valueFormatter = { value, _ -> "${value.toInt()}" }
                            ),
                            bottomAxis = rememberBottomAxis(
                                valueFormatter = { value, _ -> "${value.toInt()}h" }
                            ),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Bottom Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { 
                            logButtonClick("history_button")
                            context.startActivity(Intent(context, HistoryActivity::class.java))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(
                            "Lịch Sử",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    
                    Button(
                        onClick = { 
                            logButtonClick("settings_button")
                            context.startActivity(Intent(context, SettingsActivity::class.java))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(
                            "Cài Đặt",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    
                    Button(
                        onClick = { 
                            logButtonClick("contact_button")
                            context.startActivity(Intent(context, ContactActivity::class.java))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(
                            "Liên Hệ",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun PermissionsDialog(
        onDismiss: () -> Unit,
        onConfirm: () -> Unit,
        onSkip: () -> Unit
    ) {
        val needsNotificationPermission = !isNotificationServiceEnabled()
        val needsBatteryPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && 
                                   !isIgnoringBatteryOptimizations()
        val needsPostNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                            ContextCompat.checkSelfPermission(
                                                LocalContext.current,
                                                Manifest.permission.POST_NOTIFICATIONS
                                            ) != PackageManager.PERMISSION_GRANTED

        Log.d("TingTingMoney", "PermissionsDialog: needsNotificationPermission = $needsNotificationPermission, needsBatteryPermission = $needsBatteryPermission, needsPostNotificationPermission = $needsPostNotificationPermission")

        // Nếu đã có đủ quyền thì không hiển thị dialog
        if (!needsNotificationPermission && !needsBatteryPermission && !needsPostNotificationPermission) {
            onConfirm()
            return
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text("Cấp quyền cho ứng dụng",
                    style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Ứng dụng cần các quyền sau để hoạt động đầy đủ:")
                    
                    var permissionCount = 1
                    
                    if (needsPostNotificationPermission) {
                        Text("${permissionCount}. Quyền hiển thị thông báo:",
                            fontWeight = FontWeight.Bold)
                        Text("Để ứng dụng có thể gửi thông báo đến bạn khi cần thiết.")
                        permissionCount++
                    }

                    if (needsNotificationPermission) {
                        Text("${permissionCount}. Quyền đọc thông báo:",
                            fontWeight = FontWeight.Bold)
                        Text("Để có thể lắng nghe và đọc to các thông báo chuyển khoản ngân hàng khi bạn nhận được.")
                        permissionCount++
                    }
                    
                    if (needsBatteryPermission) {
                        Text("${permissionCount}. Quyền chạy nền:",
                            fontWeight = FontWeight.Bold)
                        Text("Để ứng dụng có thể tiếp tục hoạt động ngay cả khi điện thoại ở chế độ tiết kiệm pin hoặc màn hình tắt.")
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Bỏ qua")
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Đồng ý")
                    }
                }
            }
        )
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun startNotificationService() {
        Log.i("TingTingMoney", "Notification Listener Service Started")
        if (Build.VERSION.SDK_INT >= 30) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            intent.putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(packageName, NotificationListener::class.java.name).flattenToString()
            ).flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            return
        }
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    @Composable
    fun TopSection(
        name: String, // Remove unused parameters avatarUrl and email
        isSpeakEnabled: Boolean,
        onSpeakEnabledChange: (Boolean) -> Unit
    ) {
        val context = LocalContext.current as MainActivity  // Add this line
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 4.dp
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Avatar circle with initials
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name.split(" ")
                                .filter { it.isNotEmpty() }
                                .take(2)
                                .joinToString("") { it.first().uppercase() },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Column {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium
                        )
//                    Text(
//                        text = email,
//                        style = MaterialTheme.typography.bodySmall
//                    )
                    }
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .shadow(
                                elevation = 4.dp,
                                shape = CircleShape
                            )
                            .background(
                                color = if (isSpeakEnabled) Color(0xFF4CAF50) else Color(0xFFE57373),
                                shape = CircleShape
                            )
                            // Use simple clickable without ripple since it's causing issues
                            .clickable { 
                                context.logButtonClick(if(isSpeakEnabled) "disable_speak" else "enable_speak")
                                onSpeakEnabledChange(!isSpeakEnabled) 
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSpeakEnabled) 
                                Icons.AutoMirrored.Rounded.VolumeUp
                            else 
                                Icons.AutoMirrored.Rounded.VolumeOff,
                            contentDescription = if (isSpeakEnabled) "Tắt" else "Bật",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = if (isSpeakEnabled) "Bật Âm Báo" else "Tắt Âm Báo",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSpeakEnabled) Color(0xFF4CAF50) else Color(0xFFE57373),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    TingTingMoneyTheme {
        Greeting("Android")
    }
}