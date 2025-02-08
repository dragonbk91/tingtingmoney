package money.tingting

import android.content.Intent
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay  // Add this import
import money.tingting.data.AppDatabase
import money.tingting.service.FptAiService
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource

class LoadingActivity : BaseActivity() {
    private lateinit var db: AppDatabase
    private lateinit var fptService: FptAiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = AppDatabase.getDatabase(this)
        fptService = FptAiService(this)

        setContent {
            TingTingMoneyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var progress by remember { mutableStateOf(0f) }
                    var currentText by remember { mutableStateOf("Đang tải dữ liệu...") }
                    
                    LaunchedEffect(Unit) {
                        preloadVoiceData { current, total, text ->
                            progress = current.toFloat() / total.toFloat()
                            currentText = text
                        }
                    }

                    LoadingScreen(progress, currentText)
                }
            }
        }
    }

    @Composable
    fun LoadingScreen(progress: Float, currentText: String) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "App Logo",
                modifier = Modifier.size(120.dp)
            )
            Text(
                text = "Đang chuẩn bị giọng đọc",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(12.dp)
                        )
                )
            }

            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )

            Text(
                text = currentText,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    private fun logVoiceOperation(type: String, text: String, voice: String, message: String) {
        Log.i("VoiceCache", "[$type] '$text' (voice: $voice) - $message")
    }

    private suspend fun preloadVoiceData(
        onProgress: (current: Int, total: Int, text: String) -> Unit
    ) {
        try {
            val phraseDao = db.phraseDao()
            val phrases = phraseDao.getAllPhrases().first()
            val voiceId = "banmai" // Only use banmai voice for initial loading
            
            // Calculate total tasks (common words + phrases) for single voice
            val totalTasks = Utils.COMMON_WORDS.size + phrases.size
            var currentTask = 0

            // Clean old cache first
            fptService.cleanOldCache(7)

            // Cache common words first
            for (word in Utils.COMMON_WORDS) {
                currentTask++
                onProgress(
                    currentTask, 
                    totalTasks, 
                    "Đang tải âm thanh cơ bản"
                )
                
                try {
                    var trimmedWord = word.trim()
                    val cache = db.voiceCacheDao().getByTextAndVoice(trimmedWord, voiceId).first()
                    if (cache == null) {
                        logVoiceOperation("WORD", trimmedWord, voiceId, "Cache miss - Starting TTS request")
                        fptService.textToSpeech(trimmedWord, voiceId)
                            .onSuccess { path ->
                                logVoiceOperation("WORD", trimmedWord, voiceId, "Successfully cached to: $path")
                            }
                            .onFailure { error ->
                                logVoiceOperation("WORD", trimmedWord, voiceId, "Failed to cache: ${error.message}")
                            }
                        delay(100)
                    }
                } catch (e: Exception) {
                    Log.e("LoadingActivity", "Error caching word $word", e)
                }
            }

            // Then cache phrases
            for (phrase in phrases) {
                currentTask++
                onProgress(
                    currentTask, 
                    totalTasks, 
                    "Đang tải các giọng nói cơ bản"
                )
                
                try {
                    val cache = db.voiceCacheDao().getByTextAndVoice(phrase.content, voiceId).first()
                    if (cache?.localPath == null) {
                        logVoiceOperation("PHRASE", phrase.content, voiceId, "Cache miss - Starting TTS request")
                        fptService.textToSpeech(phrase.content, voiceId)
                            .onSuccess { path ->
                                logVoiceOperation("PHRASE", phrase.content, voiceId, "Successfully cached to: $path")
                            }
                            .onFailure { error ->
                                logVoiceOperation("PHRASE", phrase.content, voiceId, "Failed to cache: ${error.message}")
                            }
                        delay(100)
                    }
                } catch (e: Exception) {
                    Log.e("LoadingActivity", "Error caching phrase: ${phrase.content}", e)
                }
            }

            delay(500)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } catch (e: Exception) {
            Log.e("LoadingActivity", "Error during preloading", e)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}