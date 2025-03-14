package com.petar.smrdici.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.navigation.Screen

@Composable
fun ProfileScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    val user = if (authState is AuthState.Authenticated) {
        (authState as AuthState.Authenticated).user
    } else null
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        AppHeader(
            title = "Профил",
            user = user,
            navController = navController
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Профилна слика и информације о кориснику
            if (user != null) {
                // Профилна слика (овде можете додати стварну слику)
                Surface(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = user.displayName?.firstOrNull()?.toString() ?: user.email?.firstOrNull()?.toString() ?: "?",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Име корисника
                Text(
                    text = user.displayName ?: "Корисник",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                
                // Емаил корисника
                Text(
                    text = user.email ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Опције профила
                ProfileOption(
                    icon = Icons.Default.Edit,
                    title = "Уреди профил",
                    onClick = { /* Имплементирати уређивање профила */ }
                )
                
                ProfileOption(
                    icon = Icons.Default.Settings,
                    title = "Подешавања",
                    onClick = { /* Имплементирати подешавања */ }
                )
                
                ProfileOption(
                    icon = Icons.Default.ExitToApp,
                    title = "Одјави се",
                    onClick = {
                        authViewModel.signOut()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    }
                )
            } else {
                // Ако корисник није пријављен, приказати поруку
                Text("Нисте пријављени")
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        navController.navigate(Screen.Login.route)
                    }
                ) {
                    Text("Пријави се")
                }
            }
        }
    }
}

@Composable
fun ProfileOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            
            // Уклоњена икона оловке са десне стране
        }
    }
} 