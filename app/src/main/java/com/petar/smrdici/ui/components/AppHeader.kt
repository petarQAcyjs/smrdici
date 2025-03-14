package com.petar.smrdici.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseUser
import com.petar.smrdici.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppHeader(
    title: String,
    user: FirebaseUser?,
    navController: NavController,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = { navController.popBackStack() }
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            if (showBackButton) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Назад"
                    )
                }
            }
        },
        actions = {
            // Профилна слика у горњем десном углу
            if (user != null) {
                Surface(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable {
                            navController.navigate(Screen.Profile.route)
                        },
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.displayName?.firstOrNull()?.toString() ?: 
                                  user.email?.firstOrNull()?.toString() ?: "?",
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
            }
        },
        modifier = Modifier.height(48.dp)
    )
} 