package money.tingting

import java.text.DecimalFormat;
import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import money.tingting.data.AppDatabase
import money.tingting.data.PhraseDao
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import money.tingting.service.FptAiService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Utils {
    object NumberToWordsConverter {
        private val teens = arrayOf(
            "mười",
            "mười một",
            "mười hai",
            "mười ba",
            "mười bốn",
            "mười lăm",
            "mười sáu",
            "mười bảy",
            "mười tám",
            "mười chín"
        )
        private val tens = arrayOf(
            "",
            "mười",
            "hai mươi",
            "ba mươi",
            "bốn mươi",
            "năm mươi",
            "sáu mươi",
            "bảy mươi",
            "tám mươi",
            "chín mươi"
        )
        private val thousands = arrayOf("", "nghìn", "triệu", "tỷ")
        private val units =
            arrayOf("", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín")

        fun convert(j: Long): String {
            val str: String
            val str2: String
            val str3: String
            if (j == 0L) {
                return "không"
            }
            j.toString()
            val format = DecimalFormat("000000000000").format(j)
            val parseInt = format.substring(0, 3).toInt()
            val parseInt2 = format.substring(3, 6).toInt()
            val parseInt3 = format.substring(6, 9).toInt()
            val parseInt4 = format.substring(9, 12).toInt()
            str = if (parseInt == 0) {
                ""
            } else {
                convertLessThanOneThousand(parseInt, 0) + " tỷ "
            }
            str2 = if (parseInt2 == 0) {
                ""
            } else {
                convertLessThanOneThousand(parseInt2, parseInt) + " triệu "
            }
            str3 = if (parseInt3 == 0) {
                ""
            } else if (parseInt3 == 1) {
                "một nghìn "
            } else {
                convertLessThanOneThousand(parseInt3, parseInt2) + " nghìn "
            }
            return (str + str2 + str3 + convertLessThanOneThousand(parseInt4, parseInt3)).replace(
                "^\\s+".toRegex(),
                ""
            ).replace("\\b\\s{2,}\\b".toRegex(), " ").trim { it <= ' ' }
        }

        private fun convertLessThanOneThousand(i: Int, pre: Int): String {
            val khong = if (pre == 0) "" else " không trăm "
            val i3 = i % 100
            
            // Handle special case when i3 is 0
            if (i3 == 0) {
                val i2 = i / 100
                return if (i2 == 0) "" else units[i2] + " trăm"
            }
            
            // Handle teens (10-19)
            if (i3 < 20) {
                if (i3 < 10) {
                    val str = units[i3]
                    val i2 = i / 100
                    return if (i2 == 0) {
                        if (khong == "") str else khong + " lẻ " + str
                    } else {
                        units[i2] + " trăm lẻ " + str
                    }
                }
                val str = teens[i3 - 10]
                val i2 = i / 100
                return if (i2 == 0) khong+str else units[i2] + " trăm " + str
            }
            
            // Handle regular cases (20-99)
            val units_value = units[i3 % 10]
            val tens_index = (i3 / 10) % 10
            val str = if (i3 % 10 == 0) tens[tens_index] else tens[tens_index] + " " + units_value
            val i2 = i / 100
            
            return if (i2 == 0) khong+str else units[i2] + " trăm " + str
        }

        fun convertNumberToWords(value: Long): String {
            var number = value
            if (number == 0L) {
                return "không"
            }
            var str = ""
            var i = 0
            do {
                val remainder = number % 1000
                if (remainder != 0L) {
                    str = convertLessThanOneThousand2(remainder) + thousands[i] + " " + str
                }
                i++
                number /= 1000
            } while (number > 0)
            return str.trim { it <= ' ' }
        }

        private fun convertLessThanOneThousand2(j: Long): String {
            val j2: Long
            val str: String
            val j3 = j % 100
            if (j3 < 10) {
                val str2 = units[(j % 10).toInt()]
                val j4 = j / 10
                str = tens[(j4 % 10).toInt()] + " " + str2
                j2 = j4 / 10
            } else if (j3 < 20) {
                val strArr = teens
                val str3 = strArr[(j % 10).toInt()]
                val j5 = j / 10
                str = strArr[(j5 % 10).toInt()] + " " + str3
                j2 = j5 / 10
            } else {
                val str4 = units[(j % 10).toInt()]
                val j6 = j / 10
                str = tens[(j6 % 10).toInt()] + " " + str4
                j2 = j6 / 10
            }
            if (j2 == 0L) {
                return str
            }
            return units[j2.toInt()] + " trăm " + str
        }

        // Add this helper function to split the number text into words
        fun splitIntoWords(text: String): List<String> {
            return text.split(" ").map { word -> "$word" }
        }

        // Modify convert function to return List<String> instead of String
        fun convertToWordsList(j: Long): List<String> {
            if (j == 0L) {
                return listOf("không")
            }
            return splitIntoWords(convert(j))
        }
    }

    object TextToSpeechHelper {
        private var fptService: FptAiService? = null
        private var isInitialized = false
        // Add a mutex to queue speech requests
        private val speechMutex = Mutex()

        fun initialize(context: Context, onInitListener: (Boolean) -> Unit) {
            try {
                fptService = FptAiService(context)
                isInitialized = true
                onInitListener(true)
            } catch (e: Exception) {
                Log.e("TingTingMoney", "Failed to initialize FPT TTS: ${e.message}")
                isInitialized = false
                onInitListener(false)
            }
        }

        // Change to public and add ting-ting sound
        // Modified speakSequentially to queue new notifications if one is in progress
        suspend fun speakSequentially(words: List<String>) {
            speechMutex.withLock {
                Log.d("TingTingMoney", "Starting speakSequentially with words: $words")
                if (!isInitialized || fptService == null) {
                    Log.w("TingTingMoney", "TTS not initialized, attempting to initialize...")
                    val context = TingTingApp.instance?.applicationContext
                    if (context != null) {
                        initialize(context) { success ->
                            if (!success) {
                                Log.e("TingTingMoney", "Failed to initialize TTS")
                                return@initialize
                            }
                        }
                    } else {
                        Log.e("TingTingMoney", "Context not available for TTS initialization")
                        return
                    }
                    
                    if (!isInitialized || fptService == null) {
                        Log.e("TingTingMoney", "TTS initialization failed")
                        return
                    }
                }
        
                val selectedVoice = TingTingApp.instance?.getSharedPreferences("settings", Context.MODE_PRIVATE)
                    ?.getString("selected_voice", "banmai") ?: "banmai"
                Log.d("TingTingMoney", "Using voice: $selectedVoice")
        
                suspendCoroutine<Unit> { continuation ->
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            Log.d("TingTingMoney", "Converting words to speech")
                            // First convert all words to audio paths
                            val audioPaths = mutableListOf<String>()
                            for (word in words) {
                                try {
                                    val result = fptService!!.textToSpeech(word.trim(), selectedVoice)
                                    result.fold(
                                        onSuccess = { audioPath ->
                                            Log.d("TingTingMoney", "Got audio path: $audioPath for word: $word")
                                            audioPaths.add(audioPath)
                                        },
                                        onFailure = { error ->
                                            Log.e("TingTingMoney", "TTS failed for word '$word': ${error.message}")
                                        }
                                    )
                                } catch (e: Exception) {
                                    Log.e("TingTingMoney", "Error in TTS for word '$word': ${e.message}")
                                    e.printStackTrace()
                                }
                            }
        
                            // Then play ting-ting sound
                            Log.d("TingTingMoney", "Playing ting-ting sound")
                            val context = TingTingApp.instance?.applicationContext
                            context?.let {
                                val mediaPlayer = MediaPlayer.create(it, R.raw.tingting)
                                suspendCoroutine<Unit> { audioCompletion ->
                                    mediaPlayer.setOnCompletionListener { mp ->
                                        Log.d("TingTingMoney", "Ting-ting sound completed")
                                        mp.release()
                                        audioCompletion.resume(Unit)
                                    }
                                    mediaPlayer.start()
                                }
                            }
        
                            // Finally play all converted words sequentially
                            for (audioPath in audioPaths) {
                                suspendCoroutine<Unit> { audioCompletion ->
                                    val mediaPlayer = MediaPlayer()
                                    mediaPlayer.setDataSource(audioPath)
                                    mediaPlayer.prepare()
                                    mediaPlayer.setOnCompletionListener { mp ->
                                        Log.d("TingTingMoney", "Finished playing audio: $audioPath")
                                        mp.release()
                                        audioCompletion.resume(Unit)
                                    }
                                    mediaPlayer.start()
                                }
                            }
                            Log.d("TingTingMoney", "Completed playing all audio")
                        } catch (e: Exception) {
                            Log.e("TingTingMoney", "Error in speakSequentially: ${e.message}")
                            e.printStackTrace()
                        }
                        continuation.resume(Unit)
                    }
                }
                Log.d("TingTingMoney", "Exiting speakSequentially")
            }
        }

        // Modify speak function to handle number conversion
        fun speak(text: String, isNumber: Boolean = false, onComplete: (() -> Unit)? = null) {
            if (isNumber) {
                try {
                    val number = text.replace(",", "").toLong()
                    val words = NumberToWordsConverter.convertToWordsList(number)
                    CoroutineScope(Dispatchers.IO).launch {
                        speakSequentially(words)
                        onComplete?.invoke()
                    }
                } catch (e: Exception) {
                    Log.e("TingTingMoney", "Error converting number: ${e.message}")
                    speak(text, false, onComplete) // Fallback to normal speech
                }
            } else {
                // Original speak functionality
                if (!isInitialized || fptService == null) {
                    Log.w("TingTingMoney", "FPT TTS not initialized, reinitializing...")
                    val context = TingTingApp.instance?.applicationContext
                    if (context != null) {
                        initialize(context) { success ->
                            if (success) {
                                speak(text)
                            } else {
                                Log.e("TingTingMoney", "Failed to reinitialize FPT TTS")
                            }
                        }
                    } else {
                        Log.e("TingTingMoney", "No context available for TTS initialization")
                    }
                    return
                }

                // Get selected voice from preferences
                val selectedVoice = TingTingApp.instance?.getSharedPreferences("settings", Context.MODE_PRIVATE)
                    ?.getString("selected_voice", "banmai") ?: "banmai"

                // Launch coroutine to handle TTS with selected voice
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val result = fptService!!.textToSpeech(text, selectedVoice)
                        result.fold(
                            onSuccess = { audioPath: String ->  // Add explicit type
                                suspendCoroutine<Unit> { continuation ->
                                    val mediaPlayer = MediaPlayer()
                                    mediaPlayer.setDataSource(audioPath)
                                    mediaPlayer.prepare()
                                    mediaPlayer.setOnCompletionListener { mp ->
                                        mp.release()
                                        continuation.resume(Unit)
                                        onComplete?.invoke()
                                    }
                                    mediaPlayer.start()
                                }
                            },
                            onFailure = { error: Throwable ->  // Add explicit type
                                Log.e("TingTingMoney", "TTS failed: ${error.message}")
                                onComplete?.invoke()
                            }
                        )
                    } catch (e: Exception) {
                        Log.e("TingTingMoney", "Error in TTS: ${e.message}")
                        onComplete?.invoke()
                    }
                }
            }
        }

        private fun playAudio(path: String) {
            try {
                val mediaPlayer = MediaPlayer()
                mediaPlayer.setDataSource(path)
                mediaPlayer.prepare()
                mediaPlayer.setOnCompletionListener { mp ->  // Change 'it' to 'mp'
                    mp.release()
                }
                mediaPlayer.start()
            } catch (e: Exception) {
                Log.e("TingTingMoney", "Error playing audio: ${e.message}")
            }
        }

        fun shutdown() {
            fptService = null
            isInitialized = false
        }
    }

    object VoiceScriptCustomizer {
        private var lastCallTime: Long = 0
        private var lastCallDate: String = ""
        private val random = Random()
        private lateinit var phraseDao: PhraseDao
        
        fun initialize(db: AppDatabase) {
            phraseDao = db.phraseDao()
            // Initialize lastCallDate from SharedPreferences
            val prefs = TingTingApp.instance?.getSharedPreferences("voice_customizer", Context.MODE_PRIVATE)
            lastCallDate = prefs?.getString("last_call_date", "") ?: ""
        }

        fun customize(): String = runBlocking {
            val currentTime = System.currentTimeMillis()
            val currentDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            
            // Get enabled phrases
            val openingPhrases = phraseDao.getPhrasesByType("opening").first()
                .filter { phrase -> phrase.isEnabled }
                .map { phrase -> phrase.content }
            val randomPhrases = phraseDao.getPhrasesByType("random").first()
                .filter { phrase -> phrase.isEnabled }
                .map { phrase -> phrase.content }
            val busyPhrases = phraseDao.getPhrasesByType("busy").first()
                .filter { phrase -> phrase.isEnabled }
                .map { phrase -> phrase.content }
            
            if (currentDate != lastCallDate) {
                lastCallDate = currentDate
                // Save lastCallDate to SharedPreferences
                TingTingApp.instance?.getSharedPreferences("voice_customizer", Context.MODE_PRIVATE)
                    ?.edit()?.putString("last_call_date", lastCallDate)?.apply()
                    
                lastCallTime = currentTime
                return@runBlocking if (openingPhrases.isNotEmpty()) {
                    "${openingPhrases.random()}"
                } else {
                    ""
                }
            }

            if (currentTime - lastCallTime < 2 * 60 * 1000) {
                lastCallTime = currentTime
                return@runBlocking if (busyPhrases.isNotEmpty()) {
                    "${busyPhrases.random()}"
                } else {
                    ""
                }
            }

            lastCallTime = currentTime
            return@runBlocking if (randomPhrases.isNotEmpty()) {
                "${randomPhrases.random()}"
            } else {
                ""
            }
        }
    }

    companion object {
        fun parseIncomeMoney(content: String): Long {
            try {
            // Updated regex: accounts for both "(+)" and "+" with an optional space before the number (which may include commas)
            val regex = """(?:\(\+\)|\+)\s*([0-9,]+)""".toRegex()
            val matchResult = regex.find(content) ?: return 0

            val numberStr = matchResult.groupValues[1]
            return numberStr.replace(",", "").toLong()
            } catch (e: Exception) {
            return 0
            }
        }

        fun formatMoney(amount: Long): String {
            return String.format("%,d", amount)
        }

        val VOICE_OPTIONS = mapOf(
            "banmai" to "Ban Mai (nữ miền bắc)",
            "linhsan" to "Linh San (nữ miền nam)", 
            "ngoclam" to "Ngọc Lam (nữ miền trung)"
        )

        val COMMON_WORDS = listOf(
            "mười nghìn đồng", 
            "mười lăm nghìn đồng", 
            "hai mươi nghìn đồng", 
            "hai mươi lăm nghìn đồng", 
            "ba mươi nghìn đồng", 
            "ba mươi lăm nghìn đồng", 
            "bốn mươi nghìn đồng", 
            "bốn mươi lăm nghìn đồng", 
            "năm mươi nghìn đồng", 
            "năm mươi lăm nghìn đồng",
            "một trăm nghìn đồng"
        )
    }
}