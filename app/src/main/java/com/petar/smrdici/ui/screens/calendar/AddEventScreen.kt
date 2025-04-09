package com.petar.smrdici.ui.screens.calendar

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.data.model.EventColor
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Додајемо помоћну класу за време
data class EventTime(
    val hour: Int,
    val minute: Int
) {
    @SuppressLint("DefaultLocale")
    fun formatted(): String = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
    
    companion object {
        fun fromDate(date: Date): EventTime {
            val calendar = Calendar.getInstance().apply { time = date }
            return EventTime(
                hour = calendar.get(Calendar.HOUR_OF_DAY),
                minute = calendar.get(Calendar.MINUTE)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(),
    calendarViewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.Factory())
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
                value = formState.description,
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
                            formState.startTime?.formatted() ?: "--:--"
                        )
                    }
                }
                
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
                        Text(formState.endTime?.formatted() ?: "Није постављено")
                    }
                }
                
                // Валидација времена
                formState.startTime?.let { startTime ->
                    formState.endTime?.let { endTime ->
                        val startMinutes = startTime.hour * 60 + startTime.minute
                        val endMinutes = endTime.hour * 60 + endTime.minute
                        
                        if (endMinutes <= startMinutes) {
                            Text(
                                text = "Време завршетка мора бити након времена почетка",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
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
                EventColor.entries.forEach { eventColor ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(eventColor.colorHex.toColorInt()))
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
        
        DatePickerDialog(
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
    
    // Дијалог за избор времена
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = when (timePickerMode) {
                TimePickerMode.START -> formState.startTime?.hour ?: 0
                TimePickerMode.END -> formState.endTime?.hour ?: (formState.startTime?.hour?.plus(1) ?: 0)
            },
            initialMinute = when (timePickerMode) {
                TimePickerMode.START -> formState.startTime?.minute ?: 0
                TimePickerMode.END -> formState.endTime?.minute ?: 0
            }
        )
        
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Изаберите време") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newTime = EventTime(
                            hour = timePickerState.hour,
                            minute = timePickerState.minute
                        )
                        when (timePickerMode) {
                            TimePickerMode.START -> {
                                calendarViewModel.updateEventField("startTime", newTime)
                                Log.d("AddEventScreen", "Постављено време почетка: ${newTime.formatted()}")
                            }
                            TimePickerMode.END -> {
                                calendarViewModel.updateEventField("endTime", newTime)
                                Log.d("AddEventScreen", "Постављено време краја: ${newTime.formatted()}")
                            }
                        }
                        showTimePicker = false
                    }
                ) {
                    Text("ОК")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Откажи")
                }
            }
        )
    }
} 