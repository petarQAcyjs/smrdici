package com.petar.smrdici.ui.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.petar.smrdici.data.model.Event
import com.petar.smrdici.data.repository.EventRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class CalendarViewModel : ViewModel() {
    private val repository = EventRepository()
    
    private val _uiState = MutableStateFlow<CalendarUiState>(CalendarUiState.Loading)
    val uiState: StateFlow<CalendarUiState> = _uiState
    
    private val _selectedDate = MutableStateFlow(Calendar.getInstance().time)
    val selectedDate: StateFlow<Date> = _selectedDate
    
    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events
    
    // Форма за унос новог догађаја
    private val _eventFormState = MutableStateFlow(EventFormState())
    val eventFormState: StateFlow<EventFormState> = _eventFormState
    
    init {
        loadEvents()
    }
    
    private fun loadEvents() {
        viewModelScope.launch {
            repository.getEventsForCurrentUser()
                .catch { e ->
                    _uiState.value = CalendarUiState.Error(e.message ?: "Грешка при учитавању догађаја")
                }
                .collect { events ->
                    _events.value = events
                    _uiState.value = CalendarUiState.Success(events)
                }
        }
    }
    
    // Промена изабраног датума
    fun selectDate(date: Date) {
        _selectedDate.value = date
    }
    
    // Ажурирање форме за унос догађаја
    fun updateEventForm(update: (EventFormState) -> EventFormState) {
        _eventFormState.value = update(_eventFormState.value)
    }
    
    // Додавање новог догађаја
    fun addEvent() {
        viewModelScope.launch {
            val form = _eventFormState.value
            
            if (!form.isValid) {
                return@launch
            }
            
            val startCalendar = Calendar.getInstance().apply {
                time = form.date
                set(Calendar.HOUR_OF_DAY, form.startHour)
                set(Calendar.MINUTE, form.startMinute)
            }
            
            val endCalendar = if (form.endHour != null && form.endMinute != null) {
                Calendar.getInstance().apply {
                    time = form.date
                    set(Calendar.HOUR_OF_DAY, form.endHour)
                    set(Calendar.MINUTE, form.endMinute)
                }
            } else null
            
            val event = Event(
                title = form.title,
                description = form.description,
                startTime = Timestamp(startCalendar.time),
                endTime = endCalendar?.let { Timestamp(it.time) },
                allDay = form.allDay,
                location = form.location,
                color = form.color
            )
            
            repository.addEvent(event)
                .onSuccess {
                    // Ресетујемо форму
                    _eventFormState.value = EventFormState()
                }
                .onFailure { e ->
                    _uiState.value = CalendarUiState.Error(e.message ?: "Грешка при додавању догађаја")
                }
        }
    }
    
    // Брисање догађаја
    fun deleteEvent(eventId: String) {
        viewModelScope.launch {
            repository.deleteEvent(eventId)
                .onFailure { e ->
                    _uiState.value = CalendarUiState.Error(e.message ?: "Грешка при брисању догађаја")
                }
        }
    }
}

// Стање корисничког интерфејса
sealed class CalendarUiState {
    object Loading : CalendarUiState()
    data class Success(val events: List<Event>) : CalendarUiState()
    data class Error(val message: String) : CalendarUiState()
}

// Стање форме за унос догађаја
data class EventFormState(
    val title: String = "",
    val description: String = "",
    val date: Date = Calendar.getInstance().time,
    val startHour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
    val startMinute: Int = Calendar.getInstance().get(Calendar.MINUTE),
    val endHour: Int? = null,
    val endMinute: Int? = null,
    val allDay: Boolean = false,
    val location: String = "",
    val color: String = "#4285F4" // Подразумевана плава боја
) {
    val isValid: Boolean
        get() = title.isNotBlank()
}