package com.petar.smrdici.ui.screens.finance

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.petar.smrdici.data.model.Account
import com.petar.smrdici.data.model.CategoryIcons
import com.petar.smrdici.data.model.Expense
import com.petar.smrdici.data.model.ExpenseCategory
import com.petar.smrdici.data.model.Income
import com.petar.smrdici.data.model.Transaction
import com.petar.smrdici.data.repository.ExpenseRepository
import com.petar.smrdici.data.repository.IncomeRepository
import com.petar.smrdici.data.repository.AccountRepository
import com.petar.smrdici.data.repository.SettingsRepository
import com.petar.smrdici.data.repository.TransactionRepository
import com.petar.smrdici.ui.screens.settings.Period
import com.petar.smrdici.util.CurrencyConverter
import com.petar.smrdici.utils.LogUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import org.threeten.bp.LocalDate
import org.threeten.bp.YearMonth
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.temporal.ChronoUnit
import java.util.*
import kotlin.random.Random
import kotlin.math.abs

enum class SortOption {
    DATE_NEWEST,
    DATE_OLDEST,
    AMOUNT_HIGHEST,
    AMOUNT_LOWEST,
    CATEGORY_A_Z,
    CATEGORY_Z_A
}

enum class PeriodType {
    YEAR, MONTH, WEEK, DAY, CUSTOM
}

sealed class TimePeriod {
    data class Day(val date: LocalDate) : TimePeriod()
    data class Week(val startDate: LocalDate) : TimePeriod()
    data class Month(val yearMonth: YearMonth) : TimePeriod()
    data class Year(val year: Int) : TimePeriod()
    data class Custom(val startDate: LocalDate, val endDate: LocalDate) : TimePeriod()

    fun calculateStartDate(): LocalDate = when (this) {
        is Day -> date
        is Week -> startDate
        is Month -> yearMonth.atDay(1)
        is Year -> LocalDate.of(year, 1, 1)
        is Custom -> startDate
    }

    fun calculateEndDate(): LocalDate = when (this) {
        is Day -> date
        is Week -> startDate.plus(6, ChronoUnit.DAYS)
        is Month -> yearMonth.atEndOfMonth()
        is Year -> LocalDate.of(year, 12, 31)
        is Custom -> endDate
    }

    fun isCurrentPeriod(): Boolean {
        val now = LocalDate.now()
        return when (this) {
            is Day -> date == now
            is Week -> {
                val currentWeekStart = now.minusDays(now.dayOfWeek.value.toLong() - 1)
                val thisWeekStart = startDate.minusDays(startDate.dayOfWeek.value.toLong() - 1)
                currentWeekStart == thisWeekStart
            }
            is Month -> yearMonth == YearMonth.from(now)
            is Year -> year == now.year
            is Custom -> {
                // For custom periods, we need to check if today falls within the current period
                (now.isEqual(startDate) || now.isAfter(startDate)) &&
                (now.isEqual(endDate) || now.isBefore(endDate))
            }
        }
    }

    override fun toString(): String {
        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.Builder().setLanguage("sr").setRegion("RS").build())
        
        return when (this) {
            is Day -> date.format(dateFormatter)
            is Week -> "${startDate.format(dateFormatter)} - ${startDate.plus(6, ChronoUnit.DAYS).format(dateFormatter)}"
            is Month -> yearMonth.format(monthFormatter)
            is Year -> "Година $year"
            is Custom -> "${startDate.format(dateFormatter)} - ${endDate.format(dateFormatter)}"
        }
    }
}

sealed class TransactionType {
    object Income : TransactionType()
    object Expense : TransactionType()

    override fun toString(): String = when (this) {
        is Income -> "Приходи"
        is Expense -> "Расходи"
    }
}

sealed class UITransaction {
    abstract val amount: Double
    abstract val date: String
}

data class IncomeTransaction(val income: Income) : UITransaction() {
    override val amount: Double = income.amount
    override val date: String = income.date
}

data class ExpenseTransaction(val expense: Expense) : UITransaction() {
    override val amount: Double = expense.amount
    override val date: String = expense.date
}

