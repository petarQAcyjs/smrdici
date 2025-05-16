package com.petar.smrdici.ui.screens.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.navigation.Screen
import com.petar.smrdici.ui.screens.budget.BudgetViewModel

/**
 * Tranzicioni ekran koji automatski preusmerava na BudgetListScreen.
 * Ovaj ekran je zadržan zbog kompatibilnosti sa postojećim rutama navigacije,
 * ali sada samo služi kao most do novog ekrana budžeta.
 */
@Composable
fun BudgetScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(),
    budgetViewModel: BudgetViewModel
) {
    val authState by authViewModel.authState.collectAsState()
    val user = if (authState is AuthState.Authenticated) {
        (authState as AuthState.Authenticated).user
    } else null
    
    // Stanje za snekbar
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Dodat efekt koji će automatski preusmeriti na novi ekran budžeta
    LaunchedEffect(key1 = true) {
        // Navigacija na novi BudgetListScreen
        navController.navigate(Screen.BudgetList.route) {
            // Brisanje starog ekrana iz steka navigacije
            popUpTo(Screen.Budget.route) {
                inclusive = true
            }
        }
    }
    
    // Prikazujemo ekran učitavanja ili informaciju dok se navigacija ne završi
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF1A1C1E),
        topBar = {
            AppHeader(
                title = "Budžet",
                navController = navController,
                showBackButton = true,
                user = user
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Учитавање унапређеног буџета...",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Опционо - дугме за мануелну навигацију ако аутоматска не ради
                Button(
                    onClick = {
                        navController.navigate(Screen.BudgetList.route) {
                            popUpTo(Screen.Budget.route) {
                                inclusive = true
                            }
                        }
                    }
                ) {
                    Text("Прикажи нови буџет")
                }
            }
        }
    }
}