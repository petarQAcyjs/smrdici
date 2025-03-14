package com.petar.smrdici.ui.screens.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.petar.smrdici.data.model.Event
import com.petar.smrdici.data.repository.EventRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*

class HomeViewModel(private val context: Context) : ViewModel() {
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
                
                // Учитавамо све догађаје за данас
                firestore.collection("users")
                    .document(userId)
                    .collection("events")
                    .whereGreaterThanOrEqualTo("startTime", Timestamp(startOfDay))
                    .whereLessThanOrEqualTo("startTime", Timestamp(endOfDay))
                    .orderBy("startTime", Query.Direction.ASCENDING)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val events = snapshot.documents.mapNotNull { doc ->
                            try {
                                val event = doc.toObject(Event::class.java)
                                event?.id = doc.id
                                event
                            } catch (e: Exception) {
                                Log.e("HomeViewModel", "Грешка при обради догађаја", e)
                                null
                            }
                        }
                        
                        // Филтрирамо прошле догађаје
                        val currentTime = Calendar.getInstance().timeInMillis / 1000 // Тренутно време у секундама
                        val filteredEvents = events.filter { event ->
                            // Задржавамо догађаје који су у току или у будућности
                            event.endTime?.seconds ?: Long.MAX_VALUE >= currentTime
                        }
                        
                        // Сортирамо догађаје по времену почетка
                        val sortedEvents = filteredEvents.sortedBy { it.startTime?.seconds }
                        
                        _todayEvents.value = sortedEvents
                    }
                    .addOnFailureListener { e ->
                        Log.e("HomeViewModel", "Грешка при учитавању из Firestore-а", e)
                    }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Општа грешка", e)
            }
        }
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
    fun refreshEvents() {
        loadTodayEvents()
    }
    
    // Функција за синхронизацију догађаја
    fun syncEvents() {
        viewModelScope.launch {
            try {
                _syncStatus.value = SyncStatus.Syncing
                
                val success = eventRepository.syncEvents()
                
                if (success) {
                    _syncStatus.value = SyncStatus.Success
                    refreshEvents()
                } else {
                    _syncStatus.value = SyncStatus.Error("Грешка при синхронизацији")
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
}

// Класа за праћење статуса синхронизације
sealed class SyncStatus {
    object Idle : SyncStatus()
    object Syncing : SyncStatus()
    object Success : SyncStatus()
    data class Error(val message: String) : SyncStatus()
} 