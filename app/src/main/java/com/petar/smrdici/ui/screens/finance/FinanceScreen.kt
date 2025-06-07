package com.petar.smrdici.ui.screens.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.components.StandardPullRefreshIndicator
import com.petar.smrdici.ui.navigation.Screen
import com.petar.smrdici.utils.LogUtils
import kotlinx.coroutines.launch
import org.threeten.bp.LocalDate
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.temporal.ChronoUnit
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FinanceScreen(
    modifier: Modifier = Modifier,
    viewModel: FinanceViewModel,
    navController: NavController,
    authViewModel: AuthViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val authState by authViewModel.authState.collectAsState()
    val user = if (authState is AuthState.Authenticated) (authState as AuthState.Authenticated).user else null
    
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            coroutineScope.launch {
                isRefreshing = true
                viewModel.syncWithRemote()
                isRefreshing = false
            }
        }
    )

    // Log screen entry and refresh data
    LaunchedEffect(Unit) {
        LogUtils.i("FinanceScreen", "Screen entered - using unified transactions collection", "ui")
        viewModel.refreshOnResume()
    }

    val numberFormat = remember { 
        NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("sr").setRegion("RS").build())
    }

    var showAccountSelector by remember { mutableStateOf(false) }

    // Create currency formatters for each currency
    val currencyFormatters = remember {
        mapOf(
            "RSD" to NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("sr").setRegion("RS").build()),
            "EUR" to NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("de").setRegion("DE").build()),
            "USD" to NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("US").build()),
            "GBP" to NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("GB").build())
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        Scaffold(
            topBar = {
                AppHeader(
                    title = "Финансије",
                    user = user,
                    navController = navController,
                    showBackButton = true
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        when (state.selectedTransactionType) {
                            is TransactionType.Income -> navController.navigate(Screen.AddIncome.route)
                            is TransactionType.Expense -> {
                                // Pass the selected date when navigating to AddExpense
                                val selectedDate = when (val period = state.selectedTimePeriod) {
                                    is TimePeriod.Day -> period.date.toString()
                                    is TimePeriod.Week, is TimePeriod.Month, is TimePeriod.Year, is TimePeriod.Custom -> 
                                        LocalDate.now().toString() // Default to today for other period types
                                }
                                navController.navigate(Screen.AddExpense.createRoute(selectedDate))
                            }
                        }
                    },
                    containerColor = when (state.selectedTransactionType) {
                        is TransactionType.Income -> MaterialTheme.colorScheme.primary
                        is TransactionType.Expense -> MaterialTheme.colorScheme.error
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = when (state.selectedTransactionType) {
                            is TransactionType.Income -> "Додај приход"
                            is TransactionType.Expense -> "Додај расход"
                        }
                    )
                }
            }
        ) { paddingValues ->
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Time Period Selector
                TimePeriodSelector(
                    currentPeriod = state.selectedTimePeriod,
                    onNavigatePrevious = { viewModel.navigateToPreviousPeriod() },
                    onNavigateNext = { viewModel.navigateToNextPeriod() },
                    onResetToCurrentPeriod = { viewModel.resetToCurrentPeriod() },
                    isCurrentPeriod = state.selectedTimePeriod.isCurrentPeriod(),
                    onPeriodTypeSelected = { viewModel.setTimePeriodType(it) }
                )

                // Transaction Type Selector
                TransactionTypeSelector(
                    selectedType = state.selectedTransactionType,
                    onTypeSelected = { viewModel.setTransactionType(it) }
                )

                // Total Amount Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (state.selectedTransactionType) {
                            is TransactionType.Income -> MaterialTheme.colorScheme.primaryContainer
                            is TransactionType.Expense -> MaterialTheme.colorScheme.errorContainer
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Account selection button
                        Button(
                            onClick = { showAccountSelector = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text(
                                text = state.accounts.find { it.id == state.selectedAccountId }?.name ?: "Сви рачуни",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // If a specific account is selected, show its balance in native currency
                        if (state.selectedAccountId != null) {
                            val accountBalance = state.accountBalances[state.selectedAccountId]
                            if (accountBalance != null) {
                                val nativeCurrencyFormatter = currencyFormatters[accountBalance.nativeCurrency]
                                
                                // Display transaction total for the period
                                Text(
                                    text = when (state.selectedTransactionType) {
                                        is TransactionType.Income -> "Укупни приходи за период:"
                                        is TransactionType.Expense -> "Укупни расходи за период:"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when (state.selectedTransactionType) {
                                        is TransactionType.Income -> MaterialTheme.colorScheme.onPrimaryContainer
                                        is TransactionType.Expense -> MaterialTheme.colorScheme.onErrorContainer
                                    }
                                )
                                
                                Text(
                                    text = nativeCurrencyFormatter?.format(accountBalance.transactionTotal)
                                        ?: "${accountBalance.transactionTotal} ${accountBalance.nativeCurrency}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = when (state.selectedTransactionType) {
                                        is TransactionType.Income -> MaterialTheme.colorScheme.onPrimaryContainer
                                        is TransactionType.Expense -> MaterialTheme.colorScheme.onErrorContainer
                                    }
                                )
                                
                                // Show account balance
                                val account = state.accounts.find { it.id == state.selectedAccountId }
                                if (account != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        thickness = 1.dp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                    )
                                    
                                    Text(
                                        text = "Тренутно стање рачуна:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    // Get the current balance from the accountBalances map which has the latest data
                                    val currentBalance = state.accountBalances[account.id]?.let { 
                                        // Use the current balance from repository
                                        val currentAccountBalance = accountBalance.currentBalance
                                        
                                        nativeCurrencyFormatter?.format(currentAccountBalance)
                                            ?: "$currentAccountBalance ${accountBalance.nativeCurrency}"
                                    } ?: (nativeCurrencyFormatter?.format(account.balance)
                                        ?: "${account.balance} ${accountBalance.nativeCurrency}")
                                    
                                    Text(
                                        text = currentBalance,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            // Show total in EUR for all accounts
                            val eurFormatter = currencyFormatters["EUR"]
                            
                            Text(
                                text = when (state.selectedTransactionType) {
                                    is TransactionType.Income -> "Укупни приходи за период (EUR):"
                                    is TransactionType.Expense -> "Укупни расходи за период (EUR):"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = when (state.selectedTransactionType) {
                                    is TransactionType.Income -> MaterialTheme.colorScheme.onPrimaryContainer
                                    is TransactionType.Expense -> MaterialTheme.colorScheme.onErrorContainer
                                }
                            )
                            
                            Text(
                                text = eurFormatter?.format(state.totalAmountInEur) ?: "${state.totalAmountInEur} EUR",
                                style = MaterialTheme.typography.headlineMedium,
                                color = when (state.selectedTransactionType) {
                                    is TransactionType.Income -> MaterialTheme.colorScheme.onPrimaryContainer
                                    is TransactionType.Expense -> MaterialTheme.colorScheme.onErrorContainer
                                }
                            )

                            // Show individual account balances
                            if (state.accountBalances.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                )
                                
                                Text(
                                    text = "По рачунима:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.Start)
                                )
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    state.accounts.forEach { account ->
                                        val balance = state.accountBalances[account.id]
                                        if (balance != null && balance.transactionTotal != 0.0) {
                                            val formatter = currencyFormatters[balance.nativeCurrency]
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = account.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = formatter?.format(balance.transactionTotal)
                                                        ?: "${balance.transactionTotal} ${balance.nativeCurrency}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Account Selection Dialog
                if (showAccountSelector) {
                    LogUtils.d("FinanceScreen", "Opening account selector dialog", "ui")
                    AlertDialog(
                        onDismissRequest = { 
                            LogUtils.d("FinanceScreen", "Account selector dialog dismissed", "ui")
                            showAccountSelector = false 
                        },
                        title = { Text("Изаберите рачун") },
                        text = {
                            Column(
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // All Accounts Option
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            LogUtils.i("FinanceScreen", "Selected: All accounts", "ui")
                                            viewModel.setSelectedAccount(null)
                                            showAccountSelector = false
                                        },
                                    color = if (state.selectedAccountId == null)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                ) {
                                    Text(
                                        text = "Сви рачуни",
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }

                                // Individual Accounts
                                state.accounts.forEach { account ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                LogUtils.i("FinanceScreen", "Selected account: ${account.name}", "ui")
                                                viewModel.setSelectedAccount(account.id)
                                                showAccountSelector = false
                                            },
                                        color = if (state.selectedAccountId == account.id)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surface
                                    ) {
                                        Text(
                                            text = account.name,
                                            modifier = Modifier.padding(16.dp),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showAccountSelector = false }) {
                                Text("Затвори")
                            }
                        }
                    )
                }

                // Transactions List
                if (state.isLoading) {
                    LogUtils.d("FinanceScreen", "Loading transactions...", "ui")
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (state.transactions.isEmpty()) {
                    LogUtils.i("FinanceScreen", "No transactions found for selected period", "ui")
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Нема трансакција за изабрани период",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    LogUtils.d("FinanceScreen", "Displaying ${state.transactions.size} transactions from unified collection", "ui")
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.transactions) { transaction ->
                            TransactionItem(
                                transaction = transaction,
                                numberFormat = numberFormat,
                                onEdit = {
                                    when (transaction) {
                                        is IncomeTransaction -> {
                                            LogUtils.i("FinanceScreen", "Editing income: ${transaction.income.id}", "ui")
                                            navController.navigate("edit_income/${transaction.income.id}")
                                        }
                                        is ExpenseTransaction -> {
                                            LogUtils.i("FinanceScreen", "Editing expense: ${transaction.expense.id}", "ui")
                                            navController.navigate("edit_expense/${transaction.expense.id}")
                                        }
                                    }
                                },
                                onDelete = {
                                    when (transaction) {
                                        is IncomeTransaction -> {
                                            LogUtils.i("FinanceScreen", "Deleting income: ${transaction.income.id}", "ui")
                                            viewModel.deleteIncome(transaction.income.id)
                                        }
                                        is ExpenseTransaction -> {
                                            LogUtils.i("FinanceScreen", "Deleting expense: ${transaction.expense.id}", "ui")
                                            viewModel.deleteExpense(transaction.expense.id)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        StandardPullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
    
    // Display error if any
    if (state.error != null) {
        LaunchedEffect(state.error) {
            LogUtils.e("FinanceScreen", "Error: ${state.error}", category = "ui")
        }
    }
}

@Composable
fun TimePeriodSelector(
    currentPeriod: TimePeriod,
    onNavigatePrevious: () -> Unit,
    onNavigateNext: () -> Unit,
    onResetToCurrentPeriod: () -> Unit,
    isCurrentPeriod: Boolean,
    onPeriodTypeSelected: (PeriodType) -> Unit
) {
    var showPeriodTypeDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { 
                    LogUtils.i("FinanceScreen", "Navigating to previous period", "ui")
                    onNavigatePrevious() 
                }
            ) {
                Icon(
                    Icons.Default.ChevronLeft,
                    contentDescription = "Претходни период",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = when (currentPeriod) {
                    is TimePeriod.Day -> currentPeriod.date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                    is TimePeriod.Week -> {
                        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
                        "${currentPeriod.startDate.format(formatter)} - ${currentPeriod.startDate.plus(6, ChronoUnit.DAYS).format(formatter)}"
                    }
                    is TimePeriod.Month -> {
                        val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.Builder().setLanguage("sr").setRegion("RS").build())
                        currentPeriod.yearMonth.format(formatter).replaceFirstChar { it.uppercase() }
                    }
                    is TimePeriod.Year -> currentPeriod.year.toString()
                    is TimePeriod.Custom -> {
                        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
                        "${currentPeriod.startDate.format(formatter)} - ${currentPeriod.endDate.format(formatter)}"
                    }
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { 
                    LogUtils.d("FinanceScreen", "Opening period type selector", "ui")
                    showPeriodTypeDialog = true 
                }
            )

            IconButton(
                onClick = { 
                    LogUtils.i("FinanceScreen", "Navigating to next period", "ui")
                    onNavigateNext() 
                }
            ) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Следећи период",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        if (!isCurrentPeriod) {
            Text(
                text = when (currentPeriod) {
                    is TimePeriod.Day -> "Врати се на данашњи дан"
                    is TimePeriod.Week -> "Врати се на тренутну недељу"
                    is TimePeriod.Month -> "Врати се на тренутни месец"
                    is TimePeriod.Year -> "Врати се на тренутну годину"
                    is TimePeriod.Custom -> "Врати се на тренутни период"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable {
                        LogUtils.i("FinanceScreen", "Resetting to current period", "ui")
                        onResetToCurrentPeriod()
                    }
                    .padding(bottom = 8.dp),
                textDecoration = TextDecoration.Underline
            )
        }
    }

    if (showPeriodTypeDialog) {
        AlertDialog(
            onDismissRequest = { 
                LogUtils.d("FinanceScreen", "Period type selector dismissed", "ui")
                showPeriodTypeDialog = false 
            },
            title = { Text("Изаберите период") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PeriodType.values().forEach { periodType ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val periodName = when (periodType) {
                                        PeriodType.YEAR -> "Year"
                                        PeriodType.MONTH -> "Month"
                                        PeriodType.WEEK -> "Week"
                                        PeriodType.DAY -> "Day"
                                        PeriodType.CUSTOM -> "Custom"
                                    }
                                    LogUtils.i("FinanceScreen", "Selected period type: $periodName", "ui")
                                    onPeriodTypeSelected(periodType)
                                    showPeriodTypeDialog = false
                                },
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = when (periodType) {
                                    PeriodType.YEAR -> "Година"
                                    PeriodType.MONTH -> "Месец"
                                    PeriodType.WEEK -> "Недеља"
                                    PeriodType.DAY -> "Дан"
                                    PeriodType.CUSTOM -> "Прилагођени период"
                                },
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPeriodTypeDialog = false }) {
                    Text("Откажи")
                }
            }
        )
    }
}

@Composable
fun TransactionTypeSelector(
    selectedType: TransactionType,
    onTypeSelected: (TransactionType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedType is TransactionType.Income,
            onClick = { 
                LogUtils.i("FinanceScreen", "Selected transaction type: Income", "ui")
                onTypeSelected(TransactionType.Income) 
            },
            label = { Text("Приходи") },
            leadingIcon = {
                Icon(
                    Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            },
            modifier = Modifier.weight(1f)
        )
        
        FilterChip(
            selected = selectedType is TransactionType.Expense,
            onClick = { 
                LogUtils.i("FinanceScreen", "Selected transaction type: Expense", "ui")
                onTypeSelected(TransactionType.Expense) 
            },
            label = { Text("Расходи") },
            leadingIcon = {
                Icon(
                    Icons.AutoMirrored.Filled.TrendingDown,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionItem(
    transaction: UITransaction,
    numberFormat: NumberFormat,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                when (transaction) {
                    is IncomeTransaction -> {
                        Text(
                            text = transaction.income.description ?: "Приход",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = transaction.date,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is ExpenseTransaction -> {
                        Text(
                            text = transaction.expense.description ?: "Расход",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = transaction.date,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = numberFormat.format(transaction.amount),
                    style = MaterialTheme.typography.titleMedium,
                    color = when (transaction) {
                        is IncomeTransaction -> MaterialTheme.colorScheme.primary
                        is ExpenseTransaction -> MaterialTheme.colorScheme.error
                    }
                )
                
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Измени",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Обриши",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
} 