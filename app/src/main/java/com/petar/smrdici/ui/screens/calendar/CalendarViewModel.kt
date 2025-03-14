package com.petar.smrdici.ui.screens.calendar

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.petar.smrdici.data.model.Event
import com.petar.smrdici.data.model.EventColor
import com.petar.smrdici.data.repository.EventRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import android.util.Log

class CalendarViewModel(private val context: Context) : ViewModel() {
    private val eventRepository = EventRepository(context)
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
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
            eventRepository.getEventsForCurrentUser()
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
            
            eventRepository.addEvent(event)
                .onSuccess {
                    // Ресетујемо форму
                    _eventFormState.value = EventFormState()
                }
                .onFailure { e ->
                    _uiState.value = CalendarUiState.Error(e.message ?: "Грешка при додавању догађаја")
                }
        }
    }
    
    // Функција за брисање догађаја
    fun deleteEvent(eventId: String) {
        viewModelScope.launch {
            eventRepository.deleteEvent(eventId)
                .onSuccess {
                    // Успешно обрисан догађај
                    _uiState.value = CalendarUiState.Success(
                        (_uiState.value as? CalendarUiState.Success)?.events?.filter { it.id != eventId } ?: emptyList()
                    )
                }
                .onFailure { e ->
                    _uiState.value = CalendarUiState.Error(e.message ?: "Грешка при брисању догађаја")
                }
        }
    }
    
    // Додајемо нову функцију за учитавање догађаја за одређени датум
    fun loadEventsForDate(date: Date) {
        viewModelScope.launch {
            _uiState.value = CalendarUiState.Loading
            
            try {
                // Постављамо временски опсег за изабрани датум (од поноћи до 23:59:59)
                val calendar = Calendar.getInstance()
                calendar.time = date
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val startOfDay = calendar.time
                
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val endOfDay = calendar.time
                
                // Користимо постојећу функцију из репозиторијума
                eventRepository.getEventsForPeriod(startOfDay, endOfDay)
                    .collect { events -> // Користимо collect уместо first
                        _events.value = events
                        _uiState.value = CalendarUiState.Success(events)
                    }
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "Грешка при учитавању догађаја", e)
                _uiState.value = CalendarUiState.Error(e.message ?: "Грешка при учитавању догађаја")
            }
        }
    }
    
    // Додајемо Factory класу за креирање CalendarViewModel са Context параметром
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
                return CalendarViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
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