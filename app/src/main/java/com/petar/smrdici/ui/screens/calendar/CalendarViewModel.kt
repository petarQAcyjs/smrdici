package com.petar.smrdici.ui.screens.calendar

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.petar.smrdici.SmrdiciApplication
import com.petar.smrdici.data.model.Event
import com.petar.smrdici.data.model.EventAssignee
import com.petar.smrdici.data.repository.EventRepository
import com.petar.smrdici.notification.NotificationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

class CalendarViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val auth: FirebaseAuth,
    context: Context? = null
) : ViewModel() {
    private val appContext = context?.applicationContext
    private val _uiState = MutableStateFlow<CalendarUiState>(CalendarUiState.Loading)
    val uiState: StateFlow<CalendarUiState> = _uiState

    private val _selectedDate = MutableStateFlow(Date())
    val selectedDate: StateFlow<Date> = _selectedDate

    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events

    private val _datesWithBirthdays = MutableStateFlow<Set<Date>>(emptySet())
    val datesWithBirthdays: StateFlow<Set<Date>> = _datesWithBirthdays

    private val _eventFormState = MutableStateFlow(EventFormState())
    val eventFormState: StateFlow<EventFormState> = _eventFormState

    private val _editingEvent = MutableStateFlow<Event?>(null)
    val editingEvent: StateFlow<Event?> = _editingEvent

    private val _datesWithEvents = MutableStateFlow<Set<Date>>(emptySet())
    val datesWithEvents: StateFlow<Set<Date>> = _datesWithEvents

    private var eventsJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                _uiState.value = CalendarUiState.Loading

                eventRepository.cleanupDatabase()

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
                    .collect { events ->
                        _events.value = events
                        updateDatesWithEvents(events)
                        val eventsForSelectedDate = filterEventsForDate(_selectedDate.value, events)
                        _uiState.value = CalendarUiState.Success(eventsForSelectedDate)
                    }
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "Грешка при иницијализацији", e)
                _uiState.value = CalendarUiState.Error("Грешка при иницијализацији")
            }
        }
    }

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

        val selectedCalendar = Calendar.getInstance().apply { time = date }
        val selectedMonth = selectedCalendar.get(Calendar.MONTH)
        val selectedDayOfMonth = selectedCalendar.get(Calendar.DAY_OF_MONTH)

        val filteredEvents = events.filter { event ->
            val isNormalEvent = event.startTime?.toDate()?.let { eventDate ->
                !eventDate.before(startOfDay) && !eventDate.after(endOfDay)
            } ?: false

            val isRecurringYearlyMatch = if (event.isRecurringYearly && event.startTime != null) {
                val eventCalendar = Calendar.getInstance().apply { time = event.startTime.toDate() }
                val eventMonth = eventCalendar.get(Calendar.MONTH)
                val eventDayOfMonth = eventCalendar.get(Calendar.DAY_OF_MONTH)

                (eventMonth == selectedMonth && eventDayOfMonth == selectedDayOfMonth)
            } else false

            isNormalEvent || isRecurringYearlyMatch
        }.map { event ->
            if (event.isRecurringYearly && event.startTime != null) {
                val eventDate = event.startTime.toDate()
                val eventCalendar = Calendar.getInstance().apply { time = eventDate }
                val selectedCalendar = Calendar.getInstance().apply { time = date }

                if (eventCalendar.get(Calendar.MONTH) == selectedCalendar.get(Calendar.MONTH) &&
                    eventCalendar.get(Calendar.DAY_OF_MONTH) == selectedCalendar.get(Calendar.DAY_OF_MONTH) &&
                    eventCalendar.get(Calendar.YEAR) != selectedCalendar.get(Calendar.YEAR)) {

                    val adjustedCalendar = Calendar.getInstance().apply {
                        time = eventDate
                        set(Calendar.YEAR, selectedCalendar.get(Calendar.YEAR))
                    }

                    return@map event.copy(
                        startTime = Timestamp(adjustedCalendar.time),
                        endTime = event.endTime?.let {
                            val endCalendar = Calendar.getInstance().apply {
                                time = it.toDate()
                                set(Calendar.YEAR, selectedCalendar.get(Calendar.YEAR))
                            }
                            Timestamp(endCalendar.time)
                        }
                    )
                }
            }
            event
        }

        return filteredEvents
    }

    private fun loadEvents() {
        viewModelScope.launch {
            try {
                if (_events.value.isEmpty()) {
                    _uiState.value = CalendarUiState.Loading
                }

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
                    .collect { events ->
                        _events.value = events
                        updateDatesWithEvents(events)
                        val eventsForSelectedDate = filterEventsForDate(_selectedDate.value, events)
                        _uiState.value = CalendarUiState.Success(eventsForSelectedDate)
                    }
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "Грешка при учитавању догађаја", e)
                _uiState.value = CalendarUiState.Error("Грешка при учитавању догађаја")
            }
        }
    }

    fun selectDate(date: Date) {
        _selectedDate.value = date

        val filteredEvents = filterEventsForDate(date, _events.value)
        _uiState.value = CalendarUiState.Success(filteredEvents)

        updateEventForm { form ->
            form.copy(date = date)
        }
    }

    private fun updateEventForm(update: (EventFormState) -> EventFormState) {
        val currentForm = _eventFormState.value
        val updatedForm = update(currentForm)

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
    }

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
            "isRecurringYearly" -> currentForm.copy(isRecurringYearly = value as Boolean)
            else -> currentForm
        }

        _eventFormState.value = updatedForm
    }

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

    fun addEvent() {
        viewModelScope.launch {
            val form = _eventFormState.value

            if (!form.isValid) return@launch

            try {
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
                    familyId = "default",
                    isRecurringYearly = form.isRecurringYearly
                )

                eventRepository.addEvent(event)
                    .onSuccess { eventId ->
                        val currentDate = _selectedDate.value
                        val lastAssignee = form.assignee
                        val lastColor = form.color

                        _eventFormState.value = EventFormState(
                            date = currentDate,
                            assignee = lastAssignee,
                            color = lastColor
                        )

                        val eventWithId = event.copy(id = eventId)
                        scheduleNotifications(eventWithId)
                        loadEvents()
                    }
                    .onFailure { e ->
                        _uiState.value = CalendarUiState.Error(e.message ?: "Грешка при додавању догађаја")
                    }
            } catch (e: Exception) {
                _uiState.value = CalendarUiState.Error("Неочекивана грешка: ${e.message}")
            }
        }
    }

    fun deleteEvent(eventId: String) {
        viewModelScope.launch {
            cancelNotifications(eventId)
            eventRepository.deleteEvent(eventId)
                .onSuccess {
                    loadEvents()
                }
                .onFailure { e ->
                    _uiState.value = CalendarUiState.Error(e.message ?: "Грешка при брисању догађаја")
                }
        }
    }

    fun loadEventsForDate(date: Date) {
        viewModelScope.launch {
            try {
                if (_events.value.isEmpty()) {
                    _uiState.value = CalendarUiState.Loading
                    loadEvents()
                } else {
                    val filteredEvents = filterEventsForDate(date, _events.value)
                    _uiState.value = CalendarUiState.Success(filteredEvents)
                }
            } catch (_: Exception) {
                _uiState.value = CalendarUiState.Error("Грешка при филтрирању догађаја")
            }
        }
    }

    fun startEditingEvent(event: Event) {
        _editingEvent.value = event

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
            assignee = event.assignee,
            isRecurringYearly = event.isRecurringYearly
        )

        _eventFormState.value = newForm
    }

    fun cancelEditing() {
        _editingEvent.value = null
        resetEventForm()
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

    fun updateEvent() {
        viewModelScope.launch {
            try {
                val currentEvent = editingEvent.value ?: return@launch
                val formState = eventFormState.value

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
                    color = formState.color,
                    isRecurringYearly = formState.isRecurringYearly
                )

                eventRepository.updateEvent(updatedEvent)
                    .onSuccess {
                        currentEvent.id?.let { eventId ->
                            cancelNotifications(eventId)
                            scheduleNotifications(updatedEvent)
                        }

                        _editingEvent.value = null
                        loadEventsForDate(selectedDate.value)
                    }
                    .onFailure { e ->
                        _uiState.value = CalendarUiState.Error(e.message ?: "Непозната грешка")
                    }
            } catch (e: Exception) {
                _uiState.value = CalendarUiState.Error(e.message ?: "Непозната грешка")
            }
        }
    }

    override fun onCleared() {
        eventsJob?.cancel()
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
                val appContext = SmrdiciApplication.getInstance().applicationContext

                return CalendarViewModel(
                    EventRepository(FirebaseFirestore.getInstance(), FirebaseAuth.getInstance()),
                    FirebaseAuth.getInstance(),
                    appContext
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    private fun updateDatesWithEvents(events: List<Event>) {
        val normalDates = mutableSetOf<Date>()
        val birthdayDates = mutableSetOf<Date>()

        val selectedCal = Calendar.getInstance().apply { time = _selectedDate.value }
        val currentViewYear = selectedCal.get(Calendar.YEAR)
        val currentViewMonth = selectedCal.get(Calendar.MONTH)

        events.forEach { event ->
            event.startTime?.toDate()?.let { date ->
                val eventCal = Calendar.getInstance().apply { time = date }

                if (event.isRecurringYearly) {
                    val virtualBirthdayCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, currentViewYear)
                        set(Calendar.MONTH, eventCal.get(Calendar.MONTH))
                        set(Calendar.DAY_OF_MONTH, eventCal.get(Calendar.DAY_OF_MONTH))
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    if (virtualBirthdayCal.get(Calendar.MONTH) == currentViewMonth) {
                        birthdayDates.add(virtualBirthdayCal.time)
                    }
                } else {
                    val normalizedDate = Calendar.getInstance().apply {
                        time = date
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.time
                    normalDates.add(normalizedDate)
                }
            }
        }
        _datesWithEvents.value = normalDates
        _datesWithBirthdays.value = birthdayDates
    }

    fun syncEvents() {
        viewModelScope.launch {
            try {
                eventRepository.syncEvents()
                    .onSuccess {
                        loadEvents()
                    }
                    .onFailure {
                        _uiState.value = CalendarUiState.Error("Грешка при синхронизацији")
                    }
            } catch (_: Exception) {
                _uiState.value = CalendarUiState.Error("Грешка при синхронизацији")
            }
        }
    }

    private fun scheduleNotifications(event: Event) {
        appContext?.let { ctx ->
            if (event.id != null && event.startTime != null) {
                val notificationHelper = NotificationHelper.getInstance(ctx)
                notificationHelper.scheduleNotificationsForEvent(event)
                eventRepository.scheduleEventMorningNotification(event)
            }
        }
    }

    private fun cancelNotifications(eventId: String) {
        appContext?.let { ctx ->
            NotificationHelper.getInstance(ctx).cancelNotificationsForEvent(eventId)
        }
    }
}

sealed class CalendarUiState {
    data object Loading : CalendarUiState()
    data class Success(val events: List<Event>) : CalendarUiState()
    data class Error(val message: String) : CalendarUiState()
}

data class EventFormState(
    val title: String = "",
    val description: String = "",
    val date: Date = Calendar.getInstance().time,
    val startTime: EventTime? = null,
    val endTime: EventTime? = null,
    val allDay: Boolean = false,
    val location: String = "",
    val color: String = "#4285F4",
    val assignee: String = EventAssignee.EVERYONE.name,
    val isRecurringYearly: Boolean = false
) {
    val isValid: Boolean
        get() = title.isNotBlank() &&
                (!allDay && startTime != null || allDay) &&
                (endTime == null || startTime != null &&
                        (endTime.hour * 60 + endTime.minute) >
                        (startTime.hour * 60 + startTime.minute))
}