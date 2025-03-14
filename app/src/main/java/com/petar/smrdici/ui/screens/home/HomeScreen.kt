package com.petar.smrdici.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.R
import com.petar.smrdici.data.model.Event
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.navigation.Screen
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(),
    homeViewModel: HomeViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    val todayEvents by homeViewModel.todayEvents.collectAsState()
    val user = if (authState is AuthState.Authenticated) {
        (authState as AuthState.Authenticated).user
    } else null
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        AppHeader(
            title = "Femici",
            user = user,
            navController = navController
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Приказ данашњих активности
            TodayActivitiesCard(
                events = todayEvents,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Картице за навигацију
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Листе
                NavigationCard(
                    title = "Листе",
                    iconResId = R.drawable.ic_list,
                    onClick = { navController.navigate(Screen.Lists.route) }
                )
                
                // Календар
                NavigationCard(
                    title = "Календар",
                    iconResId = R.drawable.ic_calendar,
                    onClick = { navController.navigate(Screen.Calendar.route) }
                )
                
                // Буџет
                NavigationCard(
                    title = "Буџет",
                    iconResId = R.drawable.ic_budget,
                    onClick = { navController.navigate(Screen.Budget.route) }
                )
            }
        }
    }
}

@Composable
fun NavigationCard(
    title: String,
    iconResId: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            
            Image(
                painter = painterResource(id = iconResId),
                contentDescription = title,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
fun TodayActivitiesCard(
    events: List<Event>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Данашњи датум
            val today = Calendar.getInstance().time
            val dateFormat = SimpleDateFormat("EEE d MMM", Locale("sr"))
            Text(
                text = dateFormat.format(today),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            if (events.isEmpty()) {
                Text(
                    text = "Нема активности за данас",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            } else {
                // Приказ догађаја
                events.forEach { event ->
                    EventRow(event = event)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun EventRow(event: Event) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val startTime = timeFormat.format(event.startTime.toDate())
    val endTime = event.endTime?.let { timeFormat.format(it.toDate()) }
    val timeText = if (event.allDay) {
        "Цео дан"
    } else {
        if (endTime != null) "$startTime - $endTime" else startTime
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Време догађаја
        Text(
            text = timeText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.width(80.dp)
        )
        
        // Индикатор боје
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 36.dp)
                .background(
                    color = Color(android.graphics.Color.parseColor(event.color)),
                    shape = RoundedCornerShape(2.dp)
                )
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Наслов догађаја
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            if (event.location.isNotBlank()) {
                Text(
                    text = event.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
} 