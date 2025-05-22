package com.petar.smrdici.ui.screens.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petar.smrdici.data.model.Account
import com.petar.smrdici.data.model.Expense
import com.petar.smrdici.data.model.Income
import com.petar.smrdici.data.repository.ExpenseRepository
import com.petar.smrdici.data.repository.IncomeRepository
import com.petar.smrdici.data.repository.AccountRepository
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
            is Custom -> false // Custom periods are never considered current
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
    val isLoading: Boolean = false,
    val error: String? = null,
    val accounts: List<Account> = emptyList()
)

class FinanceViewModel : ViewModel() {
    private val _state = MutableStateFlow(FinanceScreenState())
    val state: StateFlow<FinanceScreenState> = _state.asStateFlow()

    private val expenseRepository: ExpenseRepository by lazy { ExpenseRepository.getInstance() }
    private val incomeRepository: IncomeRepository by lazy { IncomeRepository.getInstance() }
    private val accountRepository: AccountRepository by lazy { AccountRepository.getInstance() }

    init {
        refreshData()
        loadAccounts()
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            try {
                accountRepository.accounts.collect { accounts ->
                    _state.value = _state.value.copy(accounts = accounts)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    // Time period navigation
    fun navigateToNextPeriod() {
        _state.value = _state.value.let { currentState ->
            currentState.copy(
                selectedTimePeriod = when (val period = currentState.selectedTimePeriod) {
                    is TimePeriod.Day -> TimePeriod.Day(period.date.plus(1, ChronoUnit.DAYS))
                    is TimePeriod.Week -> TimePeriod.Week(period.startDate.plus(7, ChronoUnit.DAYS))
                    is TimePeriod.Month -> TimePeriod.Month(period.yearMonth.plusMonths(1))
                    is TimePeriod.Year -> TimePeriod.Year(period.year + 1)
                    is TimePeriod.Custom -> period // Custom periods don't support navigation
                }
            )
        }
        refreshData()
    }

    fun navigateToPreviousPeriod() {
        _state.value = _state.value.let { currentState ->
            currentState.copy(
                selectedTimePeriod = when (val period = currentState.selectedTimePeriod) {
                    is TimePeriod.Day -> TimePeriod.Day(period.date.minus(1, ChronoUnit.DAYS))
                    is TimePeriod.Week -> TimePeriod.Week(period.startDate.minus(7, ChronoUnit.DAYS))
                    is TimePeriod.Month -> TimePeriod.Month(period.yearMonth.minusMonths(1))
                    is TimePeriod.Year -> TimePeriod.Year(period.year - 1)
                    is TimePeriod.Custom -> period // Custom periods don't support navigation
                }
            )
        }
        refreshData()
    }

    fun setTimePeriod(period: TimePeriod) {
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
                    is TimePeriod.Custom -> currentState.selectedTimePeriod
                }
            )
        }
        refreshData()
    }

    // Account selection
    fun setSelectedAccount(accountId: String?) {
        _state.value = _state.value.copy(selectedAccountId = accountId)
        refreshData()
    }

    // Transaction type
    fun setTransactionType(type: TransactionType) {
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
                
                val transactions = when (_state.value.selectedTransactionType) {
                    is TransactionType.Income -> {
                        incomeRepository.getAllIncomes().first().filter { income ->
                            val incomeDate = LocalDate.parse(income.date, formatter)
                            (incomeDate.isEqual(startDate) || incomeDate.isAfter(startDate)) &&
                            (incomeDate.isEqual(endDate) || incomeDate.isBefore(endDate)) &&
                            (_state.value.selectedAccountId == null || income.accountId == _state.value.selectedAccountId)
                        }.map { IncomeTransaction(it) }
                    }
                    is TransactionType.Expense -> {
                        expenseRepository.getAllExpenses().first().filter { expense ->
                            val expenseDate = LocalDate.parse(expense.date, formatter)
                            (expenseDate.isEqual(startDate) || expenseDate.isAfter(startDate)) &&
                            (expenseDate.isEqual(endDate) || expenseDate.isBefore(endDate)) &&
                            (_state.value.selectedAccountId == null || expense.accountId == _state.value.selectedAccountId)
                        }.map { ExpenseTransaction(it) }
                    }
                }

                val total = transactions.sumOf { transaction ->
                    when (_state.value.selectedTransactionType) {
                        is TransactionType.Income -> transaction.amount
                        is TransactionType.Expense -> -transaction.amount
                    }
                }
                
                _state.value = _state.value.copy(
                    transactions = transactions,
                    totalAmount = total,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = e.message,
                    isLoading = false
                )
            }
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
            PeriodType.CUSTOM -> TimePeriod.Custom(now, now) // This will be handled separately
        }
        setTimePeriod(currentPeriod)
    }
} 