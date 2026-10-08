package com.petar.smrdici.ui.screens.calendar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.toColorInt
import androidx.lifecycle.viewmodel.compose.viewModel
import com.petar.smrdici.R
import com.petar.smrdici.data.model.Event
import com.petar.smrdici.data.model.EventAssignee
import com.petar.smrdici.notification.NotificationHelper
import com.petar.smrdici.ui.components.StandardPullRefreshIndicator
import com.petar.smrdici.ui.components.TimePickerWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CalendarScreen(
    calendarViewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.Factory())
) {
    val calendarUiState by calendarViewModel.uiState.collectAsState()
    val eventFormState by calendarViewModel.eventFormState.collectAsState()
    val selectedDate by calendarViewModel.selectedDate.collectAsState()
    val events by calendarViewModel.events.collectAsState()
    val editingEvent by calendarViewModel.editingEvent.collectAsState()
    val datesWithEvents by calendarViewModel.datesWithEvents.collectAsState()
    val datesWithBirthdays by calendarViewModel.datesWithBirthdays.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            CoroutineScope(Dispatchers.Main).launch {
                snackbarHostState.showSnackbar("Потребна је дозвола за обавештења да бисте примали подсетнике.")
            }
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!NotificationHelper.canScheduleExactAlarms(context)) {
                CoroutineScope(Dispatchers.Main).launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "Потребна је дозвола за тачне аларме да бисте примали тачна обавештења.",
                        actionLabel = "Дозволи"
                    )
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        NotificationHelper.getExactAlarmSettingsIntent(context)?.let { intent ->
                            context.startActivity(intent)
                        }
                    }
                }
            }
        }
    }

    var showAddEventDialog by remember { mutableStateOf(false) }
    var selectedEvent by remember { mutableStateOf<Event?>(null) }
    var showEventDetailsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(editingEvent) {
        if (editingEvent != null) {
            showAddEventDialog = true
        }
    }

    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            coroutineScope.launch {
                isRefreshing = true
                calendarViewModel.syncEvents()
                delay(1000.milliseconds)
                isRefreshing = false
            }
        }
    )

    LaunchedEffect(selectedDate.time) {
        calendarViewModel.loadEventsForDate(selectedDate)
    }

    LaunchedEffect(calendarUiState) {
        when (calendarUiState) {
            is CalendarUiState.Success -> isRefreshing = false
            is CalendarUiState.Error -> isRefreshing = false
            else -> {}
        }
    }

    val dates = remember(selectedDate) {
        val calendar = Calendar.getInstance().apply {
            time = selectedDate
            set(Calendar.DAY_OF_MONTH, 1)
        }

        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val previousMonthDays = (firstDayOfWeek - Calendar.MONDAY + 7) % 7

        calendar.add(Calendar.DAY_OF_MONTH, -previousMonthDays)

        buildList {
            repeat(previousMonthDays) {
                add(calendar.time)
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
            repeat(daysInMonth) {
                add(calendar.time)
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
            val remainingDays = (7 - size % 7) % 7
            repeat(remainingDays) {
                add(calendar.time)
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        calendarViewModel.resetEventForm()
                        showAddEventDialog = true
                    },
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, "Додај догађај")
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val calendar = Calendar.getInstance().apply { time = selectedDate }
                            calendar.add(Calendar.MONTH, -1)
                            calendarViewModel.selectDate(calendar.time)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Претходни месец"
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = SimpleDateFormat("MMMM yyyy", Locale.forLanguageTag("sr")).format(selectedDate),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        val today = remember { Date() }
                        val isCurrentMonth = remember(selectedDate) {
                            val calSelected = Calendar.getInstance().apply { time = selectedDate }
                            val calToday = Calendar.getInstance().apply { time = today }
                            calSelected.get(Calendar.MONTH) == calToday.get(Calendar.MONTH) &&
                                    calSelected.get(Calendar.YEAR) == calToday.get(Calendar.YEAR)
                        }

                        if (!isCurrentMonth) {
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    calendarViewModel.selectDate(Date())
                                },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 10.dp,
                                    vertical = 4.dp
                                ),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Today,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Данас",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            val calendar = Calendar.getInstance().apply { time = selectedDate }
                            calendar.add(Calendar.MONTH, 1)
                            calendarViewModel.selectDate(calendar.time)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Следећи месец",
                            modifier = Modifier.rotate(180f)
                        )
                    }
                }

                CalendarGrid(
                    dates = dates,
                    selectedDate = selectedDate,
                    datesWithEvents = datesWithEvents,
                    datesWithBirthdays = datesWithBirthdays,
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
                                selectedDate = selectedDate,
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
                            selectedDate = selectedDate,
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

        StandardPullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    if (showAddEventDialog) {
        AddEventDialog(
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
    selectedDate: Date,
    hasEvents: Boolean,
    hasBirthday: Boolean,
    onClick: () -> Unit
) {
    val isSelected = isSameDay(selectedDate, date)
    val isToday = isSameDay(Date(), date)

    val isCurrentMonth = remember(date, selectedDate) {
        val calDate = Calendar.getInstance().apply { time = date }
        val calSelected = Calendar.getInstance().apply { time = selectedDate }
        calDate.get(Calendar.MONTH) == calSelected.get(Calendar.MONTH) &&
                calDate.get(Calendar.YEAR) == calSelected.get(Calendar.YEAR)
    }

    val textColor = when {
        isSelected -> Color.White
        isToday -> MaterialTheme.colorScheme.primary
        !isCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
            )
            .then(
                if (isToday && !isSelected) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = SimpleDateFormat("d", LocalLocale.current.platformLocale).format(date),
                color = textColor,
                fontSize = 13.sp,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
            )

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(14.dp)
                    .padding(top = 1.dp)
            ) {
                if (hasBirthday) {
                    Text(
                        text = "🎂",
                        fontSize = 10.sp,
                        lineHeight = 10.sp
                    )
                }
                if (hasEvents) {
                    if (hasBirthday) Spacer(modifier = Modifier.width(2.dp))
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
}

@Composable
fun CalendarGrid(
    dates: List<Date>,
    selectedDate: Date,
    datesWithEvents: Set<Date>,
    datesWithBirthdays: Set<Date>,
    onDateSelected: (Date) -> Unit
) {
    val daysOfWeek = remember { listOf("П", "У", "С", "Ч", "П", "С", "Н") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            daysOfWeek.forEach { day ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

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

                val hasBirthday = datesWithBirthdays.any { isSameDay(it, normalizedDate) }
                val hasNormalEvents = datesWithEvents.any { isSameDay(it, normalizedDate) }

                DateCell(
                    date = date,
                    selectedDate = selectedDate,
                    hasEvents = hasNormalEvents,
                    hasBirthday = hasBirthday,
                    onClick = { onDateSelected(date) }
                )
            }
        }

        // --- ELEGANTNI RAZDVOJNIK (DIVIDER) ISPOD KALENDARA ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
            )
        }
    }
}

