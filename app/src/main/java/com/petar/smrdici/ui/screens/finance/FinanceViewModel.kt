package com.petar.smrdici.ui.screens.finance

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.petar.smrdici.data.model.Account
import com.petar.smrdici.data.model.Expense
import com.petar.smrdici.data.model.ExpenseCategory
import com.petar.smrdici.data.model.Income
import com.petar.smrdici.data.repository.AccountRepository
import com.petar.smrdici.data.repository.SettingsRepository
import com.petar.smrdici.data.repository.TransactionRepository
import com.petar.smrdici.ui.screens.settings.Period
import com.petar.smrdici.util.CurrencyConverter
import com.petar.smrdici.utils.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.threeten.bp.LocalDate
import org.threeten.bp.YearMonth
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.temporal.ChronoUnit
import java.util.Locale
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

    private var hasInitialLoad = false

    private val categoryColors = listOf(
        Color(0xFFFF5252), Color(0xFFFF9800), Color(0xFFFFEB3B),
        Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFF673AB7),
        Color(0xFFE91E63), Color(0xFF009688), Color(0xFF795548),
        Color(0xFF607D8B), Color(0xFFFFA000), Color(0xFF00BCD4),
        Color(0xFF3F51B5)
    )

    private val categoryIconMapping = mapOf(
        "GROCERIES" to "LocalGroceryStore", "UTILITIES" to "Receipt", "RENT" to "Home",
        "TRANSPORTATION" to "DirectionsCar", "ENTERTAINMENT" to "SportsEsports",
        "HEALTH" to "LocalHospital", "EDUCATION" to "School", "CLOTHING" to "Checkroom",
        "TRAVEL" to "Flight", "FOOD" to "Restaurant", "COFFEE" to "LocalCafe",
        "ALCOHOL" to "LocalBar", "CIGARETTES" to "SmokingRooms", "GIFTS" to "CardGiftcard",
        "SUBSCRIPTIONS" to "Subscriptions", "ELECTRONICS" to "Devices", "HOME" to "Home",
        "BEAUTY" to "Face", "PETS" to "Pets", "SPORTS" to "FitnessCenter",
        "INVESTMENTS" to "TrendingUp", "DEBT" to "CreditCard", "INSURANCE" to "Security",
        "TAXES" to "Receipt", "CHARITY" to "Favorite", "BUSINESS" to "BusinessCenter",
        "CHILDREN" to "ChildCare", "PERSONAL_CARE" to "Face", "SHOPPING" to "ShoppingCart",
        "MAINTENANCE" to "Handyman", "SERVICES" to "Receipt", "SAVINGS" to "Savings",
        "LOAN" to "CreditCard", "RAMPA" to "DirectionsCar", "PARKING" to "DirectionsCar",
        "OTHER" to "Receipt"
    )

    init {
        initializeState()
        refreshData(silent = false)
        loadAccounts()
    }

    fun refreshOnResume() {
        LogUtils.i("FinanceViewModel", "Refreshing data on resume", category = "finance")

        _state.value = _state.value.copy(
            selectedCategory = null,
            categoryTransactions = emptyList()
        )

        if (hasInitialLoad) {
            refreshData(silent = true)
        } else {
            loadAccounts()
            refreshData(silent = false)
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

                    val startDate = if (now.dayOfMonth >= startDay) {
                        currentMonth.atDay(startDay)
                    } else {
                        currentMonth.minusMonths(1).atDay(startDay)
                    }

                    val endDate = startDate.plusMonths(1).minusDays(1)
                    TimePeriod.Custom(startDate, endDate)
                }
                Period.ALL -> TimePeriod.Month(YearMonth.from(now))
            }
            _state.value = _state.value.copy(selectedTimePeriod = currentPeriod)
        }
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            try {
                accountRepository.accounts.collect { accounts ->
                    val defaultAccount = accounts.find { it.isDefault }

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
        refreshData(silent = false)
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
        refreshData(silent = false)
    }

    fun setTimePeriod(period: TimePeriod) {
        LogUtils.i("FinanceViewModel", "Setting time period to: $period", category = "finance")
        _state.value = _state.value.copy(selectedTimePeriod = period)
        refreshData(silent = false)
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
                        val today = LocalDate.now()
                        val startDay = settingsRepository.customPeriodStartDay.value
                        val currentMonth = YearMonth.from(today)

                        val startDate = if (today.dayOfMonth >= startDay) {
                            currentMonth.atDay(startDay)
                        } else {
                            currentMonth.minusMonths(1).atDay(startDay)
                        }

                        val endDate = startDate.plusMonths(1).minusDays(1)
                        TimePeriod.Custom(startDate, endDate)
                    }
                }
            )
        }
        refreshData(silent = false)
    }

    fun setSelectedAccount(accountId: String?) {
        LogUtils.i("FinanceViewModel", "Setting selected account: ${accountId ?: "All accounts"}", category = "finance")
        _state.value = _state.value.copy(selectedAccountId = accountId)
        refreshData(silent = false)
    }

    fun setTransactionType(type: TransactionType) {
        LogUtils.i("FinanceViewModel", "Setting transaction type to: $type", category = "finance")
        _state.value = _state.value.copy(selectedTransactionType = type)
        refreshData(silent = false)
    }

    fun setSortOption(sortOption: SortOption) {
        LogUtils.i("FinanceViewModel", "Setting sort option to: $sortOption", category = "finance")
        val currentState = _state.value
        val sortedTransactions = sortTransactions(currentState.transactions, sortOption)
        val updatedSummaries = calculateCategorySummaries(sortedTransactions, sortOption)

        _state.value = currentState.copy(
            sortOption = sortOption,
            transactions = sortedTransactions,
            categorySummaries = updatedSummaries
        )
    }

    fun selectCategory(category: CategorySummary) {
        LogUtils.i("FinanceViewModel", "Selected category: ${category.categoryName}", category = "finance")

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

    private fun refreshData(silent: Boolean = false) {
        viewModelScope.launch {
            try {
                if (!silent) {
                    _state.value = _state.value.copy(isLoading = true, error = null)
                }

                val startDate = _state.value.selectedTimePeriod.calculateStartDate()
                val endDate = _state.value.selectedTimePeriod.calculateEndDate()

                val defaultAccount = _state.value.accounts.find { it.isDefault } ?: _state.value.accounts.firstOrNull()
                val targetAccountId = _state.value.selectedAccountId ?: defaultAccount?.id

                transactionRepository.getAllTransactions().collect { allTransactions ->
                    val filteredTransactions = withContext(Dispatchers.Default) {
                        allTransactions.filter { transaction ->
                            val matchesType = when (_state.value.selectedTransactionType) {
                                is TransactionType.Income -> transaction is Income
                                is TransactionType.Expense -> transaction is Expense
                            }
                            if (!matchesType) return@filter false

                            if (targetAccountId != null && transaction.accountId != targetAccountId) {
                                return@filter false
                            }

                            val transactionDate = try {
                                val parts = transaction.date.split("-")
                                if (parts.size == 3) {
                                    LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
                                } else {
                                    LocalDate.parse(transaction.date)
                                }
                            } catch (_: Exception) {
                                null
                            } ?: return@filter false

                            (transactionDate.isEqual(startDate) || transactionDate.isAfter(startDate)) &&
                                    (transactionDate.isEqual(endDate) || transactionDate.isBefore(endDate))
                        }
                    }

                    val uiTransactions = withContext(Dispatchers.Default) {
                        filteredTransactions.map { transaction ->
                            when (transaction) {
                                is Income -> IncomeTransaction(transaction)
                                is Expense -> ExpenseTransaction(transaction)
                            }
                        }
                    }

                    hasInitialLoad = true
                    updateTransactionsState(uiTransactions)
                }
            } catch (e: Exception) {
                LogUtils.e("FinanceViewModel", "Error refreshing data", e, category = "finance")
                _state.value = _state.value.copy(error = e.message, isLoading = false)
            }
        }
    }

    private fun updateTransactionsState(transactions: List<UITransaction>) {
        viewModelScope.launch {
            try {
                val currentAccountBalances = accountRepository.getAccountBalances()

                val (accountBalances, totalEurAmount, sortedTransactions, categorySummaries) = withContext(Dispatchers.Default) {
                    val accountBalancesMap = mutableMapOf<String, AccountBalance>()
                    var eurSum = 0.0

                    _state.value.accounts.forEach { account ->
                        val accountTransactions = transactions.filter {
                            when (it) {
                                is IncomeTransaction -> it.income.accountId == account.id
                                is ExpenseTransaction -> it.expense.accountId == account.id
                            }
                        }

                        val transactionTotal = accountTransactions.sumOf { transaction ->
                            when (_state.value.selectedTransactionType) {
                                is TransactionType.Income -> transaction.amount
                                is TransactionType.Expense -> -transaction.amount
                            }
                        }

                        val currentBalance = currentAccountBalances[account.id] ?: account.balance
                        val eurAmount = CurrencyConverter.convert(transactionTotal, account.currency, "EUR")
                        eurSum += eurAmount

                        accountBalancesMap[account.id] = AccountBalance(
                            nativeAmount = transactionTotal,
                            nativeCurrency = account.currency,
                            eurAmount = eurAmount,
                            transactionTotal = transactionTotal,
                            currentBalance = currentBalance
                        )
                    }

                    val sorted = sortTransactions(transactions, _state.value.sortOption)
                    val summaries = calculateCategorySummaries(sorted, _state.value.sortOption)

                    Tuple4(accountBalancesMap, eurSum, sorted, summaries)
                }

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
                _state.value = _state.value.copy(error = e.message, isLoading = false)
            }
        }
    }

    private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

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
                    is ExpenseTransaction -> transaction.expense.category
                    is IncomeTransaction -> transaction.income.category
                }
            }
            SortOption.CATEGORY_Z_A -> transactions.sortedByDescending { transaction ->
                when (transaction) {
                    is ExpenseTransaction -> transaction.expense.category
                    is IncomeTransaction -> transaction.income.category
                }
            }
        }
    }

    private fun calculateCategorySummaries(
        transactions: List<UITransaction>,
        sortOption: SortOption = _state.value.sortOption
    ): List<CategorySummary> {
        if (transactions.isEmpty()) return emptyList()

        val categoryGroups = transactions.groupBy { transaction ->
            when (transaction) {
                is ExpenseTransaction -> transaction.expense.category
                is IncomeTransaction -> transaction.income.category
            }
        }

        val totalAmount = transactions.sumOf { it.amount }

        val summaries = categoryGroups.map { (category, categoryTransactions) ->
            val categoryAmount = categoryTransactions.sumOf { it.amount }
            val percentage = if (totalAmount > 0) (categoryAmount / totalAmount) * 100 else 0.0

            val colorIndex = abs(category.hashCode()) % categoryColors.size
            val color = categoryColors[colorIndex]

            val iconName = categoryIconMapping[category] ?: "Receipt"

            val displayName = try {
                if (_state.value.selectedTransactionType is TransactionType.Expense) {
                    ExpenseCategory.valueOf(category).getDisplayName()
                } else {
                    category
                }
            } catch (_: Exception) {
                category
            }

            CategorySummary(
                categoryName = displayName,
                iconName = iconName,
                color = color,
                amount = categoryAmount,
                percentage = percentage
            )
        }

        return when (sortOption) {
            SortOption.AMOUNT_HIGHEST -> summaries.sortedByDescending { it.amount }
            SortOption.AMOUNT_LOWEST -> summaries.sortedBy { it.amount }
            SortOption.CATEGORY_A_Z -> summaries.sortedBy { it.categoryName }
            SortOption.CATEGORY_Z_A -> summaries.sortedByDescending { it.categoryName }
            SortOption.DATE_NEWEST, SortOption.DATE_OLDEST -> summaries.sortedByDescending { it.amount }
        }
    }

    fun deleteIncome(incomeId: String) {
        viewModelScope.launch {
            try {
                transactionRepository.deleteIncome(incomeId)
                refreshData(silent = false)
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
                refreshData(silent = false)
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
                val startDay = settingsRepository.customPeriodStartDay.value
                val today = LocalDate.now()
                val currentMonth = YearMonth.from(today)

                val startDate = if (today.dayOfMonth >= startDay) {
                    currentMonth.atDay(startDay)
                } else {
                    currentMonth.minusMonths(1).atDay(startDay)
                }

                val endDate = startDate.plusMonths(1).minusDays(1)
                TimePeriod.Custom(startDate, endDate)
            }
        }
        setTimePeriod(currentPeriod)
    }

    fun syncWithRemote() {
        try {
            accountRepository.loadAccounts()
            refreshData(silent = false)
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