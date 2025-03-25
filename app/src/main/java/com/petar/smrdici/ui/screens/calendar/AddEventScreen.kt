package com.petar.smrdici.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.data.model.EventColor
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(),
    calendarViewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.Factory(LocalContext.current))
) {
    val authState by authViewModel.authState.collectAsState()
    val user = if (authState is com.petar.smrdici.ui.auth.AuthState.Authenticated) {
        (authState as com.petar.smrdici.ui.auth.AuthState.Authenticated).user
    } else null
    
    val formState by calendarViewModel.eventFormState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var timePickerMode by remember { mutableStateOf(TimePickerMode.START) }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        AppHeader(
            title = "Додај догађај",
            user = user,
            navController = navController,
            showBackButton = true
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Форма за унос догађаја
            OutlinedTextField(
                value = formState.title,
                onValueChange = { newTitle -> calendarViewModel.updateEventField("title", newTitle) },
                label = { Text("Наслов") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
            
            OutlinedTextField(
                value = formState.description ?: "",
                onValueChange = { newDescription -> calendarViewModel.updateEventField("description", newDescription) },
                label = { Text("Опис") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
            
            // Цео дан прекидач
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Цео дан")
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = formState.allDay,
                    onCheckedChange = { isAllDay -> calendarViewModel.updateEventField("allDay", isAllDay) }
                )
            }
            
            // Датум
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Датум:")
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = { showDatePicker = true }
                ) {
                    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("sr"))
                    Text(dateFormat.format(formState.date))
                }
            }
            
            if (!formState.allDay) {
                // Време почетка
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
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
                
                // Време завршетка
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
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
            
            // Локација
            OutlinedTextField(
                value = formState.location,
                onValueChange = { newLocation -> calendarViewModel.updateEventField("location", newLocation) },
                label = { Text("Локација") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
            
            // Боја догађаја
            Text(
                text = "Боја догађаја:",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                EventColor.values().forEach { eventColor ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(android.graphics.Color.parseColor(eventColor.colorHex)))
                            .clickable { calendarViewModel.updateEventField("color", eventColor.colorHex) }
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
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    calendarViewModel.addEvent()
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = formState.isValid
            ) {
                Text("Сачувај догађај")
            }
        }
    }
    
    // Дијалог за избор датума - сада је у @Composable контексту
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = formState.date.time
        )
        
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val newDate = Date(millis)
                            calendarViewModel.updateEventField("date", newDate)
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
    
    // Дијалог за избор времена - сада користимо Material3 TimePickerDialog
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = when (timePickerMode) {
                TimePickerMode.START -> formState.startHour
                TimePickerMode.END -> formState.endHour ?: (formState.startHour + 1).coerceAtMost(23)
            },
            initialMinute = when (timePickerMode) {
                TimePickerMode.START -> formState.startMinute
                TimePickerMode.END -> formState.endMinute ?: 0
            }
        )
        
        // Користимо Dialog компоненту да обмотамо TimePicker
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Изаберите време") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (timePickerMode) {
                            TimePickerMode.START -> {
                                calendarViewModel.updateEventField("startHour", timePickerState.hour)
                                calendarViewModel.updateEventField("startMinute", timePickerState.minute)
                                Log.d("AddEventScreen", "Постављено време почетка: ${timePickerState.hour}:${timePickerState.minute}")
                            }
                            TimePickerMode.END -> {
                                calendarViewModel.updateEventField("endHour", timePickerState.hour)
                                calendarViewModel.updateEventField("endMinute", timePickerState.minute)
                                Log.d("AddEventScreen", "Постављено време краја: ${timePickerState.hour}:${timePickerState.minute}")
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
        )
    }
} 