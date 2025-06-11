package com.petar.smrdici.ui.screens.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.SmrdiciApplication
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.components.SwitchSettingsItem
import kotlinx.coroutines.launch

@Composable
fun NotificationSettingsScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val notificationManager = SmrdiciApplication.getNotificationManager()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
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
    
    // Check if notification permission is granted
    val notificationPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true // For older Android versions, permission is granted at install time
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
                navController = navController,
                user = user,
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
                        androidx.compose.material3.Button(
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
                        icon = Icons.Default.Schedule,
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
                        icon = Icons.Default.Alarm,
                        title = "Сат пре догађаја",
                        subtitle = "Обавештење сат времена пре заказаног догађаја",
                        checked = hourBeforeNotificationEnabled,
                        onCheckedChange = { checked ->
                            hourBeforeNotificationEnabled = checked
                            notificationManager.hourBeforeNotificationEnabled = checked
                        },
                        enabled = notificationsEnabled && notificationPermissionGranted
                    )
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
                        icon = Icons.Default.VolumeUp,
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
            androidx.compose.material3.Button(
                onClick = {
                    notificationManager.resetToDefaults()
                    
                    // Update local state
                    notificationsEnabled = notificationManager.notificationsEnabled
                    dayBeforeNotificationEnabled = notificationManager.dayBeforeNotificationEnabled
                    hourBeforeNotificationEnabled = notificationManager.hourBeforeNotificationEnabled
                    notificationSoundEnabled = notificationManager.notificationSoundEnabled
                    notificationVibrationEnabled = notificationManager.notificationVibrationEnabled
                    
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Подешавања враћена на подразумеване вредности"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Врати на подразумевано"
                )
                androidx.compose.material3.Text(
                    text = "Врати на подразумевано",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
} 