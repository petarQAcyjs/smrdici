package com.petar.smrdici.ui.screens.budget

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.petar.smrdici.data.model.Account
import com.petar.smrdici.data.model.Budget
import com.petar.smrdici.data.model.BudgetType
import com.petar.smrdici.data.model.DisplayBudget
import com.petar.smrdici.data.model.Expense
import com.petar.smrdici.data.model.Income
import com.petar.smrdici.data.repository.AccountRepository
import com.petar.smrdici.data.repository.BudgetRepository
import com.petar.smrdici.data.repository.ExpenseRepository
import com.petar.smrdici.data.repository.IncomeRepository
import com.petar.smrdici.data.repository.RepositoryManager
import com.petar.smrdici.ui.screens.settings.BudgetSettingsViewModel
import com.petar.smrdici.ui.screens.settings.Period
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

/**
 * ViewModel za upravljanje listom budžeta i ukupnom potrošnjom
 */
class BudgetsViewModel(
    private val budgetRepository: BudgetRepository,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val accountRepository: AccountRepository,
    private val settingsViewModel: BudgetSettingsViewModel
) : ViewModel() {
    
    private val tag = "BudgetsViewModel"
    
    // Lista svih računa
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()
    
    // Stanje za period
    private val _selectedPeriodIndex = MutableStateFlow(2) // Podrazumevano mesečno
    val selectedPeriodIndex: StateFlow<Int> = _selectedPeriodIndex.asStateFlow()
    
    // Interno stanje za period - koristimo interno
    private val _selectedPeriod = MutableStateFlow(Period.MONTHLY)
    
    // Svi budžeti
    private val _allBudgets = MutableStateFlow<List<Budget>>(emptyList())
    
    // Interna stanja za budžete
    private val _expenseBudgets = MutableStateFlow<List<Budget>>(emptyList())
    private val _incomeBudgets = MutableStateFlow<List<Budget>>(emptyList())
    
    // DisplayBudget lista za rashode
    private val _displayExpenseBudgets = MutableStateFlow<List<DisplayBudget>>(emptyList())
    val displayExpenseBudgets: StateFlow<List<DisplayBudget>> = _displayExpenseBudgets.asStateFlow()
    
    // DisplayBudget lista za prihode
    private val _displayIncomeBudgets = MutableStateFlow<List<DisplayBudget>>(emptyList())
    val displayIncomeBudgets: StateFlow<List<DisplayBudget>> = _displayIncomeBudgets.asStateFlow()
    
    // Odabrani račun
    private val _selectedAccountId = MutableStateFlow<String?>(null)
    val selectedAccountId: StateFlow<String?> = _selectedAccountId.asStateFlow()
    
    // UI stanje
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage
    
    // Ukupni podaci o budžetu - interno stanje
    private val _totalExpenseBudget = MutableStateFlow(0.0)
    private val _totalIncomeBudget = MutableStateFlow(0.0)
    
    private val _totalExpenseSpent = MutableStateFlow(0.0)
    val totalExpenseSpent: StateFlow<Double> = _totalExpenseSpent.asStateFlow()
    
    private val _totalIncomeReceived = MutableStateFlow(0.0)
    val totalIncomeReceived: StateFlow<Double> = _totalIncomeReceived.asStateFlow()
    
    init {
        // Inicijalno učitavamo račune
        loadAccounts()
        
        // Inicijalno učitavamo budžete
        viewModelScope.launch {
            // Učitavamo period iz podešavanja
            settingsViewModel.period.collect { period: Period ->
                _selectedPeriod.value = period
                
                // Ažuriramo izabrani period indeks na osnovu perioda
                val periodIndex = when (period) {
                    Period.DAILY -> 0
                    Period.WEEKLY -> 1
                    Period.MONTHLY -> 2
                    Period.YEARLY -> 3
                    Period.CUSTOM -> 4
                    Period.ALL -> 5
                }
                _selectedPeriodIndex.value = periodIndex
                
                // Učitavamo sve budžete
                loadBudgets()
            }
        }
    }
    
    /**
     * Učitavanje svih računa
     */
    private fun loadAccounts() {
        viewModelScope.launch {
            try {
                // Učitavamo sve račune direktno
                val accountsList = accountRepository.getAllAccounts()
                
                // Ažuriramo stanje sa listom računa
                _accounts.value = accountsList
                
                // Proveravamo da li treba da postavimo podrazumevani račun
                if (accountsList.isNotEmpty() && _selectedAccountId.value == null) {
                    // Tražimo glavni račun ili uzimamo prvi ako glavni ne postoji
                    val defaultAccount = accountsList.find { account -> account.isDefault } 
                        ?: accountsList[0]
                    
                    // Postavljamo ID podrazumevanog računa
                    _selectedAccountId.value = defaultAccount.id
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    // Preskačemo grešku ako je posao otkazan
                    Log.d(tag, "Учитавање рачуна отказано", e)
                } else {
                    Log.e(tag, "Грешка при учитавању рачуна", e)
                    _errorMessage.value = "Грешка при учитавању рачуна: ${e.message}"
                }
            }
        }
    }
    
    /**
     * Učitavanje svih budžeta
     */
    fun loadBudgets() {
        viewModelScope.launch {
            _isLoading.value = true
            
            try {
                Log.d(tag, "Учитавам буџете...")
                
                // Учитавамо све буџете
                budgetRepository.getAllBudgets().collect { budgets ->
                    // Филтрирамо буџете ако је потребно
                    val filteredBudgets = if (_selectedAccountId.value != null) {
                        budgets.filter { it.accountId == _selectedAccountId.value }
                    } else {
                        budgets
                    }
                    
                    _allBudgets.value = filteredBudgets
                    
                    // Сада израчунавамо трошкове за све буџете
                    calculateBudgetSpending(filteredBudgets)
                    
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e(tag, "Грешка при учитавању буџета", e)
                _errorMessage.value = "Грешка при учитавању буџета: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Функција за израчунавање потрошње за буџете
     */
    private fun calculateBudgetSpending(budgets: List<Budget>) {
        viewModelScope.launch {
            try {
                // Раздвајамо буџете по типу
                _expenseBudgets.value = budgets.filter { it.type == BudgetType.EXPENSE }
                _incomeBudgets.value = budgets.filter { it.type == BudgetType.INCOME }
                
                // Рачунамо укупне вредности буџета
                _totalExpenseBudget.value = _expenseBudgets.value.sumOf { it.amount }
                _totalIncomeBudget.value = _incomeBudgets.value.sumOf { it.amount }
                
                // Учитавамо поторшњу за расходе и приходе
                loadExpenseSpending()
                loadIncomeReceived()
            } catch (e: Exception) {
                Log.e(tag, "Грешка при израчунавању потрошње буџета", e)
            }
        }
    }
    
    /**
     * Učitava potrošnju za rashode u budžetu
     */
    private fun loadExpenseSpending() {
        viewModelScope.launch {
            try {
                val (startDate, endDate) = calculatePeriodDates(_selectedPeriod.value)
                
                // Učitavamo sve troškove za trenutni period
                val periodExpensesFlow = if (_selectedAccountId.value != null) {
                    expenseRepository.getExpensesForAccount(_selectedAccountId.value!!)
                } else {
                    expenseRepository.getAllExpenses()
                }
                
                periodExpensesFlow.collect { allExpenses: List<Expense> ->
                    // Filtriramo troškove po periodu
                    val periodExpenses = allExpenses.filter { expense: Expense ->
                        val expenseDate = expense.date.toDate().time
                        expenseDate in startDate.time..endDate.time
                    }
                    
                    // Računamo potrošnju za svaki budžet
                    val displayBudgets = _expenseBudgets.value.map { budget: Budget ->
                        // Filtriramo troškove relevantne za ovaj budžet
                        val relevantExpenses = periodExpenses.filter { expense: Expense ->
                            // Provera računa
                            val matchesAccount = budget.accountId.isEmpty() || 
                                                budget.accountId == expense.accountId
                            
                            // Provera kategorije
                            val matchesCategory = budget.categoryIds.isEmpty() || 
                                                 budget.categoryIds.contains(expense.category)
                            
                            matchesAccount && matchesCategory
                        }
                        
                        // Ukupna potrošnja za ovaj budžet
                        val spent = relevantExpenses.sumOf { expense: Expense -> expense.amount }
                        
                        // Kreiramo DisplayBudget objekat
                        DisplayBudget(
                            budget = budget,
                            spentAmount = spent
                        )
                    }
                    
                    // Ažuriramo displayBudgets
                    _displayExpenseBudgets.value = displayBudgets
                    
                    // Ukupna potrošnja
                    _totalExpenseSpent.value = displayBudgets.sumOf { displayBudget: DisplayBudget -> displayBudget.spentAmount }
                    
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    // Preskačemo grešku ako je posao otkazan
                    Log.d(tag, "Учитавање потрошње отказано", e)
                } else {
                    Log.e(tag, "Грешка при учитавању потрошње", e)
                    _errorMessage.value = "Грешка при учитавању потрошње: ${e.message}"
                }
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Učitava prihode za budžete prihoda
     */
    private fun loadIncomeReceived() {
        viewModelScope.launch {
            try {
                val (startDate, endDate) = calculatePeriodDates(_selectedPeriod.value)
                
                // Učitavamo sve prihode za trenutni period
                val periodIncomesFlow = if (_selectedAccountId.value != null) {
                    incomeRepository.getIncomesForAccount(_selectedAccountId.value!!)
                } else {
                    incomeRepository.getAllIncomes()
                }
                
                periodIncomesFlow.collect { allIncomes: List<Income> ->
                    // Filtriramo prihode po periodu
                    val periodIncomes = allIncomes.filter { income: Income ->
                        val incomeDate = income.date.toDate().time
                        incomeDate in startDate.time..endDate.time
                    }
                    
                    // Računamo prihode za svaki budžet
                    val displayBudgets = _incomeBudgets.value.map { budget: Budget ->
                        // Filtriramo prihode relevantne za ovaj budžet
                        val relevantIncomes = periodIncomes.filter { income: Income ->
                            // Provera računa
                            val matchesAccount = budget.accountId.isEmpty() || 
                                                budget.accountId == income.accountId
                            
                            // Provera kategorije
                            val matchesCategory = budget.categoryIds.isEmpty() || 
                                                 budget.categoryIds.contains(income.category)
                            
                            matchesAccount && matchesCategory
                        }
                        
                        // Ukupan prihod za ovaj budžet
                        val received = relevantIncomes.sumOf { income: Income -> income.amount }
                        
                        // Kreiramo DisplayBudget objekat
                        DisplayBudget(
                            budget = budget,
                            spentAmount = received // U slučaju prihoda, ovo je primljeni iznos
                        )
                    }
                    
                    // Ažuriramo displayBudgets
                    _displayIncomeBudgets.value = displayBudgets
                    
                    // Ukupan prihod
                    _totalIncomeReceived.value = displayBudgets.sumOf { displayBudget: DisplayBudget -> displayBudget.spentAmount }
                    
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    // Preskačemo grešku ako je posao otkazan
                    Log.d(tag, "Учитавање прихода отказано", e)
                } else {
                    Log.e(tag, "Грешка при учитавању прихода", e)
                    _errorMessage.value = "Грешка при учитавању прихода: ${e.message}"
                }
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Postavlja period po indeksu
     */
    fun updatePeriodIndex(index: Int) {
        if (_selectedPeriodIndex.value != index) {
            _selectedPeriodIndex.value = index
            
            // Ažuriramo i podešavanja
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
            
            // Ažuriramo podešavanja i učitavamo budžete ponovo
            viewModelScope.launch {
                settingsViewModel.setPeriod(period)
                loadBudgets()
            }
        }
    }
    
    /**
     * Filtrira budžete po računu
     */
    fun filterByAccount(accountId: String?) {
        _selectedAccountId.value = accountId
        
        // Ponovo učitavamo podatke sa novim filterom
        loadBudgets()
    }
    
    /**
     * Vraća ime odabranog računa
     */
    fun getSelectedAccountName(): String {
        val accountId = _selectedAccountId.value ?: return "Сви рачуни"
        return _accounts.value.find { account -> account.id == accountId }?.name ?: "Непознат рачун"
    }
    
    /**
     * Formatira iznos sa valutom izabranog računa
     */
    fun formatAmount(amount: Double): String {
        // Uzimamo valutu iz izabranog računa ili podrazumevani "RSD" ako nema izabranog računa
        val currency = if (_selectedAccountId.value != null) {
            _accounts.value.find { it.id == _selectedAccountId.value }?.currency ?: "RSD"
        } else {
            // Ako nisu izabrani svi računi, koristimo podrazumevanu valutu 
            // ili prvu dostupnu valutu ako ima više računa sa različitim valutama
            val currencies = _accounts.value.mapNotNull { it.currency }.distinct()
            if (currencies.isEmpty()) "RSD" else currencies.first()
        }
        
        return "${amount.toInt()} $currency"
    }
    
    /**
     * Izračunava početni i krajnji datum za odabrani period
     */
    private fun calculatePeriodDates(period: Period): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        
        when (period) {
            Period.DAILY -> {
                // Danas (od ponoći do ponoći)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val startDate = calendar.time
                
                calendar.add(Calendar.DAY_OF_MONTH, 1)
                calendar.add(Calendar.SECOND, -1)
                val endDate = calendar.time
                
                return Pair(startDate, endDate)
            }
            
            Period.WEEKLY -> {
                // Trenutna nedelja
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val startDate = calendar.time
                
                calendar.add(Calendar.WEEK_OF_YEAR, 1)
                calendar.add(Calendar.SECOND, -1)
                val endDate = calendar.time
                
                return Pair(startDate, endDate)
            }
            
            Period.MONTHLY -> {
                // Trenutni mesec
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val startDate = calendar.time
                
                calendar.add(Calendar.MONTH, 1)
                calendar.add(Calendar.SECOND, -1)
                val endDate = calendar.time
                
                return Pair(startDate, endDate)
            }
            
            Period.YEARLY -> {
                // Trenutna godina
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val startDate = calendar.time
                
                calendar.add(Calendar.YEAR, 1)
                calendar.add(Calendar.SECOND, -1)
                val endDate = calendar.time
                
                return Pair(startDate, endDate)
            }
            
            Period.CUSTOM -> {
                // Koristimo custom period iz podešavanja
                val startDay = settingsViewModel.customPeriodStartDay.value
                
                calendar.set(Calendar.DAY_OF_MONTH, startDay)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                
                // Ako je današnji dan pre startDay-a, onda je period od prošlog meseca
                if (calendar.get(Calendar.DAY_OF_MONTH) > Calendar.getInstance().get(Calendar.DAY_OF_MONTH)) {
                    calendar.add(Calendar.MONTH, -1)
                }
                
                val startDate = calendar.time
                
                calendar.add(Calendar.MONTH, 1)
                calendar.add(Calendar.SECOND, -1)
                val endDate = calendar.time
                
                return Pair(startDate, endDate)
            }
            
            Period.ALL -> {
                // Svi vremenski periodi (koristimo vrlo stari datum i budući datum)
                calendar.set(1970, 0, 1, 0, 0, 0)
                val startDate = calendar.time
                
                calendar.set(2100, 11, 31, 23, 59, 59)
                val endDate = calendar.time
                
                return Pair(startDate, endDate)
            }
        }
    }
    
    /**
     * Factory za kreiranje BudgetsViewModel
     */
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BudgetsViewModel::class.java)) {
                val firestore = FirebaseFirestore.getInstance()
                val auth = FirebaseAuth.getInstance()
                
                // Dobijamo instance preko RepositoryManager-a
                val expenseRepo = RepositoryManager.getExpenseRepositoryForBudget()
                val incomeRepo = RepositoryManager.getIncomeRepositoryForBudget()
                val accountRepo = RepositoryManager.getAccountRepositoryForBudget(context)
                
                return BudgetsViewModel(
                    budgetRepository = BudgetRepository(firestore, auth),
                    expenseRepository = expenseRepo,
                    incomeRepository = incomeRepo,
                    accountRepository = accountRepo,
                    settingsViewModel = BudgetSettingsViewModel.Factory(context).create(BudgetSettingsViewModel::class.java)
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
} 