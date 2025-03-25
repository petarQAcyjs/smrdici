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
import com.petar.smrdici.data.model.EventAssignee
import com.petar.smrdici.data.repository.EventRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

class CalendarViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val auth: FirebaseAuth
) : ViewModel() {
    private val firestore = FirebaseFirestore.getInstance()
    
    private val _uiState = MutableStateFlow<CalendarUiState>(CalendarUiState.Loading)
    val uiState: StateFlow<CalendarUiState> = _uiState
    
    private val _selectedDate = MutableStateFlow(Date())
    val selectedDate: StateFlow<Date> = _selectedDate
    
    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events
    
    // Форма за унос новог догађаја
    private val _eventFormState = MutableStateFlow(EventFormState())
    val eventFormState: StateFlow<EventFormState> = _eventFormState
    
    private val _editingEvent = MutableStateFlow<Event?>(null)
    val editingEvent: StateFlow<Event?> = _editingEvent
    
    init {
        loadEvents()
    }
    
    // Функција за ресетовање форме за догађај
    fun resetEventForm() {
        _eventFormState.value = EventFormState(
            date = _selectedDate.value,
            assignee = EventAssignee.EVERYONE.name
        )
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
        
        // Ажурирамо датум у форми за догађај
        updateEventForm { form ->
            form.copy(date = date)
        }
    }
    
    // Ажурирање форме за унос догађаја
    fun updateEventForm(update: (EventFormState) -> EventFormState) {
        _eventFormState.value = update(_eventFormState.value)
        // Додајемо логовање за дебаговање
        val form = _eventFormState.value
        Log.d("CalendarViewModel", "Форма ажурирана: датум=${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(form.date)}, assignee=${form.assignee}")
    }
    
    // Функција за једноставно ажурирање једног поља форме
    fun updateEventField(fieldName: String, value: Any) {
        updateEventForm { form ->
            when (fieldName) {
                "title" -> form.copy(title = value as String)
                "description" -> form.copy(description = value as String)
                "date" -> form.copy(date = value as Date)
                "startHour" -> form.copy(startHour = value as Int)
                "startMinute" -> form.copy(startMinute = value as Int)
                "endHour" -> form.copy(endHour = value as Int)
                "endMinute" -> form.copy(endMinute = value as Int)
                "allDay" -> form.copy(allDay = value as Boolean)
                "location" -> form.copy(location = value as String)
                "color" -> form.copy(color = value as String)
                "assignee" -> form.copy(assignee = value as String)
                else -> form
            }
        }
    }
    
    // Додавање новог догађаја
    fun addEvent() {
        viewModelScope.launch {
            val form = _eventFormState.value
            
            if (!form.isValid) {
                Log.e("CalendarViewModel", "Неуспело додавање догађаја: форма није валидна")
                return@launch
            }
            
            try {
                // Додајемо додатно логовање стања форме пре креирања догађаја
                Log.d("CalendarViewModel", "Форма пре креирања догађаја - title: ${form.title}, date: ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(form.date)}, assignee: ${form.assignee}, color: ${form.color}")
                
                // Узимамо датум из форме и постављамо време
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
                
                // Експлицитно логујемо датум и време пре креирања догађаја
                Log.d("CalendarViewModel", "Креирам догађај за датум: ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(startCalendar.time)}")
                Log.d("CalendarViewModel", "Додељено особи: ${form.assignee}")
                
                // Креирамо догађај са експлицитним параметрима
                val event = Event(
                    id = null,
                    title = form.title,
                    description = form.description,
                    startTime = Timestamp(startCalendar.time),
                    endTime = endCalendar?.let { Timestamp(it.time) },
                    allDay = form.allDay,
                    location = form.location,
                    color = form.color,
                    assignee = form.assignee,
                    createdBy = auth.currentUser?.uid ?: "",
                    familyId = "" // Ако имате логику за породични ИД, овде је поставите
                )
                
                Log.d("CalendarViewModel", "Покушај додавања догађаја: ${event.title}, assignee: ${event.assignee}, color: ${event.color}")
                
                eventRepository.addEvent(event)
                    .onSuccess {
                        // Ресетујемо форму али задржавамо изабрани датум и последњу особу
                        val currentDate = _selectedDate.value
                        val lastAssignee = form.assignee
                        val lastColor = form.color
                        
                        _eventFormState.value = EventFormState(
                            date = currentDate,
                            assignee = lastAssignee,
                            color = lastColor
                        )
                        
                        Log.d("CalendarViewModel", "Успешно додат догађај: ${event.title} за особу ${event.assignee}")
                        
                        // Освежавамо листу догађаја за тренутни датум
                        loadEventsForDate(_selectedDate.value)
                    }
                    .onFailure { e ->
                        Log.e("CalendarViewModel", "Грешка при додавању догађаја", e)
                        _uiState.value = CalendarUiState.Error(e.message ?: "Грешка при додавању догађаја")
                    }
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "Неочекивана грешка при додавању догађаја", e)
                _uiState.value = CalendarUiState.Error("Неочекивана грешка: ${e.message}")
            }
        }
    }
    
    // Функција за брисање догађаја
    fun deleteEvent(eventId: String) {
        viewModelScope.launch {
            eventRepository.deleteEvent(eventId)
                .onSuccess {
                    // Успешно обрисан догађај, али не модификујемо директно uiState
                    // већ позивамо loadEventsForDate да освежи листу догађаја за текући датум
                    loadEventsForDate(_selectedDate.value)
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
                Log.d("CalendarViewModel", "Учитавање догађаја за датум: ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(date)}")
                
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
                        Log.d("CalendarViewModel", "Успешно учитано ${events.size} догађаја за датум: ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(date)}")
                    }
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "Грешка при учитавању догађаја за датум: ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(date)}", e)
                _uiState.value = CalendarUiState.Error(e.message ?: "Грешка при учитавању догађаја")
            }
        }
    }
    
    fun startEditingEvent(event: Event) {
        Log.d("CalendarViewModel", "Почињем уређивање догађаја: ${event.title}")
        _editingEvent.value = event
        
        // Попуњавамо форму са подацима догађаја
        val startCalendar = Calendar.getInstance().apply {
            time = event.startTime?.toDate() ?: Date()
        }
        
        val endCalendar = event.endTime?.toDate()?.let {
            Calendar.getInstance().apply { time = it }
        }
        
        _eventFormState.value = EventFormState(
            title = event.title,
            description = event.description ?: "",
            date = startCalendar.time,
            startHour = startCalendar.get(Calendar.HOUR_OF_DAY),
            startMinute = startCalendar.get(Calendar.MINUTE),
            endHour = endCalendar?.get(Calendar.HOUR_OF_DAY),
            endMinute = endCalendar?.get(Calendar.MINUTE),
            allDay = event.allDay,
            location = event.location,
            color = event.color,
            assignee = event.assignee
        )
    }
    
    fun cancelEditing() {
        _editingEvent.value = null
        resetEventForm()
    }
    
    fun updateEvent() {
        viewModelScope.launch {
            val form = _eventFormState.value
            val event = _editingEvent.value
            
            if (!form.isValid || event == null) {
                Log.e("CalendarViewModel", "Неуспело ажурирање догађаја: форма није валидна или догађај није изабран")
                return@launch
            }
            
            try {
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
                
                val updatedEvent = event.copy(
                    title = form.title,
                    description = form.description,
                    startTime = Timestamp(startCalendar.time),
                    endTime = endCalendar?.let { Timestamp(it.time) },
                    allDay = form.allDay,
                    location = form.location,
                    color = form.color,
                    assignee = form.assignee
                )
                
                Log.d("CalendarViewModel", "Ажурирам догађај: ${updatedEvent.title}, assignee: ${updatedEvent.assignee}")
                
                eventRepository.updateEvent(updatedEvent)
                    .onSuccess {
                        _editingEvent.value = null
                        resetEventForm()
                        loadEventsForDate(_selectedDate.value)
                        Log.d("CalendarViewModel", "Успешно ажуриран догађај: ${updatedEvent.title}")
                    }
                    .onFailure { e ->
                        Log.e("CalendarViewModel", "Грешка при ажурирању догађаја", e)
                        _uiState.value = CalendarUiState.Error(e.message ?: "Грешка при ажурирању догађаја")
                    }
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "Неочекивана грешка при ажурирању догађаја", e)
                _uiState.value = CalendarUiState.Error("Неочекивана грешка: ${e.message}")
            }
        }
    }
    
    // Додајемо нову функцију за освежавање података
    fun refresh() {
        viewModelScope.launch {
            Log.d("CalendarViewModel", "Освежавам податке...")
            loadEventsForDate(_selectedDate.value)
        }
    }
    
    // Додајемо Factory класу за креирање CalendarViewModel са Context параметром
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
                return CalendarViewModel(EventRepository(context), FirebaseAuth.getInstance()) as T
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
    val color: String = "#4285F4", // Подразумевана плава боја
    val assignee: String = EventAssignee.EVERYONE.name // Користимо име из енумерације
) {
    val isValid: Boolean
        get() = title.isNotBlank()
}