data class FinanceScreenState(
    val selectedTimePeriod: TimePeriod = TimePeriod.Month(YearMonth.now()),
    val selectedAccountId: String? = null,
    val selectedTransactionType: TransactionType = TransactionType.Expense,
    val sortOption: SortOption = SortOption.DATE_NEWEST,
    val transactions: List<UITransaction> = emptyList(),
    val totalAmount: Double = 0.0,
    val totalAmountInEur: Double = 0.0,
    val accountBalances: Map<String, AccountBalance> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val accounts: List<Account> = emptyList(),
    val categorySummaries: List<CategorySummary> = emptyList(),
    val selectedCategory: CategorySummary? = null,
    val categoryTransactions: List<UITransaction> = emptyList()
)

data class AccountBalance(
    val nativeAmount: Double,
    val nativeCurrency: String,
    val eurAmount: Double,
    val transactionTotal: Double,
    val currentBalance: Double
)

data class CategorySummary(
    val categoryName: String,
    val iconName: String,
    val color: Color,
    val amount: Double,
    val percentage: Double
)

class FinanceViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {
    private val _state = MutableStateFlow(FinanceScreenState())
    val state: StateFlow<FinanceScreenState> = _state.asStateFlow()

    private val transactionRepository: TransactionRepository by lazy { TransactionRepository.getInstance() }
    private val accountRepository: AccountRepository by lazy { AccountRepository.getInstance() }

    // Predefined colors for category cards
    private val categoryColors = listOf(
        Color(0xFFFF5252),  // Red
        Color(0xFFFF9800),  // Orange
        Color(0xFFFFEB3B),  // Yellow
        Color(0xFF4CAF50),  // Green
        Color(0xFF2196F3),  // Blue
        Color(0xFF673AB7),  // Purple
        Color(0xFFE91E63),  // Pink
        Color(0xFF009688),  // Teal
        Color(0xFF795548),  // Brown
        Color(0xFF607D8B),  // Blue Grey
        Color(0xFFFFA000),  // Amber
        Color(0xFF00BCD4),  // Cyan
        Color(0xFF3F51B5)   // Indigo
    )

    // Category icon mapping
    private val categoryIconMapping = mapOf(
        "GROCERIES" to "LocalGroceryStore",
        "UTILITIES" to "Receipt",
        "RENT" to "Home",
        "TRANSPORTATION" to "DirectionsCar",
        "ENTERTAINMENT" to "SportsEsports",
        "HEALTH" to "LocalHospital",
        "EDUCATION" to "School",
        "CLOTHING" to "Checkroom",
        "TRAVEL" to "Flight",
        "FOOD" to "Restaurant",
        "COFFEE" to "LocalCafe",
        "ALCOHOL" to "LocalBar",
        "CIGARETTES" to "SmokingRooms",
        "GIFTS" to "CardGiftcard",
        "SUBSCRIPTIONS" to "Subscriptions",
        "ELECTRONICS" to "Devices",
        "HOME" to "Home",
        "BEAUTY" to "Face",
        "PETS" to "Pets",
        "SPORTS" to "FitnessCenter",
        "INVESTMENTS" to "TrendingUp",
        "DEBT" to "CreditCard",
        "INSURANCE" to "Security",
        "TAXES" to "Receipt",
        "CHARITY" to "Favorite",
        "BUSINESS" to "BusinessCenter",
        "CHILDREN" to "ChildCare",
        "PERSONAL_CARE" to "Face",
        "SHOPPING" to "ShoppingCart",
        "MAINTENANCE" to "Handyman",
        "SERVICES" to "Receipt",
        "SAVINGS" to "Savings",
        "LOAN" to "CreditCard",
        "RAMPA" to "DirectionsCar",
        "PARKING" to "DirectionsCar",
        "OTHER" to "Receipt"
    )

    init {
        initializeState()
        refreshData()
        loadAccounts()
    }

    // Add this method to force refresh data when returning to the screen
    fun refreshOnResume() {
        LogUtils.i("FinanceViewModel", "Refreshing data on resume", category = "finance")
        
        // Clear any selected category
        _state.value = _state.value.copy(
            selectedCategory = null,
            categoryTransactions = emptyList()
        )
        
        // Reload accounts and transactions
        viewModelScope.launch {
            try {
                // Force reload from remote
                syncWithRemote()
            } catch (e: Exception) {
                LogUtils.e("FinanceViewModel", "Error during refresh on resume", e, category = "finance")
                // If remote sync fails, still try to refresh local data
                loadAccounts()
                refreshData()
            }
        }
    }

