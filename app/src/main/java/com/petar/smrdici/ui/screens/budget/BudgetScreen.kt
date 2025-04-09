package com.petar.smrdici.ui.screens.budget

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.data.model.Account
import com.petar.smrdici.data.model.Expense
import com.petar.smrdici.data.model.Income
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.navigation.Screen
import com.petar.smrdici.ui.screens.settings.BudgetSettingsViewModel
import androidx.compose.ui.platform.LocalContext

@Composable
fun BudgetScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(),
    budgetViewModel: BudgetViewModel = viewModel(factory = BudgetViewModel.Factory(LocalContext.current)),
    budgetSettingsViewModel: BudgetSettingsViewModel = viewModel(factory = BudgetSettingsViewModel.Factory(LocalContext.current))
) {
    // Спречавамо непотребно учитавање EventRepository-а
    DisposableEffect(Unit) {
        // Ништа не радимо, само спречавамо непотребно учитавање
        onDispose { }
    }
    
    val authState by authViewModel.authState.collectAsState()
    val user = if (authState is AuthState.Authenticated) {
        (authState as AuthState.Authenticated).user
    } else null
    
    // Стање за снекбар
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // Стање за табове
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Расходи", "Приходи", "Графикони")
    
    // Добијамо подешени период из BudgetViewModel
    val selectedPeriodIndex by budgetViewModel.selectedPeriodIndex.collectAsState()
    
    // Мапирамо Period енумерацију на стрингове за приказ
    val periodStrings = listOf("Дан", "Недеља", "Месец", "Година", "Период", "Све")
    
    // Логујемо подешени период за дебаговање
    LaunchedEffect(selectedPeriodIndex) {
        Log.d("BudgetScreen", "Подешени период индекс: $selectedPeriodIndex")
    }
    
    // Стање за падајући мени
    var expanded by remember { mutableStateOf(false) }
    
    // Добијамо подешену валуту из BudgetSettingsViewModel
    val currency by budgetSettingsViewModel.currency.collectAsState()
    
    // Добијамо дан почетка прилагођеног периода из BudgetSettingsViewModel
    val customPeriodStartDay by budgetSettingsViewModel.customPeriodStartDay.collectAsState()
    
    // Добијамо податке из BudgetViewModel
    val expenses by budgetViewModel.expenses.collectAsState()
    val incomes by budgetViewModel.incomes.collectAsState()
    
    // Креирамо празне листе за accounts и isLoading
    val accounts = emptyList<Account>()
    val isLoading = false
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppHeader(
                title = "Буџет",
                user = user,
                navController = navController,
                showBackButton = false
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedTabIndex == 0) {
                        navController.navigate(Screen.AddExpense.route)
                    } else if (selectedTabIndex == 1) {
                        navController.navigate(Screen.AddIncome.route)
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Додај")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Период селектор
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Период:",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Box {
                    TextButton(
                        onClick = { expanded = true }
                    ) {
                        Text(periodStrings[selectedPeriodIndex])
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Изабери период"
                        )
                    }
                    
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        periodStrings.forEachIndexed { index, period ->
                            DropdownMenuItem(
                                text = { Text(text = period) },
                                onClick = {
                                    budgetViewModel.updatePeriodIndex(index)
                                    expanded = false
                                    Log.d("BudgetScreen", "Изабран период: $period, индекс: $index")
                                }
                            )
                        }
                    }
                }
            }
            
            // Табови
            TabRow(
                selectedTabIndex = selectedTabIndex
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }
            
            // Садржај таба
            when (selectedTabIndex) {
                0 -> ExpensesTab(
                    expenses = expenses,
                    selectedPeriod = periodStrings[selectedPeriodIndex]
                )
                1 -> IncomesTab(
                    incomes = incomes,
                    selectedPeriod = periodStrings[selectedPeriodIndex]
                )
                2 -> ChartsTab()
            }
        }
    }
}

@Composable
fun ExpensesTab(
    expenses: List<Expense>,
    selectedPeriod: String
) {
    if (expenses.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Нема расхода за приказ",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Овде би био приказ расхода
            Text("Расходи за период: $selectedPeriod")
        }
    }
}

@Composable
fun IncomesTab(
    incomes: List<Income>,
    selectedPeriod: String
) {
    if (incomes.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Нема прихода за приказ",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Овде би био приказ прихода
            Text("Приходи за период: $selectedPeriod")
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
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}