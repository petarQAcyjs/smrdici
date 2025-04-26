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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay

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
    
    // Dodajemo budžetski limit
    private val _budgetLimit = MutableStateFlow(0.0)
    val budgetLimit: StateFlow<Double> = _budgetLimit.asStateFlow()
    
    // Proširujemo isRefreshing stanje za pull-to-refresh
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    
    // Mutex za sinhronizaciju pristupa loadBudgets metodi
    private val loadBudgetsMutex = Mutex()
    
    // Praćenje poslednjeg vremena poziva za debounce
    private var lastLoadBudgetsCallTime = 0L
    
    // Minimalno vreme između uzastopnih poziva (debounce period u ms)
    private val DEBOUNCE_PERIOD_MS = 1000L
    
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
    @Suppress("UNUSED")
    fun loadBudgets() {
        viewModelScope.launch {
            // Provera debounce perioda - propuštamo poziv samo ako je prošlo dovoljno vremena
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastLoadBudgetsCallTime < DEBOUNCE_PERIOD_MS) {
                Log.w(tag, "Debounce: Ignorišem poziv loadBudgets() jer je pozvan pre manje od ${DEBOUNCE_PERIOD_MS}ms")
                return@launch
            }
            
            // Dodatna provera da li je već u toku učitavanje
            if (_isLoading.value) {
                Log.w(tag, "Učitavanje budžeta je već u toku, preskačem novi poziv")
                return@launch
            }
            
            // Koristimo mutex za sinhronizaciju pristupa - samo jedan poziv može ući u kritičnu sekciju
            if (!loadBudgetsMutex.tryLock()) {
                Log.w(tag, "Mutex zaključan: Drugi loadBudgets() poziv je već u toku, preskačem")
                return@launch
            }
            
            try {
                lastLoadBudgetsCallTime = currentTime
                Log.d(tag, "===== POČETAK UČITAVANJA BUDŽETA =====")
                _isLoading.value = true
                
                // Definišemo timeout za operaciju (30 sekundi kao pre)
                val timeoutMs = 30000L
                val startTime = System.currentTimeMillis()
                
                try {
                    // Kreiranje job-a koji će biti otkazan kada se podaci učitaju
                    val timeoutJob = viewModelScope.launch {
                        try {
                            delay(timeoutMs)
                            val elapsedMs = System.currentTimeMillis() - startTime
                            Log.e(tag, "TIMEOUT: Učitavanje budžeta prekoračilo vremensko ograničenje (${elapsedMs}ms > ${timeoutMs}ms)")
                            
                            // Ne prikazujemo poruku korisniku ako su podaci već učitani
                            if (_allBudgets.value.isEmpty()) {
                                _errorMessage.value = "Učitavanje budžeta je predugo trajalo i automatski je prekinuto."
                            } else {
                                Log.w(tag, "Budžeti su već učitani, ignorišem timeout")
                            }
                            
                            // Garantujemo da se isLoading uvek resetuje, čak i ako je došlo do timeout-a
                            _isLoading.value = false
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) {
                                Log.d(tag, "Timeout job je otkazan jer su podaci uspešno učitani")
                            } else {
                                Log.e(tag, "Greška u timeout job-u", e)
                            }
                        }
                    }
                    
                    // Dobavljamo budžete - ovo će biti otkazano ako timeoutJob izvrši delay
                    budgetRepository.getAllBudgets().collect { budgetsList ->
                        val elapsedMs = System.currentTimeMillis() - startTime
                        Log.d(tag, "Učitano ${budgetsList.size} budžeta za ${elapsedMs}ms")
                        
                        // Otkazujemo timeout job jer su podaci uspešno učitani
                        if (timeoutJob.isActive) {
                            Log.d(tag, "Otkazujem timeout job jer su podaci uspešno učitani")
                            timeoutJob.cancel()
                        }
                        
                        // Odmah nakon dobijanja podataka, resetujemo isLoading
                        // Ne čekamo kalkulaciju potrošnje jer ona može raditi u pozadini
                        _isLoading.value = false
                        
                        // Odvajamo filtriranje budžeta prema tipu - pomerili smo ovo nakon reseta isLoading
                        // da bi UI odmah reagovao i korisnik video podatke
                        val expenseBudgets = budgetsList.filter { it.type == BudgetType.EXPENSE }
                        val incomeBudgets = budgetsList.filter { it.type == BudgetType.INCOME }
                        
                        _allBudgets.value = budgetsList
                        
                        val selectedAccountId = _selectedAccountId.value
                        
                        // Filtriramo budžete prema odabranom računu
                        val filteredExpenseBudgets = if (selectedAccountId != null) {
                            // Samo budžeti za odabrani račun
                            expenseBudgets.filter { it.accountId.isEmpty() || it.accountId == selectedAccountId }
                        } else {
                            // Svi budžeti
                            expenseBudgets
                        }
                        
                        val filteredIncomeBudgets = if (selectedAccountId != null) {
                            // Samo budžeti za odabrani račun
                            incomeBudgets.filter { it.accountId.isEmpty() || it.accountId == selectedAccountId }
                        } else {
                            // Svi budžeti
                            incomeBudgets
                        }
                        
                        _expenseBudgets.value = filteredExpenseBudgets
                        _incomeBudgets.value = filteredIncomeBudgets
                        
                        // Učitavamo potrošnju za budžete - ovo je sada "fire and forget"
                        // nećemo čekati da se završi calculateBudgetSpending da bi resetovali isLoading
                        calculateBudgetSpending()
                        
                        Log.d(tag, "Glavni podaci učitani, resetujem isLoading=false")
                    }
                } catch (e: Exception) {
                    val elapsedMs = System.currentTimeMillis() - startTime
                    
                    // Resetujemo isLoading status bez obzira na grešku
                    _isLoading.value = false
                    
                    if (e is kotlinx.coroutines.TimeoutCancellationException) {
                        Log.e(tag, "TIMEOUT: Učitavanje budžeta prekoračilo vremensko ograničenje (${elapsedMs}ms > ${timeoutMs}ms)", e)
                        
                        // Ne prikazujemo poruku korisniku ako su podaci već učitani
                        if (_allBudgets.value.isEmpty()) {
                            _errorMessage.value = "Učitavanje budžeta je predugo trajalo i automatski je prekinuto."
                        } else {
                            Log.w(tag, "Budžeti su već učitani, ignorišem timeout")
                        }
                    } else {
                        Log.e(tag, "GREŠKA: Učitavanje budžeta nije uspelo (${elapsedMs}ms)", e)
                        _errorMessage.value = "Greška prilikom učitavanja budžeta: ${e.message}"
                    }
                } finally {
                    // Garantujemo da se isLoading uvek resetuje, čak i ako je došlo do greške
                    _isLoading.value = false
                    
                    // Pokrećemo kalkulaciju trošenja budžeta ako nije već pokrenuta,
                    // čak i ako je došlo do greške pri učitavanju budžeta
                    if (_allBudgets.value.isNotEmpty() && 
                        (_displayExpenseBudgets.value.isEmpty() || _displayIncomeBudgets.value.isEmpty())) {
                        Log.d(tag, "Pokrećem kalkulaciju trošenja budžeta iz finally bloka")
                        calculateBudgetSpending()
                    }
                    
                    Log.d(tag, "===== ZAVRŠENO UČITAVANJE BUDŽETA =====")
                }
            } finally {
                // Uvek otključavamo mutex, čak i ako je došlo do greške
                loadBudgetsMutex.unlock()
            }
        }
    }
    
    /**
     * Функција за израчунавање потрошње за буџете
     */
    private fun calculateBudgetSpending() {
        viewModelScope.launch {
            // Ne menjamo globalno isLoading stanje ovde
            // Koristimo lokalno stanje za praćenje
            val calculationStartTime = System.currentTimeMillis()
            Log.d("BudgetsViewModel", "===== POČETAK IZRAČUNAVANJA BUDŽETSKE POTROŠNJE =====")
            
            try {
                loadExpenseSpending()
                loadIncomeReceived()
                
                val endTime = System.currentTimeMillis()
                Log.d("BudgetsViewModel", "Izračunavanje budžetske potrošnje završeno za ${endTime - calculationStartTime}ms")
                Log.d("BudgetsViewModel", "KONAČNI REZULTATI: displayExpenseBudgets=${_displayExpenseBudgets.value.size}, displayIncomeBudgets=${_displayIncomeBudgets.value.size}")
                Log.d("BudgetsViewModel", "KONAČNI IZNOSI: totalExpenseSpent=${_totalExpenseSpent.value}, totalIncomeReceived=${_totalIncomeReceived.value}")
                
                // Logujemo uzorke DisplayBudget objekata
                if (_displayExpenseBudgets.value.isNotEmpty()) {
                    val sampleExpenseBudgets = _displayExpenseBudgets.value.take(2).joinToString {
                        "DisplayBudget(name=${it.budget.name}, amount=${it.budget.amount}, spent=${it.spentAmount})"
                    }
                    Log.d("BudgetsViewModel", "Uzorci expense budžeta: $sampleExpenseBudgets")
                }
                
                if (_displayIncomeBudgets.value.isNotEmpty()) {
                    val sampleIncomeBudgets = _displayIncomeBudgets.value.take(2).joinToString {
                        "DisplayBudget(name=${it.budget.name}, amount=${it.budget.amount}, spent=${it.spentAmount})"
                    }
                    Log.d("BudgetsViewModel", "Uzorci income budžeta: $sampleIncomeBudgets")
                }
            } catch (e: Exception) {
                Log.e("BudgetsViewModel", "Greška pri izračunavanju budžetske potrošnje", e)
                _errorMessage.value = "Greška pri izračunavanju budžetske potrošnje: ${e.message}"
            } finally {
                // Dodajemo log koji će nam pomoći da pratimo kada se završava kalkulacija
                Log.d("BudgetsViewModel", "===== ZAVRŠENO IZRAČUNAVANJE BUDŽETSKE POTROŠNJE =====")
            }
        }
    }
    
    /**
     * Funkcija za učitavanje troškova za budžet rashoda
     */
    private suspend fun loadExpenseSpending() {
        Log.d("BudgetsViewModel", "===== UČITAVANJE RASHODA ZA BUDŽETE =====")
        
        val currentPeriodIndex = _selectedPeriodIndex.value
        val period = Period.values()[currentPeriodIndex]
        val (startDate, endDate) = calculatePeriodDates(period)
        
        // Format datuma za log
        val dateFormat = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
        Log.d("BudgetsViewModel", "Period za rashode: ${period.name}, od ${dateFormat.format(startDate)} do ${dateFormat.format(endDate)}")
        
        // Učitavamo sve rashode za odabrani period
        val expenses = try {
            val selectedAccountId = _selectedAccountId.value
            if (selectedAccountId != null) {
                Log.d("BudgetsViewModel", "Učitavam rashode za račun: $selectedAccountId i period")
                // Filtriramo po računu i periodu
                val expensesForAccount = expenseRepository.getExpensesForAccount(selectedAccountId)
                val filteredExpenses = mutableListOf<Expense>()
                
                expensesForAccount.collect { expensesList ->
                    filteredExpenses.addAll(expensesList.filter { expense ->
                        val expenseDate = expense.getDateObject()?.time ?: 0L
                        // Direktno koristimo startDate i endDate objekte bez konverzije
                        val startTime = startDate.time
                        val endTime = endDate.time
                        
                        val isInTimeRange = expenseDate in startTime..endTime
                        
                        isInTimeRange
                    })
                }
                
                filteredExpenses
            } else {
                Log.d("BudgetsViewModel", "Učitavam sve rashode za period")
                // Svi računi za period
                val allExpenses = mutableListOf<Expense>()
                expenseRepository.getExpensesForPeriod(startDate, endDate).collect { expensesList ->
                    allExpenses.addAll(expensesList)
                }
                allExpenses
            }
        } catch (e: Exception) {
            Log.e("BudgetsViewModel", "Greška pri učitavanju rashoda za period", e)
            emptyList()
        }
        
        Log.d("BudgetsViewModel", "Ukupno učitano ${expenses.size} rashoda za period")
        
        // Dodajemo log za datume nekoliko rashoda
        if (expenses.isNotEmpty()) {
            val dates = expenses.take(5).map { expense -> 
                dateFormat.format(expense.getDateObject() ?: Date()) 
            }
            Log.d("BudgetsViewModel", "Primeri datuma prvih 5 rashoda: $dates")
        }
        
        // Filtriramo rashode po kategorijama budžeta i računamo ukupne troškove
        // Moramo da filtriramo budžete za rashode
        val expenseBudgets = _expenseBudgets.value.filter { it.type == BudgetType.EXPENSE }
        
        Log.d("BudgetsViewModel", "Broj budžeta za rashode: ${expenseBudgets.size}")
        
        val expenseBudgetsWithSpending = mutableListOf<DisplayBudget>()
        var totalExpenseSpent = 0.0
        
        for (budget in expenseBudgets) {
            // Filtriramo rashode za ovaj budžet (po kategoriji)
            val budgetExpenses = expenses.filter { expense ->
                budget.categoryIds.contains(expense.category)
            }
            
            // Računamo ukupnu potrošnju za ovaj budžet
            val budgetSpent = budgetExpenses.sumOf { expense -> expense.amount }
            totalExpenseSpent += budgetSpent
            
            Log.d("BudgetsViewModel", "Budžet ${budget.name}: potrošeno $budgetSpent od ${budget.amount} (${budgetExpenses.size} transakcija)")
            
            // Kreiramo DisplayBudget sa kalkulisanom potrošnjom
            val displayBudget = DisplayBudget(
                budget = budget,
                spentAmount = budgetSpent
            )
            
            expenseBudgetsWithSpending.add(displayBudget)
        }
        
        // Ako nema budžeta za rashode, dodajemo podrazumevani
        if (expenseBudgetsWithSpending.isEmpty()) {
            Log.d("BudgetsViewModel", "Nema budžeta za rashode - dodajem podrazumevani")
            
            // Računamo ukupnu potrošnju za sve rashode
            totalExpenseSpent = expenses.sumOf { expense -> expense.amount }
            
            // Kreiramo podrazumevani budžet
            val defaultBudget = Budget(
                id = "default_expense",
                name = "Ukupni rashodi",
                amount = totalExpenseSpent,
                type = BudgetType.EXPENSE,
                categoryIds = emptyList(),
                accountId = _selectedAccountId.value ?: ""
            )
            
            val displayBudget = DisplayBudget(
                budget = defaultBudget,
                spentAmount = totalExpenseSpent
            )
            
            expenseBudgetsWithSpending.add(displayBudget)
        }
        
        Log.d("BudgetsViewModel", "Ukupna potrošnja rashoda: $totalExpenseSpent, broj budžeta: ${expenseBudgetsWithSpending.size}")
        
        // Postavljamo vrednosti u stanje
        _displayExpenseBudgets.value = expenseBudgetsWithSpending
        _totalExpenseSpent.value = totalExpenseSpent
    }
    
    /**
     * Funkcija za učitavanje prihoda za budžet prihoda
     */
    private suspend fun loadIncomeReceived() {
        Log.d("BudgetsViewModel", "===== UČITAVANJE PRIHODA ZA BUDŽETE =====")
        
        val currentPeriodIndex = _selectedPeriodIndex.value
        val period = Period.values()[currentPeriodIndex]
        val (startDate, endDate) = calculatePeriodDates(period)
        
        // Format datuma za log
        val dateFormat = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
        Log.d("BudgetsViewModel", "Period za prihode: ${period.name}, od ${dateFormat.format(startDate)} do ${dateFormat.format(endDate)}")
        
        // Učitavamo sve prihode za odabrani period
        val incomes = try {
            val selectedAccountId = _selectedAccountId.value
            if (selectedAccountId != null) {
                Log.d("BudgetsViewModel", "Učitavam prihode za račun: $selectedAccountId i period")
                // Filtriramo po računu i periodu
                val incomesForAccount = incomeRepository.getAllIncomes()
                val filteredIncomes = mutableListOf<Income>()
                
                incomesForAccount.collect { incomesList ->
                    filteredIncomes.addAll(incomesList.filter { income ->
                        val incomeDate = income.getDateObject()?.time ?: 0L
                        // Direktno koristimo startDate i endDate objekte bez konverzije
                        val startTime = startDate.time
                        val endTime = endDate.time
                        
                        val isInTimeRange = incomeDate in startTime..endTime
                        
                        isInTimeRange
                    })
                }
                
                filteredIncomes
            } else {
                Log.d("BudgetsViewModel", "Učitavam sve prihode za period")
                // Svi računi za period
                val allIncomes = mutableListOf<Income>()
                incomeRepository.getIncomesForPeriod(startDate, endDate).collect { incomesList ->
                    allIncomes.addAll(incomesList)
                }
                allIncomes
            }
        } catch (e: Exception) {
            Log.e("BudgetsViewModel", "Greška pri učitavanju prihoda za period", e)
            emptyList()
        }
        
        Log.d("BudgetsViewModel", "Ukupno učitano ${incomes.size} prihoda za period")
        
        // Dodajemo log za datume nekoliko prihoda
        if (incomes.isNotEmpty()) {
            val dates = incomes.take(5).map { income -> 
                dateFormat.format(income.getDateObject() ?: Date()) 
            }
            Log.d("BudgetsViewModel", "Primeri datuma prvih 5 prihoda: $dates")
        }
        
        // Filtriramo budžete za prihode
        val incomeBudgets = _incomeBudgets.value.filter { it.type == BudgetType.INCOME }
        
        Log.d("BudgetsViewModel", "Broj budžeta za prihode: ${incomeBudgets.size}")
        
        val incomeBudgetsWithReceived = mutableListOf<DisplayBudget>()
        var totalIncomeReceived = 0.0
        
        for (budget in incomeBudgets) {
            // Filtriramo prihode za ovaj budžet (po kategoriji)
            val budgetIncomes = incomes.filter { income ->
                budget.categoryIds.contains(income.category)
            }
            
            // Računamo ukupan primljeni iznos za ovaj budžet
            val budgetReceived = budgetIncomes.sumOf { income -> income.amount }
            totalIncomeReceived += budgetReceived
            
            Log.d("BudgetsViewModel", "Budžet ${budget.name}: primljeno $budgetReceived od ${budget.amount} (${budgetIncomes.size} transakcija)")
            
            // Kreiramo DisplayBudget sa kalkulisanim primljenim iznosom
            val displayBudget = DisplayBudget(
                budget = budget,
                spentAmount = budgetReceived
            )
            
            incomeBudgetsWithReceived.add(displayBudget)
        }
        
        // Ako nema budžeta za prihode, dodajemo podrazumevani
        if (incomeBudgetsWithReceived.isEmpty()) {
            Log.d("BudgetsViewModel", "Nema budžeta za prihode - dodajem podrazumevani")
            
            // Računamo ukupni primljeni iznos za sve prihode
            totalIncomeReceived = incomes.sumOf { income -> income.amount }
            
            // Kreiramo podrazumevani budžet
            val defaultBudget = Budget(
                id = "default_income",
                name = "Ukupni prihodi",
                amount = totalIncomeReceived,
                type = BudgetType.INCOME,
                categoryIds = emptyList(),
                accountId = _selectedAccountId.value ?: ""
            )
            
            val displayBudget = DisplayBudget(
                budget = defaultBudget,
                spentAmount = totalIncomeReceived
            )
            
            incomeBudgetsWithReceived.add(displayBudget)
        }
        
        Log.d("BudgetsViewModel", "Ukupno primljeno prihoda: $totalIncomeReceived, broj budžeta: ${incomeBudgetsWithReceived.size}")
        
        // Postavljamo vrednosti u stanje
        _displayIncomeBudgets.value = incomeBudgetsWithReceived
        _totalIncomeReceived.value = totalIncomeReceived
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
     * Forsira zaustavljanje stanja učitavanja.
     * Koristi se kao sigurnosni mehanizam da bi se sprečilo "beskonačno" učitavanje.
     */
    fun forceStopLoading() {
        _isLoading.value = false
        Log.d(tag, "Prinudno zaustavljeno učitavanje (forceStopLoading pozvana)")
        
        // Ako je mutex zaključan, otključavamo ga
        if (loadBudgetsMutex.isLocked) {
            loadBudgetsMutex.unlock()
            Log.d(tag, "Mutex otključan u forceStopLoading")
        }
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