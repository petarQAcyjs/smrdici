package com.petar.smrdici.ui.screens.finance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.R
import com.petar.smrdici.data.model.Account
import com.petar.smrdici.ui.navigation.Screen
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.temporal.ChronoUnit
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceScreen(
    modifier: Modifier = Modifier,
    viewModel: FinanceViewModel = viewModel(),
    navController: NavController,
    authViewModel: AuthViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val authState by authViewModel.authState.collectAsState()
    val user = if (authState is AuthState.Authenticated) (authState as AuthState.Authenticated).user else null
    
    val numberFormat = remember { 
        NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("sr").setRegion("RS").build())
    }

    var showAccountSelector by remember { mutableStateOf(false) }

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
                        is TransactionType.Expense -> navController.navigate(Screen.AddExpense.route)
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
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Account Selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.selectedAccountId?.let { id ->
                                state.accounts.find { it.id == id }?.name
                            } ?: "Сви рачуни",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.clickable { showAccountSelector = true }
                        )
                        IconButton(onClick = { showAccountSelector = true }) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Изаберите рачун"
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )

                    Text(
                        text = "Укупно",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = numberFormat.format(state.totalAmount),
                        style = MaterialTheme.typography.headlineMedium,
                        color = when (state.selectedTransactionType) {
                            is TransactionType.Income -> MaterialTheme.colorScheme.onPrimaryContainer
                            is TransactionType.Expense -> MaterialTheme.colorScheme.onErrorContainer
                        }
                    )
                }
            }

            // Account Selection Dialog
            if (showAccountSelector) {
                AlertDialog(
                    onDismissRequest = { showAccountSelector = false },
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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (state.transactions.isEmpty()) {
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
                                    is IncomeTransaction -> navController.navigate("edit_income/${transaction.income.id}")
                                    is ExpenseTransaction -> navController.navigate("edit_expense/${transaction.expense.id}")
                                }
                            },
                            onDelete = {
                                when (transaction) {
                                    is IncomeTransaction -> viewModel.deleteIncome(transaction.income.id)
                                    is ExpenseTransaction -> viewModel.deleteExpense(transaction.expense.id)
                                }
                            }
                        )
                    }
                }
            }
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
            IconButton(onClick = onNavigatePrevious) {
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
                modifier = Modifier.clickable { showPeriodTypeDialog = true }
            )

            IconButton(onClick = onNavigateNext) {
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
                    .clickable(onClick = onResetToCurrentPeriod)
                    .padding(bottom = 8.dp),
                textDecoration = TextDecoration.Underline
            )
        }
    }

    if (showPeriodTypeDialog) {
        AlertDialog(
            onDismissRequest = { showPeriodTypeDialog = false },
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
            onClick = { onTypeSelected(TransactionType.Income) },
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
            onClick = { onTypeSelected(TransactionType.Expense) },
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
    transaction: Transaction,
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