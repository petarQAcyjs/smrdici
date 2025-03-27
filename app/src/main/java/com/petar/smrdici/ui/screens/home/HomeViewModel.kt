package com.petar.smrdici.ui.screens.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
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

class HomeViewModel(context: Context) : ViewModel() {
    private val eventRepository = EventRepository(context)
    private val _todayEvents = MutableStateFlow<List<Event>>(emptyList())
    val todayEvents: StateFlow<List<Event>> = _todayEvents
    
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus
    
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    init {
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
                val userId = auth.currentUser?.uid ?: return@launch
                
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
                    .onSuccess { events ->
                        Log.d("HomeViewModel", "Учитано ${events.size} догађаја")
                        
                        // Филтрирамо само будуће догађаје
                        val currentTime = Calendar.getInstance().time
                        val activeEvents = events.filter { event ->
                            val endTime = event.endTime?.toDate() ?: Date(Long.MAX_VALUE)
                            endTime >= currentTime
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
                    }
                    .onFailure { e ->
                        Log.e("HomeViewModel", "Грешка при учитавању догађаја", e)
                    }
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
    
    // Функција за додавање тест догађаја директно у Firestore
    fun addTestEvents() {
        val userId = auth.currentUser?.uid ?: return
        
        // Креирамо данашње тест догађаје
        val calendar = Calendar.getInstance()
        val events = mutableListOf<Event>()
        
        // Подеси време за први догађај (данас у 10:30)
        calendar.set(Calendar.HOUR_OF_DAY, 10)
        calendar.set(Calendar.MINUTE, 30)
        events.add(Event(
            title = "Час пливања Нина",
            startTime = Timestamp(calendar.time),
            endTime = Timestamp(Date(calendar.time.time + 7200000)), // +2 сата
            color = "#4285F4"
        ))
        
        // Подеси време за други догађај (данас у 14:30)
        calendar.set(Calendar.HOUR_OF_DAY, 14)
        calendar.set(Calendar.MINUTE, 30)
        events.add(Event(
            title = "Биоскоп са девојчицама",
            startTime = Timestamp(calendar.time),
            endTime = Timestamp(Date(calendar.time.time + 7200000)), // +2 сата
            color = "#EA4335"
        ))
        
        // Подеси време за трећи догађај (данас у 19:30)
        calendar.set(Calendar.HOUR_OF_DAY, 19)
        calendar.set(Calendar.MINUTE, 30)
        events.add(Event(
            title = "Вечера код баке",
            startTime = Timestamp(calendar.time),
            endTime = Timestamp(Date(calendar.time.time + 5400000)), // +1.5 сата
            color = "#FBBC05"
        ))
        
        // Додајемо догађаје у Firestore
        events.forEach { event ->
            firestore.collection("users")
                .document(userId)
                .collection("events")
                .add(event)
                .addOnSuccessListener { documentReference ->
                    Log.d("HomeViewModel", "Додат тест догађај са ID: ${documentReference.id}")
                }
                .addOnFailureListener { e ->
                    Log.e("HomeViewModel", "Грешка при додавању тест догађаја", e)
                }
        }
        
        // Освежавамо приказ након додавања догађаја
        refreshEvents()
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
                    .onSuccess {
                        _syncStatus.value = SyncStatus.Success
                        refreshEvents()
                    }
                    .onFailure { e ->
                        Log.e("HomeViewModel", "Грешка при синхронизацији", e)
                        _syncStatus.value = SyncStatus.Error(e.message ?: "Грешка при синхронизацији")
                    }
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
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                return HomeViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    // Додајемо функцију за освежавање података
    fun refreshData() {
        viewModelScope.launch {
            _syncStatus.value = SyncStatus.Syncing
            try {
                loadTodayEvents()
                _syncStatus.value = SyncStatus.Success
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.Error(e.message ?: "Error refreshing data")
            }
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