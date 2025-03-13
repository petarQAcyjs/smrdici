package com.petar.smrdici.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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

@OptIn(ExperimentalMaterial3Api::class)
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
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopAppBar(
            title = { Text("Почетна") }
        )
        
        // Приказ информација о кориснику
        if (authState is AuthState.Authenticated) {
            val user = (authState as AuthState.Authenticated).user
            Text(
                text = "Добродошли, ${user.displayName ?: user.email}",
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
        
        Text(
            text = "Породична Апликација",
            modifier = Modifier.padding(vertical = 16.dp)
        )
        
        // Дугме за одјаву остаје
        androidx.compose.material3.Button(
            onClick = {
                authViewModel.signOut()
                navController.navigate(com.petar.smrdici.ui.navigation.Screen.Login.route) {
                    popUpTo(com.petar.smrdici.ui.navigation.Screen.Home.route) { inclusive = true }
                }
            },
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            Text("Одјави се")
        }
    }
} 