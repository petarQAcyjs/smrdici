package com.petar.smrdici.ui.screens.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.petar.smrdici.data.model.Account
import com.petar.smrdici.data.model.Expense
import com.petar.smrdici.data.model.Income
import com.petar.smrdici.data.repository.ExpenseRepository
import com.petar.smrdici.data.repository.IncomeRepository
import com.petar.smrdici.data.repository.AccountRepository
import com.petar.smrdici.data.repository.SettingsRepository
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

sealed class Transaction {
    abstract val amount: Double
    abstract val date: String
}

data class IncomeTransaction(val income: Income) : Transaction() {
    override val amount: Double = income.amount
    override val date: String = income.date
}

data class ExpenseTransaction(val expense: Expense) : Transaction() {
    override val amount: Double = expense.amount
    override val date: String = expense.date
}

data class FinanceScreenState(
    val selectedTimePeriod: TimePeriod = TimePeriod.Month(YearMonth.now()),
    val selectedAccountId: String? = null,
    val selectedTransactionType: TransactionType = TransactionType.Expense,
    val transactions: List<Transaction> = emptyList(),
    val totalAmount: Double = 0.0,
    val totalAmountInEur: Double = 0.0,
    val accountBalances: Map<String, AccountBalance> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val accounts: List<Account> = emptyList()
)

data class AccountBalance(
    val nativeAmount: Double,
    val nativeCurrency: String,
    val eurAmount: Double
)

class FinanceViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {
    private val _state = MutableStateFlow(FinanceScreenState())
    val state: StateFlow<FinanceScreenState> = _state.asStateFlow()

    private val expenseRepository: ExpenseRepository by lazy { ExpenseRepository.getInstance() }
    private val incomeRepository: IncomeRepository by lazy { IncomeRepository.getInstance() }
    private val accountRepository: AccountRepository by lazy { AccountRepository.getInstance() }

    init {
        initializeState()
        refreshData()
        loadAccounts()
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
                
                when (_state.value.selectedTransactionType) {
                    is TransactionType.Income -> {
                        incomeRepository.getIncomes().collect { incomes ->
                            LogUtils.d("FinanceViewModel", 
                                "Received ${incomes.size} incomes before filtering", 
                                category = "finance")
                            
                            val filteredIncomes = incomes.filter { income ->
                                val incomeDate = LocalDate.parse(income.date, formatter)
                                (incomeDate.isEqual(startDate) || incomeDate.isAfter(startDate)) &&
                                (incomeDate.isEqual(endDate) || incomeDate.isBefore(endDate)) &&
                                (_state.value.selectedAccountId == null || income.accountId == _state.value.selectedAccountId)
                            }.map { IncomeTransaction(it) }
                            
                            LogUtils.i("FinanceViewModel", 
                                "Filtered to ${filteredIncomes.size} incomes in selected period" +
                                "\nTotal amount: ${filteredIncomes.sumOf { it.amount }}", 
                                category = "finance")
                            
                            updateTransactionsState(filteredIncomes)
                        }
                    }
                    is TransactionType.Expense -> {
                        expenseRepository.getAllExpenses().collect { expenses ->
                            LogUtils.d("FinanceViewModel", 
                                "Received ${expenses.size} expenses before filtering", 
                                category = "finance")
                            
                            val filteredExpenses = expenses.filter { expense ->
                                val expenseDate = LocalDate.parse(expense.date, formatter)
                                (expenseDate.isEqual(startDate) || expenseDate.isAfter(startDate)) &&
                                (expenseDate.isEqual(endDate) || expenseDate.isBefore(endDate)) &&
                                (_state.value.selectedAccountId == null || expense.accountId == _state.value.selectedAccountId)
                            }.map { ExpenseTransaction(it) }
                            
                            LogUtils.i("FinanceViewModel", 
                                "Filtered to ${filteredExpenses.size} expenses in selected period" +
                                "\nTotal amount: ${filteredExpenses.sumOf { it.amount }}", 
                                category = "finance")
                            
                            updateTransactionsState(filteredExpenses)
                        }
                    }
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

    private fun updateTransactionsState(transactions: List<Transaction>) {
        viewModelScope.launch {
            // Calculate balances for each account
            val accountBalances = mutableMapOf<String, AccountBalance>()
            var totalEurAmount = 0.0

            _state.value.accounts.forEach { account ->
                val accountTransactions = transactions.filter { 
                    when (it) {
                        is IncomeTransaction -> it.income.accountId == account.id
                        is ExpenseTransaction -> it.expense.accountId == account.id
                    }
                }

                val nativeAmount = accountTransactions.sumOf { transaction ->
                    when (_state.value.selectedTransactionType) {
                        is TransactionType.Income -> transaction.amount
                        is TransactionType.Expense -> -transaction.amount
                    }
                }

                val eurAmount = CurrencyConverter.convert(nativeAmount, account.currency, "EUR")
                totalEurAmount += eurAmount

                accountBalances[account.id] = AccountBalance(
                    nativeAmount = nativeAmount,
                    nativeCurrency = account.currency,
                    eurAmount = eurAmount
                )
            }

            LogUtils.i("FinanceViewModel", 
                "Updated state:" +
                "\nTotal transactions: ${transactions.size}" +
                "\nTotal amount in EUR: $totalEurAmount" +
                "\nAccounts with transactions: ${accountBalances.size}", 
                category = "finance")

            _state.value = _state.value.copy(
                transactions = transactions,
                totalAmount = accountBalances.values.sumOf { it.nativeAmount },
                totalAmountInEur = totalEurAmount,
                accountBalances = accountBalances,
                isLoading = false
            )
        }
    }

    // Delete functions
    fun deleteIncome(incomeId: String) {
        viewModelScope.launch {
            try {
                incomeRepository.deleteIncome(incomeId)
                refreshData()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            try {
                expenseRepository.deleteExpense(expenseId)
                refreshData()
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