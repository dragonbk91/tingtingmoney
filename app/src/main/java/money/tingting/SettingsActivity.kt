package money.tingting

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import money.tingting.ui.theme.TingTingMoneyTheme
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.TextButton
import androidx.lifecycle.viewmodel.compose.viewModel
import money.tingting.data.PhraseEntity
import androidx.compose.runtime.collectAsState
import android.util.Log
import money.tingting.data.PhraseSettings
import money.tingting.data.PhraseType
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import money.tingting.service.FptAiService
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import androidx.activity.enableEdgeToEdge

class SettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Add this line
        // Initialize TTS when activity starts
        Utils.TextToSpeechHelper.initialize(this) { success ->
            if (!success) {
                Log.e("SettingsActivity", "Failed to initialize TTS")
            }
        }
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
                        val viewModel: SettingsViewModel = viewModel(factory = ViewModelFactory(application))
                        SettingsScreen(viewModel)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Utils.TextToSpeechHelper.shutdown()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current as SettingsActivity
    val scope = rememberCoroutineScope()
    val phrases by viewModel.phrases.collectAsState()
    var showAddDialog by remember { mutableStateOf<String?>(null) }
    var newPhrase by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }

    @Composable
    fun PhraseTypeSection(
        title: String,
        phrases: List<PhraseEntity>,
        onToggleEnabled: (Boolean) -> Unit,
        onAddPhrase: () -> Unit,
        onPlayPhrase: (PhraseEntity) -> Unit,
        onDeletePhrase: (PhraseEntity) -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Switch(
                        checked = phrases.isNotEmpty() && phrases[0].isEnabled,
                        onCheckedChange = onToggleEnabled
                    )
                }

                phrases.forEach { phrase ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = phrase.content,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Row {
                            IconButton(
                                onClick = { 
                                    context.logButtonClick("play_phrase_${phrase.type}")
                                    Utils.TextToSpeechHelper.speak(phrase.content) 
                                }
                            ) {
                                Icon(Icons.Default.PlayArrow, "Play")
                            }
                            IconButton(onClick = { 
                                context.logButtonClick("delete_phrase_${phrase.type}")
                                onDeletePhrase(phrase) 
                            }) {
                                Icon(Icons.Default.Delete, "Delete")
                            }
                        }
                    }
                }

                TextButton(
                    onClick = { 
                        context.logButtonClick("add_phrase_${title}")
                        onAddPhrase()
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.Add, "Add")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Thêm câu mới")
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Cấu hình",
                style = MaterialTheme.typography.titleLarge
            )
            Box(modifier = Modifier.width(48.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Add Voice Selection Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Cài đặt giọng nói",
                        style = MaterialTheme.typography.titleMedium
                    )

                    var expanded by remember { mutableStateOf(false) }
                    var selectedVoice by remember { mutableStateOf(viewModel.getSelectedVoice()) }

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        TextField(
                            readOnly = true,
                            value = Utils.VOICE_OPTIONS[selectedVoice] ?: "",
                            onValueChange = { },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            Utils.VOICE_OPTIONS.forEach { (voiceId, voiceName) ->
                                DropdownMenuItem(
                                    text = { Text(voiceName) },
                                    onClick = {
                                        context.logButtonClick("voice_select_${voiceId}")
                                        if (voiceId != selectedVoice) {
                                            isLoading = true
                                            loadingMessage = "Đang chuyển giọng nói mới..."
                                            expanded = false // Close dropdown immediately
                                            scope.launch {
                                                try {
                                                    val fptService = FptAiService(context)
                                                    // Get all phrases
                                                    var successCount = 0
                                                    val allPhrases = viewModel.getAllPhrases()
                                                    
                                                    // Download common words first
                                                    for (word in Utils.COMMON_WORDS) {
                                                        loadingMessage = ""
                                                        fptService.textToSpeech(word, voiceId)
                                                            .onSuccess { successCount++ }
                                                        delay(100)
                                                    }
                                                    
                                                    // Then download phrases
                                                    for (phrase in allPhrases) {
                                                        loadingMessage = ""
                                                        fptService.textToSpeech(phrase.content, voiceId)
                                                            .onSuccess { successCount++ }
                                                        delay(100)
                                                    }

                                                    if (successCount > 0) {
                                                        selectedVoice = voiceId
                                                        viewModel.setSelectedVoice(voiceId)
                                                        Toast.makeText(context, "Đã chuyển xong giọng nói mới", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "Không thể chuyển giọng nói", Toast.LENGTH_SHORT).show()
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("SettingsActivity", "Error downloading voice", e)
                                                    Toast.makeText(context, "Lỗi chuyển giọng nói", Toast.LENGTH_SHORT).show()
                                                } finally {
                                                    isLoading = false
                                                }
                                            }
                                        } else {
                                            expanded = false
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Customer Interaction Phrases Section
            Text(
                text = "Các câu giao tiếp với khách hàng",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )

            // Phrase Types
            listOf("opening", "busy", "random").forEach { type ->
                val typeTitle = when(type) {
                    "opening" -> "Mở hàng"
                    "busy" -> "Khi đông khách"
                    "random" -> "Ngẫu nhiên"
                    else -> type
                }
                
                val typePhrases = phrases[type] ?: emptyList()
                
                PhraseTypeSection(
                    title = typeTitle,
                    phrases = typePhrases,
                    onToggleEnabled = { enabled: Boolean -> 
                        viewModel.updatePhraseEnabled(type, enabled)
                    },
                    onAddPhrase = { showAddDialog = type },
                    onPlayPhrase = { phrase: PhraseEntity -> 
                        Utils.TextToSpeechHelper.speak(phrase.content) 
                    },
                    onDeletePhrase = { phrase: PhraseEntity -> 
                        viewModel.deletePhrase(phrase) 
                    }
                )
            }
        }
    }

    // Add loading dialog state
    if (isLoading) {
        AlertDialog(
            onDismissRequest = { /* Do nothing to prevent dismiss */ },
            title = { Text("Đang chuyển giọng nói") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = loadingMessage,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = { }  // No buttons needed
        )
    }

    if (showAddDialog != null) {
        AlertDialog(
            onDismissRequest = { 
                if (!isLoading) showAddDialog = null 
            },
            title = { Text("Thêm câu mới") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newPhrase,
                        onValueChange = { newPhrase = it },
                        label = { Text("Nội dung") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    )
                    
                    if (isLoading) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = loadingMessage,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPhrase.length < 3 || newPhrase.length > 100) {
                            Toast.makeText(
                                context,
                                "Nội dung phải từ 3 đến 100 ký tự",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }
                        
                        if (newPhrase.isNotEmpty() && !isLoading) {
                            isLoading = true
                            loadingMessage = "Đang tạo giọng nói..."
                            
                            scope.launch {
                                try {
                                    val fptService = FptAiService(context)
                                    var totalSuccess = 0
                                    
                                    // Cache for all voices
                                    Utils.VOICE_OPTIONS.forEach { (voiceId, voiceName) ->
                                        loadingMessage = "Đang tạo giọng $voiceName..."
                                        
                                        fptService.textToSpeech(newPhrase, voiceId)
                                            .onSuccess { _ ->
                                                totalSuccess++
                                                Log.i("SettingsActivity", "Cached voice $voiceName for phrase: $newPhrase")
                                            }
                                            .onFailure { error ->
                                                Log.e("SettingsActivity", "Failed to cache voice $voiceName: ${error.message}")
                                            }
                                        
                                        delay(100) // Small delay between requests
                                    }

                                    // Only add phrase if at least one voice was cached successfully
                                    if (totalSuccess > 0) {
                                        viewModel.addPhrase(showAddDialog!!, newPhrase)
                                        newPhrase = ""
                                        showAddDialog = null
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Không thể tạo giọng nói",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } catch (e: Exception) {
                                    Log.e("SettingsActivity", "Error caching voices", e)
                                    Toast.makeText(
                                        context,
                                        "Lỗi tạo giọng nói",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    },
                    enabled = newPhrase.isNotEmpty() && !isLoading
                ) {
                    Text("Thêm")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddDialog = null },
                    enabled = !isLoading
                ) {
                    Text("Hủy")
                }
            }
        )
    }
}