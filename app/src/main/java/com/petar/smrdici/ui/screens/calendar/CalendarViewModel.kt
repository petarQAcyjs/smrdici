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
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ListenerRegistration

class CalendarViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val auth: FirebaseAuth
) : ViewModel() {
    private val firestore = FirebaseFirestore.getInstance()
    private val eventsCollection = firestore.collection("calendar_events")
    private var eventsListener: ListenerRegistration? = null
    
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
    
    private var lastLogTime = 0L
    private fun shouldLog(): Boolean {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastLogTime > 1000) { // 1 секунда између логова
            lastLogTime = currentTime
            return true
        }
        return false
    }
    
    init {
        viewModelScope.launch {
            try {
                eventRepository.cleanupDatabase()
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "Грешка при чишћењу базе", e)
            }
            loadEvents()
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
        
        Log.d("CalendarViewModel", "Филтрирам догађаје за датум: ${formatDate(date)}")
        Log.d("CalendarViewModel", "Почетак дана: ${formatDate(startOfDay)}")
        Log.d("CalendarViewModel", "Крај дана: ${formatDate(endOfDay)}")
        
        // Користимо distinctBy да избегнемо дупликате
        return events.distinctBy { "${it.title}${it.startTime}${it.assignee}" }
            .filter { event ->
                event.startTime?.toDate()?.let { eventDate ->
                    !eventDate.before(startOfDay) && !eventDate.after(endOfDay)
                } ?: false
            }.also { filtered ->
                events.forEach { event ->
                    val message = if (filtered.contains(event)) "је у опсега" else "је ван опсега"
                    Log.d("CalendarViewModel", "Догађај '${event.title}' време: ${formatDate(event.startTime?.toDate())} $message")
                }
                Log.d("CalendarViewModel", "Пронађено ${filtered.size} догађаја од укупно ${events.size}")
            }
    }
    
    private fun loadEvents() {
        eventsJob?.cancel()
        
        // Рачунамо почетак и крај месеца за тренутно изабрани датум
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

        eventsJob = viewModelScope.launch {
            try {
                eventRepository.observeEvents(startDate, endDate)
                    .catch { e ->
                        if (e !is CancellationException) {
                            Log.e("CalendarViewModel", "Грешка при учитавању догађаја", e)
                            _uiState.value = CalendarUiState.Error(e.message ?: "Непозната грешка")
                        }
                    }
                    .collect { events ->
                        _allEvents.value = events
                        updateDatesWithEvents(events)
                        updateFilteredEvents()
                    }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e("CalendarViewModel", "Грешка при учитавању догађаја", e)
                    _uiState.value = CalendarUiState.Error(e.message ?: "Непозната грешка")
                }
            }
        }
    }
    
    private fun updateFilteredEvents() {
        val filteredEvents = filterEventsForDate(_selectedDate.value, _allEvents.value)
        _events.value = filteredEvents.distinctBy { "${it.title}${it.startTime}${it.assignee}" }
        _uiState.value = CalendarUiState.Success(filteredEvents)
        
        if (shouldLog()) {
            Log.d("CalendarViewModel", "Изабран датум: ${formatDate(_selectedDate.value)}, приказујем ${filteredEvents.size} догађаја од укупно ${_allEvents.value.size}")
        }
        
        // Ажурирамо датум у форми за догађај
        updateEventForm { form ->
            form.copy(date = _selectedDate.value)
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
    fun updateEventForm(update: (EventFormState) -> EventFormState) {
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
            startHour = updatedForm.startHour,
            startMinute = updatedForm.startMinute,
            endHour = updatedForm.endHour,
            endMinute = updatedForm.endMinute,
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
            "startHour" -> currentForm.copy(startHour = value as Int)
            "startMinute" -> currentForm.copy(startMinute = value as Int)
            "endHour" -> currentForm.copy(endHour = value as Int)
            "endMinute" -> currentForm.copy(endMinute = value as Int)
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
    
    // Функција за учитавање догађаја за одређени период
    private fun loadEventsForPeriod(startDate: Date, endDate: Date) {
        viewModelScope.launch {
            try {
                Log.d("CalendarViewModel", "Учитавање догађаја за период ${
                    SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(startDate)
                } - ${
                    SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(endDate)
                }")
                
                eventRepository.observeEvents(startDate, endDate)
                    .catch { e ->
                        if (e is CancellationException) {
                            Log.d("CalendarViewModel", "Учитавање догађаја отказано")
                            return@catch
                        }
                        Log.e("CalendarViewModel", "Грешка при учитавању догађаја за период", e)
                        _uiState.value = CalendarUiState.Error(e.message ?: "Грешка при учитавању догађаја")
                    }
                    .collect { allEvents ->
                        _allEvents.value = allEvents
                        _events.value = allEvents
                        _uiState.value = CalendarUiState.Success(allEvents)
                        
                        Log.d("CalendarViewModel", "Учитано ${allEvents.size} догађаја за период")
                    }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    Log.d("CalendarViewModel", "Учитавање догађаја отказано")
                    return@launch
                }
                Log.e("CalendarViewModel", "Грешка при учитавању догађаја за период", e)
                _uiState.value = CalendarUiState.Error(e.message ?: "Грешка при учитавању догађаја")
            }
        }
    }
    
    // Функција за учитавање догађаја за одређени датум
    fun loadEventsForDate(date: Date) {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfDay = calendar.time
        
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        calendar.add(Calendar.MILLISECOND, -1)
        val endOfDay = calendar.time
        
        loadEventsForPeriod(startOfDay, endOfDay)
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
            startHour = startCalendar.get(Calendar.HOUR_OF_DAY),
            startMinute = startCalendar.get(Calendar.MINUTE),
            endHour = endCalendar?.get(Calendar.HOUR_OF_DAY),
            endMinute = endCalendar?.get(Calendar.MINUTE),
            allDay = event.allDay,
            location = event.location,
            color = event.color,
            assignee = event.assignee // Осигуравамо да се assignee правилно постави
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
                    startTime = combineDateAndTime(formState.date, formState.startHour, formState.startMinute),
                    endTime = if (!formState.allDay && formState.endHour != null && formState.endMinute != null) {
                        combineDateAndTime(formState.date, formState.endHour, formState.endMinute)
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
    
    // Додајемо нову функцију за освежавање података
    fun refresh() {
        viewModelScope.launch {
            try {
                Log.d("CalendarViewModel", "Освежавам податке...")
                loadEvents()
            } catch (e: Exception) {
                if (e is CancellationException) {
                    Log.d("CalendarViewModel", "Освежавање отказано")
                    return@launch
                }
                Log.e("CalendarViewModel", "Неочекивана грешка при освежавању", e)
            }
        }
    }
    
    fun cleanupDuplicates() {
        viewModelScope.launch {
            try {
                eventRepository.removeDuplicates()
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "Грешка при чишћењу дупликата", e)
            }
        }
    }
    
    fun cleanupDatabase() {
        viewModelScope.launch {
            try {
                eventRepository.cleanupDatabase()
                loadEvents()
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "Грешка при чишћењу базе", e)
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        eventsJob?.cancel()
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
                Log.d("CalendarViewModel", "Почињем синхронизацију догађаја")
                eventRepository.syncEvents() // Додати ову методу у репозиторијум
                loadEventsForDate(selectedDate.value) // Освежи приказ
                Log.d("CalendarViewModel", "Синхронизација успешно завршена")
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "Грешка при синхронизацији", e)
                _uiState.value = CalendarUiState.Error("Грешка при синхронизацији")
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
    val color: String = "#4285F4", // Подразумевана плава боја
    val assignee: String = EventAssignee.EVERYONE.name // Користимо име из енумерације
) {
    val isValid: Boolean
        get() = title.isNotBlank()
}