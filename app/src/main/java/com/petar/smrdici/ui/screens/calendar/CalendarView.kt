package com.petar.smrdici.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import com.petar.smrdici.data.model.Event
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CalendarView(
    selectedDate: Date,
    onDateSelected: (Date) -> Unit,
    events: List<Event>,
    modifier: Modifier = Modifier
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
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Заглавље календара - поједностављен layout без додатних модификатора
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
                            modifier = Modifier.size(40.dp)
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
            // Једноставнији clickable без додатних модификатора
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

// Помоћна функција за капитализацију првог слова
fun String.capitalize(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } 
} 