package money.tingting

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.patrykandpatrick.vico.compose.axis.horizontal.bottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.startAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.core.entry.entryModelOf
import money.tingting.ui.theme.TingTingMoneyTheme
import java.text.SimpleDateFormat
import java.util.*
import money.tingting.data.NotificationHistory
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import money.tingting.ui.theme.Primary  // Add this import
import money.tingting.ui.theme.GreenChart
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis  // Add this import
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis     // Add this import
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer                    // Add this import
import com.patrykandpatrick.vico.core.component.shape.LineComponent          // Add this import
import androidx.activity.enableEdgeToEdge  // Add this import

class HistoryActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Add this line
        setContent {
            TingTingMoneyTheme {
                // Wrap with Scaffold
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        HistoryScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel()) {
    var selectedDays by remember { mutableStateOf(7) }
    val historyData by viewModel.getHistoryData(selectedDays).collectAsState(initial = emptyList())
    val context = LocalContext.current as HistoryActivity
    val scrollState = rememberScrollState()
    val activity = LocalContext.current as HistoryActivity

    LaunchedEffect(selectedDays) {
        // Log when time range changes
        activity.logButtonClick("view_history_${selectedDays}_days")
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Lịch sử",
                style = MaterialTheme.typography.titleLarge
            )
            // Empty box to maintain center alignment
            Box(modifier = Modifier.width(48.dp))
        }

        // Existing content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState)  // Add this modifier
        ) {
            // Summary Section
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    val dateFormat = SimpleDateFormat("d/M", Locale.getDefault())
                    
                    // Calculate daily totals
                    val dailyTotals = historyData
                        .groupBy { 
                            dateFormat.format(it.time)
                        }
                        .mapValues { (_, notifications) ->
                            notifications.sumOf { it.amount }
                        }

                    // Calculate summary data
                    val totalRevenue = dailyTotals.values.sum()
                    val maxRevenue = dailyTotals.maxByOrNull { it.value }
                    val minRevenue = dailyTotals.minByOrNull { it.value }

                    Text(
                        text = "Thống kê ${selectedDays} ngày gần nhất",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = "Tổng doanh thu: ${Utils.formatMoney(totalRevenue)} đ",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    maxRevenue?.let {
                        Text(
                            text = "Ngày cao nhất: ${it.key} (${Utils.formatMoney(it.value)} đ)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    minRevenue?.let {
                        Text(
                            text = "Ngày thấp nhất: ${it.key} (${Utils.formatMoney(it.value)} đ)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }

            // Common variables for both charts
            val dateFormat = SimpleDateFormat("d/M", Locale.getDefault())
            val calendar = Calendar.getInstance()
            val dates = (0 until selectedDays).map { daysAgo ->
                calendar.apply {
                    time = Date()
                    add(Calendar.DAY_OF_YEAR, -daysAgo)
                }.time
            }.reversed()

            // Revenue Chart Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Lịch sử doanh thu theo ngày",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        textAlign = TextAlign.Center
                    )

                    // Create map of dates to amounts
                    val dateAmountMap = historyData
                        .groupBy { 
                            dateFormat.format(it.time)
                        }
                        .mapValues { (_, notifications) ->
                            notifications.sumOf { it.amount }.toFloat() / 1000
                        }

                    // Create chart data with all dates (including zeros for missing dates)
                    val dailyData = dates.mapIndexed { index, date ->
                        val dateStr = dateFormat.format(date)
                        index.toFloat() to (dateAmountMap[dateStr] ?: 0f)
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
                        model = entryModelOf(*dailyData.toTypedArray()),
                        startAxis = rememberStartAxis(
                            itemPlacer = AxisItemPlacer.Vertical.default(maxItemCount = 5),
                            valueFormatter = { value, _ -> "${value.toInt()}K" }
                        ),
                        bottomAxis = rememberBottomAxis(
                            valueFormatter = { value, _ ->
                                val index = value.toInt()
                                if (index < dates.size) {
                                    dateFormat.format(dates[index])
                                } else ""
                            },
                            labelRotationDegrees = 270f
                        ),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Notification Count Chart Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .padding(top = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Số lượng thông báo theo ngày",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        textAlign = TextAlign.Center
                    )

                    // Create map of dates to notification counts
                    val dateCountMap = historyData
                        .groupBy { 
                            dateFormat.format(it.time)
                        }
                        .mapValues { (_, notifications) ->
                            notifications.size.toFloat()
                        }

                    // Create chart data with all dates (including zeros for missing dates)
                    val dailyCountData = dates.mapIndexed { index, date ->
                        val dateStr = dateFormat.format(date)
                        index.toFloat() to (dateCountMap[dateStr] ?: 0f)
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
                        model = entryModelOf(*dailyCountData.toTypedArray()),
                        startAxis = rememberStartAxis(
                            itemPlacer = AxisItemPlacer.Vertical.default(maxItemCount = 5),
                            valueFormatter = { value, _ -> "${value.toInt()}" }
                        ),
                        bottomAxis = rememberBottomAxis(
                            valueFormatter = { value, _ ->
                                val index = value.toInt()
                                if (index < dates.size) {
                                    dateFormat.format(dates[index])
                                } else ""
                            },
                            labelRotationDegrees = 270f
                        ),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Toggle Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { 
                        activity.logButtonClick("7_days_filter")
                        selectedDays = 7 
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedDays == 7) 
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface,
                    contentColor = if (selectedDays == 7)
                        MaterialTheme.colorScheme.onPrimary
                    else Primary // Text color when not selected
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 2.dp
                    )
                ) {
                    Text("7 Ngày")
                }
                
                Button(
                    onClick = { 
                        activity.logButtonClick("30_days_filter")
                        selectedDays = 30 
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedDays == 30) 
                            MaterialTheme.colorScheme.primary 
                        else MaterialTheme.colorScheme.surface,
                    contentColor = if (selectedDays == 30)
                        MaterialTheme.colorScheme.onPrimary
                    else Primary // Text color when not selected
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 2.dp
                    )
                ) {
                    Text("30 Ngày")
                }
            }

            // History List
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
                    .height(400.dp)  // Add fixed height for the list
            ) {
                items(historyData.reversed()) { notification ->
                    HistoryItem(notification)
                }
            }
        }
    }
}

@Composable
fun HistoryItem(notification: NotificationHistory) {
    val appName = notification.bank

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = SimpleDateFormat("d/M/yyyy HH:mm", Locale.getDefault())
                        .format(notification.time),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = appName, //notification.bank,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = "${Utils.formatMoney(notification.amount)} đ",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}