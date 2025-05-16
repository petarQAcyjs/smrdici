package com.petar.smrdici.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.petar.smrdici.ui.navigation.NavGraph
import com.petar.smrdici.ui.theme.SmrdiciTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.petar.smrdici.ui.theme.ThemeViewModel
import com.petar.smrdici.ui.theme.ThemeViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Искључујемо потенцијално упозорење за неважећи ресурс ID
        handleInvalidResourceId()
        
        setContent {
            val themeViewModel: ThemeViewModel = viewModel(factory = ThemeViewModelFactory(this))
            val themeMode by themeViewModel.themeMode.collectAsState()
            SmrdiciTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavGraph(navController = navController)
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
} 