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
import kotlinx.coroutines.Job

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
    
    // Кеш за учитане догађаје
    private val loadedEventsCache = mutableMapOf<String, List<Event>>()
    
    // Застава за спречавање вишеструких истовремених учитавања
    private var isLoadingEvents = false
    
    // Job за праћење текућег учитавања
    private var currentLoadJob: Job? = null
    
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
        
        // Отказујемо претходни посао ако постоји
        currentLoadJob?.cancel()
        
        viewModelScope.launch {
            isLoadingEvents = true
            try {
                Log.d("HomeViewModel", "\n=== УЧИТАВАЊЕ ДАНАШЊИХ ДОГАЂАЈА ===")
                // Проверавамо да ли је корисник пријављен
                if (auth.currentUser?.uid == null) {
                    Log.d("HomeViewModel", "Корисник није пријављен, прекидам учитавање догађаја")
                    return@launch
                }
                
                // Постављамо временски опсег за данас
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
                
                // Проверавамо кеш
                val cacheKey = "${formatDate(startOfDay)}_${formatDate(endOfDay)}"
                if (loadedEventsCache.containsKey(cacheKey)) {
                    Log.d("HomeViewModel", "Користим кеширане догађаје")
                    val cachedEvents = loadedEventsCache[cacheKey]!!
                    updateTodayEvents(cachedEvents)
                    return@launch
                }
                
                // Учитавамо све догађаје за данас
                eventRepository.getEvents(startOfDay, endOfDay)
                    .collect { events ->
                        Log.d("HomeViewModel", "Учитано ${events.size} догађаја")
                        
                        // Кеширамо учитане догађаје
                        loadedEventsCache[cacheKey] = events
                        
                        // Филтрирамо и приказујемо само данашње догађаје
                        updateTodayEvents(events)
                    }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    Log.d("HomeViewModel", "Учитавање догађаја отказано")
                } else {
                    Log.e("HomeViewModel", "Грешка при учитавању догађаја", e)
                }
            } finally {
                isLoadingEvents = false
            }
        }.also { currentLoadJob = it }
    }
    
    private fun updateTodayEvents(allEvents: List<Event>) {
        val currentTime = Calendar.getInstance().time
        
        // Постављамо почетак и крај дана
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.time
        
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfDay = calendar.time
        
        val todayEvents = allEvents.filter { event ->
            val eventDate = event.startTime?.toDate() ?: return@filter false
            val eventEndTime = event.endTime?.toDate() ?: eventDate
            
            // Додајемо детаљно логовање за сваки догађај
            Log.d("HomeViewModel", """
                Проверавам догађај:
                - Наслов: ${event.title}
                - Време почетка: ${formatDate(eventDate)}
                - Време краја: ${formatDate(eventEndTime)}
                - Почетак дана: ${formatDate(startOfDay)}
                - Крај дана: ${formatDate(endOfDay)}
                - Тренутно време: ${formatDate(currentTime)}
                - Целодневни догађај: ${event.allDay}
            """.trimIndent())
            
            // Проверавамо да ли је догађај данас
            val isToday = !eventDate.before(startOfDay) && !eventDate.after(endOfDay)
            
            // За целодневне догађаје, проверавамо само да ли су данас
            // За остале догађаје, проверавамо да ли су данас и још нису завршени
            val isValid = if (event.allDay) {
                isToday
            } else {
                isToday && eventEndTime >= currentTime
            }
            
            Log.d("HomeViewModel", """
                Резултат провере за догађај ${event.title}:
                - Је данас: $isToday
                - Је активан: ${eventEndTime >= currentTime}
                - Је валидан: $isValid
            """.trimIndent())
            
            isValid
        }.sortedBy { it.startTime?.toDate() }
        
        Log.d("HomeViewModel", """
            Филтрирање догађаја:
            - Укупно догађаја: ${allEvents.size}
            - Данашњих активних догађаја: ${todayEvents.size}
            - Тренутно време: ${formatDate(currentTime)}
            - Детаљи догађаја:
            ${todayEvents.joinToString("\n") { "- ${it.title} (${formatDate(it.startTime?.toDate())})" }}
        """.trimIndent())
        
        // Обавезно проверавамо да ли је листа заиста различита пре ажурирања
        if (_todayEvents.value != todayEvents) {
            _todayEvents.value = todayEvents
        }
    }
    
    private fun formatDate(date: Date?): String {
        return date?.let { 
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(it) 
        } ?: "null"
    }
    
    // Функција за синхронизацију догађаја
    fun syncEvents() {
        if (_syncStatus.value == SyncStatus.Syncing) {
            Log.d("HomeViewModel", "Синхронизација у току, нећу поново покренути")
            return
        }
        
        viewModelScope.launch {
            try {
                Log.d("HomeViewModel", "Почињем синхронизацију догађаја...")
                _syncStatus.value = SyncStatus.Syncing
                isLoadingEvents = true
                
                // Затим покушавамо синхронизацију
                Log.d("HomeViewModel", "Покрећем синхронизацију са сервером...")
                eventRepository.syncEvents()
                    .onSuccess {
                        Log.d("HomeViewModel", "Синхронизација успешна!")
                        _syncStatus.value = SyncStatus.Success
                        // Поново учитавамо догађаје након успешне синхронизације
                        loadTodayEvents()
                        // Враћамо статус на Idle након кратког времена
                        delay(3000)
                        if (_syncStatus.value == SyncStatus.Success) {
                            _syncStatus.value = SyncStatus.Idle
                        }
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
                Log.d("HomeViewModel", "Синхронизација завршена!")
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        currentLoadJob?.cancel()
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