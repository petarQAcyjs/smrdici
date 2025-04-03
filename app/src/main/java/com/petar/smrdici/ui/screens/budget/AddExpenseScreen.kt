package com.petar.smrdici.ui.screens.budget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavController

@Composable
fun AddExpenseScreen(navController: NavController) {
    // Спречавамо непотребно учитавање EventRepository-а
    DisposableEffect(Unit) {
        onDispose { }
    }
    
    // Остатак кода...
} 