package money.tingting.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.Properties
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.Calendar
import money.tingting.data.AppDatabase
import money.tingting.data.VoiceCache
import kotlinx.coroutines.flow.first

class FptAiService(private val context: Context) {
    private val client = OkHttpClient()
    private val db = AppDatabase.getDatabase(context)
    private val voiceCacheDao = db.voiceCacheDao()
    private lateinit var apiKey: String
    private lateinit var baseUrl: String
    private lateinit var cdnAuthKey: String
    private lateinit var cdnBaseUrl: String  // Changed from cdnUploadUrl

    init {
        loadConfig()
    }

    private fun loadConfig() {
        try {
            val properties = Properties()
            context.assets.open("config.properties").use { 
                properties.load(it)
            }
            apiKey = properties.getProperty("fptai.tts.api_key")
            baseUrl = properties.getProperty("fptai.tts.url")
            cdnAuthKey = properties.getProperty("cdn.auth.key")
            cdnBaseUrl = properties.getProperty("cdn.upload.url")
            // Remove the substringBeforeLast call as we want the full URL
        } catch (e: IOException) {
            Log.e("FptAiService", "Error loading config: ${e.message}")
            throw e
        }
    }

    private fun getHash(text: String, voice: String): String {
        val combined = "$voice$text"
        val md = java.security.MessageDigest.getInstance("MD5")
        val digest = md.digest(combined.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    // Change from private to public
    fun getCdnUrl(text: String, voice: String): String {
        return if (cdnBaseUrl.endsWith("/")) {
            "${cdnBaseUrl}${getHash(text, voice)}.mp3"
        } else {
            "$cdnBaseUrl/${getHash(text, voice)}.mp3"
        }
    }

    data class TTSResponse(
        val async: String?,
        val error: Int,
        val message: String,
        val request_id: String
    )

    private suspend fun tryDownloadFromCdn(url: String): File? {
        return try {
            val request = Request.Builder()
                .url(url)
                .addHeader("X-Custom-Auth-Key", cdnAuthKey)
                .get()  // Explicitly use GET method
                .build()

            val response = client.newCall(request).execute()
            Log.i("FptAiService", "Trying to download from CDN: $url")
            
            if (response.isSuccessful) {
                val tempFile = File.createTempFile("tts_", ".mp3", context.cacheDir)
                response.body?.bytes()?.let { bytes ->
                    tempFile.writeBytes(bytes)
                    Log.i("FptAiService", "Successfully downloaded from CDN: $url")
                    tempFile
                } ?: run {
                    Log.e("FptAiService", "Empty response body from CDN")
                    null
                }
            } else {
                Log.e("FptAiService", "CDN download failed with code: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e("FptAiService", "Error downloading from CDN: ${e.message}")
            null
        }
    }

    suspend fun textToSpeech(text: String, voice: String = "banmai", speed: Int = 0): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val cache = voiceCacheDao.getByTextAndVoice(text, voice).first()
                Log.d("FptAiService", "Cache data for text: '$text', voice: '$voice':")
                if (cache != null) {
                    if (cache.localPath != null && File(cache.localPath).exists()) {
                        Log.d("FptAiService", "Using cached local file: ${cache.localPath}")
                        Result.success(cache.localPath)
                    }
                    // New branch: if cache has fptLink, attempt downloading via fptLink.
                    else if (cache.fptLink != null) {
                        try {
                            Log.d("FptAiService", "Downloading from cached FPT link: ${cache.fptLink}")
                            val audioFile = downloadAudioFile(cache.fptLink)
                            // --- Added upload to CDN if download from FPT is successful
                            val cdnUrl = cache.cdnLink ?: getCdnUrl(text, voice)
                            Log.d("FptAiService", "Uploading downloaded FPT file to CDN at: $cdnUrl")
                            uploadToCdn(audioFile, cdnUrl)
                            voiceCacheDao.insert(cache.copy(localPath = audioFile.absolutePath, cdnLink = cdnUrl))
                            Result.success(audioFile.absolutePath)
                        } catch (e: Exception) {
                            Log.e("FptAiService", "Download from cached FPT link failed: ${e.message}")
                            Result.failure(e)
                        }
                    }
                    else {
                        // No valid local file or fptLink, try CDN first.
                        val cdnUrl = cache.cdnLink ?: getCdnUrl(text, voice)
                        Log.d("FptAiService", "Trying to download from CDN: $cdnUrl for text: $text and voice: $voice")
                        tryDownloadFromCdn(cdnUrl)?.let { file ->
                            voiceCacheDao.insert(cache.copy(localPath = file.absolutePath, cdnLink = cdnUrl))
                            Result.success(file.absolutePath)
                        } ?: handleFPTTtsRequest(text, voice, speed)
                    }
                } else {
                    val cdnUrl = getCdnUrl(text, voice)
                    Log.d("FptAiService", "Trying to download from CDN: $cdnUrl for text: $text and voice: $voice")
                    tryDownloadFromCdn(cdnUrl)?.let { file ->
                        val newCache = VoiceCache(
                            text = text,
                            voice = voice,
                            localPath = file.absolutePath,
                            cdnLink = cdnUrl,
                            fptLink = null
                        )
                        voiceCacheDao.insert(newCache)
                        Result.success(file.absolutePath)
                    } ?: handleFPTTtsRequest(text, voice, speed)
                }
            } catch (e: Exception) {
                Log.e("FptAiService", "Error in text-to-speech process: ${e.message}")
                Result.failure(e)
            }
        }
    }

    private suspend fun handleFPTTtsRequest(text: String, voice: String, speed: Int): Result<String> {
        Log.d("FptAiService", "handleFPTTtsRequest called with text: '$text', voice: '$voice', speed: $speed")
        // Add space to short words
        var textData = text
        if (textData.length > 1 && textData.length < 3) {
            textData = "$text "
            Log.d("FptAiService", "Text modified to add space for short word: '$textData'")
        }
        val requestBody = textData.toRequestBody("text/plain".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(baseUrl)
            .post(requestBody)
            .addHeader("api-key", apiKey)
            .addHeader("speed", speed.toString())
            .addHeader("voice", voice)
            .build()

        Log.d("FptAiService", "Sending TTS API request to: $baseUrl")
        val response = client.newCall(request).execute()
        Log.d("FptAiService", "Received response with code: ${response.code}")
        
        return if (response.isSuccessful) {
            val responseStr = response.body?.string() ?: ""
            Log.d("FptAiService", "Response body: $responseStr")
            val ttsResponse = parseResponse(responseStr)
            Log.d("FptAiService", "Parsed TTS response: error=${ttsResponse.error}, async=${ttsResponse.async}, message=${ttsResponse.message}")
            
            if (ttsResponse.error == 0 && ttsResponse.async != null) {
                Log.d("FptAiService", "TTS API accepted request. Waiting 2 seconds for audio file generation...")
                delay(2000)
                Log.d("FptAiService", "Attempting to download audio file from: ${ttsResponse.async}")
                try {
                    val audioFile = downloadAudioFile(ttsResponse.async)
                    val cdnUrl = getCdnUrl(text, voice)
                    Log.d("FptAiService", "Uploading audio file to CDN at: $cdnUrl")
                    uploadToCdn(audioFile, cdnUrl)
                    
                    val voiceCache = VoiceCache(
                        text = text,
                        voice = voice,
                        localPath = audioFile.absolutePath,
                        fptLink = ttsResponse.async,
                        cdnLink = cdnUrl
                    )
                    voiceCacheDao.insert(voiceCache)
                    Log.d("FptAiService", "Audio file processed and cached at: ${audioFile.absolutePath}")
                    
                    Result.success(audioFile.absolutePath)
                } catch (e: Exception) {
                    Log.e("FptAiService", "Download from FPT link failed after retries: ${e.message}")
                    // Cache the fptLink for future attempts.
                    val voiceCache = VoiceCache(
                        text = text,
                        voice = voice,
                        localPath = null,
                        fptLink = ttsResponse.async,
                        cdnLink = getCdnUrl(text, voice)
                    )
                    voiceCacheDao.insert(voiceCache)
                    Result.failure(IOException("Downloading audio file from FPT link failed after max retries; fptLink cached for future use"))
                }
            } else {
                Log.d("FptAiService", "TTS failed with message: ${ttsResponse.message}")
                Result.failure(IOException("TTS failed: ${ttsResponse.message}"))
            }
        } else {
            Log.d("FptAiService", "TTS API call failed with HTTP code: ${response.code}")
            Result.failure(IOException("API call failed with code: ${response.code}"))
        }
    }

    private fun parseResponse(responseStr: String): TTSResponse {
        return try {
            val json = JSONObject(responseStr)
            TTSResponse(
                async = json.optString("async", null),
                error = json.optInt("error", -1),
                message = json.optString("message", ""),
                request_id = json.optString("request_id", "")
            )
        } catch (e: Exception) {
            Log.e("FptAiService", "Error parsing response: ${e.message}")
            throw e
        }
    }

    private suspend fun downloadAudioFile(url: String, maxRetries: Int = 45): File {
        return withContext(Dispatchers.IO) {
            var lastException: Exception? = null
            
            for (attempt in 1..maxRetries) {
                try {
                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    
                    if (response.isSuccessful) {
                        val tempFile = File.createTempFile("tts_", ".mp3", context.cacheDir)
                        response.body?.bytes()?.let { bytes ->
                            tempFile.writeBytes(bytes)
                            return@withContext tempFile
                        }
                    }
                    
                    // If we get here, either response wasn't successful or body was null
                    Log.w("FptAiService", "Download attempt $attempt failed, response code: ${response.code}")
                } catch (e: Exception) {
                    lastException = e
                    Log.e("FptAiService", "Download attempt $attempt failed: ${e.message}")
                }
                
                if (attempt < maxRetries) {
                    Log.i("FptAiService", "Waiting 1 second before retry...")
                    delay(1000) // Wait 1 second before retrying
                }
            }
            
            throw lastException ?: IOException("Failed to download audio file after $maxRetries attempts")
        }
    }

    private suspend fun uploadToCdn(audioFile: File, url: String) {
        return withContext(Dispatchers.IO) {
            Log.i("FptAiService", "Starting uploadToCdn for file: ${audioFile.absolutePath} to URL: $url")
            try {
                val requestBody = audioFile.readBytes().toRequestBody("audio/mpeg".toMediaTypeOrNull())
                
                val request = Request.Builder()
                    .url(url)  // Use dynamic URL
                    .put(requestBody)
                    .addHeader("X-Custom-Auth-Key", cdnAuthKey)
                    .build()

                Log.i("FptAiService", "Sending CDN upload request")
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    Log.e("FptAiService", "CDN upload failed with HTTP code: ${response.code}")
                    throw IOException("CDN upload failed with code: ${response.code}")
                }
                
                Log.i("FptAiService", "CDN upload succeeded for file: ${audioFile.absolutePath}")
            } catch (e: Exception) {
                Log.e("FptAiService", "Error uploading to CDN: ${e.message}")
                throw e
            }
        }
    }

    // Add method to clean old cache entries
    suspend fun cleanOldCache(daysToKeep: Int = 7) {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -daysToKeep)
        voiceCacheDao.deleteOlderThan(calendar.time)
    }
}