    private fun initializeState() {
        viewModelScope.launch {
            val configuredPeriod = settingsRepository.period.value
            val now = LocalDate.now()
            val currentPeriod = when (configuredPeriod) {
                Period.DAILY -> TimePeriod.Day(now)
                Period.WEEKLY -> TimePeriod.Week(now.minusDays(now.dayOfWeek.value.toLong() - 1))
                Period.MONTHLY -> TimePeriod.Month(YearMonth.from(now))
                Period.YEARLY -> TimePeriod.Year(now.year)
                Period.CUSTOM -> {
                    val startDay = settingsRepository.customPeriodStartDay.value
                    val currentMonth = YearMonth.from(now)
                    
                    // Calculate the current period's start date
                    val startDate = if (now.dayOfMonth >= startDay) {
                        currentMonth.atDay(startDay)
                    } else {
                        currentMonth.minusMonths(1).atDay(startDay)
                    }
                    
                    // End date is the day before start day in the next month
                    val endDate = startDate.plusMonths(1).minusDays(1)
                    
                    TimePeriod.Custom(startDate, endDate)
                }
                Period.ALL -> TimePeriod.Month(YearMonth.from(now)) // Default to current month for "ALL"
            }
            _state.value = _state.value.copy(selectedTimePeriod = currentPeriod)
        }
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            try {
                accountRepository.accounts.collect { accounts ->
                    // Find the default account
                    val defaultAccount = accounts.find { it.isDefault }
                    
                    // Update state with accounts and set the default account as selected if no account is currently selected
                    _state.value = _state.value.copy(
                        accounts = accounts,
                        selectedAccountId = if (_state.value.selectedAccountId == null) defaultAccount?.id else _state.value.selectedAccountId
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    // Time period navigation
    fun navigateToNextPeriod() {
        LogUtils.d("FinanceViewModel", "Navigating to next period from ${_state.value.selectedTimePeriod}", category = "finance")
        _state.value = _state.value.let { currentState ->
            currentState.copy(
                selectedTimePeriod = when (val period = currentState.selectedTimePeriod) {
                    is TimePeriod.Day -> TimePeriod.Day(period.date.plus(1, ChronoUnit.DAYS))
                    is TimePeriod.Week -> TimePeriod.Week(period.startDate.plus(7, ChronoUnit.DAYS))
                    is TimePeriod.Month -> TimePeriod.Month(period.yearMonth.plusMonths(1))
                    is TimePeriod.Year -> TimePeriod.Year(period.year + 1)
                    is TimePeriod.Custom -> {
                        val nextStartDate = period.startDate.plusMonths(1)
                        val nextEndDate = period.endDate.plusMonths(1)
                        TimePeriod.Custom(nextStartDate, nextEndDate)
                    }
                }
            )
        }
        refreshData()
    }

    fun navigateToPreviousPeriod() {
        LogUtils.d("FinanceViewModel", "Navigating to previous period from ${_state.value.selectedTimePeriod}", category = "finance")
        _state.value = _state.value.let { currentState ->
            currentState.copy(
                selectedTimePeriod = when (val period = currentState.selectedTimePeriod) {
                    is TimePeriod.Day -> TimePeriod.Day(period.date.minus(1, ChronoUnit.DAYS))
                    is TimePeriod.Week -> TimePeriod.Week(period.startDate.minus(7, ChronoUnit.DAYS))
                    is TimePeriod.Month -> TimePeriod.Month(period.yearMonth.minusMonths(1))
                    is TimePeriod.Year -> TimePeriod.Year(period.year - 1)
                    is TimePeriod.Custom -> {
                        val previousStartDate = period.startDate.minusMonths(1)
                        val previousEndDate = period.endDate.minusMonths(1)
                        TimePeriod.Custom(previousStartDate, previousEndDate)
                    }
                }
            )
        }
        refreshData()
    }

    fun setTimePeriod(period: TimePeriod) {
        LogUtils.i("FinanceViewModel", "Setting time period to: $period", category = "finance")
        _state.value = _state.value.copy(selectedTimePeriod = period)
        refreshData()
    }

    fun resetToCurrentPeriod() {
        _state.value = _state.value.let { currentState ->
            currentState.copy(
                selectedTimePeriod = when (currentState.selectedTimePeriod) {
                    is TimePeriod.Day -> TimePeriod.Day(LocalDate.now())
                    is TimePeriod.Week -> TimePeriod.Week(LocalDate.now())
                    is TimePeriod.Month -> TimePeriod.Month(YearMonth.now())
                    is TimePeriod.Year -> TimePeriod.Year(LocalDate.now().year)
                    is TimePeriod.Custom -> {
                        // For custom period, create a new period starting from the configured start day
                        val today = LocalDate.now()
                        val startDay = settingsRepository.customPeriodStartDay.value
                        val currentMonth = YearMonth.from(today)
                        
                        // Calculate the current period's start date
                        val startDate = if (today.dayOfMonth >= startDay) {
                            currentMonth.atDay(startDay)
                        } else {
                            currentMonth.minusMonths(1).atDay(startDay)
                        }
                        
                        // End date is the day before start day in the next month
                        val endDate = startDate.plusMonths(1).minusDays(1)
                        
                        TimePeriod.Custom(startDate, endDate)
                    }
                }
            )
        }
        refreshData()
    }

    // Account selection
    fun setSelectedAccount(accountId: String?) {
        LogUtils.i("FinanceViewModel", 
            "Setting selected account: ${accountId ?: "All accounts"}", 
            category = "finance")
        _state.value = _state.value.copy(selectedAccountId = accountId)
        refreshData()
    }

    // Transaction type
    fun setTransactionType(type: TransactionType) {
        LogUtils.i("FinanceViewModel", "Setting transaction type to: $type", category = "finance")
        _state.value = _state.value.copy(selectedTransactionType = type)
        refreshData()
    }

    fun setSortOption(sortOption: SortOption) {
        LogUtils.i("FinanceViewModel", "Setting sort option to: $sortOption", category = "finance")
        _state.value = _state.value.copy(sortOption = sortOption)
        // Re-sort existing transactions without refreshing data
        val sortedTransactions = sortTransactions(_state.value.transactions, sortOption)
        _state.value = _state.value.copy(transactions = sortedTransactions)
    }

    // Category selection
    fun selectCategory(category: CategorySummary) {
        LogUtils.i("FinanceViewModel", "Selected category: ${category.categoryName}", category = "finance")
        
        // Filter transactions for this category
        val categoryTransactions = _state.value.transactions.filter { transaction ->
            when (transaction) {
                is ExpenseTransaction -> transaction.expense.category == category.categoryName
                is IncomeTransaction -> transaction.income.category == category.categoryName
            }
        }
        
        _state.value = _state.value.copy(
            selectedCategory = category,
            categoryTransactions = categoryTransactions
        )
    }
    
    fun clearSelectedCategory() {
        _state.value = _state.value.copy(
            selectedCategory = null,
            categoryTransactions = emptyList()
        )
    }

    // Data refresh
    private fun refreshData() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, error = null)
                
                val startDate = _state.value.selectedTimePeriod.calculateStartDate()
                val endDate = _state.value.selectedTimePeriod.calculateEndDate()
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                
                LogUtils.i("FinanceViewModel", 
                    "Refreshing data with filters:" +
                    "\nPeriod: ${_state.value.selectedTimePeriod}" +
                    "\nDate range: $startDate to $endDate" +
                    "\nTransaction type: ${_state.value.selectedTransactionType}" +
                    "\nSelected account: ${_state.value.selectedAccountId ?: "All accounts"}",
                    category = "finance")
                
                // Get all transactions and filter them based on criteria
                transactionRepository.getAllTransactions().collect { allTransactions ->
                    LogUtils.d("FinanceViewModel", 
                        "Received ${allTransactions.size} transactions before filtering", 
                        category = "finance")
                    
                    // Filter transactions based on type, date range, and account
                    val filteredTransactions = allTransactions.filter { transaction ->
                        // First filter by transaction type
                        val matchesType = when (_state.value.selectedTransactionType) {
                            is TransactionType.Income -> transaction is Income
                            is TransactionType.Expense -> transaction is Expense
                        }
                        
                        if (!matchesType) return@filter false
                        
                        // Then filter by date range
                        val transactionDate = LocalDate.parse(transaction.date, formatter)
                        val inDateRange = (transactionDate.isEqual(startDate) || transactionDate.isAfter(startDate)) &&
                                        (transactionDate.isEqual(endDate) || transactionDate.isBefore(endDate))
                        
                        if (!inDateRange) return@filter false
                        
                        // Finally filter by account if one is selected
                        _state.value.selectedAccountId == null || transaction.accountId == _state.value.selectedAccountId
                    }
                    
                    // Convert to UI transaction model
                    val uiTransactions = filteredTransactions.map { transaction ->
                        when (transaction) {
                            is Income -> IncomeTransaction(transaction)
                            is Expense -> ExpenseTransaction(transaction)
                        }
                    }
                    
                    LogUtils.i("FinanceViewModel", 
                        "Filtered to ${uiTransactions.size} transactions in selected period", 
                        category = "finance")
                    
                    updateTransactionsState(uiTransactions)
                }
            } catch (e: Exception) {
                LogUtils.e("FinanceViewModel", "Error refreshing data", e, category = "finance")
                _state.value = _state.value.copy(
                    error = e.message,
                    isLoading = false
                )
            }
        }
    }

