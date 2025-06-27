package com.petar.smrdici.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import com.petar.smrdici.data.model.Event
import com.petar.smrdici.ui.screens.calendar.CalendarViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@Composable
fun EventCreationScreen(
    calendarViewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.Factory()),
    navController: NavHostController
) {
    val coroutineScope = rememberCoroutineScope()

    // Function to handle event creation success
    fun onEventCreated(newEvent: Event) {
        // Schedule hybrid notifications (local and FCM) for the event
        coroutineScope.launch {
            calendarViewModel.scheduleEventMorningNotification(newEvent)
        }
        
        // Navigate back or to confirmation screen
        navController.popBackStack()
    }
    
    // Rest of the EventCreationScreen implementation goes here
    // ...
} 