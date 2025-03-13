package com.petar.smrdici.ui.screens.calendar

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.data.model.Event
import com.petar.smrdici.data.model.EventColor
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    navController: NavController,
    viewModel: CalendarViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val formState by viewModel.eventFormState.collectAsState()
    var showAddEventDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TopAppBar(
            title = { Text("Породични Календар") },
            actions = {
                IconButton(onClick = { showAddEventDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Додај догађај")
                }
            }
        )
        
        // Једноставан календарски приказ
        SimpleCalendarView(
            selectedDate = selectedDate,
            onDateSelected = { viewModel.selectDate(it) }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
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
            
            is CalendarUiState.Error -> {
                val errorState = uiState as CalendarUiState.Error
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorState.message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            is CalendarUiState.Success -> {
                val successState = uiState as CalendarUiState.Success
                val eventsForSelectedDate = successState.events.filter { event ->
                    val eventDate = event.startTime.toDate()
                    val calendar1 = Calendar.getInstance().apply { time = eventDate }
                    val calendar2 = Calendar.getInstance().apply { time = selectedDate }
                    
                    calendar1.get(Calendar.YEAR) == calendar2.get(Calendar.YEAR) &&
                    calendar1.get(Calendar.MONTH) == calendar2.get(Calendar.MONTH) &&
                    calendar1.get(Calendar.DAY_OF_MONTH) == calendar2.get(Calendar.DAY_OF_MONTH)
                }
                
                if (eventsForSelectedDate.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Нема догађаја за изабрани датум")
                    }
                } else {
                    Text(
                        text = "Догађаји за ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(selectedDate)}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    
                    LazyColumn(
                        modifier = Modifier.weight(1f)
                    ) {
                        items(eventsForSelectedDate) { event ->
                            EventItem(
                                event = event,
                                onDelete = { viewModel.deleteEvent(event.id) }
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Дијалог за додавање новог догађаја
    if (showAddEventDialog) {
        AddEventDialog(
            formState = formState,
            onFormChanged = { updatedForm -> viewModel.updateEventForm { updatedForm } },
            onAddEvent = {
                viewModel.addEvent()
                showAddEventDialog = false
            },
            onDismiss = { showAddEventDialog = false }
        )
    }
}

@Composable
fun SimpleCalendarView(
    selectedDate: Date,
    onDateSelected: (Date) -> Unit
) {
    val calendar = Calendar.getInstance()
    calendar.time = selectedDate
    
    val currentMonth = calendar.get(Calendar.MONTH)
    val currentYear = calendar.get(Calendar.YEAR)
    val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
    
    // Приказ месеца и године
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            calendar.add(Calendar.MONTH, -1)
            onDateSelected(calendar.time)
        }) {
            Text("<")
        }
        
        Text(
            text = SimpleDateFormat("MMMM yyyy", Locale("sr")).format(selectedDate),
            style = MaterialTheme.typography.titleMedium
        )
        
        IconButton(onClick = {
            calendar.add(Calendar.MONTH, 1)
            onDateSelected(calendar.time)
        }) {
            Text(">")
        }
    }
    
    Spacer(modifier = Modifier.height(8.dp))
    
    // Приказ дана у недељи
    Row(modifier = Modifier.fillMaxWidth()) {
        val daysOfWeek = listOf("Пон", "Уто", "Сре", "Чет", "Пет", "Суб", "Нед")
        daysOfWeek.forEach { day ->
            Text(
                text = day,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    
    Spacer(modifier = Modifier.height(4.dp))
    
    // Добијање првог дана у месецу
    val firstDayCalendar = Calendar.getInstance()
    firstDayCalendar.set(currentYear, currentMonth, 1)
    val firstDayOfWeek = firstDayCalendar.get(Calendar.DAY_OF_WEEK)
    
    // Прилагођавање за почетак недеље од понедељка (у Java Calendar, недеља је 1)
    val offset = if (firstDayOfWeek == Calendar.SUNDAY) 6 else firstDayOfWeek - 2
    
    // Добијање броја дана у месецу
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    
    // Приказ дана у месецу
    val rows = (offset + daysInMonth + 6) / 7 // Број редова потребних за приказ месеца
    
    Column {
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val day = row * 7 + col - offset + 1
                    
                    if (day in 1..daysInMonth) {
                        val dayCalendar = Calendar.getInstance()
                        dayCalendar.set(currentYear, currentMonth, day)
                        
                        val isSelected = day == currentDay
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                                )
                                .clickable {
                                    calendar.set(Calendar.DAY_OF_MONTH, day)
                                    onDateSelected(calendar.time)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day.toString(),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                       else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        // Празан простор за дане који не припадају тренутном месецу
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EventItem(
    event: Event,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp)
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
                    .background(
                        color = Color(android.graphics.Color.parseColor(event.color)),
                        shape = CircleShape
                    )
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Детаљи догађаја
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                if (event.description.isNotBlank()) {
                    Text(
                        text = event.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val timeText = if (event.allDay) {
                        "Цео дан"
                    } else {
                        val startTime = timeFormat.format(event.startTime.toDate())
                        val endTime = event.endTime?.let { timeFormat.format(it.toDate()) }
                        if (endTime != null) "$startTime - $endTime" else startTime
                    }
                    
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    if (event.location.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = event.location,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Обриши догађај"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventDialog(
    formState: EventFormState,
    onFormChanged: (EventFormState) -> Unit,
    onAddEvent: () -> Unit,
    onDismiss: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var timePickerMode by remember { mutableStateOf(TimePickerMode.START) }
    
    val colors = EventColor.values()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Додај нови догађај") },
        text = {
            Column {
                // Наслов догађаја
                OutlinedTextField(
                    value = formState.title,
                    onValueChange = { onFormChanged(formState.copy(title = it)) },
                    label = { Text("Наслов") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Опис
                OutlinedTextField(
                    value = formState.description,
                    onValueChange = { onFormChanged(formState.copy(description = it)) },
                    label = { Text("Опис") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Локација
                OutlinedTextField(
                    value = formState.location,
                    onValueChange = { onFormChanged(formState.copy(location = it)) },
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
                    Button(onClick = { showDatePicker = true }) {
                        Text(SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(formState.date))
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Цео дан
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Цео дан")
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = formState.allDay,
                        onCheckedChange = { onFormChanged(formState.copy(allDay = it)) }
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
                            onClick = {
                                timePickerMode = TimePickerMode.START
                                showTimePicker = true
                            }
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
                            onClick = {
                                timePickerMode = TimePickerMode.END
                                showTimePicker = true
                            }
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
                                .clickable { onFormChanged(formState.copy(color = eventColor.colorHex)) }
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
                onClick = onAddEvent,
                enabled = formState.isValid
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
    
    // Дијалог за избор датума
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = formState.date.time
        )
        
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val newDate = Date(millis)
                            onFormChanged(formState.copy(date = newDate))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("ОК")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false }
                ) {
                    Text("Откажи")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    
    // Дијалог за избор времена
    if (showTimePicker) {
        val hour = when (timePickerMode) {
            TimePickerMode.START -> formState.startHour
            TimePickerMode.END -> formState.endHour ?: formState.startHour
        }
        
        val minute = when (timePickerMode) {
            TimePickerMode.START -> formState.startMinute
            TimePickerMode.END -> formState.endMinute ?: formState.startMinute
        }
        
        val timePickerState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute
        )
        
        TimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (timePickerMode) {
                            TimePickerMode.START -> {
                                onFormChanged(
                                    formState.copy(
                                        startHour = timePickerState.hour,
                                        startMinute = timePickerState.minute
                                    )
                                )
                            }
                            TimePickerMode.END -> {
                                onFormChanged(
                                    formState.copy(
                                        endHour = timePickerState.hour,
                                        endMinute = timePickerState.minute
                                    )
                                )
                            }
                        }
                        showTimePicker = false
                    }
                ) {
                    Text("ОК")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTimePicker = false }
                ) {
                    Text("Откажи")
                }
            }
        ) {
            TimePicker(state = timePickerState)
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