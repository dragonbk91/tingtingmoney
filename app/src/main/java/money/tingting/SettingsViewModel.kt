package money.tingting

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import money.tingting.data.AppDatabase
import money.tingting.data.PhraseEntity

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val phraseDao = AppDatabase.getDatabase(application).phraseDao()
    private val _phrases = MutableStateFlow<Map<String, List<PhraseEntity>>>(emptyMap())
    val phrases: StateFlow<Map<String, List<PhraseEntity>>> = _phrases.asStateFlow()
    private val sharedPrefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)

    init {
        viewModelScope.launch {
            observePhrases()
        }
    }

    private suspend fun observePhrases() {
        phraseDao.getAllPhrases().collect { phrases ->
            _phrases.value = phrases.groupBy { it.type }
        }
    }

    fun updatePhraseEnabled(type: String, enabled: Boolean) {
        viewModelScope.launch {
            phraseDao.updateTypeEnabled(type, enabled)
        }
    }

    fun addPhrase(type: String, content: String) {
        viewModelScope.launch {
            val order = _phrases.value[type]?.size ?: 0
            phraseDao.insert(PhraseEntity(type = type, content = content, order = order))
        }
    }

    fun deletePhrase(phrase: PhraseEntity) {
        viewModelScope.launch {
            phraseDao.delete(phrase)
        }
    }

    fun getSelectedVoice(): String {
        return sharedPrefs.getString("selected_voice", "banmai") ?: "banmai"
    }

    fun setSelectedVoice(voice: String) {
        sharedPrefs.edit().putString("selected_voice", voice).apply()
    }

    fun getSpeakEnabled(): Boolean {
        return sharedPrefs.getBoolean("speak_enabled", true)
    }

    fun setSpeakEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("speak_enabled", enabled).apply()
    }

    // Add this to SettingsViewModel.kt
    fun getAllPhrases(): List<PhraseEntity> {
        val result = mutableListOf<PhraseEntity>()
        phrases.value.forEach { (_, phrases) ->
            result.addAll(phrases)
        }
        return result
    }
}