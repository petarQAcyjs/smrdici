package com.petar.smrdici.ui.screens.budget

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.data.model.Expense
import com.petar.smrdici.data.model.Income
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.navigation.Screen
import com.petar.smrdici.ui.screens.settings.BudgetSettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun BudgetScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(),
    budgetViewModel: BudgetViewModel = viewModel(factory = BudgetViewModel.Factory(LocalContext.current)),
    budgetSettingsViewModel: BudgetSettingsViewModel = viewModel(factory = BudgetSettingsViewModel.Factory(LocalContext.current))
) {
    val authState by authViewModel.authState.collectAsState()
    val user = if (authState is AuthState.Authenticated) {
        (authState as AuthState.Authenticated).user
    } else null
    
    // Stanje za snekbar
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
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
                    text = "Učitavanje unapređenog budžeta...",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Opciono - dugme za manuelnu navigaciju ako automatska ne radi
                Button(
                    onClick = {
                        navController.navigate(Screen.BudgetList.route) {
                            popUpTo(Screen.Budget.route) {
                                inclusive = true
                            }
                        }
                    }
                ) {
                    Text("Prikaži novi budžet")
                }
            }
        }
    }
}

@Composable
fun ExpensesTab(
    expenses: List<Expense>,
    selectedPeriod: String,
    selectedAccountName: String,
    budgetViewModel: BudgetViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        expenses.forEach { expense ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Опис трансакције
                Text(
                    text = expense.description,
                    color = Color.White,
                    modifier = Modifier
                        .weight(1f)
                )
                
                // Износ
                Text(
                    text = "${expense.amount}",
                    color = Color.White
                )
                
                // Дугме за брисање
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Обриши",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clickable {
                            scope.launch {
                                budgetViewModel.deleteExpense(expense.id)
                            }
                        }
                )
            }
        }
    }
}

@Composable
fun IncomesTab(
    incomes: List<Income>,
    selectedPeriod: String,
    selectedAccountName: String,
    budgetViewModel: BudgetViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        incomes.forEach { income ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Опис трансакције
                Text(
                    text = income.description,
                    color = Color.White,
                    modifier = Modifier
                        .weight(1f)
                )
                
                // Износ
                Text(
                    text = "${income.amount}",
                    color = Color.White
                )
                
                // Дугме за брисање
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Обриши",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clickable {
                            scope.launch {
                                budgetViewModel.deleteIncome(income.id)
                            }
                        }
                )
            }
        }
    }
}

@Composable
fun ChartsTab() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Графикони ће бити доступни ускоро",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

/**
 * Помоћна функција која враћа симбол за дату валуту
 */
private fun getSymbolForCurrency(currencyCode: String): String {
    return when (currencyCode) {
        "RSD" -> "РСД "
        "EUR" -> "€ "
        "USD" -> "$ "
        "GBP" -> "£ "
        else -> ""
    }
}