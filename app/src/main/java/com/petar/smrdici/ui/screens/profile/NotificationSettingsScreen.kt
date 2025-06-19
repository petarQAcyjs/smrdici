@file:OptIn(ExperimentalMaterial3Api::class)
package com.petar.smrdici.ui.screens.profile

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.Timestamp
import com.petar.smrdici.SmrdiciApplication
import com.petar.smrdici.data.model.Event
import com.petar.smrdici.notification.NotificationWorker
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.components.SwitchSettingsItem
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun NotificationSettingsScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val notificationManager = SmrdiciApplication.getNotificationManager()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    // Get the current user
    val authState by authViewModel.authState.collectAsState()
    val user = if (authState is AuthState.Authenticated) {
        (authState as AuthState.Authenticated).user
    } else null
    
    // State for notification settings
    var notificationsEnabled by remember { mutableStateOf(notificationManager.notificationsEnabled) }
    var dayBeforeNotificationEnabled by remember { mutableStateOf(notificationManager.dayBeforeNotificationEnabled) }
    var hourBeforeNotificationEnabled by remember { mutableStateOf(notificationManager.hourBeforeNotificationEnabled) }
    var notificationSoundEnabled by remember { mutableStateOf(notificationManager.notificationSoundEnabled) }
    var notificationVibrationEnabled by remember { mutableStateOf(notificationManager.notificationVibrationEnabled) }
    
    // State for new notification features
    var avatarNotificationsEnabled by remember { mutableStateOf(notificationManager.avatarNotificationsEnabled) }
    var dynamicTimingEnabled by remember { mutableStateOf(notificationManager.dynamicTimingEnabled) }
    var smartGroupingEnabled by remember { mutableStateOf(notificationManager.smartGroupingEnabled) }
    var weatherAwareEnabled by remember { mutableStateOf(notificationManager.weatherAwareEnabled) }
    
    // Check if notification permission is granted
    var notificationPermissionGranted by remember { mutableStateOf(false) }
    
    // Check notification permission
    LaunchedEffect(Unit) {
        notificationPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionChecker = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            permissionChecker == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true // Permission not required on Android <13
        }
    }
    
    // Show a message if notification permission is not granted
    LaunchedEffect(notificationPermissionGranted) {
        if (!notificationPermissionGranted) {
            snackbarHostState.showSnackbar(
                message = "Дозвола за обавештења није одобрена. Неке функције неће радити."
            )
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppHeader(
                title = "Подешавања обавештења",
                user = user,
                navController = navController,
                showBackButton = true,
                showProfileIcon = false
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            // Main notification settings card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Општа подешавања",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Main notification toggle
                    SwitchSettingsItem(
                        icon = if (notificationsEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                        title = "Обавештења",
                        subtitle = if (notificationsEnabled) "Укључена" else "Искључена",
                        checked = notificationsEnabled,
                        onCheckedChange = { checked ->
                            notificationsEnabled = checked
                            notificationManager.notificationsEnabled = checked
                            
                            // If notifications are disabled, show a message
                            if (!checked) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "Обавештења су искључена"
                                    )
                                }
                            }
                        },
                        enabled = notificationPermissionGranted
                    )
                    
                    // If permission is not granted, show a button to open settings
                    if (!notificationPermissionGranted) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Отвори подешавања апликације")
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Notification timing settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Време обавештења",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Day before notification toggle
                    SwitchSettingsItem(
                        icon = Icons.Default.CalendarToday,
                        title = "Дан пре догађаја",
                        subtitle = "Обавештење дан пре заказаног догађаја",
                        checked = dayBeforeNotificationEnabled,
                        onCheckedChange = { checked ->
                            dayBeforeNotificationEnabled = checked
                            notificationManager.dayBeforeNotificationEnabled = checked
                        },
                        enabled = notificationsEnabled && notificationPermissionGranted
                    )
                    
                    // Hour before notification toggle
                    SwitchSettingsItem(
                        icon = Icons.Default.Bolt,
                        title = "Сат пре догађаја",
                        subtitle = "Обавештење сат времена пре заказаног догађаја",
                        checked = hourBeforeNotificationEnabled,
                        onCheckedChange = { checked ->
                            hourBeforeNotificationEnabled = checked
                            notificationManager.hourBeforeNotificationEnabled = checked
                        },
                        enabled = notificationsEnabled && notificationPermissionGranted
                    )
                    
                    // Dynamic timing toggle
                    SwitchSettingsItem(
                        icon = Icons.Default.AccessTime,
                        title = "Динамичко време",
                        subtitle = "Паметно време обавештења (08:00 или 12:00)",
                        checked = dynamicTimingEnabled,
                        onCheckedChange = { checked ->
                            dynamicTimingEnabled = checked
                            notificationManager.dynamicTimingEnabled = checked
                        },
                        enabled = notificationsEnabled && notificationPermissionGranted
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Advanced notification features
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Напредне функције",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Avatar notifications toggle
                    SwitchSettingsItem(
                        icon = Icons.Default.Person,
                        title = "Аватари у обавештењима",
                        subtitle = "Прикажи аватаре особа у обавештењима",
                        checked = avatarNotificationsEnabled,
                        onCheckedChange = { checked ->
                            avatarNotificationsEnabled = checked
                            notificationManager.avatarNotificationsEnabled = checked
                        },
                        enabled = notificationsEnabled && notificationPermissionGranted
                    )
                    
                    // Smart grouping toggle
                    SwitchSettingsItem(
                        icon = Icons.AutoMirrored.Filled.Sort,
                        title = "Паметно груписање",
                        subtitle = "Групиши више догађаја у једном дану",
                        checked = smartGroupingEnabled,
                        onCheckedChange = { checked ->
                            smartGroupingEnabled = checked
                            notificationManager.smartGroupingEnabled = checked
                        },
                        enabled = notificationsEnabled && notificationPermissionGranted
                    )
                    
                    // Weather-aware notifications toggle
                    SwitchSettingsItem(
                        icon = Icons.Default.WbSunny,
                        title = "Временска прогноза",
                        subtitle = "Додај информације о времену у обавештења",
                        checked = weatherAwareEnabled,
                        onCheckedChange = { checked ->
                            weatherAwareEnabled = checked
                            notificationManager.weatherAwareEnabled = checked
                        },
                        enabled = notificationsEnabled && notificationPermissionGranted
                    )
                }
            }
            
            // Add a separate card for the test weather button
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Тестирање",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Button(
                        onClick = {
                            // Show simple weather test notification
                            val notificationService = com.petar.smrdici.notification.NotificationService(context)
                            notificationService.showDailyWeatherNotification(
                                "🌤️ Тест временске прогнозе",
                                "Ово је тест обавештење о временској прогнози.\n\n" +
                                "🌡️ 22°C | 💨 5 km/h | 💧 60% | ☁️ Делимично облачно\n\n" +
                                "Данас је добар дан за шетњу. Понесите лагану јакну!"
                            )
                            
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "Тест обавештења о времену је приказан"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = weatherAwareEnabled && notificationsEnabled && notificationPermissionGranted
                    ) {
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = "Тестирај обавештење о времену"
                        )
                        Text(
                            text = "Тестирај дневно обавештење о времену",
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Test morning notification using the actual alarm mechanism
                    Button(
                        onClick = {
                            // Launch a coroutine to get events first
                            coroutineScope.launch {
                                try {
                                    // Get today's events - same code as in the other button
                                    val eventsRepository = SmrdiciApplication.getInstance().getEventsRepository()
                                    val today = Calendar.getInstance().apply {
                                        set(Calendar.HOUR_OF_DAY, 0)
                                        set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }.time
                                    val tomorrow = Calendar.getInstance().apply {
                                        add(Calendar.DAY_OF_YEAR, 1)
                                        set(Calendar.HOUR_OF_DAY, 0)
                                        set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }.time
                                    
                                    // Get events using suspend function
                                    val eventsResult = eventsRepository.getEventsSync(today, tomorrow)
                                    val events = if (eventsResult.isSuccess) eventsResult.getOrNull() ?: emptyList() else emptyList()
                                    
                                    val todayEvents = if (events.isEmpty()) "Нема догађаја за данас. Имате слободан дан!" 
                                    else {
                                        val eventsList = events.take(3).joinToString(", ") { event -> event.title }
                                        "Данашњи догађаји: $eventsList" + (if (events.size > 3) "... и још ${events.size - 3}" else "")
                                    }
                                    
                                    // Use NotificationService directly to ensure consistent formatting
                                    val notificationService = com.petar.smrdici.notification.NotificationService(context)
                                    notificationService.showDailyMorningNotification(
                                        "☀️ Јутарње обавештење",
                                        "$todayEvents\n\n" +
                                        "🌡️ 22°C | 💨 5 km/h | 💧 60% | ☁️ Делимично облачно\n\n" +
                                        "Данас је добар дан за шетњу. Понесите лагану јакну!"
                                    )
                                    
                                    // Log the test
                                    android.util.Log.d(
                                        "NotificationTest",
                                        "Morning notification test triggered with today's events"
                                    )
                                    
                                    snackbarHostState.showSnackbar(
                                        message = "Тест јутарњег обавештења је приказан"
                                    )
                                } catch (e: Exception) {
                                    // Fallback if getting events fails
                                    val notificationService = com.petar.smrdici.notification.NotificationService(context)
                                    notificationService.showDailyMorningNotification(
                                        "☀️ Јутарње обавештење",
                                        "Доброј јутро! Проверите данашње догађаје.\n\n" +
                                        "🌡️ 22°C | 💨 5 km/h | 💧 60% | ☁️ Делимично облачно\n\n" +
                                        "Данас је добар дан за шетњу. Понесите лагану јакну!"
                                    )
                                    
                                    snackbarHostState.showSnackbar(
                                        message = "Тест јутарњег обавештења је приказан (fallback)"
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = notificationsEnabled && notificationPermissionGranted
                    ) {
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = "Тестирај јутарње обавештење"
                        )
                        Text(
                            text = "Тестирај јутарње обавештење (08:00)",
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    
                    // Show exact alarm permission status
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val canScheduleExact = com.petar.smrdici.notification.NotificationHelper.canScheduleExactAlarms(context)
                        
                        if (!canScheduleExact) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val intent = com.petar.smrdici.notification.NotificationHelper.getExactAlarmSettingsIntent(context)
                                    if (intent != null) {
                                        context.startActivity(intent)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Дозвола за тачне аларме"
                                )
                                Text(
                                    text = "Дозволи тачне аларме (потребно за динамичка обавештења)",
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Notification style settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Стил обавештења",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Sound toggle
                    SwitchSettingsItem(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        title = "Звук",
                        subtitle = "Пуштај звук при обавештењу",
                        checked = notificationSoundEnabled,
                        onCheckedChange = { checked ->
                            notificationSoundEnabled = checked
                            notificationManager.notificationSoundEnabled = checked
                        },
                        enabled = notificationsEnabled && notificationPermissionGranted
                    )
                    
                    // Vibration toggle
                    SwitchSettingsItem(
                        icon = Icons.Default.Vibration,
                        title = "Вибрација",
                        subtitle = "Вибрирај при обавештењу",
                        checked = notificationVibrationEnabled,
                        onCheckedChange = { checked ->
                            notificationVibrationEnabled = checked
                            notificationManager.notificationVibrationEnabled = checked
                        },
                        enabled = notificationsEnabled && notificationPermissionGranted
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Reset button
            Button(
                onClick = {
                    notificationManager.resetToDefaults()
                    
                    // Update local state
                    notificationsEnabled = notificationManager.notificationsEnabled
                    dayBeforeNotificationEnabled = notificationManager.dayBeforeNotificationEnabled
                    hourBeforeNotificationEnabled = notificationManager.hourBeforeNotificationEnabled
                    notificationSoundEnabled = notificationManager.notificationSoundEnabled
                    notificationVibrationEnabled = notificationManager.notificationVibrationEnabled
                    avatarNotificationsEnabled = notificationManager.avatarNotificationsEnabled
                    dynamicTimingEnabled = notificationManager.dynamicTimingEnabled
                    smartGroupingEnabled = notificationManager.smartGroupingEnabled
                    weatherAwareEnabled = notificationManager.weatherAwareEnabled
                    
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Подешавања враћена на подразумеване вредности"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Врати на подразумевано"
                )
                Text(
                    text = "Врати на подразумевано",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            
            // Add a button to test FCM notifications directly
            Button(
                onClick = {
                    // Create a test event for today at the current time + 1 minute
                    val calendar = Calendar.getInstance().apply {
                        add(Calendar.MINUTE, 1) // 1 minute from now
                    }
                    
                    val testEvent = Event(
                        id = "test_fcm_${System.currentTimeMillis()}",
                        title = "Test FCM Notification",
                        description = "This is a test FCM notification",
                        startTime = Timestamp(calendar.time),
                        endTime = Timestamp(Date(calendar.timeInMillis + 3600000)), // 1 hour later
                        createdBy = SmrdiciApplication.getInstance().getCurrentUserId(),
                        assignee = "EVERYONE"
                    )
                    
                    // Create a WorkManager job directly instead of using the event notification path
                    val workData = androidx.work.Data.Builder()
                        .putString("EVENT_ID", testEvent.id)
                        .putString("EVENT_TITLE", testEvent.title)
                        .putString("EVENT_ASSIGNEE", testEvent.assignee)
                        .putLong("EVENT_START_TIME", calendar.timeInMillis)
                        .putString("EVENT_LOCATION", "Београд") // Default location
                        .build()
                        
                    val notificationWork = OneTimeWorkRequestBuilder<NotificationWorker>()
                        .setInputData(workData)
                        .setInitialDelay(1, java.util.concurrent.TimeUnit.MINUTES)
                        .build()
                    
                    // Enqueue the work
                    val workManager = WorkManager.getInstance(context)
                    workManager.enqueue(notificationWork)
                    
                    // Also try to schedule a local notification as backup
                    com.petar.smrdici.notification.NotificationHelper.getInstance(context).scheduleTestDynamicNotification()
                    
                    // Display scheduled time and job state
                    val scheduledTimeFormatted = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(calendar.time)
                    Toast.makeText(
                        context,
                        "Test FCM notification scheduled for 1 minute from now at $scheduledTimeFormatted\nJob ID: ${notificationWork.id}",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    // Show additional job info in a snackbar
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Check notification scheduled for $scheduledTimeFormatted. Use Android Studio Logcat to monitor job: ${notificationWork.id.toString().takeLast(8)}"
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test FCM Notification (1 min)")
                }
            }
            
            // Add extra space at the bottom to ensure everything is visible
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
} 