    private fun updateTransactionsState(transactions: List<UITransaction>) {
        viewModelScope.launch {
            try {
                // Get current account balances from repository
                val currentAccountBalances = accountRepository.getAccountBalances()
                LogUtils.d("FinanceViewModel", "Retrieved ${currentAccountBalances.size} account balances", category = "finance")

                // Calculate balances for each account based on filtered transactions
                val accountBalances = mutableMapOf<String, AccountBalance>()
                var totalEurAmount = 0.0

                _state.value.accounts.forEach { account ->
                    val accountTransactions = transactions.filter { 
                        when (it) {
                            is IncomeTransaction -> it.income.accountId == account.id
                            is ExpenseTransaction -> it.expense.accountId == account.id
                        }
                    }

                    // Calculate transaction total for the filtered period
                    val transactionTotal = accountTransactions.sumOf { transaction ->
                        when (_state.value.selectedTransactionType) {
                            is TransactionType.Income -> transaction.amount
                            is TransactionType.Expense -> -transaction.amount
                        }
                    }

                    // Get current balance or default to 0.0
                    val currentBalance = currentAccountBalances[account.id] ?: account.balance
                    
                    // For display purposes in the current view
                    val nativeAmount = transactionTotal

                    // Convert to EUR for total calculation
                    val eurAmount = CurrencyConverter.convert(nativeAmount, account.currency, "EUR")
                    totalEurAmount += eurAmount

                    LogUtils.d("FinanceViewModel", 
                        "Account ${account.name} (${account.currency}): " +
                        "Transaction total: $transactionTotal, " +
                        "Current balance: $currentBalance, " +
                        "EUR amount: $eurAmount", 
                        category = "finance")

                    accountBalances[account.id] = AccountBalance(
                        nativeAmount = nativeAmount,
                        nativeCurrency = account.currency,
                        eurAmount = eurAmount,
                        transactionTotal = transactionTotal,
                        currentBalance = currentBalance
                    )
                }
                
                // Apply sorting to transactions
                val sortedTransactions = sortTransactions(transactions, _state.value.sortOption)
                
                // Calculate category summaries
                val categorySummaries = calculateCategorySummaries(sortedTransactions)

                LogUtils.i("FinanceViewModel", 
                    "Updated state:" +
                    "\nTotal transactions: ${sortedTransactions.size}" +
                    "\nTotal amount in EUR: $totalEurAmount" +
                    "\nAccounts with transactions: ${accountBalances.size}" +
                    "\nCategory summaries: ${categorySummaries.size}" +
                    "\nSort option: ${_state.value.sortOption}", 
                    category = "finance")

                _state.value = _state.value.copy(
                    transactions = sortedTransactions,
                    totalAmount = transactions.sumOf { 
                        when (_state.value.selectedTransactionType) {
                            is TransactionType.Income -> it.amount
                            is TransactionType.Expense -> -it.amount
                        }
                    },
                    totalAmountInEur = totalEurAmount,
                    accountBalances = accountBalances,
                    categorySummaries = categorySummaries,
                    isLoading = false
                )
            } catch (e: Exception) {
                LogUtils.e("FinanceViewModel", "Error updating transaction state", e, category = "finance")
                _state.value = _state.value.copy(
                    error = e.message,
                    isLoading = false
                )
            }
        }
    }
    
