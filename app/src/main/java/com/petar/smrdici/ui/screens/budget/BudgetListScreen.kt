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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.data.model.Expense
import com.petar.smrdici.data.model.Income
import com.petar.smrdici.data.model.DisplayBudget
import com.petar.smrdici.data.model.Account
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.components.BudgetBattery
import com.petar.smrdici.ui.components.StandardPullRefreshIndicator
import com.petar.smrdici.ui.navigation.Screen
import kotlinx.coroutines.launch
import com.petar.smrdici.data.model.CategoryManager

/**
 * Враћа валуту за приказ на основу изабраног рачуна
 */
private fun getCurrencyForAccount(accounts: List<Account>, selectedAccountId: String?): String {
    return if (selectedAccountId != null) {
        accounts.find { it.id == selectedAccountId }?.currency ?: "RSD"
    } else {
        val currencies = accounts.mapNotNull { it.currency }.distinct()
        if (currencies.isEmpty()) "RSD" else currencies.first()
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER")
@Composable
fun BudgetListScreen(
    navController: NavController,
    budgetsViewModel: BudgetsViewModel = viewModel(factory = BudgetsViewModel.Factory(LocalContext.current)),
    budgetViewModel: BudgetViewModel = viewModel(factory = BudgetViewModel.Factory(LocalContext.current))
) {
    // Za snackbar poruke
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // Stanje ScrollView-a
    val scrollState = rememberScrollState()
    
    // LaunchedEffect za inicialno učitavanje podataka
    LaunchedEffect(Unit) {
        Log.d("BudgetListScreen", "Inicijalno učitavanje podataka")
        budgetsViewModel.loadBudgets()
        budgetViewModel.reloadTransactions()
    }
    
    // Učitavamo podatke iz ViewModela
    val isLoading by budgetsViewModel.isLoading.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }
    val totalExpenseSpent by budgetsViewModel.totalExpenseSpent.collectAsState()
    val totalIncomeReceived by budgetsViewModel.totalIncomeReceived.collectAsState()
    val errorMessage by budgetsViewModel.errorMessage.collectAsState()
    val displayExpenseBudgets by budgetsViewModel.displayExpenseBudgets.collectAsState()
    val displayIncomeBudgets by budgetsViewModel.displayIncomeBudgets.collectAsState()
    val selectedPeriodIndex by budgetsViewModel.selectedPeriodIndex.collectAsState()
    
    // Dobijamo transakcije iz BudgetViewModel
    val expenses by budgetViewModel.expenses.collectAsState(initial = emptyList())
    val incomes by budgetViewModel.incomes.collectAsState(initial = emptyList())
    
    // Dodatno - direktno pratimo UI stanje iz BudgetViewModel za dijagnostiku
    val uiState by budgetViewModel.uiState.collectAsState()
    
    // Dodajemo log za praćenje učitanih transakcija
    LaunchedEffect(expenses, incomes) {
        Log.d("BudgetListScreen", "Učitane transakcije: ${expenses.size} troškova, ${incomes.size} prihoda")
        Log.d("BudgetListScreen", "Provera uiState: ${uiState.expenses.size} troškova, ${uiState.incomes.size} prihoda")
        
        // Dodajemo dodatne provere za expenses i incomes
        if (expenses.isEmpty() && uiState.expenses.isEmpty()) {
            Log.d("BudgetListScreen", "UPOZORENJE: Lista troškova je prazna! Pozivam reloadTransactions()")
            budgetViewModel.reloadTransactions()
        }
        if (incomes.isEmpty() && uiState.incomes.isEmpty()) {
            Log.d("BudgetListScreen", "UPOZORENJE: Lista prihoda je prazna! Pozivam reloadTransactions()")
            budgetViewModel.reloadTransactions()
        }
    }
    
    // Dodatna provera za praćenje promena u uiState
    LaunchedEffect(uiState) {
        Log.d("BudgetListScreen", "PROMENA UI STANJA: ${uiState.expenses.size} troškova, ${uiState.incomes.size} prihoda, isLoading=${uiState.isLoading}")
        
        // Ako expenses ili incomes postoje u uiState ali ne i u direktnom StateFlow, treba ih uskladiti
        if (uiState.expenses.isNotEmpty() && expenses.isEmpty()) {
            Log.d("BudgetListScreen", "Nesklad između uiState.expenses i expenses - pokušavam ponovno učitavanje")
            budgetViewModel.reloadTransactions()
        }
        
        if (uiState.incomes.isNotEmpty() && incomes.isEmpty()) {
            Log.d("BudgetListScreen", "Nesklad između uiState.incomes i incomes - pokušavam ponovno učitavanje")
            budgetViewModel.reloadTransactions()
        }
    }
    
    // Dodatni hack da osiguramo da se podaci učitaju - direktno preko coroutineScope
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            Log.d("BudgetListScreen", "Direktno osvežavanje podataka preko coroutineScope")
            budgetViewModel.reloadTransactions()
            
            // Pokušavamo da pristupimo stateFlow vrednostima direktno
            val expensesCount = budgetViewModel.expenses.value.size
            val incomesCount = budgetViewModel.incomes.value.size
            val uiStateExpensesCount = budgetViewModel.uiState.value.expenses.size
            val uiStateIncomesCount = budgetViewModel.uiState.value.incomes.size
            
            Log.d("BudgetListScreen", "Direktni pristup: expenses=$expensesCount, incomes=$incomesCount")
            Log.d("BudgetListScreen", "Direktni pristup uiState: expenses=$uiStateExpensesCount, incomes=$uiStateIncomesCount")
            
            // Dodatna sigurnosna provera - ako postoje podaci u uiState ali ne u StateFlow-ovima
            if ((uiStateExpensesCount > 0 && expensesCount == 0) || 
                (uiStateIncomesCount > 0 && incomesCount == 0)) {
                Log.d("BudgetListScreen", "KRITIČNO: Podaci postoje u uiState ali ne u direktnim StateFlow-ovima!")
                // Pokušavamo još jednom nakon kratke pauze
                kotlinx.coroutines.delay(500)
                budgetViewModel.reloadTransactions()
            }
        }
    }
    
    // Pratimo promene u displayExpenseBudgets i displayIncomeBudgets
    LaunchedEffect(displayExpenseBudgets, displayIncomeBudgets) {
        Log.d("BudgetListScreen", "PROMENA BUDGETS: ${displayExpenseBudgets.size} expense budgets, ${displayIncomeBudgets.size} income budgets")
        Log.d("BudgetListScreen", "Ukupni iznosi: totalExpenseSpent=$totalExpenseSpent, totalIncomeReceived=$totalIncomeReceived")
    }
    
    // LaunchedEffect za praćenje promena perioda ili računa
    LaunchedEffect(selectedPeriodIndex, budgetsViewModel.selectedAccountId.collectAsState().value) {
        Log.d("BudgetListScreen", "Osvežavanje podataka zbog promene perioda ili računa")
        budgetsViewModel.loadBudgets()
        budgetViewModel.reloadTransactions()
    }
    
    // Stanje za tabove
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Расходи", "Приходи")
    
    // Pratimo promenu taba
    LaunchedEffect(selectedTabIndex) {
        Log.d("BudgetListScreen", "Promenjen tab na: ${tabs[selectedTabIndex]}")
    }
    
    // Stanje za dropdown menu za izbor računa
    var showAccountsDropdown by remember { mutableStateOf(false) }
    
    // Vrednosti budžeta
    val budgetLimit by budgetsViewModel.budgetLimit.collectAsState()
    
    // Konvertujemo period u čitljiv tekst
    val periodText = when(selectedPeriodIndex) {
        0 -> "danas"
        1 -> "ove sedmice"
        2 -> "ovog meseca"
        3 -> "ove godine"
        4 -> "u ovom periodu"
        else -> ""
    }
    
    // Efekat za prikazivanje greške
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            budgetsViewModel.errorMessage.value = null
        }
    }
    
    // Stanje učitavanja povezujemo sa isRefreshing
    LaunchedEffect(isLoading) {
        if (!isLoading) {
            isRefreshing = false
        }
    }
    
    // Stanje za pull-to-refresh
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                
                // Pozivamo relevantne metode za osvežavanje podataka
                budgetsViewModel.loadBudgets() 
                budgetViewModel.reloadTransactions()
            }
        }
    )
    
    // Lista perioda
    val periodStrings = listOf("Дан", "Недеља", "Месец", "Година", "Период", "Све")
    
    val accounts by budgetsViewModel.accounts.collectAsState(initial = emptyList())
    val selectedAccountId by budgetsViewModel.selectedAccountId.collectAsState()
    
    // Učitava ime odabranog računa
    var selectedAccount by remember { mutableStateOf("") }
    LaunchedEffect(selectedAccountId) {
        selectedAccount = budgetsViewModel.getSelectedAccountName()
    }
    
    // Sortiranje
    var sortOrder by remember { mutableStateOf(SortOrder.DATE_DESC) }
    
    // Stanje za dialog za postavljanje budžetskog limit
    var showBudgetLimitDialog by remember { mutableStateOf(false) }
    var budgetLimitInput by remember { mutableStateOf("") }
    
    // Pull-to-refresh
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            budgetsViewModel.loadBudgets()
            budgetViewModel.reloadTransactions()
            isRefreshing = false
        }
    }
    
    // Dodajem LaunchedEffect za inicijalno učitavanje pri ulasku na ekran
    LaunchedEffect(Unit) {
        Log.d("BudgetListScreen", "Inicijalno učitavanje podataka pri otvaranju ekrana")
        budgetsViewModel.loadBudgets()
        budgetViewModel.reloadTransactions()
    }
    
    // Основни layout sa scaffold
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF1A1C1E), // Tamna pozadina
        topBar = {
            AppHeader(
                title = "Буџети",
                navController = navController,
                showBackButton = true,
                user = null
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // Navigiramo na ekran za dodavanje rashoda ili prihoda
                    if (selectedTabIndex == 0) {
                        navController.navigate(Screen.AddExpense.route)
                    } else {
                        navController.navigate(Screen.AddIncome.route)
                    }
                },
                containerColor = Color(0xFFB2C5FF), // Светло плава боја
            ) {
                Icon(Icons.Default.Add, contentDescription = "Додај ставку")
            }
        }
    ) { paddingValues ->
        // Box sa pullRefresh модификатором око целог садржаја
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pullRefresh(pullRefreshState)
        ) {
            // Главни садржај је Column са scrollState
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .background(Color(0xFF1A1C1E)) // Tamna pozadina
                    .padding(horizontal = 16.dp)
            ) {
                // Tab bar za rashode/prihode
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
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Selektor perioda
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .background(
                            color = Color(0xFF303436),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(periodStrings.size) { index ->
                        val periodName = periodStrings[index]
                        val isSelected = selectedPeriodIndex == index
                        val textColor = if (isSelected)
                            Color(0xFFB2C5FF) // Svetlo plava za aktivni tab
                            else Color.White.copy(alpha = 0.7f)
                        val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        
                        TextButton(
                            onClick = { 
                                budgetsViewModel.updatePeriodIndex(index)
                                budgetViewModel.updatePeriodIndex(index)
                                
                                budgetLimitInput = if (budgetLimit > 0) budgetLimit.toString() else ""
                            },
                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = periodName,
                                color = textColor,
                                fontWeight = fontWeight,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Prikaz ukupnog budžeta
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF303436)
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 4.dp
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Naslov sa dropdown filterom za račun
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAccountsDropdown = true },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = selectedAccount,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Одабери рачун",
                                tint = Color.White
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Стање рачуна уместо буџета
                        val selectedAccountBalance = if (selectedAccountId != null) {
                            accounts.find { it.id == selectedAccountId }?.balance ?: 0.0
                        } else {
                            accounts.sumOf { it.balance }
                        }
                        
                        Text(
                            text = "Тренутно стање: ${budgetsViewModel.formatAmount(selectedAccountBalance)}",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Додајемо прогрес бар буџета који се може кликнути
                        if (selectedTabIndex == 0) { // Само за расходе
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Наслов прогрес бара
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Буџет $periodText:",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                
                                Text(
                                    text = if (budgetLimit > 0.0) budgetsViewModel.formatAmount(budgetLimit) else "Није подешено",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Прогрес бар буџета
                            val progress = if (budgetLimit > 0.0) (totalExpenseSpent / budgetLimit).coerceIn(0.0, 1.0) else 0.0
                            val progressColor = when {
                                progress >= 1.0 -> Color.Red
                                progress >= 0.75 -> Color(0xFFFF9800) // Наранџаста
                                else -> Color(0xFF4CAF50) // Зелена
                            }
                            
                            // Заменимо обичан прогрес бар са BudgetBattery компонентом
                            BudgetBattery(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 8.dp)
                                    .clickable { showBudgetLimitDialog = true },
                                expenses = totalExpenseSpent,
                                budget = budgetLimit,
                                currency = getCurrencyForAccount(accounts, selectedAccountId),
                                backgroundNotFilled = Color(0xFF303436),
                                onClick = { showBudgetLimitDialog = true }
                            )
                        }
                        
                        // За приходе не приказујемо прогрес бар, већ само информацију
                        if (selectedTabIndex == 1) {
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "Укупно примљено: ${budgetsViewModel.formatAmount(totalIncomeReceived)}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Opcije za sortiranje
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { 
                            sortOrder = when(sortOrder) {
                                SortOrder.DATE_DESC -> SortOrder.AMOUNT_DESC
                                SortOrder.AMOUNT_DESC -> SortOrder.AMOUNT_ASC
                                SortOrder.AMOUNT_ASC -> SortOrder.DATE_DESC
                            }
                        }
                    ) {
                        val sortText = when(sortOrder) {
                            SortOrder.DATE_DESC -> "Сортирај по: Датуму"
                            SortOrder.AMOUNT_DESC -> "Сортирај по: Износу (↓)"
                            SortOrder.AMOUNT_ASC -> "Сортирај по: Износу (↑)"
                        }
                        Text(
                            text = sortText,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Prikazujemo listu TRANSAKCIJA, ne budžeta
                val transactions = if (selectedTabIndex == 0) {
                    // Sortiranje troškova
                    Log.d("BudgetListScreen", "TRANSAKCIJE: Pripremam troškove za prikaz, ukupno: ${expenses.size}")
                    expenses.forEach { expense ->
                        Log.d("BudgetListScreen", "EXPENSE: id=${expense.id}, amount=${expense.amount}, date=${expense.date}, category=${expense.category}")
                    }
                    
                    val sortedExpenses = when(sortOrder) {
                        SortOrder.DATE_DESC -> expenses.sortedByDescending { it.getDateObject()?.time ?: 0L }
                        SortOrder.AMOUNT_DESC -> expenses.sortedByDescending { it.amount }
                        SortOrder.AMOUNT_ASC -> expenses.sortedBy { it.amount }
                    }
                    Log.d("BudgetListScreen", "Pripremljeno za prikaz: ${sortedExpenses.size} troškova")
                    sortedExpenses
                } else {
                    // Sortiranje prihoda
                    Log.d("BudgetListScreen", "TRANSAKCIJE: Pripremam prihode za prikaz, ukupno: ${incomes.size}")
                    incomes.forEach { income ->
                        Log.d("BudgetListScreen", "INCOME: id=${income.id}, amount=${income.amount}, date=${income.date}, category=${income.category}")
                    }
                    
                    val sortedIncomes = when(sortOrder) {
                        SortOrder.DATE_DESC -> incomes.sortedByDescending { it.getDateObject()?.time ?: 0L }
                        SortOrder.AMOUNT_DESC -> incomes.sortedByDescending { it.amount }
                        SortOrder.AMOUNT_ASC -> incomes.sortedBy { it.amount }
                    }
                    Log.d("BudgetListScreen", "Pripremljeno za prikaz: ${sortedIncomes.size} prihoda")
                    sortedIncomes
                }
                
                if (transactions.isNotEmpty()) {
                    Log.d("BudgetListScreen", "TRANSAKCIJE: Prikazujem ${transactions.size} transakcija")
                    transactions.forEach { transaction ->
                        Log.d("BudgetListScreen", "Tip transakcije: ${transaction.javaClass.simpleName}, iznos: ${
                            when(transaction) {
                                is Expense -> transaction.amount
                                is Income -> transaction.amount
                                else -> 0.0
                            }
                        }")
                        when (transaction) {
                            is Expense -> ExpenseListItem(
                                expense = transaction,
                                formatAmount = { amount -> budgetsViewModel.formatAmount(amount) },
                                accounts = accounts
                            )
                            is Income -> IncomeListItem(
                                income = transaction,
                                formatAmount = { amount -> budgetsViewModel.formatAmount(amount) },
                                accounts = accounts
                            )
                            else -> {
                                Log.e("BudgetListScreen", "Nepoznat tip transakcije: ${transaction.javaClass.name}")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                } else {
                    Log.d("BudgetListScreen", "Nema transakcija za prikaz za tab: $selectedTabIndex")
                    // Prikaz za prazan ekran
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val emptyMessage = if (selectedTabIndex == 0) {
                                "Нема евидентираних трошкова у овом периоду"
                            } else {
                                "Нема евидентираних прихода у овом периоду"
                            }
                            
                            val emptyDetailsMessage = if (selectedTabIndex == 0) {
                                "Кликните на + дугме да додате трошак"
                            } else {
                                "Кликните на + дугме да додате приход"
                            }
                            
                            Text(
                                text = emptyMessage,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = emptyDetailsMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                
                // Додајемо простор на дну
                Spacer(modifier = Modifier.height(80.dp))
            }
            
            // Dropdown meni za izbor računa
            DropdownMenu(
                expanded = showAccountsDropdown,
                onDismissRequest = { showAccountsDropdown = false },
                modifier = Modifier.background(Color(0xFF303436))
            ) {
                // Opcija "Svi računi"
                DropdownMenuItem(
                    text = { Text("Сви рачуни", color = Color.White) },
                    onClick = {
                        budgetsViewModel.filterByAccount(null)
                        budgetViewModel.selectAccount(null)
                        showAccountsDropdown = false
                    }
                )
                
                // Lista računa
                accounts.forEach { account ->
                    DropdownMenuItem(
                        text = { Text(account.name, color = Color.White) },
                        onClick = {
                            budgetsViewModel.filterByAccount(account.id)
                            budgetViewModel.selectAccount(account.id)
                            showAccountsDropdown = false
                        }
                    )
                }
            }
            
            // Заменићу PullRefreshIndicator стандардизованом компонентом
            StandardPullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
    
    // Dialog za podešavanje budžetskog limita
    if (showBudgetLimitDialog) {
        AlertDialog(
            onDismissRequest = { showBudgetLimitDialog = false },
            title = { Text("Подесите буџетски лимит") },
            text = {
                Column {
                    Text("Унесите максимални износ који желите да потрошите $periodText.")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = budgetLimitInput,
                        onValueChange = { budgetLimitInput = it },
                        label = { Text("Износ") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            val limitValue = budgetLimitInput.toDoubleOrNull()
                            if (limitValue != null && limitValue > 0) {
                                budgetViewModel.saveBudgetLimit(limitValue)
                                showBudgetLimitDialog = false
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Унесите валидан износ већи од нуле")
                                }
                            }
                        } catch (_: Exception) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Грешка при чувању буџетског лимита")
                            }
                        }
                    }
                ) {
                    Text("Сачувај")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBudgetLimitDialog = false }) {
                    Text("Откажи")
                }
            }
        )
    }
}

// Dodajemo enum za sortiranje
enum class SortOrder {
    DATE_DESC, AMOUNT_DESC, AMOUNT_ASC
}

@Composable
fun ExpenseListItem(
    expense: Expense,
    formatAmount: (Double) -> String,
    accounts: List<Account>
) {
    val account = accounts.find { it.id == expense.accountId }
    // Koristimo getFormattedDate metodu za prikazivanje datuma
    val dateFormatted = expense.getFormattedDate()
    
    // Koristimo CategoryManager za dobijanje imena kategorije
    val context = LocalContext.current
    val categoryManager = CategoryManager.getInstance(context)
    val categoryName = categoryManager.getExpenseCategoryDisplayName(expense.category)
    
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF303436)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Levi deo - kategorija i opis
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                
                if (expense.description.isNotEmpty()) {
                    Text(
                        text = expense.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                
                Text(
                    text = dateFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
                
                if (accounts.size > 1) {
                    Text(
                        text = account?.name ?: "Непознат рачун",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
            
            // Desni deo - iznos
            Text(
                text = formatAmount(expense.amount),
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFFF9800), // Narandžasta za troškove
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun IncomeListItem(
    income: Income,
    formatAmount: (Double) -> String,
    accounts: List<Account>
) {
    val account = accounts.find { it.id == income.accountId }
    // Koristimo getFormattedDate metodu za prikazivanje datuma
    val dateFormatted = income.getFormattedDate()
    
    // Koristimo CategoryManager za dobijanje imena kategorije
    val context = LocalContext.current
    val categoryManager = CategoryManager.getInstance(context)
    val categoryName = categoryManager.getIncomeCategoryDisplayName(income.category)
    
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF303436)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Levi deo - kategorija i opis
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                
                if (income.description.isNotEmpty()) {
                    Text(
                        text = income.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                
                Text(
                    text = dateFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
                
                if (accounts.size > 1) {
                    Text(
                        text = account?.name ?: "Непознат рачун",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
            
            // Desni deo - iznos
            Text(
                text = formatAmount(income.amount),
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF4CAF50), // Zelena za prihode
                fontWeight = FontWeight.Bold
            )
        }
    }
} 