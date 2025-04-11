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
import java.util.Calendar
import java.util.Date

class BudgetViewModel(
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val settingsViewModel: BudgetSettingsViewModel,
    private val context: Context
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
    
    // Додајемо нове променљиве за управљање буџетским лимитом
    private val _budgetLimit = MutableStateFlow(0.0)
    val budgetLimit: StateFlow<Double> = _budgetLimit.asStateFlow()
    
    // Проценат искоришћености буџета
    private val _budgetUsagePercent = MutableStateFlow(0.0)
    val budgetUsagePercent: StateFlow<Double> = _budgetUsagePercent.asStateFlow()
    
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
                
                // Учитавамо буџетски лимит за тренутни период
                loadBudgetLimit()
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
        viewModelScope.launch {
            try {
                Log.d("BudgetViewModel", "Почињем освежавање трансакција...")
                _isLoading.value = true
                
                // Паралелно покрећемо учитавање трансакција
                loadTransactions()
                
                // Учитавамо буџетски лимит
                loadBudgetLimit()
                
                // Учитавамо рачуне
                loadAccounts()
                
                Log.d("BudgetViewModel", "Освежавање трансакција завршено!")
            } catch (e: Exception) {
                Log.e("BudgetViewModel", "Грешка при освежавању трансакција", e)
            } finally {
                _isLoading.value = false
            }
        }
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
                        try {
                            expenseRepository.getAllExpenses().collect { allExpenses ->
                                val filteredExpenses = allExpenses.filter { expense ->
                                    expense.accountId == accountId
                                }
                                _expenses.value = filteredExpenses
                                _isLoading.value = false
                                updateUiState()
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) {
                                // Игноришемо грешке отказивања корутине
                                Log.d("BudgetViewModel", "Корутина за учитавање расхода је отказана")
                            } else {
                                throw e
                            }
                        }
                    } else {
                        // Сви рачуни
                        try {
                            expenseRepository.getAllExpenses().collect { expenses ->
                                _expenses.value = expenses
                                _isLoading.value = false
                                updateUiState()
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) {
                                // Игноришемо грешке отказивања корутине
                                Log.d("BudgetViewModel", "Корутина за учитавање расхода је отказана")
                            } else {
                                throw e
                            }
                        }
                    }
                } else {
                    // За остале периоде користимо getExpensesForPeriod
                    val (startDate, endDate) = calculatePeriodDates(currentPeriod)
                    
                    Log.d("BudgetViewModel", "Учитавам расходе за период од $startDate до $endDate")
                    
                    if (accountId != null) {
                        // Филтрирамо по рачуну и периоду - пошто нема готове методе, сами филтрирамо
                        try {
                            expenseRepository.getExpensesForAccount(accountId).collect { allExpensesForAccount ->
                                val filteredExpenses = allExpensesForAccount.filter { expense ->
                                    val expenseDate = expense.date.toDate()
                                    expenseDate.time >= startDate.time && expenseDate.time <= endDate.time
                                }
                                _expenses.value = filteredExpenses
                                _isLoading.value = false
                                updateUiState()
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) {
                                // Игноришемо грешке отказивања корутине
                                Log.d("BudgetViewModel", "Корутина за учитавање расхода је отказана")
                            } else {
                                throw e
                            }
                        }
                    } else {
                        // Сви рачуни за период
                        try {
                            expenseRepository.getExpensesForPeriod(startDate, endDate).collect { expenses ->
                                _expenses.value = expenses
                                _isLoading.value = false
                                updateUiState()
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) {
                                // Игноришемо грешке отказивања корутине
                                Log.d("BudgetViewModel", "Корутина за учитавање расхода је отказана")
                            } else {
                                throw e
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    // Игноришемо грешке отказивања корутине
                    Log.d("BudgetViewModel", "Корутина за учитавање расхода је отказана")
                } else {
                    Log.e("BudgetViewModel", "Грешка при учитавању расхода", e)
                    _uiState.value = _uiState.value.copy(error = "Грешка при учитавању расхода: ${e.message}")
                }
                _isLoading.value = false
            }
            
            // Након учитавања расхода, ажурирамо проценат искоришћености буџета
            calculateBudgetUsage()
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
                        try {
                            incomeRepository.getAllIncomes().collect { allIncomes ->
                                val filteredIncomes = allIncomes.filter { income ->
                                    income.accountId == accountId
                                }
                                _incomes.value = filteredIncomes
                                _isLoading.value = false
                                updateUiState()
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) {
                                // Игноришемо грешке отказивања корутине
                                Log.d("BudgetViewModel", "Корутина за учитавање прихода је отказана")
                            } else {
                                throw e
                            }
                        }
                    } else {
                        // Сви рачуни
                        try {
                            incomeRepository.getAllIncomes().collect { incomes ->
                                _incomes.value = incomes
                                _isLoading.value = false
                                updateUiState()
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) {
                                // Игноришемо грешке отказивања корутине
                                Log.d("BudgetViewModel", "Корутина за учитавање прихода је отказана")
                            } else {
                                throw e
                            }
                        }
                    }
                } else {
                    // За остале периоде користимо getIncomesForPeriod
                    val (startDate, endDate) = calculatePeriodDates(currentPeriod)
                    
                    Log.d("BudgetViewModel", "Учитавам приходе за период од $startDate до $endDate")
                    
                    if (accountId != null) {
                        // Филтрирамо по рачуну и периоду - пошто нема готове методе, сами филтрирамо
                        try {
                            incomeRepository.getAllIncomes().collect { allIncomes ->
                                val filteredIncomes = allIncomes.filter { income ->
                                    val incomeDate = income.date.toDate()
                                    incomeDate.time >= startDate.time && incomeDate.time <= endDate.time &&
                                    income.accountId == accountId
                                }
                                _incomes.value = filteredIncomes
                                _isLoading.value = false
                                updateUiState()
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) {
                                // Игноришемо грешке отказивања корутине
                                Log.d("BudgetViewModel", "Корутина за учитавање прихода је отказана")
                            } else {
                                throw e
                            }
                        }
                    } else {
                        // Сви рачуни за период
                        try {
                            incomeRepository.getIncomesForPeriod(startDate, endDate).collect { incomes ->
                                _incomes.value = incomes
                                _isLoading.value = false
                                updateUiState()
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) {
                                // Игноришемо грешке отказивања корутине
                                Log.d("BudgetViewModel", "Корутина за учитавање прихода је отказана")
                            } else {
                                throw e
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    // Игноришемо грешке отказивања корутине
                    Log.d("BudgetViewModel", "Корутина за учитавање прихода је отказана")
                } else {
                    Log.e("BudgetViewModel", "Грешка при учитавању прихода", e)
                    _uiState.value = _uiState.value.copy(error = "Грешка при учитавању прихода: ${e.message}")
                }
                _isLoading.value = false
            }
        }
    }
    
    // Нова функција за ажурирање UI стања
    private fun updateUiState() {
        // Ажурирамо уи стање само ако су нове вредности различите од постојећих
        val currentState = _uiState.value
        val newState = currentState.copy(
            expenses = _expenses.value,
            incomes = _incomes.value,
            accounts = _accounts.value,
            isLoading = _isLoading.value,
            error = null // Ресетујемо грешку када успешно учитамо податке
        )
        
        // Проверавамо да ли је дошло до стварне промене пре ажурирања стања
        if (currentState != newState) {
            _uiState.value = newState
        }
    }
    
    // Метода за одабир рачуна
    fun selectAccount(accountId: String?) {
        if (_selectedAccountId.value != accountId) {
            _selectedAccountId.value = accountId
            
            // Поново учитавамо податке за нови рачун
            loadTransactions()
            
            // Учитавамо буџетски лимит за нови рачун
            loadBudgetLimit()
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
                
                // Прво треба да добавимо трошак да бисмо знали износ и рачун
                val expense = _expenses.value.find { it.id == expenseId }
                if (expense != null) {
                    val result = expenseRepository.deleteExpense(expenseId)
                    
                    // Користимо NonCancellable контекст да спречимо отказивање током навигације
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        if (result.isSuccess) {
                            // Када се трошак брише, балансу рачуна треба додати износ (јер смо га пре одузели)
                            val accountRepo = com.petar.smrdici.data.repository.AccountRepository.getInstance(context)
                            val updateResult = accountRepo.updateAccountBalance(expense.accountId, expense.amount)
                            
                            if (updateResult.isSuccess) {
                                Log.d("BudgetViewModel", "Баланс рачуна ажуриран након брисања трошка")
                            } else {
                                Log.e("BudgetViewModel", "Грешка при ажурирању баланса рачуна", updateResult.exceptionOrNull())
                            }
                            
                            // Поново учитај трансакције након брисања
                            Log.d("BudgetViewModel", "Трошак успешно обрисан, учитавам трансакције")
                            // Кратко сачекамо да Firebase ажурира податке
                            kotlinx.coroutines.delay(500)
                            loadTransactions()
                            // Експлицитно учитавамо и рачуне
                            loadAccounts()
                        } else {
                            Log.e("BudgetViewModel", "Грешка при брисању трошка", result.exceptionOrNull())
                            _uiState.value = _uiState.value.copy(error = "Грешка при брисању трошка: ${result.exceptionOrNull()?.message}")
                        }
                    }
                } else {
                    Log.e("BudgetViewModel", "Трошак са ID $expenseId није пронађен")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    // Игноришемо грешке отказивања корутине
                    Log.d("BudgetViewModel", "Корутина за брисање трошка је отказана")
                } else {
                    Log.e("BudgetViewModel", "Грешка при брисању трошка", e)
                    _uiState.value = _uiState.value.copy(error = "Грешка при брисању трошка: ${e.message}")
                }
            }
        }
    }
    
    // Додајемо методу за брисање прихода
    fun deleteIncome(incomeId: String) {
        viewModelScope.launch {
            try {
                Log.d("BudgetViewModel", "Бришем приход: $incomeId")
                
                // Прво треба да добавимо приход да бисмо знали износ и рачун
                val income = _incomes.value.find { it.id == incomeId }
                if (income != null) {
                    val result = incomeRepository.deleteIncome(incomeId)
                    
                    // Користимо NonCancellable контекст да спречимо отказивање током навигације
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        if (result.isSuccess) {
                            // Када се приход брише, од баланса рачуна треба одузети износ (јер смо га пре додали)
                            val accountRepo = com.petar.smrdici.data.repository.AccountRepository.getInstance(context)
                            val updateResult = accountRepo.updateAccountBalance(income.accountId, -income.amount)
                            
                            if (updateResult.isSuccess) {
                                Log.d("BudgetViewModel", "Баланс рачуна ажуриран након брисања прихода")
                            } else {
                                Log.e("BudgetViewModel", "Грешка при ажурирању баланса рачуна", updateResult.exceptionOrNull())
                            }
                            
                            // Поново учитај трансакције након брисања
                            Log.d("BudgetViewModel", "Приход успешно обрисан, учитавам трансакције")
                            // Кратко сачекамо да Firebase ажурира податке
                            kotlinx.coroutines.delay(500)
                            loadTransactions()
                            // Експлицитно учитавамо и рачуне
                            loadAccounts()
                        } else {
                            Log.e("BudgetViewModel", "Грешка при брисању прихода", result.exceptionOrNull())
                            _uiState.value = _uiState.value.copy(error = "Грешка при брисању прихода: ${result.exceptionOrNull()?.message}")
                        }
                    }
                } else {
                    Log.e("BudgetViewModel", "Приход са ID $incomeId није пронађен")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    // Игноришемо грешке отказивања корутине
                    Log.d("BudgetViewModel", "Корутина за брисање прихода је отказана")
                } else {
                    Log.e("BudgetViewModel", "Грешка при брисању прихода", e)
                    _uiState.value = _uiState.value.copy(error = "Грешка при брисању прихода: ${e.message}")
                }
            }
        }
    }
    
    // Додајемо методе за директно додавање расхода и прихода из ViewModel-а
    // Ово је много бољи приступ него да се директно приступа репозиторијуму
    suspend fun addExpense(expense: Expense): Result<Expense> {
        // Користимо NonCancellable контекст за целу операцију додавања расхода
        return kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            try {
                Log.d("BudgetViewModel", "Додајем расход: $expense")
                val result = expenseRepository.addExpense(expense)
                
                if (result.isSuccess) {
                    // Када се додаје трошак, од баланса рачуна треба одузети износ
                    val accountRepo = com.petar.smrdici.data.repository.AccountRepository.getInstance(context)
                    val updateResult = accountRepo.updateAccountBalance(expense.accountId, -expense.amount)
                    
                    if (updateResult.isSuccess) {
                        Log.d("BudgetViewModel", "Баланс рачуна ажуриран након додавања трошка")
                    } else {
                        Log.e("BudgetViewModel", "Грешка при ажурирању баланса рачуна", updateResult.exceptionOrNull())
                    }
                    
                    // Кратко сачекамо да Firebase ажурира податке
                    Log.d("BudgetViewModel", "Расход успешно додат, учитавам трансакције")
                    kotlinx.coroutines.delay(500)
                    
                    // Покушајте да освежите податке, али игноришите грешке ако се деси отказивање
                    try {
                        loadTransactions()
                        loadAccounts()
                    } catch (e: Exception) {
                        Log.d("BudgetViewModel", "Освежавање података након додавања расхода отказано", e)
                        // Не пропагирамо грешку даље јер је главна операција (додавање расхода) успешна
                    }
                }
                
                result
            } catch (e: Exception) {
                Log.e("BudgetViewModel", "Грешка при додавању расхода", e)
                // Ажурирамо UI стање чак и у случају грешке
                _uiState.value = _uiState.value.copy(error = "Грешка при додавању расхода: ${e.message}")
                Result.failure(e)
            }
        }
    }
    
    suspend fun addIncome(income: Income): Result<Income> {
        // Користимо NonCancellable контекст за целу операцију додавања прихода
        return kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            try {
                Log.d("BudgetViewModel", "Додајем приход: $income")
                val result = incomeRepository.addIncome(income)
                
                if (result.isSuccess) {
                    // Када се додаје приход, балансу рачуна треба додати износ
                    val accountRepo = com.petar.smrdici.data.repository.AccountRepository.getInstance(context)
                    val updateResult = accountRepo.updateAccountBalance(income.accountId, income.amount)
                    
                    if (updateResult.isSuccess) {
                        Log.d("BudgetViewModel", "Баланс рачуна ажуриран након додавања прихода")
                    } else {
                        Log.e("BudgetViewModel", "Грешка при ажурирању баланса рачуна", updateResult.exceptionOrNull())
                    }
                    
                    // Кратко сачекамо да Firebase ажурира податке
                    Log.d("BudgetViewModel", "Приход успешно додат, учитавам трансакције")
                    kotlinx.coroutines.delay(500)
                    
                    // Покушајте да освежите податке, али игноришите грешке ако се деси отказивање
                    try {
                        loadTransactions()
                        loadAccounts()
                    } catch (e: Exception) {
                        Log.d("BudgetViewModel", "Освежавање података након додавања прихода отказано", e)
                        // Не пропагирамо грешку даље јер је главна операција (додавање прихода) успешна
                    }
                }
                
                result
            } catch (e: Exception) {
                Log.e("BudgetViewModel", "Грешка при додавању прихода", e)
                // Ажурирамо UI стање чак и у случају грешке
                _uiState.value = _uiState.value.copy(error = "Грешка при додавању прихода: ${e.message}")
                Result.failure(e)
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
                        
                        // Проверавамо да ли је листа заиста промењена пре ажурирања стања
                        if (_accounts.value != accountsList) {
                            _accounts.value = accountsList
                            updateUiState()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("BudgetViewModel", "Грешка при учитавању рачуна", e)
            }
        }
    }
    
    // Нова метода за чување буџетског лимита
    fun saveBudgetLimit(limit: Double) {
        viewModelScope.launch {
            try {
                // Користимо SharedPreferences за чување буџетског лимита
                val sharedPrefs = context.getSharedPreferences("budget_settings", Context.MODE_PRIVATE)
                val periodKey = when (_selectedPeriod.value) {
                    Period.DAILY -> "daily"
                    Period.WEEKLY -> "weekly"
                    Period.MONTHLY -> "monthly"
                    Period.YEARLY -> "yearly"
                    Period.CUSTOM -> "custom"
                    Period.ALL -> "all"
                }
                
                // Ако је изабран конкретан рачун, додајемо ID рачуна у кључ
                val accountKey = _selectedAccountId.value ?: "all_accounts"
                val key = "budget_limit_${periodKey}_$accountKey"
                
                // Чувамо буџетски лимит у SharedPreferences
                sharedPrefs.edit().putFloat(key, limit.toFloat()).apply()
                
                // Ажурирамо вредност у StateFlow-у
                _budgetLimit.value = limit
                
                // Поново израчунавамо проценат искоришћености након промене лимита
                calculateBudgetUsage()
                
                Log.d("BudgetViewModel", "Сачуван буџетски лимит: $limit за период $periodKey и рачун $accountKey")
            } catch (e: Exception) {
                Log.e("BudgetViewModel", "Грешка при чувању буџетског лимита", e)
            }
        }
    }
    
    // Метода за учитавање буџетског лимита
    private fun loadBudgetLimit() {
        viewModelScope.launch {
            try {
                val sharedPrefs = context.getSharedPreferences("budget_settings", Context.MODE_PRIVATE)
                val periodKey = when (_selectedPeriod.value) {
                    Period.DAILY -> "daily"
                    Period.WEEKLY -> "weekly"
                    Period.MONTHLY -> "monthly"
                    Period.YEARLY -> "yearly"
                    Period.CUSTOM -> "custom"
                    Period.ALL -> "all"
                }
                
                // Ако је изабран конкретан рачун, додајемо ID рачуна у кључ
                val accountKey = _selectedAccountId.value ?: "all_accounts"
                val key = "budget_limit_${periodKey}_$accountKey"
                
                // Учитавамо буџетски лимит из SharedPreferences
                val limit = sharedPrefs.getFloat(key, 0f).toDouble()
                _budgetLimit.value = limit
                
                Log.d("BudgetViewModel", "Учитан буџетски лимит: $limit за период $periodKey и рачун $accountKey")
                
                // Израчунавамо проценат искоришћености буџета
                calculateBudgetUsage()
            } catch (e: Exception) {
                Log.e("BudgetViewModel", "Грешка при учитавању буџетског лимита", e)
            }
        }
    }
    
    // Метода за израчунавање процента искоришћености буџета
    private fun calculateBudgetUsage() {
        val limit = _budgetLimit.value
        // Ако лимит није постављен или је нула, нема смисла рачунати проценат
        if (limit <= 0) {
            _budgetUsagePercent.value = 0.0
            return
        }
        
        // Укупни трошкови за тренутни период
        val totalExpense = _expenses.value.sumOf { it.amount }
        
        // Израчунавамо проценат (0.0 - 1.0)
        val percent = totalExpense / limit
        _budgetUsagePercent.value = percent
        
        Log.d("BudgetViewModel", "Израчунат проценат буџета: $percent (потрошено $totalExpense од $limit)")
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
                return BudgetViewModel(expenseRepository, incomeRepository, settingsViewModel, context) as T
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