package com.petar.smrdici.ui.screens.finance

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.petar.smrdici.data.model.CategoryIcons
import com.petar.smrdici.data.model.CategoryManager
import com.petar.smrdici.ui.components.StandardPullRefreshIndicator
import com.petar.smrdici.ui.navigation.Screen
import com.petar.smrdici.utils.LogUtils
import com.petar.smrdici.utils.rememberWindowInfo
import kotlinx.coroutines.launch
import org.threeten.bp.LocalDate
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.temporal.ChronoUnit
import java.text.NumberFormat
import java.util.Locale

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun FinanceScreen(
    modifier: Modifier = Modifier,
    viewModel: FinanceViewModel,
    navController: NavController
) {
    val state by viewModel.state.collectAsState()
    var isFabMenuExpanded by remember { mutableStateOf(false) }

    val windowInfo = rememberWindowInfo()
    val screenHorizontalPadding = if (windowInfo.isSmallWidth) 8.dp else 16.dp
    val cardContentPadding = if (windowInfo.isSmallWidth) 10.dp else 16.dp

    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                viewModel.syncWithRemote()
                isRefreshing = false
            }
        }
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                LogUtils.d("FinanceScreen", "Resumed - refreshing data", "ui")
                viewModel.refreshOnResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val srLocale = remember { Locale.Builder().setLanguage("sr").setRegion("RS").build() }

    val numberFormat = remember(srLocale) {
        NumberFormat.getCurrencyInstance(srLocale)
    }

    var showAccountSelector by remember { mutableStateOf(false) }

    val currencyFormatters = remember(srLocale) {
        mapOf(
            "RSD" to NumberFormat.getCurrencyInstance(srLocale),
            "EUR" to NumberFormat.getCurrencyInstance(Locale.GERMANY),
            "USD" to NumberFormat.getCurrencyInstance(Locale.US),
            "GBP" to NumberFormat.getCurrencyInstance(Locale.UK)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        Scaffold(
            floatingActionButton = {
                when (state.selectedTransactionType) {
                    is TransactionType.Income -> {
                        FloatingActionButton(
                            onClick = {
                                navController.navigate(Screen.AddIncome.route)
                            },
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Додај приход"
                            )
                        }
                    }
                    is TransactionType.Expense -> {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AnimatedVisibility(
                                visible = isFabMenuExpanded,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    SmallFloatingActionButton(
                                        onClick = {
                                            isFabMenuExpanded = false
                                            navController.navigate(Screen.ReceiptScanner.route)
                                        },
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DocumentScanner,
                                            contentDescription = "OCR Unos"
                                        )
                                    }

                                    SmallFloatingActionButton(
                                        onClick = {
                                            isFabMenuExpanded = false
                                            val selectedDate = when (val period = state.selectedTimePeriod) {
                                                is TimePeriod.Day -> period.date.toString()
                                                is TimePeriod.Week, is TimePeriod.Month, is TimePeriod.Year, is TimePeriod.Custom ->
                                                    LocalDate.now().toString()
                                            }
                                            navController.navigate(Screen.AddExpense.createRoute(selectedDate))
                                        },
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = "Ручни unos"
                                        )
                                    }
                                }
                            }

                            FloatingActionButton(
                                onClick = { isFabMenuExpanded = !isFabMenuExpanded },
                                containerColor = MaterialTheme.colorScheme.error
                            ) {
                                Icon(
                                    imageVector = if (isFabMenuExpanded) Icons.Default.Close else Icons.Default.Add,
                                    contentDescription = if (isFabMenuExpanded) "Zatvori meni" else "Dodaj rashod"
                                )
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
            ) {
                TimePeriodSelector(
                    currentPeriod = state.selectedTimePeriod,
                    onNavigatePrevious = { viewModel.navigateToPreviousPeriod() },
                    onNavigateNext = { viewModel.navigateToNextPeriod() },
                    onResetToCurrentPeriod = { viewModel.resetToCurrentPeriod() },
                    isCurrentPeriod = state.selectedTimePeriod.isCurrentPeriod(),
                    onPeriodTypeSelected = { viewModel.setTimePeriodType(it) }
                )

                TransactionTypeSelector(
                    selectedType = state.selectedTransactionType,
                    onTypeSelected = { viewModel.setTransactionType(it) },
                    horizontalPadding = screenHorizontalPadding
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = screenHorizontalPadding, end = screenHorizontalPadding, top = 8.dp, bottom = 4.dp),
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
                            .padding(cardContentPadding),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val selectedAccount = state.accounts.find { it.id == state.selectedAccountId } ?: state.accounts.firstOrNull()
                            val accountName = selectedAccount?.name ?: ""

                            val contentColor = when (state.selectedTransactionType) {
                                is TransactionType.Income -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                is TransactionType.Expense -> MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                            }

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showAccountSelector = true }
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = accountName,
                                    style = if (windowInfo.isSmallWidth) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = contentColor
                                )
                                Icon(
                                    imageVector = Icons.Default.ExpandMore,
                                    contentDescription = "Изабери рачун",
                                    tint = contentColor.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showAccountSelector,
                                onDismissRequest = { showAccountSelector = false }
                            ) {
                                state.accounts.forEach { account ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = account.name,
                                                fontWeight = if (state.selectedAccountId == account.id) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            viewModel.setSelectedAccount(account.id)
                                            showAccountSelector = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Преостали износ:",
                            style = MaterialTheme.typography.bodySmall,
                            color = when (state.selectedTransactionType) {
                                is TransactionType.Income -> MaterialTheme.colorScheme.onPrimaryContainer
                                is TransactionType.Expense -> MaterialTheme.colorScheme.onErrorContainer
                            }
                        )

                        val activeAccountId = state.selectedAccountId ?: state.accounts.firstOrNull()?.id
                        val accountBalance = state.accountBalances[activeAccountId]
                        val account = state.accounts.find { it.id == activeAccountId }

                        val currencyKey = accountBalance?.nativeCurrency ?: account?.currency ?: "RSD"
                        val nativeCurrencyFormatter = currencyFormatters[currencyKey] ?: numberFormat

                        val currentAccountBalance = accountBalance?.currentBalance ?: account?.balance ?: 0.0
                        val balanceText = nativeCurrencyFormatter.format(currentAccountBalance)

                        Text(
                            text = balanceText,
                            style = if (windowInfo.isSmallWidth) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = when (state.selectedTransactionType) {
                                is TransactionType.Income -> MaterialTheme.colorScheme.onPrimaryContainer
                                is TransactionType.Expense -> MaterialTheme.colorScheme.onErrorContainer
                            }
                        )
                    }
                }

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
                    var categoriesExpanded by remember { mutableStateOf(false) }

                    // 1. Filtriramo transakcije ako je selektovana neka kategorija
                    val displayedTransactions = remember(state.transactions, state.selectedCategory) {
                        val selected = state.selectedCategory
                        if (selected != null) {
                            state.transactions.filter { transaction ->
                                val cat = when (transaction) {
                                    is ExpenseTransaction -> transaction.expense.category
                                    is IncomeTransaction -> transaction.income.category
                                }
                                cat.equals(selected.rawCategory, ignoreCase = true) ||
                                        cat.equals(selected.categoryName, ignoreCase = true)
                            }
                        } else {
                            state.transactions
                        }
                    }

                    // 2. Proveravamo sortiranje po datumu na filtriranim transakcijama
                    val isDateSort = state.sortOption == SortOption.DATE_NEWEST || state.sortOption == SortOption.DATE_OLDEST

                    val groupedTransactions = remember(displayedTransactions, state.sortOption) {
                        if (isDateSort) {
                            displayedTransactions.groupBy { it.date }
                        } else {
                            mapOf("" to displayedTransactions)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = screenHorizontalPadding, vertical = 8.dp)
                    ) {
                        item {
                            CombinedHeaderWithSort(
                                categoriesExpanded = categoriesExpanded,
                                onToggleCategories = { categoriesExpanded = !categoriesExpanded },
                                onSortSelected = { viewModel.setSortOption(it, isCategoriesExpanded = categoriesExpanded) },
                                isSmall = windowInfo.isSmallWidth
                            )
                        }

                        item {
                            AnimatedVisibility(
                                visible = categoriesExpanded,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                val summaries = state.categorySummaries
                                val itemsPerRow = 2
                                val rows = summaries.chunked(itemsPerRow)

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    rows.forEach { rowSummaries ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            rowSummaries.forEach { summary ->
                                                val icon = CategoryIcons.findIconByName(summary.iconName)
                                                    ?: Icons.Default.MoreHoriz

                                                val count = state.transactions.count { transaction ->
                                                    val cat = when (transaction) {
                                                        is ExpenseTransaction -> transaction.expense.category
                                                        is IncomeTransaction -> transaction.income.category
                                                    }
                                                    cat.equals(summary.rawCategory, ignoreCase = true) ||
                                                            cat.equals(summary.categoryName, ignoreCase = true)
                                                }

                                                CategoryCard(
                                                    icon = icon,
                                                    backgroundColor = summary.color,
                                                    categoryName = summary.categoryName,
                                                    percentage = String.format(srLocale, "%.1f%%", summary.percentage),
                                                    amount = numberFormat.format(summary.amount),
                                                    count = count,
                                                    onClick = {
                                                        viewModel.selectCategory(summary)
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    isSmall = windowInfo.isSmallWidth
                                                )
                                            }

                                            repeat(itemsPerRow - rowSummaries.size) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(2.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))

                            if (state.selectedCategory != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Филтрирано по: ${state.selectedCategory?.categoryName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    TextButton(onClick = { viewModel.clearSelectedCategory() }) {
                                        Text("Prikaži sve")
                                    }
                                }
                            }
                        }

                        groupedTransactions.forEach { (dateString, transactionsInGroup) ->
                            // Prikazujemo zaglavlje sa datumom SAMO ako je sortiranje po datumu (kada dateString nije prazan)
                            if (dateString.isNotEmpty()) {
                                item(key = "header_$dateString") {
                                    Text(
                                        text = formatGroupHeaderDate(dateString),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp, start = 4.dp)
                                    )
                                }
                            }

                            items(
                                items = transactionsInGroup,
                                key = { transaction ->
                                    when (transaction) {
                                        is IncomeTransaction -> "inc_${transaction.income.id}"
                                        is ExpenseTransaction -> "exp_${transaction.expense.id}"
                                    }
                                }
                            ) { transaction ->
                                Box(modifier = Modifier.padding(vertical = 2.dp)) {
                                    TransactionItem(
                                        transaction = transaction,
                                        numberFormat = numberFormat,
                                        isSmall = windowInfo.isSmallWidth,
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
        }

        state.selectedCategory?.let { selectedCat ->
            val icon = CategoryIcons.findIconByName(selectedCat.iconName)
                ?: Icons.Default.MoreHoriz

            CategoryDetailsDialog(
                categoryName = selectedCat.categoryName,
                icon = icon,
                backgroundColor = selectedCat.color,
                transactions = state.categoryTransactions,
                totalAmount = selectedCat.amount,
                percentage = selectedCat.percentage,
                numberFormat = numberFormat,
                onDismiss = {
                    viewModel.clearSelectedCategory()
                }
            )
        }

        StandardPullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    if (state.error != null) {
        LaunchedEffect(state.error) {
            LogUtils.e("FinanceScreen", "Error: ${state.error}", category = "ui")
        }
    }
}

private fun formatGroupHeaderDate(dateStr: String): String {
    return try {
        val parts = dateStr.split("-")
        val date = if (parts.size == 3) {
            LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
        } else {
            LocalDate.parse(dateStr)
        }

        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        when (date) {
            today -> "Данас"
            yesterday -> "Јуче"
            else -> {
                val formatter = DateTimeFormatter.ofPattern(
                    "d. MMMM yyyy.",
                    Locale.Builder().setLanguage("sr").setScript("Cyrl").setRegion("RS").build()
                )
                date.format(formatter)
            }
        }
    } catch (_: Exception) {
        dateStr
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

    val periodText = when (currentPeriod) {
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
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigatePrevious,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Претходни период",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showPeriodTypeDialog = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = periodText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Изабери тип периода",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onNavigateNext,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Следећи период",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !isCurrentPeriod,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onResetToCurrentPeriod() },
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = when (currentPeriod) {
                        is TimePeriod.Day -> "Врати се на данашњи дан"
                        is TimePeriod.Week -> "Врати се на тренутну недељу"
                        is TimePeriod.Month -> "Врати се на тренутни месец"
                        is TimePeriod.Year -> "Врати се на тренутну годину"
                        is TimePeriod.Custom -> "Врати се на тренутни период"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }

    if (showPeriodTypeDialog) {
        AlertDialog(
            onDismissRequest = { showPeriodTypeDialog = false },
            title = { Text("Изаберите период", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PeriodType.entries.forEach { periodType ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onPeriodTypeSelected(periodType)
                                    showPeriodTypeDialog = false
                                },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = when (periodType) {
                                    PeriodType.YEAR -> "Година"
                                    PeriodType.MONTH -> "Месец"
                                    PeriodType.WEEK -> "Недеља"
                                    PeriodType.DAY -> "Дан"
                                    PeriodType.CUSTOM -> "Прилагођени период"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(14.dp)
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
    onTypeSelected: (TransactionType) -> Unit,
    horizontalPadding: Dp
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 6.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isExpenseSelected = selectedType is TransactionType.Expense
            TransactionTypeNavItem(
                label = "Расходи",
                icon = Icons.AutoMirrored.Filled.TrendingDown,
                isSelected = isExpenseSelected,
                onClick = { onTypeSelected(TransactionType.Expense) }
            )

            val isIncomeSelected = selectedType is TransactionType.Income
            TransactionTypeNavItem(
                label = "Приходи",
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                isSelected = isIncomeSelected,
                onClick = { onTypeSelected(TransactionType.Income) }
            )
        }
    }
}

@Composable
private fun RowScope.TransactionTypeNavItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 220),
        label = "TransactionNavItemBackground"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        },
        animationSpec = tween(durationMillis = 200),
        label = "TransactionNavItemContent"
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = backgroundColor
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )

                AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn(tween(180, delayMillis = 30)) + expandHorizontally(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        expandFrom = Alignment.Start
                    ),
                    exit = fadeOut(tween(100)) + shrinkHorizontally(
                        animationSpec = tween(140),
                        shrinkTowards = Alignment.Start
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = label,
                            color = contentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CombinedHeaderWithSort(
    categoriesExpanded: Boolean,
    onToggleCategories: () -> Unit,
    onSortSelected: (SortOption) -> Unit,
    isSmall: Boolean
) {
    var showSortDropdown by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clickable { onToggleCategories() }
                .weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Категорије трансакција",
                style = if (isSmall) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge
            )
            Icon(
                imageVector = if (categoriesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (categoriesExpanded) "Сакриј категорије" else "Прикажи категорије",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box {
            IconButton(onClick = { showSortDropdown = true }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = "Сортирај трансакције",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DropdownMenu(
                expanded = showSortDropdown,
                onDismissRequest = { showSortDropdown = false }
            ) {
                // Ako su kategorije otvorene, filtriramo opcije sa datumom
                val availableOptions = if (categoriesExpanded) {
                    SortOption.entries.filter {
                        it != SortOption.DATE_NEWEST && it != SortOption.DATE_OLDEST
                    }
                } else {
                    SortOption.entries
                }

                availableOptions.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                when (option) {
                                    SortOption.DATE_NEWEST -> "Датум (најновији)"
                                    SortOption.DATE_OLDEST -> "Датум (најстарији)"
                                    SortOption.AMOUNT_HIGHEST -> "Износ (највећи)"
                                    SortOption.AMOUNT_LOWEST -> "Износ (најмањи)"
                                    SortOption.CATEGORY_A_Z -> "Категорија (А-Ш)"
                                    SortOption.CATEGORY_Z_A -> "Категорија (Ш-А)"
                                }
                            )
                        },
                        onClick = {
                            onSortSelected(option)
                            showSortDropdown = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionItem(
    transaction: UITransaction,
    numberFormat: NumberFormat,
    isSmall: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val categoryManager = CategoryManager.getInstance(context)

    val categoryName = when (transaction) {
        is IncomeTransaction -> transaction.income.category
        is ExpenseTransaction -> transaction.expense.category
    }
    val descriptionText = when (transaction) {
        is IncomeTransaction -> transaction.income.description
        is ExpenseTransaction -> transaction.expense.description
    }
    val isExpense = transaction is ExpenseTransaction

    val savedColorValue = categoryManager.getCategoryColor(categoryName, isExpense)
    val categoryColor = if (savedColorValue != null) {
        Color(savedColorValue)
    } else {
        if (isExpense) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        }
    }

    val itemPadding = if (isSmall) 10.dp else 14.dp

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(itemPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = descriptionText,
                    style = if (isSmall) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.labelSmall,
                    color = categoryColor
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = numberFormat.format(transaction.amount),
                    style = if (isSmall) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = categoryColor
                )

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(if (isSmall) 32.dp else 40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Измени",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(if (isSmall) 18.dp else 22.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(if (isSmall) 32.dp else 40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Обриши",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(if (isSmall) 18.dp else 22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryCard(
    icon: ImageVector,
    backgroundColor: Color,
    categoryName: String,
    percentage: String,
    amount: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSmall: Boolean = false
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isSmall) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isSmall) 8.dp else 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (isSmall) 32.dp else 40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(backgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(if (isSmall) 18.dp else 22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = categoryName,
                        style = if (isSmall) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = percentage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = amount,
                        style = if (isSmall) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "$count траж.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}