package com.petar.smrdici.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.ui.auth.AuthViewModel

@Composable
fun MainLayout(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(),
    content: @Composable () -> Unit
) {
    val authState by authViewModel.authState.collectAsState()
    val user = if (authState is com.petar.smrdici.ui.auth.AuthState.Authenticated) {
        (authState as com.petar.smrdici.ui.auth.AuthState.Authenticated).user
    } else null
    
    Scaffold { paddingValues ->
        Box(
            modifier = Modifier.padding(paddingValues)
        ) {
            content()
        }
    }
} 