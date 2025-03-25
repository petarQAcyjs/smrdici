package com.petar.smrdici.ui.screens.calendar

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.data.model.Event
import com.petar.smrdici.data.model.EventColor
import com.petar.smrdici.data.model.EventAssignee
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.navigation.Screen
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun CalendarScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(),
    calendarViewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.Factory(LocalContext.current))
) {
    val authState by authViewModel.authState.collectAsState()
    val calendarUiState by calendarViewModel.uiState.collectAsState()
    val eventFormState by calendarViewModel.eventFormState.collectAsState()
    val selectedDate by calendarViewModel.selectedDate.collectAsState()
    val events by calendarViewModel.events.collectAsState()
    val editingEvent by calendarViewModel.editingEvent.collectAsState()
    val user = if (authState is AuthState.Authenticated) {
        (authState as AuthState.Authenticated).user
    } else null
    
    var showAddEventDialog by remember { mutableStateOf(false) }
    var selectedEvent by remember { mutableStateOf<Event?>(null) }
    var showEventDetailsDialog by remember { mutableStateOf(false) }
    
    // Приказујемо дијалог за уређивање када се појави догађај за уређивање
    LaunchedEffect(editingEvent) {
        if (editingEvent != null) {
            showAddEventDialog = true
        }
    }
    
    // Додајемо корутински опсег за Compose компоненту
    val coroutineScope = rememberCoroutineScope()
    
    // Стање освежавања
    var isRefreshing by remember { mutableStateOf(false) }
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = isRefreshing)
    
    // Функција за освежавање догађаја
    val refreshEvents = {
        coroutineScope.launch {
            isRefreshing = true
            // Користимо нову функцију за освежавање података
            calendarViewModel.refresh()
            delay(1000) // Минимално трајање анимације освежавања
            isRefreshing = false
            Log.d("CalendarScreen", "Повлачење за освежавање - догађаји освежени")
        }
    }
    
    // Ефекат за логовање и учитавање догађаја када се промени selectedDate
    LaunchedEffect(selectedDate) {
        Log.d("CalendarScreen", "Изабрани датум промењен: ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(selectedDate)}")
        calendarViewModel.loadEventsForDate(selectedDate)
    }
    
    // Главни контејнер
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Додајемо дугме за повратак назад
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { navController.navigateUp() }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад"
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "Календар",
                    style = MaterialTheme.typography.headlineMedium
                )
            }
            
            // Календарски приказ
            CalendarView(
                selectedDate = selectedDate,
                onDateSelected = { calendarViewModel.selectDate(it) },
                events = if (calendarUiState is CalendarUiState.Success) (calendarUiState as CalendarUiState.Success).events else emptyList()
            )
            
            // Приказ догађаја за изабрани датум са подршком за освежавање
            SwipeRefresh(
                state = swipeRefreshState,
                onRefresh = { refreshEvents() },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (calendarUiState) {
                    is CalendarUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            // Приказујемо индикатор учитавања само ако није у току освежавање
                            if (!isRefreshing) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    is CalendarUiState.Success -> {
                        val eventsToShow = (calendarUiState as CalendarUiState.Success).events
                        EventsList(
                            events = eventsToShow,
                            onEventClick = { event ->
                                selectedEvent = event
                                showEventDetailsDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        )
                    }
                    is CalendarUiState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (calendarUiState as CalendarUiState.Error).message,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
        
        // Плутајуће дугме за додавање догађаја - премештено изван Column и EventsList
        FloatingActionButton(
            onClick = {
                // Експлицитно ажурирамо форму са тренутно изабраним датумом
                Log.d("CalendarScreen", "FAB кликнут - постављам изабрани датум: ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(selectedDate)}")
                // Ресетујемо формулар на подразумеване вредности али постављамо изабрани датум
                calendarViewModel.updateEventForm { 
                    EventFormState(
                        date = selectedDate, 
                        title = "",
                        description = "",
                        location = "",
                        // Задржавамо Сви као подразумевану вредност, али корисник ће моћи да промени
                        assignee = "EVERYONE"
                    )
                }
                showAddEventDialog = true
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Додај догађај"
            )
        }
    }
    
    // Приказујемо дијалог за додавање догађаја ако је потребно
    if (showAddEventDialog) {
        AddEventDialog(
            onDismissRequest = { 
                showAddEventDialog = false
                // Ако затворимо дијалог, ресетујемо стање уређивања
                if (editingEvent != null) {
                    calendarViewModel.cancelEditing()
                }
            },
            onConfirm = {
                // Додајемо или ажурирамо догађај у зависности од тога да ли уређујемо
                if (editingEvent != null) {
                    calendarViewModel.updateEvent()
                } else {
                    calendarViewModel.addEvent()
                }
                showAddEventDialog = false
            },
            calendarViewModel = calendarViewModel
        )
    }
    
    // Дијалог за приказ детаља догађаја
    if (showEventDetailsDialog && selectedEvent != null) {
        EventDetailsDialog(
            event = selectedEvent!!,
            onDismiss = { showEventDetailsDialog = false },
            onDelete = {
                calendarViewModel.deleteEvent(selectedEvent!!.id!!)
                showEventDetailsDialog = false
            },
            onEdit = {
                calendarViewModel.startEditingEvent(selectedEvent!!)
                showEventDetailsDialog = false
            }
        )
    }
}

@Composable
fun CalendarView(
    selectedDate: Date,
    onDateSelected: (Date) -> Unit,
    events: List<Event>
) {
    val calendar = remember { Calendar.getInstance() }
    calendar.time = selectedDate
    
    val currentMonth = calendar.get(Calendar.MONTH)
    val currentYear = calendar.get(Calendar.YEAR)
    
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    
    val firstDayOfMonth = Calendar.getInstance().apply {
        set(currentYear, currentMonth, 1)
    }
    val firstDayOfWeek = firstDayOfMonth.get(Calendar.DAY_OF_WEEK)
    
    val monthFormat = SimpleDateFormat("MMMM yyyy", Locale("sr"))
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Заглавље календара
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    calendar.add(Calendar.MONTH, -1)
                    onDateSelected(calendar.time)
                }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Претходни месец"
                )
            }
            
            Text(
                text = monthFormat.format(calendar.time).replaceFirstChar { 
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() 
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            IconButton(
                onClick = {
                    calendar.add(Calendar.MONTH, 1)
                    onDateSelected(calendar.time)
                }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Следећи месец"
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Дани у недељи
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val daysOfWeek = listOf("Пон", "Уто", "Сре", "Чет", "Пет", "Суб", "Нед")
            daysOfWeek.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Календарска мрежа
        val rows = (daysInMonth + firstDayOfWeek - 2) / 7 + 1
        
        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (col in 0 until 7) {
                    val day = row * 7 + col - (firstDayOfWeek - 2)
                    
                    if (day in 1..daysInMonth) {
                        val date = Calendar.getInstance().apply {
                            set(currentYear, currentMonth, day)
                        }.time
                        
                        val isSelected = Calendar.getInstance().apply {
                            time = selectedDate
                        }.get(Calendar.DAY_OF_MONTH) == day &&
                                Calendar.getInstance().apply {
                                    time = selectedDate
                                }.get(Calendar.MONTH) == currentMonth &&
                                Calendar.getInstance().apply {
                                    time = selectedDate
                                }.get(Calendar.YEAR) == currentYear
                        
                        val hasEvents = events.any { event ->
                            val eventDate = event.startTime?.toDate()
                            if (eventDate != null) {
                                val eventCal = Calendar.getInstance().apply { time = eventDate }
                                eventCal.get(Calendar.YEAR) == currentYear &&
                                        eventCal.get(Calendar.MONTH) == currentMonth &&
                                        eventCal.get(Calendar.DAY_OF_MONTH) == day
                            } else {
                                false
                            }
                        }
                        
                        CalendarDay(
                            day = day,
                            isSelected = isSelected,
                            hasEvents = hasEvents,
                            onClick = { onDateSelected(date) }
                        )
                    } else {
                        // Празан простор за дане који нису у тренутном месецу
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun EventsList(
    events: List<Event>,
    onEventClick: (Event) -> Unit,
    modifier: Modifier
) {
    LazyColumn(
        modifier = modifier
    ) {
        item {
            Text(
                text = "Догађаји",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        
        if (events.isEmpty()) {
            item {
                Text(
                    text = "Нема догађаја за изабрани дан",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        } else {
            items(events) { event ->
                EventItem(
                    event = event,
                    onClick = { onEventClick(event) }
                )
            }
        }
    }
}

@Composable
fun EventItem(
    event: Event,
    onClick: () -> Unit
) {
    // Нађимо објекат EventAssignee који одговара имену особе из догађаја
    val assignee = EventAssignee.values().find { it.name == event.assignee } ?: EventAssignee.EVERYONE
    
    Log.d("EventItem", "Приказујем догађај '${event.title}' додељен особи '${event.assignee}' са бојом ${event.color}")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Додајемо аватар особе
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        Color(android.graphics.Color.parseColor(event.color)).copy(alpha = 0.3f),
                        CircleShape
                    )
                    .border(1.dp, Color(android.graphics.Color.parseColor(event.color)), CircleShape)
            ) {
                // Експлицитан тип String за Text
                Text(
                    text = assignee.initial.toString(),
                    color = Color(android.graphics.Color.parseColor(event.color)),
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                if (event.location.isNotBlank()) {
                    Text(
                        text = event.location,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            if (!event.allDay && event.startTime != null) {
                Spacer(modifier = Modifier.width(8.dp))
                
                val timeText = remember(event.startTime) {
                    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                    formatter.format(event.startTime.toDate())
                }
                
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    calendarViewModel: CalendarViewModel,
    modifier: Modifier = Modifier
) {
    val eventFormState by calendarViewModel.eventFormState.collectAsState()
    val editingEvent by calendarViewModel.editingEvent.collectAsState()
    var selectedAssignee by remember { mutableStateOf(eventFormState.assignee) }
    var showingDatePicker by remember { mutableStateOf(false) }
    var showingTimePicker by remember { mutableStateOf<String?>(null) }
    
    val assignees = EventAssignee.values()
    val formattedDate = remember(eventFormState.date) {
        SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(eventFormState.date)
    }

    // Иницијализујемо изабрану особу када се дијалог отвори
    LaunchedEffect(Unit) {
        Log.d("AddEventDialog", "Дијалог отворен, форма - датум: $formattedDate, особа: ${eventFormState.assignee}")
        selectedAssignee = eventFormState.assignee
    }
    
    // Ефекат који прати промену у eventFormState.assignee
    LaunchedEffect(eventFormState.assignee) {
        Log.d("AddEventDialog", "eventFormState.assignee промењен на: ${eventFormState.assignee}")
        if (eventFormState.assignee.isNotBlank() && selectedAssignee != eventFormState.assignee) {
            Log.d("AddEventDialog", "Ажурирана selectedAssignee из форме: ${eventFormState.assignee}")
            selectedAssignee = eventFormState.assignee
        }
    }

    // Приказ дате пикера када је потребно
    if (showingDatePicker) {
        DatePickerDialog(
            selectedDate = eventFormState.date,
            onDateSelected = { newDate ->
                calendarViewModel.updateEventField("date", newDate)
                showingDatePicker = false
            },
            onDismiss = {
                showingDatePicker = false
            }
        )
    }

    // Приказ тајм пикера када је потребно
    showingTimePicker?.let { pickerType ->
        TimePickerDialog(
            initialHour = when (pickerType) {
                "start" -> eventFormState.startHour
                "end" -> eventFormState.endHour ?: (eventFormState.startHour + 1).coerceAtMost(23)
                else -> 12
            },
            initialMinute = when (pickerType) {
                "start" -> eventFormState.startMinute
                "end" -> eventFormState.endMinute ?: 0
                else -> 0
            },
            onTimeSelected = { hour, minute ->
                when (pickerType) {
                    "start" -> {
                        calendarViewModel.updateEventField("startHour", hour)
                        calendarViewModel.updateEventField("startMinute", minute)
                    }
                    "end" -> {
                        calendarViewModel.updateEventField("endHour", hour)
                        calendarViewModel.updateEventField("endMinute", minute)
                    }
                }
                showingTimePicker = null
            },
            onDismiss = {
                showingTimePicker = null
            }
        )
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (editingEvent != null) "Уреди догађај" else "Додај догађај",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Наслов поље
                OutlinedTextField(
                    value = eventFormState.title,
                    onValueChange = { newTitle -> 
                        calendarViewModel.updateEventField("title", newTitle)
                    },
                    label = { Text("Наслов") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Опис поље
                OutlinedTextField(
                    value = eventFormState.description ?: "",
                    onValueChange = { newDescription -> 
                        calendarViewModel.updateEventField("description", newDescription)
                    },
                    label = { Text("Опис") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )
                
                // Локација поље
                OutlinedTextField(
                    value = eventFormState.location,
                    onValueChange = { newLocation -> 
                        calendarViewModel.updateEventField("location", newLocation)
                    },
                    label = { Text("Локација") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Датум поље
                OutlinedTextField(
                    value = formattedDate,
                    onValueChange = { },
                    label = { Text("Датум") },
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showingDatePicker = true },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Filled.DateRange,
                            contentDescription = "Изабери датум"
                        )
                    }
                )

                // Целодневни догађај
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Целодневни догађај",
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = eventFormState.allDay,
                        onCheckedChange = { isAllDay -> 
                            calendarViewModel.updateEventField("allDay", isAllDay)
                        }
                    )
                }

                // Време почетка и краја
                if (!eventFormState.allDay) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = String.format(
                                    "%02d:%02d",
                                    eventFormState.startHour,
                                    eventFormState.startMinute
                                ),
                                onValueChange = { },
                                label = { Text("Почетак") },
                                readOnly = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showingTimePicker = "start" },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Schedule,
                                        contentDescription = "Изабери време почетка"
                                    )
                                }
                            )

                            OutlinedTextField(
                                value = if (eventFormState.endHour != null && eventFormState.endMinute != null)
                                    String.format(
                                        "%02d:%02d",
                                        eventFormState.endHour,
                                        eventFormState.endMinute
                                    ) else "",
                                onValueChange = { },
                                label = { Text("Крај") },
                                readOnly = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showingTimePicker = "end" },
                                placeholder = { Text("Опционо") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Schedule,
                                        contentDescription = "Изабери време краја"
                                    )
                                }
                            )
                        }
                    }
                }
                
                // Одабир особе - побољшана секција
                Column {
                    Text(
                        text = "Додели особи:",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    
                    // Приказ тренутно изабране особе
                    val currentAssignee = assignees.find { it.name == selectedAssignee }
                    if (currentAssignee != null) {
                        Text(
                            text = "Изабрана особа: ${currentAssignee.displayName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color(android.graphics.Color.parseColor(currentAssignee.color)),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    } else {
                        Log.d("AddEventDialog", "Није пронађен одговарајући assignee за '${selectedAssignee}'")
                    }
                    
                    // Избор особе
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(assignees) { assignee ->
                            val isSelected = assignee.name == selectedAssignee
                            AssigneeAvatar(
                                assignee = assignee,
                                isSelected = isSelected,
                                onClick = {
                                    Log.d("AddEventDialog", "Особа кликнута: ${assignee.name}")
                                    selectedAssignee = assignee.name
                                    
                                    // Експлицитно ажурирамо форму са новом особом и бојом
                                    calendarViewModel.updateEventField("assignee", assignee.name)
                                    calendarViewModel.updateEventField("color", assignee.color)
                                    
                                    Log.d("AddEventDialog", "После клика - форма assignee: ${assignee.name}, color: ${assignee.color}")
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                // Дугмад за акције - сада их смештамо у засебne редове за бољи распоред
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Дугме за сачување/додавање
                    Button(
                        onClick = {
                            if (editingEvent != null) {
                                calendarViewModel.updateEvent()
                            } else {
                                calendarViewModel.addEvent()
                            }
                            onDismissRequest()
                        },
                        enabled = eventFormState.isValid,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (editingEvent != null) "Сачувај" else "Додај")
                    }
                    
                    // Ред са дугмадима за отказивање и брисање
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Дугме за отказивање
                        OutlinedButton(
                            onClick = {
                                if (editingEvent != null) {
                                    calendarViewModel.cancelEditing()
                                }
                                onDismissRequest()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Откажи")
                        }
                        
                        // Дугме за брисање, приказ само при уређивању
                        if (editingEvent != null) {
                            OutlinedButton(
                                onClick = {
                                    editingEvent?.id?.let { calendarViewModel.deleteEvent(it) }
                                    onDismissRequest()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Обриши")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AssigneeAvatar(
    assignee: EventAssignee,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    
    val borderColor = if (isSelected) {
        Color(android.graphics.Color.parseColor(assignee.color))
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }
    
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(80.dp)
            .padding(4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(50.dp)
                .shadow(if (isSelected) 4.dp else 1.dp, CircleShape)
                .background(backgroundColor, CircleShape)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = borderColor,
                    shape = CircleShape
                )
                .clickable { onClick() }
        ) {
            // Експлицитно додајемо типизацију за Text
            Text(
                text = assignee.initial.toString(),
                color = Color(android.graphics.Color.parseColor(assignee.color)),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = assignee.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = contentColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onTimeSelected: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val hour by remember { mutableStateOf(initialHour) }
    val minute by remember { mutableStateOf(initialMinute) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Изаберите време") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Сат")
                        Spacer(modifier = Modifier.height(8.dp))
                        for (h in 0..23) {
                            Text(
                                text = String.format("%02d", h),
                                modifier = Modifier
                                    .clickable {
                                        onTimeSelected(h, minute)
                                    }
                                    .padding(8.dp),
                                fontWeight = if (h == hour) FontWeight.Bold else FontWeight.Normal,
                                color = if (h == hour) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Минут")
                        Spacer(modifier = Modifier.height(8.dp))
                        for (m in 0..59 step 5) {
                            Text(
                                text = String.format("%02d", m),
                                modifier = Modifier
                                    .clickable {
                                        onTimeSelected(hour, m)
                                    }
                                    .padding(8.dp),
                                fontWeight = if (m == minute) FontWeight.Bold else FontWeight.Normal,
                                color = if (m == minute) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onTimeSelected(hour, minute)
                }
            ) {
                Text("ОК")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Откажи")
            }
        }
    )
}

@Composable
fun EventDetailsDialog(
    event: Event,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    // Нађимо објекат EventAssignee који одговара имену особе из догађаја
    val assignee = EventAssignee.values().find { it.name == event.assignee } ?: EventAssignee.EVERYONE
    
    Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Наслов и икона за затварање
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Детаљи догађаја",
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Затвори"
                        )
                    }
                }
                
                Divider()
                
                // Аватар и име особе
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                Color(android.graphics.Color.parseColor(event.color)).copy(alpha = 0.2f),
                                CircleShape
                            )
                            .border(2.dp, Color(android.graphics.Color.parseColor(event.color)), CircleShape)
                    ) {
                        // Експлицитан тип String за Text
                        Text(
                            text = assignee.initial.toString(),
                            color = Color(android.graphics.Color.parseColor(event.color)),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column {
                        Text(
                            text = assignee.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(android.graphics.Color.parseColor(event.color))
                        )
                        Text(
                            text = "Додељено",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Divider()

                // Наслов догађаја
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                // Опис ако постоји
                if (event.description != null && event.description.isNotBlank()) {
                    Text(
                        text = event.description,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Локација ако постоји
                if (event.location.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = event.location,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                
                // Време
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (event.allDay) {
                        Text(
                            text = "Целодневни догађај",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else if (event.startTime != null) {
                        val startTimeText = remember(event.startTime) {
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(event.startTime.toDate())
                        }
                        val endTimeText = remember(event.endTime) {
                            if (event.endTime != null) {
                                SimpleDateFormat("HH:mm", Locale.getDefault()).format(event.endTime.toDate())
                            } else {
                                ""
                            }
                        }
                        
                        Text(
                            text = if (endTimeText.isNotBlank()) {
                                "$startTimeText - $endTimeText"
                            } else {
                                startTimeText
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Дугмад за акције
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Дугме за уређивање
                    OutlinedButton(
                        onClick = {
                            onEdit()
                            onDismiss()
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Уреди"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Уреди")
                    }
                    
                    // Дугме за брисање
                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Обриши"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Обриши")
                    }
                }
            }
        }
    }
}

@Composable
fun DatePickerDialog(
    selectedDate: Date,
    onDateSelected: (Date) -> Unit,
    onDismiss: () -> Unit
) {
    val calendar = Calendar.getInstance().apply { time = selectedDate }
    var year by remember { mutableStateOf(calendar.get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(calendar.get(Calendar.MONTH)) }
    var day by remember { mutableStateOf(calendar.get(Calendar.DAY_OF_MONTH)) }
    
    val monthNames = listOf("Јануар", "Фебруар", "Март", "Април", "Мај", "Јун", "Јул", "Август", "Септембар", "Октобар", "Новембар", "Децембар")
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Изаберите датум") },
        text = {
            Column {
                // Приказ за годину
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Година:")
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { year -= 1 }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Претходна година")
                    }
                    Text(year.toString())
                    IconButton(onClick = { year += 1 }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Следећа година")
                    }
                }
                
                // Приказ за месец
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Месец:")
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { month = (month - 1).coerceIn(0, 11) }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Претходни месец")
                    }
                    Text(monthNames[month])
                    IconButton(
                        onClick = { month = (month + 1).coerceIn(0, 11) }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Следећи месец")
                    }
                }
                
                // Календар за избор дана
                val maxDays = Calendar.getInstance().apply {
                    set(year, month, 1)
                }.getActualMaximum(Calendar.DAY_OF_MONTH)
                
                // Мрежа дана у месецу
                LazyColumn {
                    items((1..maxDays).chunked(7)) { weekDays ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            weekDays.forEach { dayOfMonth ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (dayOfMonth == day) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surface
                                        )
                                        .clickable { 
                                            day = dayOfMonth 
                                        }
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outline,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dayOfMonth.toString(),
                                        color = if (dayOfMonth == day) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Креирамо нови датум на основу изабраних вредности
                    val selectedCalendar = Calendar.getInstance().apply {
                        set(year, month, day)
                    }
                    onDateSelected(selectedCalendar.time)
                }
            ) {
                Text("Изабери")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Откажи")
            }
        }
    )
} 