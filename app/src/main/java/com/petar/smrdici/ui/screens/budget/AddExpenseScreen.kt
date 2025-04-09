package com.petar.smrdici.ui.screens.budget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

@Composable
fun AddExpenseScreen() {
    // Спречавамо непотребно учитавање EventRepository-а
    DisposableEffect(Unit) {
        onDispose { }
    }
    
    // Остатак кода...
} 