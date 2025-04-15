package com.petar.smrdici.ui.screens.budget

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
import com.petar.smrdici.data.model.DisplayBudget
import com.petar.smrdici.data.model.Account
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.components.BudgetBattery
import com.petar.smrdici.ui.components.StandardPullRefreshIndicator
import com.petar.smrdici.ui.navigation.Screen
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    
    // Stanja iz ViewModel-a
    val selectedPeriodIndex by budgetsViewModel.selectedPeriodIndex.collectAsState()
    val isLoading by budgetsViewModel.isLoading.collectAsState()
    val errorMessage by budgetsViewModel.errorMessage.collectAsState()
    val expenseBudgets by budgetsViewModel.displayExpenseBudgets.collectAsState()
    val incomeBudgets by budgetsViewModel.displayIncomeBudgets.collectAsState()
    val totalExpenseSpent by budgetsViewModel.totalExpenseSpent.collectAsState()
    val totalIncomeReceived by budgetsViewModel.totalIncomeReceived.collectAsState()
    
    // Stanja iz BudgetViewModel za progress bar budget
    val budgetLimit by budgetViewModel.budgetLimit.collectAsState()
    
    // UI stanja
    var selectedTabIndex by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val tabs = listOf("Расходи", "Приходи")
    var isRefreshing by remember { mutableStateOf(false) }
    var showAccountsDropdown by remember { mutableStateOf(false) }
    
    // Stanje za dialog za postavljanje budžetskog limit
    var showBudgetLimitDialog by remember { mutableStateOf(false) }
    var budgetLimitInput by remember { mutableStateOf("") }
    
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
                                    text = if (budgetLimit > 0) budgetsViewModel.formatAmount(budgetLimit) else "Није подешено",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Прогрес бар буџета
                            val progress = if (budgetLimit > 0) (totalExpenseSpent / budgetLimit).coerceIn(0.0, 1.0) else 0.0
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
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Lista budžeta - не користимо LazyColumn јер већ имамо вертикални скрол
                val budgetList = if (selectedTabIndex == 0) expenseBudgets else incomeBudgets
                
                if (budgetList.isNotEmpty()) {
                    budgetList.forEach { displayBudget ->
                        BudgetListItem(
                            displayBudget = displayBudget,
                            formatAmount = { amount -> budgetsViewModel.formatAmount(amount) },
                            onClick = {
                                navController.navigate(Screen.BudgetDetail.route + "/${displayBudget.budget.id}")
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                } else {
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

@Composable
fun BudgetListItem(
    displayBudget: DisplayBudget,
    formatAmount: (Double) -> String,
    onClick: () -> Unit
) {
    // Добијамо валуту из буџета
    val budgetViewModel: BudgetViewModel = viewModel(factory = BudgetViewModel.Factory(LocalContext.current))
    val accounts by budgetViewModel.accounts.collectAsState(initial = emptyList())
    val currency = if (displayBudget.budget.accountId.isNotEmpty()) {
        accounts.find { it.id == displayBudget.budget.accountId }?.currency ?: "RSD"
    } else {
        "RSD"
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF303436)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Gornji deo sa imenom i iznosom
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    // Ime budžeta/kategorije
                    Text(
                        text = displayBudget.budget.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Kategorije ako postoje
                    if (displayBudget.budget.categoryIds.isNotEmpty()) {
                        val categoryCount = displayBudget.budget.categoryIds.size
                        val categoryText = when (categoryCount) {
                            1 -> "Буџет за категорију"
                            else -> "Буџет за $categoryCount категорија"
                        }
                        
                        Text(
                            text = categoryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    } else {
                        Text(
                            text = "Укупан буџет",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
                
                // Iznos potrošnje (za rashode) ili prihoda (za prihode)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatAmount(displayBudget.spentAmount),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                    
                    Text(
                        text = "од ${formatAmount(displayBudget.budget.amount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Заменимо обичан прогрес бар са BudgetBattery компонентом
            BudgetBattery(
                modifier = Modifier.fillMaxWidth(),
                expenses = displayBudget.spentAmount,
                budget = displayBudget.budget.amount,
                currency = currency,
                backgroundNotFilled = Color(0xFF303436)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
} 