@Composable
fun EventsList(
    events: List<Event>,
    onEventClick: (Event) -> Unit,
    selectedDate: Date,
    modifier: Modifier = Modifier
) {
    val srLocale = remember { Locale.forLanguageTag("sr") }

    val isToday = remember(selectedDate) {
        isSameDay(Date(), selectedDate)
    }

    val dayNumber = remember(selectedDate) {
        SimpleDateFormat("d", srLocale).format(selectedDate)
    }

    val dayOfWeekName = remember(selectedDate) {
        SimpleDateFormat("EEEE", srLocale).format(selectedDate)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(srLocale) else it.toString() }
    }

    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // --- NOVO ZAGLAVLJE SA VERTIKALNIM DIVIDEROM PORED DANA ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Plava linija (Vertical Divider) sa lijeve strane
                Box(
                    modifier = Modifier
                        .height(52.dp)
                        .width(3.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = if (isToday) "ДАНАС" else "ДАТУМ",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = dayNumber,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 32.sp
                    )
                    Text(
                        text = dayOfWeekName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "${events.size} ${if (events.size == 1) "догађај" else "догађаја"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- LISTA DOGAĐAJA ---
        if (events.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .padding(bottom = 12.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )

                    Text(
                        text = "Нема догађаја за изабрани датум",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = events,
                    key = { event -> event.id ?: event.hashCode() }
                ) { event ->
                    EventItem(
                        event = event,
                        onClick = { onEventClick(event) }
                    )
                }
            }
        }
    }
}

