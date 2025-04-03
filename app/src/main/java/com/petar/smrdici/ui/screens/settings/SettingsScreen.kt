package com.petar.smrdici.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavController

@Composable
fun SettingsScreen(navController: NavController) {
    // Спречавамо непотребно учитавање EventRepository-а
    DisposableEffect(Unit) {
        onDispose { }
    }
    
    // Остатак кода...
} 