package com.petar.smrdici.ui.screens.budget

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import kotlinx.coroutines.launch

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
    
    // Добијамо подешену валуту из BudgetSettingsViewModel
    val currency by budgetSettingsViewModel.currency.collectAsState()
    
    // Добијамо дан почетка прилагођеног периода из BudgetSettingsViewModel
    val customPeriodStartDay by budgetSettingsViewModel.customPeriodStartDay.collectAsState()
    
    // Добијамо податке из BudgetViewModel
    val expenses by budgetViewModel.expenses.collectAsState()
    val incomes by budgetViewModel.incomes.collectAsState()
    
    // Доћи до података о рачунима из BudgetViewModel-а
    val accounts by budgetViewModel.accounts.collectAsState()
    val isLoading by budgetViewModel.isLoading.collectAsState()
    val selectedAccountId by budgetViewModel.selectedAccountId.collectAsState()
    val selectedAccountName = accounts.find { it.id == selectedAccountId }?.name ?: "Сви рачуни"
    
    // Учитавање рачуна и података када су доступни
    LaunchedEffect(Unit) {
        // Осигуравамо да рачуни буду учитани
        budgetViewModel.loadAccounts()
        // Осигуравамо да су трансакције учитане
        budgetViewModel.reloadTransactions()
    }
    
    // Иницијално учитавамо све рачуне ако је селектовани рачун null а имамо рачуне
    LaunchedEffect(accounts) {
        if (!budgetViewModel.isAccountSelected() && accounts.isNotEmpty()) {
            // Уместо "Сви рачуни", селектујемо подразумевани рачун
            val defaultAccount = accounts.find { it.isDefault }
            if (defaultAccount != null) {
                budgetViewModel.selectAccount(defaultAccount.id)
            } else {
                // Ако нема подразумеваног рачуна, селектујемо први у листи
                accounts.firstOrNull()?.let { 
                    budgetViewModel.selectAccount(it.id) 
                }
            }
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF1A1C1E), // Тамна позадина као на слици
        topBar = {
            AppHeader(
                title = "Буџет",
                user = user,
                navController = navController,
                showBackButton = true
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
                },
                containerColor = Color(0xFFB2C5FF), // Светло плава као на слици
            ) {
                Icon(Icons.Default.Add, contentDescription = "Додај")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1C1E)) // Тамна позадина целог екрана
                .padding(paddingValues)
        ) {
            // Таб бар на врху за расходе/приходе/графиконе
            TabRow(
                selectedTabIndex = selectedTabIndex,
                contentColor = Color.White
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }
            
            // Селектор периода
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .background(
                        color = Color(0xFF303436),
                        shape = RoundedCornerShape(8.dp)
                    ),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("Дан", "Недеља", "Месец", "Година", "Период").forEachIndexed { index, periodName ->
                    TextButton(
                        onClick = { budgetViewModel.updatePeriodIndex(index) },
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Text(
                            text = periodName,
                            color = if (selectedPeriodIndex == index) 
                                    Color(0xFFB2C5FF) // Светло плава за активни таб
                                    else Color.White.copy(alpha = 0.7f),
                            fontWeight = if (selectedPeriodIndex == index) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            // Израчунавамо укупне износе
            val totalIncome = incomes.sumOf { it.amount }
            val totalExpense = expenses.sumOf { it.amount }
            val balance = totalIncome - totalExpense
            
            // Картица рачуна са прогрес баром
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2A324B), // Тамно плава боја картице
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Назив рачуна са падајућим менијем
                    var accountMenuExpanded by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedAccountName,
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        
                        TextButton(
                            onClick = { accountMenuExpanded = true },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Промени",
                                    color = Color(0xFFB2C5FF),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = Color(0xFFB2C5FF)
                                )
                            }
                        }
                    }
                    
                    // Падајући мени за избор рачуна
                    Box {
                        DropdownMenu(
                            expanded = accountMenuExpanded,
                            onDismissRequest = { accountMenuExpanded = false },
                            modifier = Modifier.background(Color(0xFF303436))
                        ) {
                            // Опција "Сви рачуни"
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        "Сви рачуни",
                                        color = Color.White
                                    ) 
                                },
                                onClick = {
                                    budgetViewModel.selectAccount(null)
                                    accountMenuExpanded = false
                                }
                            )
                            
                            // Листа рачуна
                            accounts.forEach { account ->
                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            account.name,
                                            color = Color.White
                                        ) 
                                    },
                                    onClick = {
                                        budgetViewModel.selectAccount(account.id)
                                        accountMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    
                    // Стање рачуна
                    val selectedAccount = accounts.find { it.id == selectedAccountId }
                    
                    // Налазимо валуту подразумеваног рачуна
                    val defaultAccount = accounts.find { it.isDefault } ?: accounts.firstOrNull()
                    val defaultCurrency = defaultAccount?.currency ?: "RSD"
                    
                    // Користимо логику са конверзијом валута само када гледамо све рачуне
                    val accountBalance: Double
                    val displayCurrency: String
                    
                    if (selectedAccount != null) {
                        // За појединачни рачун приказујемо његов баланс у оригиналној валути
                        accountBalance = selectedAccount.balance
                        displayCurrency = selectedAccount.currency
                    } else {
                        // За све рачуне приказујемо суму свих баланса, са конверзијом у валуту подразумеваног рачуна
                        accountBalance = accounts.sumOf { account -> 
                            com.petar.smrdici.util.CurrencyConverter.convert(
                                account.balance,
                                account.currency,
                                defaultCurrency
                            )
                        }
                        displayCurrency = defaultCurrency
                    }
                    
                    Text(
                        text = "${getSymbolForCurrency(displayCurrency)}${String.format("%.2f", accountBalance)}",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                    
                    // Прогрес бар - приказује се само за појединачни рачун, не и за "Сви рачуни"
                    if (selectedAccount != null) {
                        // За појединачни рачун користимо рачунов income/expense
                        val accountExpenses = expenses.filter { it.accountId == selectedAccountId }.sumOf { it.amount }
                        val accountIncomes = incomes.filter { it.accountId == selectedAccountId }.sumOf { it.amount }
                        val accountProgress = if (accountIncomes > 0) accountExpenses / accountIncomes else 0.0
                        
                        LinearProgressIndicator(
                            progress = { accountProgress.toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFFB2C5FF), // Светло плава боја као на слици
                            trackColor = Color(0xFF3D455E) // Тамнија плава за позадину прогрес бара
                        )
                    }
                }
            }
            
            // Садржај за приказ трансакција
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if ((selectedTabIndex == 0 && expenses.isEmpty()) || 
                    (selectedTabIndex == 1 && incomes.isEmpty())) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.padding(bottom = 8.dp),
                            tint = Color.White.copy(alpha = 0.5f)
                        )
                        
                        Text(
                            text = if (selectedTabIndex == 0)
                                "Нема расхода у изабраном периоду.\nДодајте нове расходе користећи дугме +"
                            else
                                "Нема прихода у изабраном периоду.\nДодајте нове приходе користећи дугме +",
                            color = Color.White.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Овде бисмо додали листу трансакција када их има
                    when (selectedTabIndex) {
                        0 -> ExpensesTab(
                            expenses = expenses,
                            selectedPeriod = periodStrings[selectedPeriodIndex],
                            selectedAccountName = selectedAccountName
                        )
                        1 -> IncomesTab(
                            incomes = incomes,
                            selectedPeriod = periodStrings[selectedPeriodIndex],
                            selectedAccountName = selectedAccountName
                        )
                        2 -> ChartsTab()
                    }
                }
            }
        }
    }
}

@Composable
fun ExpensesTab(
    expenses: List<Expense>,
    selectedPeriod: String,
    selectedAccountName: String
) {
    val budgetViewModel: BudgetViewModel = viewModel()
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
    selectedAccountName: String
) {
    val budgetViewModel: BudgetViewModel = viewModel()
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