package com.petar.smrdici.ui.screens.calendar
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.petar.smrdici.data.model.Event
import com.petar.smrdici.data.model.EventAssignee
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
    val datesWithEvents by calendarViewModel.datesWithEvents.collectAsState()
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
    val onRefresh = {
        coroutineScope.launch {
            isRefreshing = true
            calendarViewModel.syncEvents()
            delay(1000)
            isRefreshing = false
        }
        Unit
    }
    
    // Ефекат за учитавање догађаја када се промени selectedDate
    LaunchedEffect(selectedDate.time) {
        Log.d("CalendarScreen", "Изабрани датум промењен: ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(selectedDate)}")
        calendarViewModel.loadEventsForDate(selectedDate)
    }
    
    // Додајемо нови LaunchedEffect за праћење стања учитавања
    LaunchedEffect(calendarUiState) {
        when (val state = calendarUiState) {
            is CalendarUiState.Loading -> {
                Log.d("CalendarScreen", "Учитавање у току...")
            }
            is CalendarUiState.Success -> {
                Log.d("CalendarScreen", "Учитавање завршено: ${state.events.size} догађаја")
                isRefreshing = false
            }
            is CalendarUiState.Error -> {
                Log.e("CalendarScreen", "Грешка: ${state.message}")
                isRefreshing = false
            }
        }
    }
    
    // Додајте ову функцију за генерисање датума за месец
    val dates = remember(selectedDate) {
        val calendar = Calendar.getInstance().apply {
            time = selectedDate
            set(Calendar.DAY_OF_MONTH, 1)  // Постављамо на први дан у месецу
        }
        
        val currentMonth = calendar.get(Calendar.MONTH)
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        
        // Додајемо дане из претходног месеца да попунимо прву недељу
        val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val previousMonthDays = (firstDayOfWeek - Calendar.MONDAY + 7) % 7
        
        calendar.add(Calendar.DAY_OF_MONTH, -previousMonthDays)
        
        // Генеришемо листу датума
        buildList {
            // Додајемо дане из претходног месеца
            repeat(previousMonthDays) {
                add(calendar.time)
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
            
            // Додајемо дане тренутног месеца
            repeat(daysInMonth) {
                add(calendar.time)
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
            
            // Додајемо дане следећег месеца да попунимо последњу недељу
            val remainingDays = (7 - size % 7) % 7
            repeat(remainingDays) {
                add(calendar.time)
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Враћамо на стари начин приказа заглавља
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
                
                // Враћамо CalendarGrid уместо MonthCalendar
                CalendarGrid(
                    dates = dates,
                    selectedDate = selectedDate,
                    datesWithEvents = datesWithEvents,
                    onDateSelected = { date -> 
                        calendarViewModel.selectDate(date)
                    }
                )
                
                when (val state = calendarUiState) {
                    is CalendarUiState.Loading -> {
                        if (!isRefreshing && events.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        } else {
                            EventsList(
                                events = emptyList(),
                                onEventClick = { },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                        }
                    }
                    is CalendarUiState.Success -> {
                        EventsList(
                            events = state.events,
                            onEventClick = { event ->
                                selectedEvent = event
                                showEventDetailsDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                    is CalendarUiState.Error -> {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
        
        FloatingActionButton(
            onClick = {
                calendarViewModel.resetEventForm()
                showAddEventDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, "Додај догађај")
        }
    }
    
    if (showAddEventDialog) {
        AddEventDialog(
            showDialog = showAddEventDialog,
            eventFormState = eventFormState,
            onEventFormChanged = { field, value -> 
                calendarViewModel.updateEventFormField(field, value)
            },
            onSaveClick = {
                if (editingEvent != null) {
                    calendarViewModel.updateEvent()
                } else {
                    calendarViewModel.addEvent()
                }
                showAddEventDialog = false
            },
            onDismissClick = {
                calendarViewModel.cancelEditing()
                showAddEventDialog = false
            },
            isEditing = editingEvent != null
        )
    }
    
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
fun DateCell(
    date: Date,
    isSelected: Boolean,
    hasEvents: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    else -> Color.Transparent
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = SimpleDateFormat("d", Locale.getDefault()).format(date),
                color = if (isSelected) Color.White else Color.Unspecified
            )
            if (hasEvents) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) Color.White 
                            else MaterialTheme.colorScheme.primary
                        )
                )
            }
        }
    }
}

@Composable
fun CalendarGrid(
    dates: List<Date>,
    selectedDate: Date,
    datesWithEvents: Set<Date>,
    onDateSelected: (Date) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(dates) { date ->
            val calendar = Calendar.getInstance().apply { time = date }
            val normalizedDate = calendar.apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time

            DateCell(
                date = date,
                isSelected = isSameDay(selectedDate, date),
                hasEvents = datesWithEvents.any { isSameDay(it, normalizedDate) },
                onClick = { onDateSelected(date) }
            )
        }
    }
}

@Composable
fun EventsList(
    events: List<Event>,
    onEventClick: (Event) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(events) { event ->
            EventCard(
                event = event,
                onClick = { onEventClick(event) }
            )
        }
    }
}

@Composable
fun EventCard(
    event: Event,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Индикатор боје догађаја
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        color = Color(android.graphics.Color.parseColor(event.color ?: "#4285F4")),
                        shape = CircleShape
                    )
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                event.startTime?.toDate()?.let { startTime ->
                    Text(
                        text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(startTime),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Индикатор особе задужене за догађај
            val assignee = EventAssignee.values().find { it.name == event.assignee } ?: EventAssignee.EVERYONE
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = Color(android.graphics.Color.parseColor(assignee.color)).copy(alpha = 0.2f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = assignee.initial.toString(),
                    color = Color(android.graphics.Color.parseColor(assignee.color)),
                    style = MaterialTheme.typography.labelMedium
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
    showDialog: Boolean,
    eventFormState: EventFormState,
    onEventFormChanged: (String, Any) -> Unit,
    onSaveClick: () -> Unit,
    onDismissClick: () -> Unit,
    isEditing: Boolean = false
) {
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    if (!showDialog) return

    Dialog(
        onDismissRequest = onDismissClick,
        properties = DialogProperties(dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (isEditing) "Уреди догађај" else "Нови догађај",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Наслов поље
                OutlinedTextField(
                    value = eventFormState.title,
                    onValueChange = { onEventFormChanged("title", it) },
                    label = { Text("Наслов") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // Опис поље
                OutlinedTextField(
                    value = eventFormState.description,
                    onValueChange = { onEventFormChanged("description", it) },
                    label = { Text("Опис") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )
                
                // Локација поље
                OutlinedTextField(
                    value = eventFormState.location,
                    onValueChange = { onEventFormChanged("location", it) },
                    label = { Text("Локација") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Датум поље
                OutlinedTextField(
                    value = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(eventFormState.date),
                    onValueChange = { },
                    label = { Text("Датум") },
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { },
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
                            onEventFormChanged("allDay", isAllDay)
                        }
                    )
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
                    val currentAssignee = EventAssignee.values().find { it.name == eventFormState.assignee }
                    if (currentAssignee != null) {
                            Text(
                            text = "Изабрана особа: ${currentAssignee.displayName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color(android.graphics.Color.parseColor(currentAssignee.color)),
                            modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                } else {
                        Log.d("AddEventDialog", "Није пронађен одговарајући assignee за '${eventFormState.assignee}'")
                    }
                    
                    // Избор особе
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(EventAssignee.values()) { assignee ->
                            AssigneeAvatar(
                                assignee = assignee,
                                isSelected = eventFormState.assignee == assignee.name,
                                onClick = {
                                    Log.d("AddEventDialog", "Особа кликнута: ${assignee.name}")
                                    onEventFormChanged("assignee", assignee.name)
                                    onEventFormChanged("color", assignee.color)
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Време почетка
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Време почетка:")
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { showStartTimePicker = true }
                    ) {
                        Text(String.format("%02d:%02d", eventFormState.startHour, eventFormState.startMinute))
                    }
                }

                if (!eventFormState.allDay) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Време завршетка:")
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = { showEndTimePicker = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(
                                if (eventFormState.endHour != null && eventFormState.endMinute != null)
                                    String.format("%02d:%02d", eventFormState.endHour, eventFormState.endMinute)
                                else
                                    "Изабери"
                            )
                        }
                    }
                }

                // Дугмад за акције
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissClick) {
                        Text("Откажи")
                    }
                    Button(onClick = onSaveClick) {
                        Text(if (isEditing) "Сачувај измене" else "Додај")
                    }
                }
            }
        }
    }

    // Дијалози за избор времена
    if (showStartTimePicker) {
        TimePickerDialog(
            showDialog = true,
            initialHour = eventFormState.startHour,
            initialMinute = eventFormState.startMinute,
            onTimeSelected = { hour, minute ->
                onEventFormChanged("startHour", hour)
                onEventFormChanged("startMinute", minute)
                showStartTimePicker = false
            },
            onDismiss = { showStartTimePicker = false }
        )
    }

    if (!eventFormState.allDay && showEndTimePicker) {
        TimePickerDialog(
            showDialog = true,
            initialHour = eventFormState.endHour ?: eventFormState.startHour,
            initialMinute = eventFormState.endMinute ?: eventFormState.startMinute,
            onTimeSelected = { hour, minute ->
                onEventFormChanged("endHour", hour)
                onEventFormChanged("endMinute", minute)
                showEndTimePicker = false
            },
            onDismiss = { showEndTimePicker = false }
        )
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
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 6.dp
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Заглавље са иконом за затварање
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Аватар и име
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Аватар особе
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = Color(android.graphics.Color.parseColor(assignee.color)).copy(alpha = 0.2f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = assignee.initial.toString(),
                                color = Color(android.graphics.Color.parseColor(assignee.color)),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Text(
                            text = assignee.displayName,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    // Дугме за затварање
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Затвори"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Наслов догађаја
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.headlineSmall
                )

                // Опис ако постоји
                if (event.description != null && event.description.isNotBlank()) {
                    Text(
                        text = event.description,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))

                // Дугмад за акције
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    // Дугме за брисање
                    FilledTonalButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Обриши")
                    }
                    
                    // Дугме за уређивање
                    FilledTonalButton(
                        onClick = onEdit,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Text("Уреди")
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

// Помоћна функција за поређење датума
private fun isSameDay(date1: Date, date2: Date): Boolean {
    val cal1 = Calendar.getInstance().apply { time = date1 }
    val cal2 = Calendar.getInstance().apply { time = date2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
           cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH)
} 