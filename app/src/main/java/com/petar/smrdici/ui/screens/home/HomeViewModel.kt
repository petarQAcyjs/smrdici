package com.petar.smrdici.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petar.smrdici.data.model.Event
import com.petar.smrdici.data.repository.EventRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class HomeViewModel : ViewModel() {
    private val eventRepository = EventRepository()
    private val _todayEvents = MutableStateFlow<List<Event>>(emptyList())
    val todayEvents: StateFlow<List<Event>> = _todayEvents

    init {
        loadTodayEvents()
    }

    private fun loadTodayEvents() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfDay = calendar.time

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val endOfDay = calendar.time

        viewModelScope.launch {
            eventRepository.getEventsForPeriod(startOfDay, endOfDay)
                .collect { events ->
                    _todayEvents.value = events.sortedBy { it.startTime }
                }
        }
    }

    fun addTestEvents() {
        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            
            // Подеси време за први догађај
            calendar.set(Calendar.HOUR_OF_DAY, 10)
            calendar.set(Calendar.MINUTE, 30)
            val event1 = Event(
                title = "Час пливања Нина",
                startTime = com.google.firebase.Timestamp(calendar.time),
                endTime = com.google.firebase.Timestamp(Date(calendar.time.time + 7200000)),
                color = "#4285F4"
            )
            
            // Подеси време за други догађај
            calendar.set(Calendar.HOUR_OF_DAY, 14)
            calendar.set(Calendar.MINUTE, 30)
            val event2 = Event(
                title = "Биоскоп са девојчицама",
                startTime = com.google.firebase.Timestamp(calendar.time),
                endTime = com.google.firebase.Timestamp(Date(calendar.time.time + 7200000)),
                color = "#EA4335"
            )
            
            // Подеси време за трећи догађај
            calendar.set(Calendar.HOUR_OF_DAY, 19)
            calendar.set(Calendar.MINUTE, 30)
            val event3 = Event(
                title = "Вечера код баке",
                startTime = com.google.firebase.Timestamp(calendar.time),
                endTime = com.google.firebase.Timestamp(Date(calendar.time.time + 5400000)),
                color = "#FBBC05"
            )

            eventRepository.addEvent(event1)
            eventRepository.addEvent(event2)
            eventRepository.addEvent(event3)
        }
    }
} 