package com.petar.smrdici.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.navigation.Screen

@Composable
fun HomeScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Prikaz informacija o korisniku
        if (authState is AuthState.Authenticated) {
            val user = (authState as AuthState.Authenticated).user
            Text(text = "Dobrodošli, ${user.displayName ?: user.email}")
        }
        
        Text(text = "Porodična Aplikacija", modifier = Modifier.padding(vertical = 16.dp))
        
        Button(
            onClick = { navController.navigate(Screen.Budget.route) },
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Text("Budžet")
        }
        
        Button(
            onClick = { navController.navigate(Screen.Calendar.route) },
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Text("Kalendar")
        }
        
        Button(
            onClick = { navController.navigate(Screen.Lists.route) },
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Text("Liste")
        }
        
        // Dugme za odjavu
        Button(
            onClick = {
                authViewModel.signOut()
                navController.navigate(Screen.Login.route) {
                    popUpTo(Screen.Home.route) { inclusive = true }
                }
            },
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            Text("Odjavi se")
        }
    }
} 