@Composable
fun EventItem(
    event: Event,
    onClick: () -> Unit
) {
    val assignee = EventAssignee.entries.find { it.name == event.assignee } ?: EventAssignee.EVERYONE

    val timeText = remember(event.allDay, event.startTime, event.endTime) {
        if (!event.allDay && event.startTime != null) {
            val start = SimpleDateFormat("HH:mm", Locale.getDefault()).format(event.startTime.toDate())
            val end = event.endTime?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it.toDate()) }
            if (end != null) "$start - $end" else start
        } else {
            "Целодневни догађај"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        Color(event.color.toColorInt()).copy(alpha = 0.25f),
                        CircleShape
                    )
                    .border(1.dp, Color(event.color.toColorInt()), CircleShape)
            ) {
                val avatarRes = when(assignee) {
                    EventAssignee.EVERYONE -> R.drawable.avatar_everyone
                    EventAssignee.PETAR -> R.drawable.avatar_petar
                    EventAssignee.NATASA -> R.drawable.avatar_natasa
                    EventAssignee.MILICA -> R.drawable.avatar_milica
                    EventAssignee.BOGDAN -> R.drawable.avatar_bogdan
                }

                Image(
                    painter = painterResource(id = avatarRes),
                    contentDescription = assignee.displayName,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = if (event.isRecurringYearly) "🎂 ${event.title}" else event.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (event.location.isNotBlank()) {
                    Text(
                        text = event.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventDialog(
    eventFormState: EventFormState,
    onEventFormChanged: (String, Any) -> Unit,
    onSaveClick: () -> Unit,
    onDismissClick: () -> Unit,
    isEditing: Boolean = false
) {
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

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

                OutlinedTextField(
                    value = eventFormState.title,
                    onValueChange = { text ->
                        onEventFormChanged("title", if (text.isNotEmpty()) {
                            text.replaceFirstChar { it.uppercase() }
                        } else {
                            text
                        })
                    },
                    label = { Text("Наслов") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = eventFormState.description,
                    onValueChange = { text ->
                        onEventFormChanged("description", if (text.isNotEmpty()) {
                            text.replaceFirstChar { it.uppercase() }
                        } else {
                            text
                        })
                    },
                    label = { Text("Опис") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )

                OutlinedTextField(
                    value = eventFormState.location,
                    onValueChange = { text ->
                        onEventFormChanged("location", if (text.isNotEmpty()) {
                            text.replaceFirstChar { it.uppercase() }
                        } else {
                            text
                        })
                    },
                    label = { Text("Локација") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = SimpleDateFormat("dd.MM.yyyy", LocalLocale.current.platformLocale).format(eventFormState.date),
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎂 Rođendan / Godišnjica",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = eventFormState.isRecurringYearly,
                        onCheckedChange = { isRecurringYearly ->
                            onEventFormChanged("isRecurringYearly", isRecurringYearly)
                            if (isRecurringYearly) {
                                onEventFormChanged("allDay", true)
                            }
                        }
                    )
                }

                if (eventFormState.isRecurringYearly) {
                    Text(
                        text = "Догађај ће се понављати сваке године на исти датум",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                Column {
                    Text(
                        text = "Додели особи:",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    val currentAssignee = EventAssignee.entries.find { it.name == eventFormState.assignee }
                    if (currentAssignee != null) {
                        Text(
                            text = "Изабрана особа: ${currentAssignee.displayName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color(currentAssignee.color.toColorInt()),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(EventAssignee.entries.toTypedArray()) { assignee ->
                            AssigneeAvatar(
                                assignee = assignee,
                                isSelected = eventFormState.assignee == assignee.name,
                                onClick = {
                                    onEventFormChanged("assignee", assignee.name)
                                    onEventFormChanged("color", assignee.color)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!eventFormState.allDay) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Време почетка:")
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = { showStartTimePicker = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(eventFormState.startTime?.formatted() ?: "--:--")
                        }
                    }

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
                            Text(eventFormState.endTime?.formatted() ?: "Изабери")
                        }
                    }
                }

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

    if (showStartTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = eventFormState.startTime?.hour ?: 0,
            initialMinute = eventFormState.startTime?.minute ?: 0,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            title = { Text("Изаберите време") },
            text = { TimePickerWrapper(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newTime = EventTime(
                            hour = timePickerState.hour,
                            minute = timePickerState.minute
                        )
                        onEventFormChanged("startTime", newTime)
                        showStartTimePicker = false
                    }
                ) {
                    Text("ОК")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartTimePicker = false }) {
                    Text("Откажи")
                }
            }
        )
    }

    if (!eventFormState.allDay && showEndTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = eventFormState.endTime?.hour ?: (eventFormState.startTime?.hour?.plus(1) ?: 0),
            initialMinute = eventFormState.endTime?.minute ?: eventFormState.startTime?.minute ?: 0,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            title = { Text("Изаберите време") },
            text = { TimePickerWrapper(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newTime = EventTime(
                            hour = timePickerState.hour,
                            minute = timePickerState.minute
                        )
                        onEventFormChanged("endTime", newTime)
                        showEndTimePicker = false
                    }
                ) {
                    Text("ОК")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndTimePicker = false }) {
                    Text("Откажи")
                }
            }
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
        Color(assignee.color.toColorInt())
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val avatarRes = when(assignee) {
        EventAssignee.EVERYONE -> R.drawable.avatar_everyone
        EventAssignee.PETAR -> R.drawable.avatar_petar
        EventAssignee.NATASA -> R.drawable.avatar_natasa
        EventAssignee.MILICA -> R.drawable.avatar_milica
        EventAssignee.BOGDAN -> R.drawable.avatar_bogdan
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
            Image(
                painter = painterResource(id = avatarRes),
                contentDescription = assignee.displayName,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
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
    val assignee = EventAssignee.entries.find { it.name == event.assignee } ?: EventAssignee.EVERYONE

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = Color(assignee.color.toColorInt()).copy(alpha = 0.2f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            val avatarRes = when(assignee) {
                                EventAssignee.EVERYONE -> R.drawable.avatar_everyone
                                EventAssignee.PETAR -> R.drawable.avatar_petar
                                EventAssignee.NATASA -> R.drawable.avatar_natasa
                                EventAssignee.MILICA -> R.drawable.avatar_milica
                                EventAssignee.BOGDAN -> R.drawable.avatar_bogdan
                            }

                            Image(
                                painter = painterResource(id = avatarRes),
                                contentDescription = assignee.displayName,
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = assignee.displayName,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Затвори"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = if (event.isRecurringYearly) "🎂 ${event.title}" else event.title,
                    style = MaterialTheme.typography.headlineSmall
                )

                if (!event.description.isNullOrBlank()) {
                    Text(
                        text = event.description,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (event.isRecurringYearly) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Годишње понављање",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "🎂 Rođendan / Godišnji događaj",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    FilledTonalButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Обриши")
                    }

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

private fun isSameDay(date1: Date, date2: Date): Boolean {
    val cal1 = Calendar.getInstance().apply { time = date1 }
    val cal2 = Calendar.getInstance().apply { time = date2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
            cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH)
}