    private fun sortTransactions(transactions: List<UITransaction>, sortOption: SortOption): List<UITransaction> {
        return when (sortOption) {
            SortOption.DATE_NEWEST -> transactions.sortedByDescending { 
                LocalDate.parse(it.date, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            }
            SortOption.DATE_OLDEST -> transactions.sortedBy { 
                LocalDate.parse(it.date, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            }
            SortOption.AMOUNT_HIGHEST -> transactions.sortedByDescending { it.amount }
            SortOption.AMOUNT_LOWEST -> transactions.sortedBy { it.amount }
            SortOption.CATEGORY_A_Z -> transactions.sortedBy { transaction ->
                when (transaction) {
                    is ExpenseTransaction -> transaction.expense.category ?: ""
                    is IncomeTransaction -> transaction.income.category ?: ""
                }
            }
            SortOption.CATEGORY_Z_A -> transactions.sortedByDescending { transaction ->
                when (transaction) {
                    is ExpenseTransaction -> transaction.expense.category ?: ""
                    is IncomeTransaction -> transaction.income.category ?: ""
                }
            }
        }
    }

    private fun calculateCategorySummaries(transactions: List<UITransaction>): List<CategorySummary> {
        if (transactions.isEmpty()) return emptyList()
        
        // Group transactions by category
        val categoryGroups = transactions.groupBy { transaction ->
            when (transaction) {
                is ExpenseTransaction -> transaction.expense.category
                is IncomeTransaction -> transaction.income.category
            }
        }
        
        // Calculate total amount for all transactions
        val totalAmount = transactions.sumOf { it.amount }
        
        // Create category summaries
        val summaries = categoryGroups.map { (category, categoryTransactions) ->
            val categoryAmount = categoryTransactions.sumOf { it.amount }
            val percentage = if (totalAmount > 0) (categoryAmount / totalAmount) * 100 else 0.0
            
            // Get a consistent color for this category
            val colorIndex = abs(category.hashCode()) % categoryColors.size
            val color = categoryColors[colorIndex]
            
            // Get icon name for this category
            val iconName = categoryIconMapping[category] ?: "Receipt"
            
            // Get display name for the category
            val displayName = try {
                if (_state.value.selectedTransactionType is TransactionType.Expense) {
                    ExpenseCategory.valueOf(category).getDisplayName()
                } else {
                    category // For income categories
                }
            } catch (e: Exception) {
                category // Fallback to raw category name
            }
            
            CategorySummary(
                categoryName = displayName,
                iconName = iconName,
                color = color,
                amount = categoryAmount,
                percentage = percentage
            )
        }.sortedByDescending { it.amount }
        
        return summaries
    }

    // Delete functions
    fun deleteIncome(incomeId: String) {
        viewModelScope.launch {
            try {
                transactionRepository.deleteIncome(incomeId)
                refreshData()
                // Force refresh account data to update balances
                accountRepository.loadAccounts()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            try {
                transactionRepository.deleteExpense(expenseId)
                refreshData()
                // Force refresh account data to update balances
                accountRepository.loadAccounts()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun setTimePeriodType(periodType: PeriodType) {
        val now = LocalDate.now()
        val currentPeriod = when (periodType) {
            PeriodType.YEAR -> TimePeriod.Year(now.year)
            PeriodType.MONTH -> TimePeriod.Month(YearMonth.from(now))
            PeriodType.WEEK -> TimePeriod.Week(now.minusDays(now.dayOfWeek.value.toLong() - 1))
            PeriodType.DAY -> TimePeriod.Day(now)
            PeriodType.CUSTOM -> {
                // Get the configured start day from settings
                val startDay = settingsRepository.customPeriodStartDay.value
                val today = LocalDate.now()
                val currentMonth = YearMonth.from(today)
                
                // Calculate the current period's start date
                val startDate = if (today.dayOfMonth >= startDay) {
                    currentMonth.atDay(startDay)
                } else {
                    currentMonth.minusMonths(1).atDay(startDay)
                }
                
                // End date is the day before start day in the next month
                val endDate = startDate.plusMonths(1).minusDays(1)
                
                TimePeriod.Custom(startDate, endDate)
            }
        }
        setTimePeriod(currentPeriod)
    }

    // Forces a remote sync with Firestore for accounts and transactions
    suspend fun syncWithRemote() {
        try {
            // Force reload accounts from Firestore
            accountRepository.loadAccounts()
            // If TransactionRepository has a similar method, call it here (e.g., transactionRepository.loadTransactions())
            // For now, just call refreshData() to update state after remote fetch
            refreshData()
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = e.message)
        }
    }

    class Factory(private val settingsRepository: SettingsRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FinanceViewModel::class.java)) {
                return FinanceViewModel(settingsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
} 