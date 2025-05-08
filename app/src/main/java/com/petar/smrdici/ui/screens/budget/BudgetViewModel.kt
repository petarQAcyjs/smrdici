package com.petar.smrdici.ui.screens.budget

import android.content.Context
import android.util.Log
import androidx.core.content.edit
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
import kotlinx.coroutines.sync.Mutex
import java.util.Calendar
import java.util.Date

@Suppress("UNUSED")
class BudgetViewModel(
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val settingsViewModel: BudgetSettingsViewModel,
    private val applicationContext: Context
) : ViewModel() {
    
    // UI стање - користи се у функцији updateUiState, али није јавно изложено
    private val _uiState = MutableStateFlow(BudgetUiState())
    
    @Suppress("UNUSED")
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()
    
    // Стање за период - користи се у рачунању периода
    private val _selectedPeriodIndex = MutableStateFlow(2) // Подразумевано месечно
    
    @Suppress("UNUSED")
    val selectedPeriodIndex: StateFlow<Int> = _selectedPeriodIndex.asStateFlow()
    
    // Стање за период као Period објекат - користи се у рачунању периода
    private val _selectedPeriod = MutableStateFlow(Period.MONTHLY)
    
    @Suppress("UNUSED")
    val selectedPeriod: StateFlow<Period> = _selectedPeriod.asStateFlow()
    
    // Nova promenljiva za offset perioda (koliko perioda unazad od današnjeg dana)
    private val _periodOffset = MutableStateFlow(0) // 0 znači tekući period
    
    @Suppress("UNUSED")
    val periodOffset: StateFlow<Int> = _periodOffset.asStateFlow()
    
    // Nova promenljiva za trenutno izabrani datum (za dnevni pregled)
    private val _selectedDate = MutableStateFlow(Calendar.getInstance().time)
    
    @Suppress("UNUSED")
    val selectedDate: StateFlow<Date> = _selectedDate.asStateFlow()
    
    // Expenses и Incomes - користе се у калкулацијама и ажурирању UI
    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    
    @Suppress("UNUSED")
    val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()
    
    private val _incomes = MutableStateFlow<List<Income>>(emptyList())
    
    @Suppress("UNUSED")
    val incomes: StateFlow<List<Income>> = _incomes.asStateFlow()
    
    // Додајемо ове променљиве у BudgetViewModel
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    
    @Suppress("UNUSED")
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    
    @Suppress("UNUSED")
    val isLoading = _isLoading.asStateFlow()
    
    // Додајемо променљиву за селектовани рачун - користи се у BudgetListScreen.kt
    private val _selectedAccountId = MutableStateFlow<String?>(null) // null значи "сви рачуни"
    
    @Suppress("UNUSED")
    val selectedAccountId: StateFlow<String?> = _selectedAccountId.asStateFlow()
    
    // Додајемо нове променљиве за управљање буџетским лимитом
    private val _budgetLimit = MutableStateFlow(0.0)
    
    @Suppress("UNUSED")
    val budgetLimit: StateFlow<Double> = _budgetLimit.asStateFlow()
    
    // Проценат искоришћености буџета - користи се на нивоу View-a
    private val _budgetUsagePercent = MutableStateFlow(0.0)
    
    @Suppress("UNUSED")
    val budgetUsagePercent: StateFlow<Double> = _budgetUsagePercent.asStateFlow()
    
    // Mutex za sinhronizaciju pristupa reloadTransactions metodi
    private val reloadTransactionsMutex = Mutex()
    
    // Praćenje poslednjeg vremena poziva za debounce
    private var lastReloadTransactionsCallTime = 0L
    
    // Minimalno vreme između uzastopnih poziva (debounce period u ms)
    private val DEBOUNCE_PERIOD_MS = 1000L
    
    // Add missing state for categories
    private val _expenseCategories = MutableStateFlow<List<String>>(emptyList())
    val expenseCategories: StateFlow<List<String>> = _expenseCategories.asStateFlow()

    private val _incomeCategories = MutableStateFlow<List<String>>(emptyList())
    val incomeCategories: StateFlow<List<String>> = _incomeCategories.asStateFlow()
    
    init {
        // Додајемо log за početak inicijalizacije
        Log.d("BudgetViewModel", "===== INICIJALIZACIJA BUDGET VIEW MODELA =====")
        
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
                Log.d("BudgetViewModel", "Pozivam loadTransactions iz init bloka")
                loadTransactions()
                
                // Учитавамо буџетски лимит за тренутни период
                loadBudgetLimit()
            }
        }
        
        // Учитавамо рачуне
        loadAccounts()
        
        // Add initialization of categories
        _expenseCategories.value = listOf("Food", "Transport", "Bills", "Entertainment", "Shopping", "Other")
        _incomeCategories.value = listOf("Salary", "Bonus", "Investment", "Gift", "Other")
    }
    
    @Suppress("UNUSED")
    fun updatePeriodIndex(index: Int) {
        if (_selectedPeriodIndex.value != index) {
            // Resetujemo offset kada menjamo tip perioda
            _periodOffset.value = 0
            _selectedDate.value = Calendar.getInstance().time
            
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
            
            // Ažuriramo podešavanja
            viewModelScope.launch {
                settingsViewModel.setPeriod(period)
                // Poново учитај податке са новим периодом
                loadTransactions()
            }
        }
    }
    
    private fun loadTransactions() {
        viewModelScope.launch {
            Log.d("BudgetViewModel", "===== POČETAK UČITAVANJA TRANSAKCIJA - INTERNO =====")
            
            // Postavljamo isLoading na true na početku
            _isLoading.value = true
            
            try {
                // Koristimo supervisorScope da dozvolimo da jedna operacija može da ne uspe bez uticaja na drugu
                kotlinx.coroutines.supervisorScope {
                    // Paralelno pokrenemo učitavanje troškova i prihoda
                    val expensesJob = launch { loadExpensesInternal() }
                    val incomesJob = launch { loadIncomesInternal() }
                    
                    // Čekamo da se oba završe
                    expensesJob.join()
                    incomesJob.join()
                    
                    // Nakon što su oba završena, ažuriramo UI stanje - samo jednom
                    updateUiState()
                    
                    // Izračunavamo procenat iskorišćenosti budžeta nakon što su svi podaci učitani
                    calculateBudgetUsage()
                }
            } catch (e: Exception) {
                Log.e("BudgetViewModel", "Greška pri učitavanju transakcija", e)
                _uiState.value = _uiState.value.copy(error = "Greška pri učitavanju transakcija: ${e.message}")
            } finally {
                _isLoading.value = false
                Log.d("BudgetViewModel", "===== ZAVRŠENO UČITAVANJE TRANSAKCIJA =====")
            }
        }
    }
    
    // Јавна метода за експлицитно учитавање трансакција
    @Suppress("UNUSED")
    fun reloadTransactions() {
        viewModelScope.launch {
            // Provera debounce perioda - propuštamo poziv samo ako je prošlo dovoljno vremena
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastReloadTransactionsCallTime < DEBOUNCE_PERIOD_MS) {
                Log.w("BudgetViewModel", "Debounce: Ignorišem poziv reloadTransactions() jer je pozvan pre manje od ${DEBOUNCE_PERIOD_MS}ms")
                return@launch
            }
            
            // Dodatna provera da li je već u toku učitavanje
            if (_isLoading.value) {
                Log.w("BudgetViewModel", "Učitavanje transakcija je već u toku, preskačem novi poziv")
                return@launch
            }
            
            // Koristimo mutex za sinhronizaciju pristupa - samo jedan poziv može ući u kritičnu sekciju
            if (!reloadTransactionsMutex.tryLock()) {
                Log.w("BudgetViewModel", "Mutex zaključan: Drugi reloadTransactions() poziv je već u toku, preskačem")
                return@launch
            }
            
            try {
                lastReloadTransactionsCallTime = currentTime
                Log.d("BudgetViewModel", "===== POČETAK OSVEŽAVANJA TRANSAKCIJA - JAVNA METODA =====")
                _isLoading.value = true
                
                // Definišemo timeout za operaciju (20 sekundi umesto 10)
                val timeoutMs = 20000L
                val startTime = System.currentTimeMillis()
                
                try {
                    // Koristimo withTimeout da izbegnemo zaglavljivanje operacije
                    kotlinx.coroutines.withTimeout(timeoutMs) {
                        // Koristimo NonCancellable za najvažniji deo
                        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                            // Pauziramo pre učitavanja da bismo bili sigurni da je UI spreman
                            kotlinx.coroutines.delay(100)
                            
                            // Učitavamo transakcije (ovo će interno postaviti i resetovati isLoading)
                            loadTransactions()
                            
                            // Učitavamo budžetski limit
                            loadBudgetLimit()
                            
                            // Učitavamo račune
                            loadAccounts()
                            
                            // Kratka pauza da osiguramo da su svi async pozivi imali vremena da završe
                            kotlinx.coroutines.delay(300)
                            
                            // Eksplicitno ažuriramo UI stanje na kraju
                            updateUiState()
                        }
                        
                        Log.d("BudgetViewModel", "Osvežavanje transakcija završeno!")
                        Log.d("BudgetViewModel", "STANJE NAKON OSVEŽAVANJA: expenses=${_expenses.value.size}, incomes=${_incomes.value.size}")
                        Log.d("BudgetViewModel", "STANJE UISTATE NAKON OSVEŽAVANJA: expenses=${_uiState.value.expenses.size}, incomes=${_uiState.value.incomes.size}")
                    }
                } catch (e: Exception) {
                    val elapsedMs = System.currentTimeMillis() - startTime
                    if (e is kotlinx.coroutines.TimeoutCancellationException) {
                        Log.e("BudgetViewModel", "TIMEOUT: Osvežavanje transakcija prekoračilo vremensko ograničenje (${elapsedMs}ms > ${timeoutMs}ms)", e)
                        _uiState.value = _uiState.value.copy(error = "Osvežavanje transakcija je predugo trajalo i automatski je prekinuto.")
                    } else {
                        Log.e("BudgetViewModel", "GREŠKA: Osvežavanje transakcija nije uspelo (${elapsedMs}ms)", e)
                        _uiState.value = _uiState.value.copy(error = "Greška prilikom osvežavanja transakcija: ${e.message}")
                    }
                } finally {
                    _isLoading.value = false
                    Log.d("BudgetViewModel", "===== ZAVRŠENO OSVEŽAVANJE TRANSAKCIJA =====")
                }
            } finally {
                // Uvek otključavamo mutex, čak i ako je došlo do greške
                reloadTransactionsMutex.unlock()
            }
        }
    }
    
    // Nova interna metoda koja učitava troškove bez postavljanja isLoading i bez pozivanja updateUiState
    private suspend fun loadExpensesInternal() {
        // Dodajem log poruku na početku učitavanja
        Log.d("BudgetViewModel", "===== POČETAK UČITAVANJA RASHODA (INTERNO) =====")
        
        // Користимо нове методе за учитавање расхода за одређени период
        val currentPeriod = _selectedPeriod.value
        val accountId = _selectedAccountId.value
        
        Log.d("BudgetViewModel", "Parametri za učitavanje rashoda: period=$currentPeriod, accountId=$accountId")
        
        try {
            if (currentPeriod == Period.ALL) {
                // За све периоде користимо getAllExpenses
                if (accountId != null) {
                    // Филтрирамо по рачуну
                    try {
                        Log.d("BudgetViewModel", "Učitavam SVE rashode i filtriram po računu: $accountId")
                        expenseRepository.getAllExpenses().collect { allExpenses ->
                            val filteredExpenses = allExpenses.filter { expense ->
                                expense.accountId == accountId
                            }
                            Log.d("BudgetViewModel", "Ukupno učitano ${allExpenses.size} rashoda, nakon filtriranja: ${filteredExpenses.size}")
                            // Dodajemo log za datume
                            if (filteredExpenses.isNotEmpty()) {
                                val dateFormat = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
                                val dates = filteredExpenses.take(5).map { 
                                    dateFormat.format(it.getDateObject() ?: Date()) 
                                }
                                Log.d("BudgetViewModel", "Primeri datuma prvih 5 rashoda: $dates")
                            }
                            _expenses.value = filteredExpenses
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
                        Log.d("BudgetViewModel", "Učitavam SVE rashode za sve račune")
                        expenseRepository.getAllExpenses().collect { expenses ->
                            Log.d("BudgetViewModel", "Ukupno učitano ${expenses.size} rashoda za sve račune")
                            // Dodajemo log za datume
                            if (expenses.isNotEmpty()) {
                                val dateFormat = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
                                val dates = expenses.take(5).map { 
                                    dateFormat.format(it.getDateObject() ?: Date()) 
                                }
                                Log.d("BudgetViewModel", "Primeri datuma prvih 5 rashoda: $dates")
                            }
                            _expenses.value = expenses
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
                // За остаle периоде користимо getExpensesForPeriod
                val (startDate, endDate) = calculatePeriodDates(currentPeriod)
                val dateFormat = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
                
                Log.d("BudgetViewModel", "Учитавам расходе за период од ${dateFormat.format(startDate)} до ${dateFormat.format(endDate)}")
                
                if (accountId != null) {
                    // Филтрирамо по рачуну и периоду - пошто нема готове методе, сами филтрирамо
                    try {
                        Log.d("BudgetViewModel", "Učitavam rashode za račun $accountId i filtriram po periodu")
                        expenseRepository.getExpensesForAccount(accountId).collect { allExpensesForAccount ->
                            Log.d("BudgetViewModel", "Učitano ${allExpensesForAccount.size} rashoda za račun pre filtriranja po datumu")
                            val filteredExpenses = allExpensesForAccount.filter { expense ->
                                val expenseDate = expense.getDateObject()
                                expenseDate?.time ?: 0L >= startDate?.time ?: 0L && expenseDate?.time ?: 0L <= endDate?.time ?: 0L
                            }
                            Log.d("BudgetViewModel", "Nakon filtriranja po datumu: ${filteredExpenses.size} rashoda")
                            // Dodajemo log za datume
                            if (filteredExpenses.isNotEmpty()) {
                                val dates = filteredExpenses.take(5).map { 
                                    dateFormat.format(it.getDateObject() ?: Date()) 
                                }
                                Log.d("BudgetViewModel", "Primeri datuma prvih 5 rashoda: $dates")
                            }
                            _expenses.value = filteredExpenses
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
                        Log.d("BudgetViewModel", "Učitavam rashode za SVE račune za određeni period")
                        expenseRepository.getExpensesForPeriod(startDate, endDate).collect { expenses ->
                            Log.d("BudgetViewModel", "Ukupno učitano ${expenses.size} rashoda za period")
                            // Dodajemo log za datume
                            if (expenses.isNotEmpty()) {
                                val dates = expenses.take(5).map { 
                                    dateFormat.format(it.getDateObject() ?: Date()) 
                                }
                                Log.d("BudgetViewModel", "Primeri datuma prvih 5 rashoda: $dates")
                            }
                            _expenses.value = expenses
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
        }
        
        Log.d("BudgetViewModel", "===== ZAVRŠENO UČITAVANJE RASHODA (INTERNO) =====")
    }
    
    // Nova interna metoda koja učitava prihode bez postavljanja isLoading i bez pozivanja updateUiState
    private suspend fun loadIncomesInternal() {
        // Dodajem log poruku na početku učitavanja
        Log.d("BudgetViewModel", "===== POČETAK UČITAVANJA PRIHODA (INTERNO) =====")
        
        // Користимо нове методе за учитавање прихода за одређени период
        val currentPeriod = _selectedPeriod.value
        val accountId = _selectedAccountId.value
        
        Log.d("BudgetViewModel", "Parametri za učitavanje prihoda: period=$currentPeriod, accountId=$accountId")
        
        try {
            if (currentPeriod == Period.ALL) {
                // За све периоде користимо getAllIncomes
                if (accountId != null) {
                    // Филтрирамо по рачуну
                    try {
                        Log.d("BudgetViewModel", "Učitavam SVE prihode i filtriram po računu: $accountId")
                        incomeRepository.getAllIncomes().collect { allIncomes ->
                            val filteredIncomes = allIncomes.filter { income ->
                                income.accountId == accountId
                            }
                            Log.d("BudgetViewModel", "Ukupno učitano ${allIncomes.size} prihoda, nakon filtriranja: ${filteredIncomes.size}")
                            // Dodajemo log za datume
                            if (filteredIncomes.isNotEmpty()) {
                                val dateFormat = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
                                val dates = filteredIncomes.take(5).map { 
                                    dateFormat.format(it.getDateObject() ?: Date()) 
                                }
                                Log.d("BudgetViewModel", "Primeri datuma prvih 5 prihoda: $dates")
                            }
                            _incomes.value = filteredIncomes
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
                        Log.d("BudgetViewModel", "Učitavam SVE prihode za sve račune")
                        incomeRepository.getAllIncomes().collect { incomes ->
                            Log.d("BudgetViewModel", "Ukupno učitano ${incomes.size} prihoda za sve račune")
                            // Dodajemo log za datume
                            if (incomes.isNotEmpty()) {
                                val dateFormat = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
                                val dates = incomes.take(5).map { 
                                    dateFormat.format(it.getDateObject() ?: Date()) 
                                }
                                Log.d("BudgetViewModel", "Primeri datuma prvih 5 prihoda: $dates")
                            }
                            _incomes.value = incomes
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
                // За остаle периоде користимо getIncomesForPeriod
                val (startDate, endDate) = calculatePeriodDates(currentPeriod)
                
                Log.d("BudgetViewModel", "Учитавам приходе за период од $startDate до $endDate")
                
                if (accountId != null) {
                    // Филтрирамо по рачуну и периоду - пошто нема готове методе, сами филтрирамо
                    try {
                        Log.d("BudgetViewModel", "Učitavam prihode za račun $accountId i filtriram po periodu")
                        incomeRepository.getAllIncomes().collect { allIncomes ->
                            Log.d("BudgetViewModel", "Učitano ${allIncomes.size} prihoda za račun pre filtriranja po datumu")
                            val filteredIncomes = allIncomes.filter { income ->
                                val incomeDate = income.getDateObject()
                                incomeDate?.time ?: 0L >= startDate?.time ?: 0L && incomeDate?.time ?: 0L <= endDate?.time ?: 0L &&
                                income.accountId == accountId
                            }
                            Log.d("BudgetViewModel", "Nakon filtriranja po datumu: ${filteredIncomes.size} prihoda")
                            // Dodajemo log za datume
                            if (filteredIncomes.isNotEmpty()) {
                                val dateFormat = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
                                val dates = filteredIncomes.take(5).map { 
                                    dateFormat.format(it.getDateObject() ?: Date()) 
                                }
                                Log.d("BudgetViewModel", "Primeri datuma prvih 5 prihoda: $dates")
                            }
                            _incomes.value = filteredIncomes
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
                        Log.d("BudgetViewModel", "Učitavam prihode za SVE račune za određeni period")
                        incomeRepository.getIncomesForPeriod(startDate, endDate).collect { incomes ->
                            Log.d("BudgetViewModel", "Ukupno učitano ${incomes.size} prihoda za period")
                            // Dodajemo log za datume
                            if (incomes.isNotEmpty()) {
                                val dateFormat = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
                                val dates = incomes.take(5).map { 
                                    dateFormat.format(it.getDateObject() ?: Date()) 
                                }
                                Log.d("BudgetViewModel", "Primeri datuma prvih 5 prihoda: $dates")
                            }
                            _incomes.value = incomes
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
        }
        
        Log.d("BudgetViewModel", "===== ZAVRŠENO UČITAVANJE PRIHODA (INTERNO) =====")
    }
    
    // Postojeće metode loadExpenses i loadIncomes sada pozivaju interne metode i postavljaju isLoading
    private fun loadExpenses() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                loadExpensesInternal()
                calculateBudgetUsage()
                updateUiState()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    private fun loadIncomes() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                loadIncomesInternal()
                updateUiState()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Računa procenat iskorišćenosti budžeta
     */
    private fun calculateBudgetUsage() {
        val currentBudgetLimit = _budgetLimit.value
        val currentExpenses = _expenses.value.sumOf { it.amount }
        
        if (currentBudgetLimit > 0) {
            val usagePercent = (currentExpenses / currentBudgetLimit).coerceIn(0.0, 1.0)
            _budgetUsagePercent.value = usagePercent
            Log.d("BudgetViewModel", "Izračunat procenat korišćenja budžeta: ${usagePercent * 100}%")
        } else {
            _budgetUsagePercent.value = 0.0
            Log.d("BudgetViewModel", "Budžetski limit nije podešen, procenat korišćenja postavljen na 0%")
        }
    }
    
    /**
     * Ažurira UI stanje sa najnovijim vrednostima
     */
    private fun updateUiState() {
        Log.d("BudgetViewModel", "===== AŽURIRANJE UI STANJA =====")
        Log.d("BudgetViewModel", "Podaci za UI stanje: expenses=${_expenses.value.size}, incomes=${_incomes.value.size}, accounts=${_accounts.value.size}")
        
        viewModelScope.launch {
            try {
                // Pravimo kopiju trenutnih vrednosti da izbegnemo potencijalnu promenu tokom ažuriranja
                val currentExpenses = _expenses.value
                val currentIncomes = _incomes.value
                val currentAccounts = _accounts.value
                
                // Logujemo više detalja o podacima
                if (currentExpenses.isNotEmpty()) {
                    Log.d("BudgetViewModel", "Uzorci rashoda: ${currentExpenses.take(3).joinToString { "ID: ${it.id}, iznos: ${it.amount}" }}")
                } else {
                    Log.d("BudgetViewModel", "UPOZORENJE: Lista rashoda je prazna pri ažuriranju UI stanja!")
                }
                
                if (currentIncomes.isNotEmpty()) {
                    Log.d("BudgetViewModel", "Uzorci prihoda: ${currentIncomes.take(3).joinToString { "ID: ${it.id}, iznos: ${it.amount}" }}")
                } else {
                    Log.d("BudgetViewModel", "UPOZORENJE: Lista prihoda je prazna pri ažuriranju UI stanja!")
                }
                
                // Ažuriramo UI stanje sa kopiranim vrednostima
                _uiState.value = BudgetUiState(
                    expenses = currentExpenses,
                    incomes = currentIncomes,
                    accounts = currentAccounts,
                    isLoading = _isLoading.value,
                    error = null
                )
                
                Log.d("BudgetViewModel", "UI stanje ažurirano sa ${_uiState.value.expenses.size} rashoda i ${_uiState.value.incomes.size} prihoda")
                Log.d("BudgetViewModel", "Verifikacija da li su ažuriranja primenjena: _expenses=${_expenses.value.size}, _uiState.expenses=${_uiState.value.expenses.size}")
            } catch (e: Exception) {
                Log.e("BudgetViewModel", "Greška pri ažuriranju UI stanja", e)
            }
        }
    }
    
    // Metoda za odabir računa - koristi se u BudgetListScreen.kt
    @Suppress("UNUSED")
    fun selectAccount(accountId: String?) {
        if (_selectedAccountId.value != accountId) {
            _selectedAccountId.value = accountId
            
            // Поново учитавамо податке за нови рачун
            loadTransactions()
            
            // Учитавамо буџетски лимит за нови рачун
            loadBudgetLimit()
        }
    }
    
    // Metoda koja proverava da li je neki račun već selektovan
    @Suppress("UNUSED")
    fun isAccountSelected(): Boolean {
        return _selectedAccountId.value != null
    }
    
    // Dodajemo metodu za brisanje troška
    @Suppress("UNUSED")
    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            try {
                Log.d("BudgetViewModel", "Brisanje troška: $expenseId")
                
                // Prvo treba da dobavimo trošak da bismo znali iznos i račun
                val expense = _expenses.value.find { it.id == expenseId }
                if (expense != null) {
                    val result = expenseRepository.deleteExpense(expenseId)
                    
                    // Koristimo NonCancellable kontekst da sprečimo otkazivanje tokom navigacije
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        if (result.isSuccess) {
                            // Kada se trošak briše, balansu računa treba dodati iznos (jer smo ga pre oduzeli)
                            val accountRepo = com.petar.smrdici.data.repository.AccountRepository.getInstance()
                            val updateResult = accountRepo.updateAccountBalance(expense.accountId, expense.amount)
                            
                            if (updateResult.isSuccess) {
                                Log.d("BudgetViewModel", "Balans računa ažuriran nakon brisanja troška")
                            } else {
                                Log.e("BudgetViewModel", "Greška pri ažuriranju balansa računa", updateResult.exceptionOrNull())
                            }
                            
                            // Ponovno učitaj transakcije nakon brisanja
                            Log.d("BudgetViewModel", "Trošak uspešno obrisan, učitavam transakcije")
                            // Kratko sačekamo da Firebase ažurira podatke
                            kotlinx.coroutines.delay(500)
                            loadTransactions()
                            // Eksplicitno učitavamo i račune
                            loadAccounts()
                        } else {
                            Log.e("BudgetViewModel", "Greška pri brisanju troška", result.exceptionOrNull())
                            _uiState.value = _uiState.value.copy(error = "Greška pri brisanju troška: ${result.exceptionOrNull()?.message}")
                        }
                    }
                } else {
                    Log.e("BudgetViewModel", "Trošak sa ID $expenseId nije pronađen")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    // Ignorišemo greške otkazivanja korutine
                    Log.d("BudgetViewModel", "Korutina za brisanje troška je otkazana")
                } else {
                    Log.e("BudgetViewModel", "Greška pri brisanju troška", e)
                    _uiState.value = _uiState.value.copy(error = "Greška pri brisanju troška: ${e.message}")
                }
            }
        }
    }
    
    // Dodajemo metodu za brisanje prihoda
    @Suppress("UNUSED")
    fun deleteIncome(incomeId: String) {
        viewModelScope.launch {
            try {
                Log.d("BudgetViewModel", "Brisanje prihoda: $incomeId")
                
                // Prvo treba da dobavimo prihod da bismo znali iznos i račun
                val income = _incomes.value.find { it.id == incomeId }
                if (income != null) {
                    val result = incomeRepository.deleteIncome(incomeId)
                    
                    // Koristimo NonCancellable kontekst da sprečimo otkazivanje tokom navigacije
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        if (result.isSuccess) {
                            // Kada se prihod briše, od balansa računa treba oduzeti iznos (jer smo ga pre dodali)
                            val accountRepo = com.petar.smrdici.data.repository.AccountRepository.getInstance()
                            val updateResult = accountRepo.updateAccountBalance(income.accountId, -income.amount)
                            
                            if (updateResult.isSuccess) {
                                Log.d("BudgetViewModel", "Balans računa ažuriran nakon brisanja prihoda")
                            } else {
                                Log.e("BudgetViewModel", "Greška pri ažuriranju balansa računa", updateResult.exceptionOrNull())
                            }
                            
                            // Ponovno učitaj transakcije nakon brisanja
                            Log.d("BudgetViewModel", "Prihod uspešno obrisan, učitavam transakcije")
                            // Kratko sačekamo da Firebase ažurira podatke
                            kotlinx.coroutines.delay(500)
                            loadTransactions()
                            // Eksplicitno učitavamo i račune
                            loadAccounts()
                        } else {
                            Log.e("BudgetViewModel", "Greška pri brisanju prihoda", result.exceptionOrNull())
                            _uiState.value = _uiState.value.copy(error = "Greška pri brisanju prihoda: ${result.exceptionOrNull()?.message}")
                        }
                    }
                } else {
                    Log.e("BudgetViewModel", "Prihod sa ID $incomeId nije pronađen")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    // Ignorišemo greške otkazivanja korutine
                    Log.d("BudgetViewModel", "Korutina za brisanje prihoda je otkazana")
                } else {
                    Log.e("BudgetViewModel", "Greška pri brisanju prihoda", e)
                    _uiState.value = _uiState.value.copy(error = "Greška pri brisanju prihoda: ${e.message}")
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
                    val accountRepo = com.petar.smrdici.data.repository.AccountRepository.getInstance()
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
                    val accountRepo = com.petar.smrdici.data.repository.AccountRepository.getInstance()
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
    @Suppress("UNUSED")
    fun saveBudgetLimit(limit: Double) {
        viewModelScope.launch {
            try {
                // Користимо SharedPreferences за чување буџетског лимита
                val sharedPrefs = applicationContext.getSharedPreferences("budget_settings", Context.MODE_PRIVATE)
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
                
                // Чувамо буџетски лимит у SharedPreferences користећи KTX екстензију
                sharedPrefs.edit {
                    putFloat(key, limit.toFloat())
                }
                
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
                val sharedPrefs = applicationContext.getSharedPreferences("budget_settings", Context.MODE_PRIVATE)
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
    
    /**
     * Vraća tekst za prikaz trenutnog perioda
     */
    fun getPeriodDisplayText(): String {
        val dateFormat = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
        val (startDate, endDate) = calculatePeriodDates(_selectedPeriod.value)
        
        return when (_selectedPeriod.value) {
            Period.DAILY -> dateFormat.format(_selectedDate.value)
            Period.WEEKLY -> "Nedelja ${dateFormat.format(startDate)} - ${dateFormat.format(endDate)}"
            Period.MONTHLY -> "Mesec ${dateFormat.format(startDate)} - ${dateFormat.format(endDate)}"
            Period.YEARLY -> "Godina ${dateFormat.format(startDate)} - ${dateFormat.format(endDate)}"
            Period.CUSTOM -> "Period ${dateFormat.format(startDate)} - ${dateFormat.format(endDate)}"
            Period.ALL -> "Svi periodi"
        }
    }
    
    // Метода за проверу да ли је буџет прекорачен
    @Suppress("UNUSED")
    fun isBudgetExceeded(): Boolean {
        val limit = _budgetLimit.value
        if (limit <= 0) return false
        
        val totalExpense = _expenses.value.sumOf { it.amount }
        return totalExpense > limit
    }
    
    // Метода за форматирање износа новца
    @Suppress("UNUSED")
    fun formatAmount(amount: Double): String {
        return String.format(java.util.Locale.getDefault(), "%,.2f", amount)
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
                return BudgetViewModel(
                    expenseRepository, 
                    incomeRepository, 
                    settingsViewModel, 
                    context.applicationContext
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
    
    /**
     * Pomera period unazad (na prethodni dan, nedelju, mesec ili godinu)
     */
    @Suppress("UNUSED")
    fun movePeriodBackward() {
        _periodOffset.value = _periodOffset.value + 1
        
        // Ažuriramo selectedDate za dnevni pregled
        val calendar = Calendar.getInstance()
        calendar.time = _selectedDate.value
        
        when (_selectedPeriod.value) {
            Period.DAILY -> {
                calendar.add(Calendar.DAY_OF_YEAR, -1)
            }
            Period.WEEKLY -> {
                calendar.add(Calendar.WEEK_OF_YEAR, -1)
            }
            Period.MONTHLY -> {
                calendar.add(Calendar.MONTH, -1)
            }
            Period.YEARLY -> {
                calendar.add(Calendar.YEAR, -1)
            }
            Period.CUSTOM -> {
                // Za custom period, pomeramo se za mesec unazad
                calendar.add(Calendar.MONTH, -1)
            }
            else -> {} // Za ALL ne radimo ništa
        }
        
        _selectedDate.value = calendar.time
        
        // Učitavamo transakcije za novi period
        loadTransactions()
    }
    
    /**
     * Pomera period unapred (na sledeći dan, nedelju, mesec ili godinu)
     * ali nikad preko trenutnog perioda
     */
    @Suppress("UNUSED")
    fun movePeriodForward() {
        if (_periodOffset.value > 0) {
            _periodOffset.value = _periodOffset.value - 1
            
            // Ažuriramo selectedDate za dnevni pregled
            val calendar = Calendar.getInstance()
            calendar.time = _selectedDate.value
            
            when (_selectedPeriod.value) {
                Period.DAILY -> {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
                Period.WEEKLY -> {
                    calendar.add(Calendar.WEEK_OF_YEAR, 1)
                }
                Period.MONTHLY -> {
                    calendar.add(Calendar.MONTH, 1)
                }
                Period.YEARLY -> {
                    calendar.add(Calendar.YEAR, 1)
                }
                Period.CUSTOM -> {
                    // Za custom period, pomeramo se za mesec unapred
                    calendar.add(Calendar.MONTH, 1)
                }
                else -> {} // Za ALL ne radimo ništa
            }
            
            _selectedDate.value = calendar.time
            
            // Učitavamo transakcije za novi period
            loadTransactions()
        }
    }
    
    /**
     * Postavlja proizvoljan datum za dnevni pregled
     */
    @Suppress("UNUSED")
    fun setSelectedDate(date: Date) {
        // Računamo offset na osnovu datuma
        val calendar = Calendar.getInstance()
        val today = calendar.time
        
        val selectedCalendar = Calendar.getInstance()
        selectedCalendar.time = date
        
        // Postavljamo vreme na ponoć
        selectedCalendar.set(Calendar.HOUR_OF_DAY, 0)
        selectedCalendar.set(Calendar.MINUTE, 0)
        selectedCalendar.set(Calendar.SECOND, 0)
        selectedCalendar.set(Calendar.MILLISECOND, 0)
        
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        // Računamo razliku u danima
        val diffInMillis = calendar.timeInMillis - selectedCalendar.timeInMillis
        val diffInDays = (diffInMillis / (1000 * 60 * 60 * 24)).toInt()
        
        _selectedDate.value = selectedCalendar.time
        _periodOffset.value = diffInDays
        
        // Ako još nismo u dnevnom režimu, postavljamo ga
        if (_selectedPeriod.value != Period.DAILY) {
            _selectedPeriod.value = Period.DAILY
            _selectedPeriodIndex.value = 0
            
            // Ažuriramo i podešavanja
            viewModelScope.launch {
                settingsViewModel.setPeriod(Period.DAILY)
            }
        }
        
        // Učitavamo transakcije za novi period
        loadTransactions()
    }
    
    /**
     * Vraća se na trenutni period (današnji dan, tekuću nedelju, mesec, godinu)
     */
    @Suppress("UNUSED")
    fun resetToCurrentPeriod() {
        _periodOffset.value = 0
        _selectedDate.value = Calendar.getInstance().time
        
        // Učitavamo transakcije za novi period
        loadTransactions()
    }
    
    // Funkcija za dobijanje date-a u odabranom periodu
    private fun calculatePeriodDates(period: Period): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        
        // Primenjujemo offset na kalendar pre računanja datuma
        when (period) {
            Period.DAILY -> calendar.add(Calendar.DAY_OF_YEAR, -_periodOffset.value)
            Period.WEEKLY -> calendar.add(Calendar.WEEK_OF_YEAR, -_periodOffset.value)
            Period.MONTHLY -> calendar.add(Calendar.MONTH, -_periodOffset.value)
            Period.YEARLY -> calendar.add(Calendar.YEAR, -_periodOffset.value)
            Period.CUSTOM -> calendar.add(Calendar.MONTH, -_periodOffset.value) // Dodajemo offset i za CUSTOM period
            else -> {} // Za ALL ne primenjujemo offset
        }
        
        // Ako je period DAILY i imamo odabrani datum, koristimo taj datum
        if (period == Period.DAILY && _periodOffset.value > 0) {
            calendar.time = _selectedDate.value
        }
        
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
                    
                    if (currentDay < customStartDay) {
                        // Ako je trenutni dan manji od dana početka perioda,
                        // period počinje prethodnog meseca
                        add(Calendar.MONTH, -1)
                    }
                    // Postavljamo dan početka perioda
                    set(Calendar.DAY_OF_MONTH, customStartDay)
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
        
        // Za Period.DAILY postavljamo endDate na kraj dana
        if (period == Period.DAILY) {
            calendar.time = startDate
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            return Pair(startDate, calendar.time)
        }
        
        // Za Period.WEEKLY postavljamo endDate na kraj nedelje
        if (period == Period.WEEKLY) {
            calendar.time = startDate
            calendar.add(Calendar.DAY_OF_WEEK, 6) // Dodaj 6 dana da dobiješ kraj nedelje
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            return Pair(startDate, calendar.time)
        }
        
        // Za Period.MONTHLY postavljamo endDate na kraj meseca
        if (period == Period.MONTHLY) {
            calendar.time = startDate
            calendar.add(Calendar.MONTH, 1)
            calendar.add(Calendar.DAY_OF_MONTH, -1)
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            return Pair(startDate, calendar.time)
        }
        
        // Za Period.YEARLY postavljamo endDate na kraj godine
        if (period == Period.YEARLY) {
            calendar.time = startDate
            calendar.add(Calendar.YEAR, 1)
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            return Pair(startDate, calendar.time)
        }
        
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

    fun updateExpense(expense: Expense) {
        viewModelScope.launch {
            try {
                expenseRepository.updateExpense(expense)
                loadTransactions()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to update expense")
            }
        }
    }

    fun updateIncome(income: Income) {
        viewModelScope.launch {
            try {
                incomeRepository.updateIncome(income)
                loadTransactions()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to update income")
            }
        }
    }
} 