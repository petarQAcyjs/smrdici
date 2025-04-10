package com.petar.smrdici.ui.screens.budget

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.petar.smrdici.data.model.Account
import com.petar.smrdici.data.model.Expense
import com.petar.smrdici.data.model.Income
import com.petar.smrdici.data.repository.ExpenseRepository
import com.petar.smrdici.data.repository.IncomeRepository
import com.petar.smrdici.ui.screens.settings.BudgetSettingsViewModel
import com.petar.smrdici.ui.screens.settings.Period
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.petar.smrdici.data.model.AccountType
import java.util.*

class BudgetViewModel(
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val settingsViewModel: BudgetSettingsViewModel
) : ViewModel() {
    
    // UI стање
    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()
    
    // Стање за период
    private val _selectedPeriodIndex = MutableStateFlow(2) // Подразумевано месечно
    val selectedPeriodIndex: StateFlow<Int> = _selectedPeriodIndex.asStateFlow()
    
    // Стање за период као Period објекат
    private val _selectedPeriod = MutableStateFlow(Period.MONTHLY)
    val selectedPeriod: StateFlow<Period> = _selectedPeriod.asStateFlow()
    
    // Остала стања из оригиналаног ViewModel-а
    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()
    
    private val _incomes = MutableStateFlow<List<Income>>(emptyList())
    val incomes: StateFlow<List<Income>> = _incomes.asStateFlow()
    
    // Додајемо ове променљиве у BudgetViewModel
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    
    // Додајемо променљиву за селектовани рачун
    private val _selectedAccountId = MutableStateFlow<String?>(null) // null значи "сви рачуни"
    val selectedAccountId: StateFlow<String?> = _selectedAccountId.asStateFlow()
    
    init {
        // Подешавамо период из BudgetSettingsViewModel
        viewModelScope.launch {
            settingsViewModel.period.collectLatest { period ->
                _selectedPeriod.value = period
                Log.d("BudgetViewModel", "Подешени период: $period")
                
                // Ажурирамо изабрани период индекс на основу периода
                val periodIndex = when (period) {
                    Period.DAILY -> 0
                    Period.WEEKLY -> 1
                    Period.MONTHLY -> 2
                    Period.YEARLY -> 3
                    Period.CUSTOM -> 4
                    Period.ALL -> 5
                }
                _selectedPeriodIndex.value = periodIndex
                
                // Учитавамо трансакције након постављања периода
                loadTransactions()
            }
        }
        
        // Учитавамо рачуне
        loadAccounts()
    }
    
    fun updatePeriodIndex(index: Int) {
        if (_selectedPeriodIndex.value != index) {
            _selectedPeriodIndex.value = index
            
            // Ажурирамо и подешавања
            val period = when (index) {
                0 -> Period.DAILY
                1 -> Period.WEEKLY
                2 -> Period.MONTHLY
                3 -> Period.YEARLY
                4 -> Period.CUSTOM
                5 -> Period.ALL
                else -> Period.MONTHLY
            }
            
            _selectedPeriod.value = period
            
            // Ажурирамо подешавања
            viewModelScope.launch {
                settingsViewModel.setPeriod(period)
                // Поново учитај податке са новим периодом
                loadTransactions()
            }
        }
    }
    
    private fun loadTransactions() {
        loadExpenses()
        loadIncomes()
    }
    
    // Јавна метода за експлицитно учитавање трансакција
    fun reloadTransactions() {
        loadTransactions()
    }
    
    private fun loadExpenses() {
        viewModelScope.launch {
            _isLoading.value = true
            
            // Користимо нове методе за учитавање расхода за одређени период
            val currentPeriod = _selectedPeriod.value
            val accountId = _selectedAccountId.value
            
            try {
                if (currentPeriod == Period.ALL) {
                    // За све периоде користимо getAllExpenses
                    if (accountId != null) {
                        // Филтрирамо по рачуну
                        expenseRepository.getExpensesForAccount(accountId).collect { expenses ->
                            _expenses.value = expenses
                            updateUiState()
                            _isLoading.value = false
                        }
                    } else {
                        // Сви рачуни
                        expenseRepository.getAllExpenses().collect { expenses ->
                            _expenses.value = expenses
                            updateUiState()
                            _isLoading.value = false
                        }
                    }
                } else {
                    // За остале периоде користимо getExpensesForPeriod
                    val (startDate, endDate) = calculatePeriodDates(currentPeriod)
                    
                    Log.d("BudgetViewModel", "Учитавам расходе за период од $startDate до $endDate")
                    
                    if (accountId != null) {
                        // Филтрирамо по рачуну и периоду - пошто нема готове методе, сами филтрирамо
                        expenseRepository.getExpensesForAccount(accountId).collect { allExpensesForAccount ->
                            val filteredExpenses = allExpensesForAccount.filter { expense ->
                                val expenseDate = expense.date.toDate()
                                expenseDate.time >= startDate.time && expenseDate.time <= endDate.time
                            }
                            _expenses.value = filteredExpenses
                            updateUiState()
                            _isLoading.value = false
                        }
                    } else {
                        // Сви рачуни за период
                        expenseRepository.getExpensesForPeriod(startDate, endDate).collect { expenses ->
                            _expenses.value = expenses
                            updateUiState()
                            _isLoading.value = false
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("BudgetViewModel", "Грешка при учитавању расхода", e)
                _uiState.value = _uiState.value.copy(error = "Грешка при учитавању расхода: ${e.message}")
                _isLoading.value = false
            }
        }
    }
    
    // Funkcija za dobijanje date-a u odabranom periodu
    private fun calculatePeriodDates(period: Period): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        val endDate = calendar.time
        
        calendar.apply {
            when (period) {
                Period.DAILY -> {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                Period.WEEKLY -> {
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                Period.MONTHLY -> {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                Period.YEARLY -> {
                    set(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                Period.CUSTOM -> {
                    // Dobavljamo prilagođeni dan početka perioda
                    val customStartDay = settingsViewModel.customPeriodStartDay.value
                    val currentDay = get(Calendar.DAY_OF_MONTH)
                    
                    // Postavljamo vreme na početak dana
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    
                    if (currentDay >= customStartDay) {
                        // Ako je trenutni dan veći ili jednak danu početka perioda,
                        // period počinje istog meseca
                        set(Calendar.DAY_OF_MONTH, customStartDay)
                    } else {
                        // Ako je trenutni dan manji od dana početka perioda,
                        // period počinje prethodnog meseca
                        add(Calendar.MONTH, -1)
                        set(Calendar.DAY_OF_MONTH, customStartDay)
                    }
                }
                Period.ALL -> {
                    // Za "Sve" opciju ne menjamo početni datum, ali stavljamo jako rani datum
                    // kao početak kako bismo uzeli sve transakcije
                    set(Calendar.YEAR, 2000)
                    set(Calendar.MONTH, Calendar.JANUARY)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
            }
        }
        
        val startDate = calendar.time
        
        // Za Period.CUSTOM moramo posebno izračunati krajnji datum
        if (period == Period.CUSTOM) {
            calendar.time = startDate
            calendar.add(Calendar.MONTH, 1)
            calendar.add(Calendar.DAY_OF_MONTH, -1)
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            return Pair(startDate, calendar.time)
        }
        
        return Pair(startDate, endDate)
    }
    
    private fun loadIncomes() {
        viewModelScope.launch {
            _isLoading.value = true
            
            // Користимо нове методе за учитавање прихода за одређени период
            val currentPeriod = _selectedPeriod.value
            val accountId = _selectedAccountId.value
            
            try {
                if (currentPeriod == Period.ALL) {
                    // За све периоде користимо getAllIncomes
                    if (accountId != null) {
                        // Филтрирамо по рачуну
                        incomeRepository.getAllIncomes().collect { allIncomes ->
                            val filteredIncomes = allIncomes.filter { income ->
                                income.accountId == accountId
                            }
                            _incomes.value = filteredIncomes
                            updateUiState()
                            _isLoading.value = false
                        }
                    } else {
                        // Сви рачуни
                        incomeRepository.getAllIncomes().collect { incomes ->
                            _incomes.value = incomes
                            updateUiState()
                            _isLoading.value = false
                        }
                    }
                } else {
                    // За остале периоде користимо getIncomesForPeriod
                    val (startDate, endDate) = calculatePeriodDates(currentPeriod)
                    
                    Log.d("BudgetViewModel", "Учитавам приходе за период од $startDate до $endDate")
                    
                    if (accountId != null) {
                        // Филтрирамо по рачуну и периоду - пошто нема готове методе, сами филтрирамо
                        incomeRepository.getAllIncomes().collect { allIncomes ->
                            val filteredIncomes = allIncomes.filter { income ->
                                val incomeDate = income.date.toDate()
                                incomeDate.time >= startDate.time && incomeDate.time <= endDate.time &&
                                income.accountId == accountId
                            }
                            _incomes.value = filteredIncomes
                            updateUiState()
                            _isLoading.value = false
                        }
                    } else {
                        // Сви рачуни за период
                        incomeRepository.getIncomesForPeriod(startDate, endDate).collect { incomes ->
                            _incomes.value = incomes
                            updateUiState()
                            _isLoading.value = false
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("BudgetViewModel", "Грешка при учитавању прихода", e)
                _uiState.value = _uiState.value.copy(error = "Грешка при учитавању прихода: ${e.message}")
                _isLoading.value = false
            }
        }
    }
    
    // Нова функција за ажурирање UI стања
    private fun updateUiState() {
        _uiState.value = _uiState.value.copy(
            expenses = _expenses.value,
            incomes = _incomes.value,
            accounts = _accounts.value,
            isLoading = _isLoading.value,
            error = null // Ресетујемо грешку када успешно учитамо податке
        )
    }
    
    // Метода за одабир рачуна
    fun selectAccount(accountId: String?) {
        if (_selectedAccountId.value != accountId) {
            _selectedAccountId.value = accountId
            
            // Поново учитавамо податке за нови рачун
            loadTransactions()
        }
    }
    
    // Метода која проверава да ли је неки рачун већ селектован
    fun isAccountSelected(): Boolean {
        return _selectedAccountId.value != null
    }
    
    // Додајемо методу за брисање трошка
    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            try {
                Log.d("BudgetViewModel", "Бришем трошак: $expenseId")
                expenseRepository.deleteExpense(expenseId).onSuccess {
                    // Поново учитај трансакције након брисања
                    loadTransactions()
                }.onFailure { error ->
                    Log.e("BudgetViewModel", "Грешка при брисању трошка", error)
                    _uiState.value = _uiState.value.copy(error = "Грешка при брисању трошка: ${error.message}")
                }
            } catch (e: Exception) {
                Log.e("BudgetViewModel", "Грешка при брисању трошка", e)
                _uiState.value = _uiState.value.copy(error = "Грешка при брисању трошка: ${e.message}")
            }
        }
    }
    
    // Додајемо методу за брисање прихода
    fun deleteIncome(incomeId: String) {
        viewModelScope.launch {
            try {
                Log.d("BudgetViewModel", "Бришем приход: $incomeId")
                incomeRepository.deleteIncome(incomeId).onSuccess {
                    // Поново учитај трансакције након брисања
                    loadTransactions()
                }.onFailure { error ->
                    Log.e("BudgetViewModel", "Грешка при брисању прихода", error)
                    _uiState.value = _uiState.value.copy(error = "Грешка при брисању прихода: ${error.message}")
                }
            } catch (e: Exception) {
                Log.e("BudgetViewModel", "Грешка при брисању прихода", e)
                _uiState.value = _uiState.value.copy(error = "Грешка при брисању прихода: ${e.message}")
            }
        }
    }
    
    // Метода за учитавање рачуна
    fun loadAccounts() {
        viewModelScope.launch {
            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                if (userId != null) {
                    val accountsCollection = FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(userId)
                        .collection("accounts")
                    
                    accountsCollection.addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("BudgetViewModel", "Грешка при учитавању рачуна", error)
                            return@addSnapshotListener
                        }
                        
                        val accountsList = snapshot?.documents?.mapNotNull { doc ->
                            val account = doc.toObject(Account::class.java)
                            // Безбедно постављамо ID пошто је сада променљив (var)
                            account?.apply { id = doc.id }
                        } ?: emptyList()
                        
                        _accounts.value = accountsList
                        updateUiState()
                    }
                }
            } catch (e: Exception) {
                Log.e("BudgetViewModel", "Грешка при учитавању рачуна", e)
            }
        }
    }
    
    // Фабрика за креирање ViewModel-а
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BudgetViewModel::class.java)) {
                val expenseRepository = ExpenseRepository.getInstance()
                val incomeRepository = IncomeRepository.getInstance()
                val settingsViewModel = BudgetSettingsViewModel.Factory(context)
                    .create(BudgetSettingsViewModel::class.java)
                return BudgetViewModel(expenseRepository, incomeRepository, settingsViewModel) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

// UI стање за буџет
data class BudgetUiState(
    val isLoading: Boolean = false,
    val expenses: List<Expense> = emptyList(),
    val incomes: List<Income> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val error: String? = null
) 