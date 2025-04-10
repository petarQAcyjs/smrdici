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
    
    init {
        // Учитавамо догађаје само када је HomeViewModel активан
        loadTodayEvents()
        
        // Периодично освежавање да би се ажурирали догађаји који су прошли
        viewModelScope.launch {
            while (true) {
                delay(60000) // Освежавање сваког минута
                refreshEvents()
            }
        }
    }
    
    private fun loadTodayEvents() {
        viewModelScope.launch {
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
                    .fold(
                        onSuccess = { events ->
                            Log.d("HomeViewModel", "Учитано ${events.size} догађаја")
                            
                            // Филтрирамо само будуће догађаје
                            val currentTime = Calendar.getInstance().time
                            val activeEvents = events.filter { event ->
                                val eventEndTime = event.endTime?.toDate() ?: Date(Long.MAX_VALUE)
                                eventEndTime >= currentTime
                            }
                            
                            Log.d("HomeViewModel", """
                                Филтрирање догађаја:
                                - Укупно догађаја: ${events.size}
                                - Активних догађаја: ${activeEvents.size}
                                - Тренутно време: ${formatDate(currentTime)}
                            """.trimIndent())
                            
                            activeEvents.forEach { event ->
                                Log.d("HomeViewModel", """
                                    Активан догађај:
                                    - Наслов: ${event.title}
                                    - Почетак: ${formatDate(event.startTime?.toDate())}
                                    - Крај: ${formatDate(event.endTime?.toDate())}
                                """.trimIndent())
                            }
                            
                            _todayEvents.value = activeEvents
                        },
                        onFailure = { e ->
                            Log.e("HomeViewModel", "Грешка при учитавању догађаја", e)
                        }
                    )
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Грешка при учитавању догађаја", e)
            }
        }
    }
    
    private fun formatDate(date: Date?): String {
        return date?.let { 
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(it) 
        } ?: "null"
    }
    
    // Функција за ручно освежавање
    private fun refreshEvents() {
        loadTodayEvents()
    }
    
    // Функција за синхронизацију догађаја
    fun syncEvents() {
        viewModelScope.launch {
            try {
                _syncStatus.value = SyncStatus.Syncing
                
                eventRepository.syncEvents()
                    .fold(
                        onSuccess = {
                            _syncStatus.value = SyncStatus.Success
                            refreshEvents()
                        },
                        onFailure = { e ->
                            Log.e("HomeViewModel", "Грешка при синхронизацији", e)
                            _syncStatus.value = SyncStatus.Error(e.message ?: "Грешка при синхронизацији")
                        }
                    )
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Грешка при синхронизацији", e)
                _syncStatus.value = SyncStatus.Error(e.message ?: "Непозната грешка")
            } finally {
                // Враћамо статус на Idle након 3 секунде
                delay(3000)
                _syncStatus.value = SyncStatus.Idle
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