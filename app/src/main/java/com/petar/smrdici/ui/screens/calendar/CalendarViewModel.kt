package com.petar.smrdici.ui.screens.calendar

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.petar.smrdici.data.model.Event
import com.petar.smrdici.data.model.EventAssignee
import com.petar.smrdici.data.repository.EventRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class CalendarViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val auth: FirebaseAuth
) : ViewModel() {
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
    
    private val _allEvents = MutableStateFlow<List<Event>>(emptyList())
    
    // Додајемо ново стање за праћење датума са догађајима
    private val _datesWithEvents = MutableStateFlow<Set<Date>>(emptySet())
    val datesWithEvents: StateFlow<Set<Date>> = _datesWithEvents
    
    private var eventsJob: Job? = null
    
    init {
        viewModelScope.launch {
            try {
                _uiState.value = CalendarUiState.Loading
                
                // Прво чистимо базу ако треба
                eventRepository.cleanupDatabase()
                
                // Учитавамо догађаје за тренутни месец
                val calendar = Calendar.getInstance().apply {
                    time = _selectedDate.value
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                val startDate = calendar.time
                
                calendar.add(Calendar.MONTH, 1)
                calendar.add(Calendar.MILLISECOND, -1)
                val endDate = calendar.time
                
                eventRepository.getEvents(startDate, endDate)
                    .fold(
                        onSuccess = { events ->
                            _events.value = events
                            updateDatesWithEvents(events)
                            // Филтрирамо догађаје за изабрани датум
                            val eventsForSelectedDate = filterEventsForDate(_selectedDate.value, events)
                            _uiState.value = CalendarUiState.Success(eventsForSelectedDate)
                        },
                        onFailure = { e ->
                            Log.e("CalendarViewModel", "Грешка при иницијалном учитавању", e)
                            _uiState.value = CalendarUiState.Error("Грешка при учитавању догађаја")
                        }
                    )
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "Грешка при иницијализацији", e)
                _uiState.value = CalendarUiState.Error("Грешка при иницијализацији")
            }
        }
    }
    
    // Функција за ресетовање форме за догађај
    fun resetEventForm() {
        _eventFormState.value = EventFormState(
            date = _selectedDate.value,
            assignee = EventAssignee.EVERYONE.name
        )
    }
    
    private fun filterEventsForDate(date: Date, events: List<Event>): List<Event> {
        Log.d("CalendarViewModel", "\n=== ПОЧЕТАК ФИЛТРИРАЊА ===")
        Log.d("CalendarViewModel", "Сви догађаји пре филтрирања:")
        events.forEach { event ->
            Log.d("CalendarViewModel", """
                Догађај: ${event.title}
                - ID: ${event.id}
                - Време: ${formatDate(event.startTime?.toDate())}
                - Assignee: ${event.assignee}
            """.trimIndent())
        }

        val calendar = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val startOfDay = calendar.time
        
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        calendar.add(Calendar.MILLISECOND, -1)
        val endOfDay = calendar.time
        
        Log.d("CalendarViewModel", """
            Параметри филтрирања:
            - Тражени датум: ${formatDate(date)}
            - Почетак дана: ${formatDate(startOfDay)} (${startOfDay.time})
            - Крај дана: ${formatDate(endOfDay)} (${endOfDay.time})
            - Укупно догађаја за проверу: ${events.size}
        """.trimIndent())
        
        return events.filter { event ->
            event.startTime?.toDate()?.let { eventDate ->
                val isInRange = !eventDate.before(startOfDay) && !eventDate.after(endOfDay)
                Log.d("CalendarViewModel", """
                    Провера догађаја '${event.title}' (${event.id}):
                    - Време догађаја: ${formatDate(eventDate)} (${eventDate.time})
                    - У опсегу: $isInRange
                    - Пре почетка дана: ${eventDate.before(startOfDay)}
                    - После краја дана: ${eventDate.after(endOfDay)}
                """.trimIndent())
                isInRange
            } ?: run {
                Log.d("CalendarViewModel", "Догађај '${event.title}' (${event.id}) нема време почетка")
                false
            }
        }.also { filtered ->
            Log.d("CalendarViewModel", "\n=== РЕЗУЛТАТИ ФИЛТРИРАЊА ===")
            Log.d("CalendarViewModel", "Пронађено ${filtered.size} догађаја:")
            filtered.forEach { event ->
                Log.d("CalendarViewModel", """
                    Прихваћен догађај:
                    - Наслов: ${event.title}
                    - ID: ${event.id}
                    - Време: ${formatDate(event.startTime?.toDate())}
                    - Assignee: ${event.assignee}
                """.trimIndent())
            }
            Log.d("CalendarViewModel", "============================\n")
        }
    }
    
    private fun loadEvents() {
        viewModelScope.launch {
            try {
                Log.d("CalendarViewModel", "\n=== УЧИТАВАЊЕ ДОГАЂАЈА ===")
                
                // Постављамо Loading стање само ако немамо податке
                if (_events.value.isEmpty()) {
                    _uiState.value = CalendarUiState.Loading
                    Log.d("CalendarViewModel", "Постављено Loading стање")
                }
                
                // Рачунамо почетак и крај месеца
                val calendar = Calendar.getInstance().apply {
                    time = _selectedDate.value
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                val startDate = calendar.time
                
                calendar.add(Calendar.MONTH, 1)
                calendar.add(Calendar.MILLISECOND, -1)
                val endDate = calendar.time
                
                Log.d("CalendarViewModel", "Учитавам догађаје за период: ${formatDate(startDate)} - ${formatDate(endDate)}")
                
                // Учитавамо догађаје за цео месец
                eventRepository.getEvents(startDate, endDate)
                    .fold(
                        onSuccess = { events ->
                            Log.d("CalendarViewModel", "Учитано ${events.size} догађаја из репозиторијума")
                            Log.d("CalendarViewModel", "Догађаји пре филтрирања:")
                            events.forEach { event ->
                                Log.d("CalendarViewModel", "- ${event.title} (${formatDate(event.startTime?.toDate())})")
                            }
                            _events.value = events
                            updateDatesWithEvents(events)
                            val eventsForSelectedDate = filterEventsForDate(_selectedDate.value, events)
                            _uiState.value = CalendarUiState.Success(eventsForSelectedDate)
                        },
                        onFailure = { e ->
                            Log.e("CalendarViewModel", "Грешка при учитавању догађаја", e)
                            _uiState.value = CalendarUiState.Error("Грешка при учитавању догађаја")
                        }
                    )
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "Грешка при учитавању догађаја", e)
                _uiState.value = CalendarUiState.Error("Грешка при учитавању догађаја")
            }
        }
    }
    
    // Промена изабраног датума
    fun selectDate(date: Date) {
        _selectedDate.value = date
        
        // Филтрирамо из свих догађаја
        val filteredEvents = filterEventsForDate(date, _allEvents.value)
        _events.value = filteredEvents
        _uiState.value = CalendarUiState.Success(filteredEvents)
        
        Log.d("CalendarViewModel", "Изабран датум: ${
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(date)
        }, приказујем ${filteredEvents.size} догађаја од укупно ${_allEvents.value.size}")
        
        // Ажурирамо датум у форми за догађај
        updateEventForm { form ->
            form.copy(date = date)
        }
    }
    
    // Ажурирање форме за унос догађаја
    private fun updateEventForm(update: (EventFormState) -> EventFormState) {
        val currentForm = _eventFormState.value
        val updatedForm = update(currentForm)
        
        Log.d("CalendarViewModel", "Ажурирање форме: тренутни assignee=${currentForm.assignee}, нови assignee=${updatedForm.assignee}")
        
        // Креирамо нови објекат са свим пољима
        _eventFormState.value = currentForm.copy(
            assignee = updatedForm.assignee,
            color = updatedForm.color,
            title = updatedForm.title,
            description = updatedForm.description,
            date = updatedForm.date,
            startTime = updatedForm.startTime,
            endTime = updatedForm.endTime,
            allDay = updatedForm.allDay,
            location = updatedForm.location
        )
        
        Log.d("CalendarViewModel", "Форма ажурирана: assignee=${_eventFormState.value.assignee}")
    }
    
    // Додајемо нову функцију за директно ажурирање поља
    fun updateEventFormField(field: String, value: Any) {
        val currentForm = _eventFormState.value
        val updatedForm = when (field) {
            "title" -> currentForm.copy(title = value as String)
            "description" -> currentForm.copy(description = value as String)
            "location" -> currentForm.copy(location = value as String)
            "assignee" -> currentForm.copy(assignee = value as String)
            "color" -> currentForm.copy(color = value as String)
            "startTime" -> currentForm.copy(startTime = value as EventTime?)
            "endTime" -> currentForm.copy(endTime = value as EventTime?)
            "allDay" -> currentForm.copy(allDay = value as Boolean)
            "date" -> currentForm.copy(date = value as Date)
            else -> {
                Log.e("CalendarViewModel", "Непознато поље: $field")
                currentForm
            }
        }
        
        Log.d("CalendarViewModel", "Ажурирање поља '$field': $value")
        Log.d("CalendarViewModel", "Стара форма: $currentForm")
        Log.d("CalendarViewModel", "Нова форма: $updatedForm")
        
        _eventFormState.value = updatedForm
    }
    
    // Функција за једноставно ажурирање једног поља форме
    fun updateEventField(fieldName: String, value: Any) {
        updateEventForm { form ->
            when (fieldName) {
                "title" -> form.copy(title = value as String)
                "description" -> form.copy(description = value as String)
                "date" -> form.copy(date = value as Date)
                "startTime" -> form.copy(startTime = value as EventTime?)
                "endTime" -> form.copy(endTime = value as EventTime?)
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
                    set(Calendar.HOUR_OF_DAY, form.startTime?.hour ?: 0)
                    set(Calendar.MINUTE, form.startTime?.minute ?: 0)
                }
                
                val endCalendar = if (form.endTime != null) {
                    Calendar.getInstance().apply {
                        time = form.date
                        set(Calendar.HOUR_OF_DAY, form.endTime.hour)
                        set(Calendar.MINUTE, form.endTime.minute)
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
                    familyId = "default" // Подразумевана породица
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
                        loadEvents()
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
                    loadEvents()
                }
                .onFailure { e ->
                    _uiState.value = CalendarUiState.Error(e.message ?: "Грешка при брисању догађаја")
                }
        }
    }
    
    // Функција за учитавање догађаја за одређени датум
    fun loadEventsForDate(date: Date) {
        viewModelScope.launch {
            try {
                if (_events.value.isEmpty()) {
                    _uiState.value = CalendarUiState.Loading
                    loadEvents() // Учитавамо све догађаје ако их немамо
                } else {
                    // Само филтрирамо постојеће догађаје
                    val filteredEvents = filterEventsForDate(date, _events.value)
                    _uiState.value = CalendarUiState.Success(filteredEvents)
                }
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "Грешка при филтрирању догађаја", e)
                _uiState.value = CalendarUiState.Error("Грешка при филтрирању догађаја")
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
        
        val newForm = EventFormState(
            title = event.title,
            description = event.description ?: "",
            date = startCalendar.time,
            startTime = EventTime.fromDate(startCalendar.time),
            endTime = endCalendar?.time?.let { EventTime.fromDate(it) },
            allDay = event.allDay,
            location = event.location,
            color = event.color,
            assignee = event.assignee
        )
        
        Log.d("CalendarViewModel", "Постављам форму за уређивање: assignee=${newForm.assignee}")
        _eventFormState.value = newForm
    }
    
    fun cancelEditing() {
        _editingEvent.value = null
        resetEventForm()
    }
    
    fun updateEvent() {
        viewModelScope.launch {
            try {
                val currentEvent = editingEvent.value ?: return@launch
                val formState = eventFormState.value

                Log.d("CalendarViewModel", "Припремам ажурирање догађаја:")
                Log.d("CalendarViewModel", "- Тренутни assignee: ${currentEvent.assignee}")
                Log.d("CalendarViewModel", "- Нови assignee из форме: ${formState.assignee}")

                // Креирамо нови Event објекат са ажурираним подацима
                val updatedEvent = currentEvent.copy(
                    title = formState.title,
                    description = formState.description,
                    startTime = combineDateAndTime(formState.date, formState.startTime?.hour ?: 0, formState.startTime?.minute ?: 0),
                    endTime = if (!formState.allDay && formState.endTime != null) {
                        combineDateAndTime(formState.date, formState.endTime.hour, formState.endTime.minute)
                    } else null,
                    allDay = formState.allDay,
                    location = formState.location,
                    assignee = formState.assignee,
                    color = formState.color
                )

                Log.d("CalendarViewModel", "Шаљем ажурирање у базу:")
                Log.d("CalendarViewModel", "- ID догађаја: ${updatedEvent.id}")
                Log.d("CalendarViewModel", "- Assignee: ${updatedEvent.assignee}")
                
                eventRepository.updateEvent(updatedEvent)
                    .onSuccess {
                        Log.d("CalendarViewModel", "Успешно ажуриран догађај у бази")
                        _editingEvent.value = null
                        loadEventsForDate(selectedDate.value)
                    }
                    .onFailure { e ->
                        Log.e("CalendarViewModel", "Грешка при ажурирању догађаја", e)
                        _uiState.value = CalendarUiState.Error(e.message ?: "Непозната грешка")
                    }
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "Грешка при ажурирању догађаја", e)
                _uiState.value = CalendarUiState.Error(e.message ?: "Непозната грешка")
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        eventsJob?.cancel()
    }
    
    // Додајемо Factory класу за креирање CalendarViewModel
    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
                return CalendarViewModel(
                    EventRepository(FirebaseFirestore.getInstance(), FirebaseAuth.getInstance()),
                    FirebaseAuth.getInstance()
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    private fun formatDate(date: Date?): String {
        if (date == null) return ""
        return SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(date)
    }

    // Функција која ажурира сет датума са догађајима
    private fun updateDatesWithEvents(events: List<Event>) {
        val dates = events.mapNotNull { event ->
            event.startTime?.toDate()?.let { date ->
                Calendar.getInstance().apply {
                    time = date
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time
            }
        }.toSet()
        _datesWithEvents.value = dates
    }

    private fun combineDateAndTime(date: Date, hour: Int, minute: Int): Timestamp {
        val calendar = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return Timestamp(calendar.time)
    }

    fun syncEvents() {
        viewModelScope.launch {
            try {
                eventRepository.syncEvents()
                    .fold(
                        onSuccess = {
                            loadEvents() // Освежи приказ
                            Log.d("CalendarViewModel", "Синхронизација успешно завршена")
                        },
                        onFailure = { e ->
                            Log.e("CalendarViewModel", "Грешка при синхронизацији", e)
                            _uiState.value = CalendarUiState.Error("Грешка при синхронизацији")
                        }
                    )
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "Грешка при синхронизацији", e)
                _uiState.value = CalendarUiState.Error("Грешка при синхронизацији")
            }
        }
    }
}

// Стање корисничког интерфејса
sealed class CalendarUiState {
    data object Loading : CalendarUiState()
    data class Success(val events: List<Event>) : CalendarUiState()
    data class Error(val message: String) : CalendarUiState()
}

// Стање форме за унос догађаја
data class EventFormState(
    val title: String = "",
    val description: String = "",
    val date: Date = Calendar.getInstance().time,
    val startTime: EventTime? = null,
    val endTime: EventTime? = null,
    val allDay: Boolean = false,
    val location: String = "",
    val color: String = "#4285F4",
    val assignee: String = EventAssignee.EVERYONE.name
) {
    val isValid: Boolean
        get() = title.isNotBlank() && 
                (!allDay && startTime != null || allDay) &&
                (endTime == null || startTime != null && 
                 (endTime.hour * 60 + endTime.minute) > 
                 (startTime.hour * 60 + startTime.minute))
}