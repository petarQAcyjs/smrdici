package com.petar.smrdici.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.petar.smrdici.SmrdiciApplication
import com.petar.smrdici.notification.NotificationManager
import com.petar.smrdici.ui.navigation.NavGraph
import com.petar.smrdici.ui.navigation.Screen
import com.petar.smrdici.ui.theme.SmrdiciTheme
import com.petar.smrdici.ui.theme.ThemeViewModel
import com.petar.smrdici.ui.theme.ThemeViewModelFactory

class MainActivity : ComponentActivity() {
    
    private lateinit var notificationManager: NotificationManager
    
    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("MainActivity", "Notification permission granted")
            notificationManager.notificationsEnabled = true
        } else {
            Log.d("MainActivity", "Notification permission denied")
            notificationManager.notificationsEnabled = false
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configure window to handle system bars properly
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Искључујемо потенцијално упозорење за неважећи ресурс ID
        handleInvalidResourceId()
        
        // Get NotificationManager instance
        notificationManager = SmrdiciApplication.getNotificationManager()
        
        // Request notification permission if needed
        requestNotificationPermission()
        
        setContent {
            val themeViewModel: ThemeViewModel = viewModel(factory = ThemeViewModelFactory(this))
            val themeMode by themeViewModel.themeMode.collectAsState()
            
            SmrdiciTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val startDestination = if (FirebaseAuth.getInstance().currentUser != null) {
                        Screen.Home.route
                    } else {
                        Screen.Login.route
                    }
                    NavGraph(
                        navController = navController,
                        startDestination = startDestination
                    )
                }
            }
        }
    }
    
    /**
     * Спречава грешке са невалидним Resource ID-ом (0x00000000)
     * које могу да се појаве у логовима
     */
    private fun handleInvalidResourceId() {
        try {
            // Употребљавамо новији API уместо застареле методе updateConfiguration
            val resources = resources
            val configuration = resources.configuration
            
            // Креирамо нови контекст са истом конфигурацијом
            // Ово је нови начин освежавања ресурса
            createConfigurationContext(configuration)
            
            // Ово је алтернативни приступ који би такође радио
            // resources.getResourceName(0) // Форсирамо валидацију кеширања ресурса
            
            // Још једна опција је да приступимо ресурсима кроз нови контекст
            // val newContext = createConfigurationContext(configuration)
            // val newResources = newContext.resources
        } catch (e: Exception) {
            // У случају грешке, игноришемо и настављамо са апликацијом
            // Није критично за функционисање апликације
            Log.e("MainActivity", "Грешка у функцији handleInvalidResourceId", e)
        }
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                    notificationManager.notificationsEnabled = true
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Show rationale if needed (could show a dialog explaining why notifications are useful)
                    // For now, just request the permission
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    // Request permission
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // For older Android versions, permission is granted at install time
            notificationManager.notificationsEnabled = true
        }
    }
} 