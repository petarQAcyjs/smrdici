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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.data.model.Event
import com.petar.smrdici.data.model.EventColor
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.navigation.Screen
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.platform.LocalContext

@Composable
fun CalendarScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(),
    calendarViewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.Factory(LocalContext.current))
) {
    val authState by authViewModel.authState.collectAsState()
    val uiState by calendarViewModel.uiState.collectAsState()
    val user = if (authState is AuthState.Authenticated) {
        (authState as AuthState.Authenticated).user
    } else null
    
    var selectedDate by remember { mutableStateOf(Date()) }
    var showAddEventDialog by remember { mutableStateOf(false) }
    var selectedEvent by remember { mutableStateOf<Event?>(null) }
    var showEventDetailsDialog by remember { mutableStateOf(false) }
    
    // Учитавамо догађаје при промени датума
    LaunchedEffect(selectedDate) {
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
                onDateSelected = { selectedDate = it },
                events = if (uiState is CalendarUiState.Success) (uiState as CalendarUiState.Success).events else emptyList()
            )
            
            // Приказ догађаја за изабрани датум
            when (uiState) {
                is CalendarUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is CalendarUiState.Success -> {
                    val events = (uiState as CalendarUiState.Success).events
                    EventsList(
                        events = events,
                        onEventClick = { event ->
                            selectedEvent = event
                            showEventDetailsDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(16.dp)
                    )
                }
                is CalendarUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (uiState as CalendarUiState.Error).message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        
        // Плутајуће дугме за додавање догађаја - премештено изван Column и EventsList
        FloatingActionButton(
            onClick = {
                showAddEventDialog = true
            },
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
    
    // Дијалог за додавање догађаја
    if (showAddEventDialog) {
        AddEventDialog(
            onDismiss = { showAddEventDialog = false },
            onEventAdded = {
                showAddEventDialog = false
                // Користимо loadEventsForDate уместо loadEvents
                calendarViewModel.loadEventsForDate(selectedDate)
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
                text = monthFormat.format(calendar.time).capitalize(),
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
fun CalendarDay(
    day: Int,
    isSelected: Boolean,
    hasEvents: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary
                else Color.Transparent
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = day.toString(),
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
        
        if (hasEvents) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.primary
                    )
                    .align(Alignment.BottomCenter)
            )
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
                EventItem(event = event, onClick = { onEventClick(event) })
            }
        }
    }
}

@Composable
fun EventItem(
    event: Event,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(android.graphics.Color.parseColor(event.color)))
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                event.description?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Време догађаја
            Column(
                horizontalAlignment = Alignment.End
            ) {
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                
                event.startTime?.toDate()?.let { startDate ->
                    Text(
                        text = timeFormat.format(startDate),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                event.endTime?.toDate()?.let { endDate ->
                    Text(
                        text = timeFormat.format(endDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventDialog(
    onDismiss: () -> Unit,
    onEventAdded: () -> Unit,
    calendarViewModel: CalendarViewModel
) {
    val formState by calendarViewModel.eventFormState.collectAsState()
    val colors = EventColor.values()
    
    // Додајемо стање за приказ бирача времена
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Додај нови догађај") },
        text = {
            Column {
                // Наслов догађаја
                OutlinedTextField(
                    value = formState.title,
                    onValueChange = { newTitle -> calendarViewModel.updateEventForm { it.copy(title = newTitle) } },
                    label = { Text("Наслов") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Опис
                OutlinedTextField(
                    value = formState.description,
                    onValueChange = { newDescription -> calendarViewModel.updateEventForm { it.copy(description = newDescription) } },
                    label = { Text("Опис") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Локација
                OutlinedTextField(
                    value = formState.location,
                    onValueChange = { newLocation -> calendarViewModel.updateEventForm { it.copy(location = newLocation) } },
                    label = { Text("Локација") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Датум
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Датум:")
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = { showDatePicker = true }
                    ) {
                        Text(SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(formState.date))
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Цео дан
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Цео дан:")
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = formState.allDay,
                        onCheckedChange = { isAllDay -> calendarViewModel.updateEventForm { it.copy(allDay = isAllDay) } }
                    )
                }
                
                if (!formState.allDay) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Време почетка
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Почетак:")
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = { showStartTimePicker = true }
                        ) {
                            Text(
                                String.format(
                                    "%02d:%02d",
                                    formState.startHour,
                                    formState.startMinute
                                )
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Време завршетка
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Завршетак:")
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = { showEndTimePicker = true }
                        ) {
                            Text(
                                if (formState.endHour != null && formState.endMinute != null) {
                                    String.format(
                                        "%02d:%02d",
                                        formState.endHour,
                                        formState.endMinute
                                    )
                                } else {
                                    "Изабери време"
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Боја догађаја
                Text("Боја догађаја:")
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    colors.forEach { eventColor ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(eventColor.colorHex)))
                                .clickable { calendarViewModel.updateEventForm { it.copy(color = eventColor.colorHex) } }
                                .then(
                                    if (formState.color == eventColor.colorHex) {
                                        Modifier.border(
                                            width = 2.dp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shape = CircleShape
                                        )
                                    } else Modifier
                                )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    calendarViewModel.addEvent()
                    onEventAdded()
                },
                enabled = formState.title.isNotBlank()
            ) {
                Text("Додај")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Откажи")
            }
        }
    )
    
    // Приказ бирача времена почетка
    if (showStartTimePicker) {
        TimePickerDialog(
            onDismissRequest = { showStartTimePicker = false },
            confirmButton = {
                Button(
                    onClick = { showStartTimePicker = false }
                ) {
                    Text("ОК")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showStartTimePicker = false }
                ) {
                    Text("Откажи")
                }
            }
        ) {
            // Овде би требало да буде компонента за избор времена
            // За сада ћемо користити једноставне бираче
            Column {
                Text("Изаберите време почетка", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Сат
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Сат")
                        Spacer(modifier = Modifier.height(8.dp))
                        for (hour in 0..23) {
                            Text(
                                text = String.format("%02d", hour),
                                modifier = Modifier
                                    .clickable {
                                        calendarViewModel.updateEventForm {
                                            it.copy(startHour = hour)
                                        }
                                    }
                                    .padding(8.dp),
                                fontWeight = if (hour == formState.startHour) FontWeight.Bold else FontWeight.Normal,
                                color = if (hour == formState.startHour) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    
                    // Минут
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Минут")
                        Spacer(modifier = Modifier.height(8.dp))
                        for (minute in 0..59 step 5) {
                            Text(
                                text = String.format("%02d", minute),
                                modifier = Modifier
                                    .clickable {
                                        calendarViewModel.updateEventForm {
                                            it.copy(startMinute = minute)
                                        }
                                    }
                                    .padding(8.dp),
                                fontWeight = if (minute == formState.startMinute) FontWeight.Bold else FontWeight.Normal,
                                color = if (minute == formState.startMinute) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Приказ бирача времена завршетка
    if (showEndTimePicker) {
        TimePickerDialog(
            onDismissRequest = { showEndTimePicker = false },
            confirmButton = {
                Button(
                    onClick = { showEndTimePicker = false }
                ) {
                    Text("ОК")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEndTimePicker = false }
                ) {
                    Text("Откажи")
                }
            }
        ) {
            // Овде би требало да буде компонента за избор времена
            // За сада ћемо користити једноставне бираче
            Column {
                Text("Изаберите време завршетка", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Сат
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Сат")
                        Spacer(modifier = Modifier.height(8.dp))
                        for (hour in 0..23) {
                            Text(
                                text = String.format("%02d", hour),
                                modifier = Modifier
                                    .clickable {
                                        calendarViewModel.updateEventForm {
                                            it.copy(endHour = hour)
                                        }
                                    }
                                    .padding(8.dp),
                                fontWeight = if (hour == formState.endHour) FontWeight.Bold else FontWeight.Normal,
                                color = if (hour == formState.endHour) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    
                    // Минут
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Минут")
                        Spacer(modifier = Modifier.height(8.dp))
                        for (minute in 0..59 step 5) {
                            Text(
                                text = String.format("%02d", minute),
                                modifier = Modifier
                                    .clickable {
                                        calendarViewModel.updateEventForm {
                                            it.copy(endMinute = minute)
                                        }
                                    }
                                    .padding(8.dp),
                                fontWeight = if (minute == formState.endMinute) FontWeight.Bold else FontWeight.Normal,
                                color = if (minute == formState.endMinute) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

enum class TimePickerMode {
    START, END
}

@Composable
fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        text = { content() },
        confirmButton = confirmButton,
        dismissButton = dismissButton
    )
}

// Помоћна функција за капитализацију првог слова
fun String.capitalize(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } 
}

@Composable
fun EventDetailsDialog(
    event: Event,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Детаљи догађаја") },
        text = {
            Column {
                Text("Наслов: ${event.title}")
                Text("Опис: ${event.description}")
                Text("Локација: ${event.location}")
                Text("Почетак: ${event.startTime?.toDate()?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) }}")
                Text("Завршетак: ${event.endTime?.toDate()?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) }}")
                Text("Цео дан: ${event.allDay}")
                Text("Боја: ${event.color}")
            }
        },
        confirmButton = {
            Button(
                onClick = onDelete
            ) {
                Text("Обриши")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Затвори")
            }
        }
    )
} 