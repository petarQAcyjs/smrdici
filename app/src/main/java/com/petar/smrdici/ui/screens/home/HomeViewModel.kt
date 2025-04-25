package com.petar.smrdici.ui.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.petar.smrdici.data.model.Event
import com.petar.smrdici.data.repository.EventRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HomeViewModel() : ViewModel() {
    // Лења иницијализација EventRepository
    private val eventRepository by lazy { 
        EventRepository(FirebaseFirestore.getInstance(), FirebaseAuth.getInstance()) 
    }
    
    private val _todayEvents = MutableStateFlow<List<Event>>(emptyList())
    val todayEvents: StateFlow<List<Event>> = _todayEvents
    
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus
    
    private val auth = FirebaseAuth.getInstance()
    
    // Застава за спречавање вишеструких истовремених учитавања
    private var isLoadingEvents = false
    
    init {
        // Учитавамо догађаје само када је HomeViewModel активан
        loadTodayEvents()
        
        // УКЛАЊАМО БЕСКОНАЧНУ ПЕТЉУ ЗА ОСВЕЖАВАЊЕ
        // viewModelScope.launch {
        //     while (true) {
        //         delay(60000)
        //         refreshEvents()
        //     }
        // }
    }
    
    private fun loadTodayEvents() {
        // Спречавамо вишеструка паралелна учитавања
        if (isLoadingEvents) return
        
        viewModelScope.launch {
            isLoadingEvents = true
            try {
                Log.d("HomeViewModel", "\n=== УЧИТАВАЊЕ ДАНАШЊИХ ДОГАЂАЈА ===")
                // Проверавамо да ли је корисник пријављен
                if (auth.currentUser?.uid == null) {
                    Log.d("HomeViewModel", "Корисник није пријављен, прекидам учитавање догађаја")
                    return@launch
                }
                
                // Постављамо временски опсег за данас (од поноћи до 23:59:59)
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val startOfDay = calendar.time
                
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val endOfDay = calendar.time
                
                Log.d("HomeViewModel", """
                    Тражим догађаје за данас:
                    - Почетак дана: ${formatDate(startOfDay)}
                    - Крај дана: ${formatDate(endOfDay)}
                """.trimIndent())
                
                // Учитавамо све догађаје за данас
                eventRepository.getEvents(startOfDay, endOfDay)
                    .collect { events ->
                        Log.d("HomeViewModel", "Учитано ${events.size} догађаја")
                        
                        // Филтрирамо само будуће догађаје
                        val currentTime = Calendar.getInstance().time
                        val activeEvents = events.filter { event: Event ->
                            val eventEndTime = event.endTime?.toDate() ?: Date(Long.MAX_VALUE)
                            eventEndTime >= currentTime
                        }
                        
                        Log.d("HomeViewModel", """
                            Филтрирање догађаја:
                            - Укупно догађаја: ${events.size}
                            - Активних догађаја: ${activeEvents.size}
                            - Тренутно време: ${formatDate(currentTime)}
                        """.trimIndent())
                        
                        // Обавезно проверавамо да ли је листа заиста различита пре ажурирања
                        if (_todayEvents.value != activeEvents) {
                            _todayEvents.value = activeEvents
                        }
                    }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Грешка при учитавању догађаја", e)
            } finally {
                isLoadingEvents = false
            }
        }
    }
    
    private fun formatDate(date: Date?): String {
        return date?.let { 
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(it) 
        } ?: "null"
    }
    
    // Функција за синхронизацију догађаја
    fun syncEvents() {
        if (isLoadingEvents) {
            Log.d("HomeViewModel", "Синхронизација у току, нећу поново покренути")
            return
        }
        
        viewModelScope.launch {
            try {
                Log.d("HomeViewModel", "Почињем синхронизацију догађаја...")
                _syncStatus.value = SyncStatus.Syncing
                isLoadingEvents = true
                
                // Прво освежимо догађаје и поставимо стање синхронизације
                loadTodayEvents()
                
                // Затим покушавамо синхронизацију
                Log.d("HomeViewModel", "Покрећем синхронизацију са сервером...")
                eventRepository.syncEvents()
                    .onSuccess {
                        Log.d("HomeViewModel", "Синхронизација успешна!")
                        _syncStatus.value = SyncStatus.Success
                        // Поново учитавамо догађаје након успешне синхронизације
                        loadTodayEvents()
                    }
                    .onFailure { e ->
                        Log.e("HomeViewModel", "Грешка при синхронизацији", e)
                        _syncStatus.value = SyncStatus.Error(e.message ?: "Грешка при синхронизацији")
                    }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Грешка при синхронизацији", e)
                _syncStatus.value = SyncStatus.Error(e.message ?: "Непозната грешка")
            } finally {
                isLoadingEvents = false
                // Враћамо статус на Idle након кратког времена
                delay(1000)
                _syncStatus.value = SyncStatus.Idle
                Log.d("HomeViewModel", "Синхронизација завршена!")
            }
        }
    }
    
    // Додајемо Factory класу за креирање HomeViewModel са Context параметром
    class Factory() : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                return HomeViewModel() as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

// Класа за праћење статуса синхронизације
sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object Syncing : SyncStatus()
    data object Success : SyncStatus()
    data class Error(val message: String) : SyncStatus()
} 