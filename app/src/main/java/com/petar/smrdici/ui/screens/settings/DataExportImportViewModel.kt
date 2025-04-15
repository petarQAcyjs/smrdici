package com.petar.smrdici.ui.screens.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.petar.smrdici.data.model.Account
import com.petar.smrdici.data.model.AccountType
import com.petar.smrdici.data.model.Event
import com.petar.smrdici.data.model.Expense
import com.petar.smrdici.data.model.Income
import com.petar.smrdici.data.model.ShoppingList
import com.petar.smrdici.data.repository.AccountRepository
import com.petar.smrdici.data.repository.DataExportImportRepository
import com.petar.smrdici.data.repository.EventRepository
import com.petar.smrdici.data.repository.ExportData
import com.petar.smrdici.data.repository.ExpenseRepository
import com.petar.smrdici.data.repository.IncomeRepository
import com.petar.smrdici.data.repository.ListRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Енумерација која дефинише типове увоза
 */
enum class ImportMode {
    REPLACE_ALL, // Замена свих података
    ADD_NEW      // Додавање нових података
}

/**
 * Класа која садржи податке за преглед пре увоза
 */
data class ImportPreview(
    val accountCount: Int = 0,
    val expenseCount: Int = 0,
    val incomeCount: Int = 0,
    val exportDate: String = "",
    val version: Int = 0
)

class DataExportImportViewModel(
    private val dataExportImportRepository: DataExportImportRepository,
    private val accountRepository: AccountRepository,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val eventRepository: EventRepository,
    private val listRepository: ListRepository
) : ViewModel() {
    
    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()
    
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()
    
    private val _exportSuccess = MutableStateFlow<Boolean?>(null)
    val exportSuccess: StateFlow<Boolean?> = _exportSuccess.asStateFlow()
    
    private val _importSuccess = MutableStateFlow<Boolean?>(null)
    val importSuccess: StateFlow<Boolean?> = _importSuccess.asStateFlow()
    
    // Ново стање за преглед података пре увоза
    private val _importPreview = MutableStateFlow<ImportPreview?>(null)
    val importPreview: StateFlow<ImportPreview?> = _importPreview.asStateFlow()
    
    // Чување тренутно изабраног режима увоза
    private val _importMode = MutableStateFlow(ImportMode.REPLACE_ALL)
    val importMode: StateFlow<ImportMode> = _importMode.asStateFlow()
    
    // Чување тренутно учитаних података за увоз (да не морамо поново учитавати)
    private var currentImportData: ExportData? = null
    
    // Праћење прогреса операције увоза
    private val _importProgress = MutableStateFlow(0f)
    val importProgress: StateFlow<Float> = _importProgress.asStateFlow()
    
    // Текст за прогрес
    private val _importProgressText = MutableStateFlow("")
    val importProgressText: StateFlow<String> = _importProgressText.asStateFlow()
    
    fun exportData(uri: Uri) {
        viewModelScope.launch {
            _isExporting.value = true
            _exportSuccess.value = null
            
            try {
                // Prikupljanje podataka iz svih repozitorijuma - samo finansijski podaci
                val accounts = accountRepository.getAllAccounts()
                val expenses = expenseRepository.getExpenses().first()  // Koristi Flow.first() da dobijemo trenutnu vrednost
                val incomes = incomeRepository.getIncomes().first()     // Koristi Flow.first() da dobijemo trenutnu vrednost
                // Ne uključujemo događaje i liste jer nisu deo finansijskih podataka
                // val events = eventRepository.getAllEvents()
                // val lists = listRepository.getAllLists()
                
                // Kreiranje objekta sa svim podacima
                val exportData = ExportData(
                    accounts = accounts,
                    expenses = expenses,
                    incomes = incomes,
                    // Ne uključujemo događaje i liste jer nisu deo finansijskih podataka
                    // events = events,
                    // lists = lists,
                    // Ovde bismo dodali podešavanja ako je potrebno
                    version = 1
                )
                
                // Izvoz podataka
                val success = dataExportImportRepository.exportData(uri, exportData)
                _exportSuccess.value = success
            } catch (_: Exception) {
                _exportSuccess.value = false
            } finally {
                _isExporting.value = false
            }
        }
    }
    
    /**
     * Учитава податке из JSON фајла и приказује преглед пре увоза
     */
    fun loadImportFile(uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            _importPreview.value = null
            _importProgress.value = 0f
            _importProgressText.value = "Учитавање датотеке..."
            
            try {
                // Учитавање података
                val importedData = dataExportImportRepository.importData(uri)
                
                if (importedData != null) {
                    // Чување података за касније
                    currentImportData = importedData
                    
                    // Креирање прегледа - без događaja i lista
                    _importPreview.value = ImportPreview(
                        accountCount = importedData.accounts.size,
                        expenseCount = importedData.expenses.size,
                        incomeCount = importedData.incomes.size,
                        exportDate = importedData.exportDate,
                        version = importedData.version
                    )
                    
                    _importProgressText.value = "Датотека успешно учитана"
                } else {
                    _importProgressText.value = "Грешка при учитавању датотеке"
                }
            } catch (e: Exception) {
                _importProgressText.value = "Грешка: ${e.message}"
            } finally {
                _isImporting.value = false
            }
        }
    }
    
    /**
     * Поставља режим увоза
     */
    fun setImportMode(mode: ImportMode) {
        _importMode.value = mode
    }
    
    /**
     * Започиње увоз података са изабраним режимом
     */
    fun importData(uri: Uri? = null) {
        viewModelScope.launch {
            _isImporting.value = true
            _importSuccess.value = null
            _importProgress.value = 0f
            _importProgressText.value = "Припрема увоза..."
            
            try {
                // Користимо већ учитане податке или учитавамо нове
                val importedData = currentImportData ?: uri?.let { dataExportImportRepository.importData(it) }
                
                if (importedData != null) {
                    // Безбедно проверавамо да ли су рачуни, расходи и приходи иницијализовани
                    val accounts = importedData.accounts.takeIf { it.isNotEmpty() } ?: emptyList()
                    val expenses = importedData.expenses.takeIf { it.isNotEmpty() } ?: emptyList()
                    val incomes = importedData.incomes.takeIf { it.isNotEmpty() } ?: emptyList()
                    
                    // Постављамо укупан број ставки за увоз ради праћења прогреса - без догађаја и листа
                    val totalItems = accounts.size + expenses.size + incomes.size
                    if (totalItems == 0) {
                        _importProgressText.value = "Нема података за увоз"
                        _importProgress.value = 1f
                        _importSuccess.value = false
                        return@launch
                    }
                    
                    var processedItems = 0
                    
                    // Учитавамо постојеће податке за проверу дуплирања
                    var existingAccounts = emptyList<Account>()
                    var existingExpenses = emptyList<Expense>()
                    var existingIncomes = emptyList<Income>()
                    
                    if (_importMode.value == ImportMode.ADD_NEW) {
                        _importProgressText.value = "Учитавање постојећих података..."
                        existingAccounts = accountRepository.getAllAccounts()
                        existingExpenses = expenseRepository.getExpenses().first()
                        existingIncomes = incomeRepository.getIncomes().first()
                    }
                    
                    _importProgressText.value = "Брисање постојећих података..."
                    
                    // Ако смо у режиму замене, бришемо постојеће податке - само финансијске податке
                    if (_importMode.value == ImportMode.REPLACE_ALL) {
                        accountRepository.deleteAllAccounts()
                        expenseRepository.deleteAllExpenses()
                        incomeRepository.deleteAllIncomes()
                    }
                    
                    // Додавање увезених података
                    _importProgressText.value = "Увоз рачуна..."
                    
                    // Безбедно касуј податке и обрађуј изузетке
                    accounts.forEach { accountData ->
                        try {
                            // Сигурна конверзија Account објекта
                            val account = when (accountData) {
                                is Account -> accountData
                                is Map<*, *> -> {
                                    val id = (accountData["id"] as? String) ?: ""
                                    val name = (accountData["name"] as? String) ?: ""
                                    val balance = when (val balanceValue = accountData["balance"]) {
                                        is Number -> balanceValue.toDouble()
                                        is String -> balanceValue.toDoubleOrNull() ?: 0.0
                                        else -> 0.0
                                    }
                                    val currency = (accountData["currency"] as? String) ?: "RSD"
                                    val color = when (val colorValue = accountData["color"]) {
                                        is Number -> colorValue.toInt()
                                        is String -> colorValue.toIntOrNull() ?: 0
                                        else -> 0
                                    }
                                    val isDefault = (accountData["isDefault"] as? Boolean) ?: false
                                    val typeStr = (accountData["type"] as? String) ?: "CASH"
                                    val type = try {
                                        AccountType.valueOf(typeStr)
                                    } catch (e: Exception) {
                                        AccountType.CASH
                                    }
                                    Account(id, name, balance, currency, color, isDefault, type)
                                }
                                else -> {
                                    Log.e("ImportData", "Неуспешна конверзија рачуна: $accountData")
                                    null
                                }
                            }
                            
                            if (account != null) {
                                // Провера да ли рачун већ постоји (за ADD_NEW режим)
                                val shouldAdd = if (_importMode.value == ImportMode.ADD_NEW) {
                                    existingAccounts.none { it.id == account.id }
                                } else {
                                    true
                                }
                                
                                if (shouldAdd) {
                                    // Директно додајемо рачун са његовим балансом - баланс у JSON-у је већ претходно израчунато стање
                                    accountRepository.addAccount(account)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ImportData", "Грешка при увозу рачуна: ${e.message}", e)
                        }
                        processedItems++
                        _importProgress.value = processedItems.toFloat() / totalItems
                    }
                    
                    _importProgressText.value = "Увоз расхода..."
                    
                    // Привремено памтимо на којим рачунима су расходи и приходи да би исправили стање на крају
                    val accountBalanceChanges = mutableMapOf<String, Double>()
                    
                    expenses.forEach { expenseData ->
                        try {
                            // Сигурна конверзија Expense објекта
                            val expense = when (expenseData) {
                                is Expense -> expenseData
                                is Map<*, *> -> {
                                    val id = (expenseData["id"] as? String) ?: ""
                                    val amount = when (val amountValue = expenseData["amount"]) {
                                        is Number -> amountValue.toDouble()
                                        is String -> amountValue.toDoubleOrNull() ?: 0.0
                                        else -> 0.0
                                    }
                                    val description = (expenseData["description"] as? String) ?: ""
                                    val category = (expenseData["category"] as? String) ?: ""
                                    val accountId = (expenseData["accountId"] as? String) ?: ""
                                    
                                    // Преузимање датума
                                    val date = when (val dateValue = expenseData["date"]) {
                                        is Map<*, *> -> {
                                            try {
                                                val seconds = when (val secondsValue = dateValue["seconds"]) {
                                                    is Number -> secondsValue.toLong()
                                                    is String -> secondsValue.toLongOrNull() ?: 0L
                                                    else -> 0L
                                                }
                                                val nanoseconds = when (val nanosecondsValue = dateValue["nanoseconds"]) {
                                                    is Number -> nanosecondsValue.toInt()
                                                    is String -> nanosecondsValue.toIntOrNull() ?: 0
                                                    else -> 0
                                                }
                                                com.google.firebase.Timestamp(seconds, nanoseconds)
                                            } catch (e: Exception) {
                                                Log.e("ImportData", "Грешка при парсирању датума расхода", e)
                                                com.google.firebase.Timestamp.now()
                                            }
                                        }
                                        is Number -> com.google.firebase.Timestamp(Date(dateValue.toLong()))
                                        else -> com.google.firebase.Timestamp.now()
                                    }
                                    
                                    Expense(id, amount, description, category, date, accountId)
                                }
                                else -> {
                                    Log.e("ImportData", "Неуспешна конверзија расхода: $expenseData")
                                    null
                                }
                            }
                            
                            if (expense != null) {
                                // Провера да ли расход већ постоји (за ADD_NEW режим)
                                val shouldAdd = if (_importMode.value == ImportMode.ADD_NEW) {
                                    existingExpenses.none { it.id == expense.id }
                                } else {
                                    true
                                }
                                
                                if (shouldAdd) {
                                    // Памтимо колико се мења баланс рачуна због додавања расхода
                                    val currentChange = accountBalanceChanges.getOrDefault(expense.accountId, 0.0)
                                    accountBalanceChanges[expense.accountId] = currentChange - expense.amount
                                    
                                    // Додајемо расход без ажурирања баланса
                                    expenseRepository.addExpense(expense, updateAccountBalance = false)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ImportData", "Грешка при увозу расхода: ${e.message}", e)
                        }
                        processedItems++
                        _importProgress.value = processedItems.toFloat() / totalItems
                    }
                    
                    _importProgressText.value = "Увоз прихода..."
                    
                    incomes.forEach { incomeData ->
                        try {
                            // Сигурна конверзија Income објекта
                            val income = when (incomeData) {
                                is Income -> incomeData
                                is Map<*, *> -> {
                                    val id = (incomeData["id"] as? String) ?: ""
                                    val amount = when (val amountValue = incomeData["amount"]) {
                                        is Number -> amountValue.toDouble()
                                        is String -> amountValue.toDoubleOrNull() ?: 0.0
                                        else -> 0.0
                                    }
                                    val description = (incomeData["description"] as? String) ?: ""
                                    val category = (incomeData["category"] as? String) ?: ""
                                    val accountId = (incomeData["accountId"] as? String) ?: ""
                                    
                                    // Преузимање датума
                                    val date = when (val dateValue = incomeData["date"]) {
                                        is Map<*, *> -> {
                                            try {
                                                val seconds = when (val secondsValue = dateValue["seconds"]) {
                                                    is Number -> secondsValue.toLong()
                                                    is String -> secondsValue.toLongOrNull() ?: 0L
                                                    else -> 0L
                                                }
                                                val nanoseconds = when (val nanosecondsValue = dateValue["nanoseconds"]) {
                                                    is Number -> nanosecondsValue.toInt()
                                                    is String -> nanosecondsValue.toIntOrNull() ?: 0
                                                    else -> 0
                                                }
                                                com.google.firebase.Timestamp(seconds, nanoseconds)
                                            } catch (e: Exception) {
                                                Log.e("ImportData", "Грешка при парсирању датума прихода", e)
                                                com.google.firebase.Timestamp.now()
                                            }
                                        }
                                        is Number -> com.google.firebase.Timestamp(Date(dateValue.toLong()))
                                        else -> com.google.firebase.Timestamp.now()
                                    }
                                    
                                    Income(id, amount, description, category, date, accountId)
                                }
                                else -> {
                                    Log.e("ImportData", "Неуспешна конверзија прихода: $incomeData")
                                    null
                                }
                            }
                            
                            if (income != null) {
                                // Провера да ли приход већ постоји (за ADD_NEW режим)
                                val shouldAdd = if (_importMode.value == ImportMode.ADD_NEW) {
                                    existingIncomes.none { it.id == income.id }
                                } else {
                                    true
                                }
                                
                                if (shouldAdd) {
                                    // Памтимо колико се мења баланс рачуна због додавања прихода
                                    val currentChange = accountBalanceChanges.getOrDefault(income.accountId, 0.0)
                                    accountBalanceChanges[income.accountId] = currentChange + income.amount
                                    
                                    // Додајемо приход без ажурирања баланса
                                    incomeRepository.addIncome(income, updateAccountBalance = false)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ImportData", "Грешка при увозу прихода: ${e.message}", e)
                        }
                        processedItems++
                        _importProgress.value = processedItems.toFloat() / totalItems
                    }
                    
                    // Након увоза, исправљамо баланс на рачунима тако да одговара вредностима из JSON-а
                    _importProgressText.value = "Корекција баланса рачуна..."
                    
                    try {
                        // Учитавамо све рачуне да бисмо видели њихове тренутне вредности
                        val currentAccounts = accountRepository.getAllAccounts()
                        
                        // Мапирамо баланс увезених рачуна из JSON-а
                        val importedBalances = accounts.associate { (it as Account).id to (it as Account).balance }
                        
                        // Поправљамо баланс рачуна
                        for (account in currentAccounts) {
                            val importedBalance = importedBalances[account.id] ?: continue
                            
                            // Ажурирамо рачун директно да има баланс из JSON-а
                            val correctedAccount = account.copy(balance = importedBalance)
                            accountRepository.updateAccount(correctedAccount)
                        }
                    } catch (e: Exception) {
                        Log.e("ImportData", "Грешка при корекцији баланса рачуна: ${e.message}")
                    }
                    
                    _importProgress.value = 1f
                    _importProgressText.value = "Увоз успешно завршен"
                    _importSuccess.value = true
                    
                    // Ресетујемо преглед
                    resetImportPreview()
                } else {
                    _importProgress.value = 0f
                    _importProgressText.value = "Грешка при увозу података"
                    _importSuccess.value = false
                }
            } catch (e: Exception) {
                _importProgress.value = 0f
                _importProgressText.value = "Грешка: ${e.message}"
                _importSuccess.value = false
            } finally {
                _isImporting.value = false
            }
        }
    }
    
    /**
     * Ресетује преглед увоза
     */
    fun resetImportPreview() {
        _importPreview.value = null
        currentImportData = null
        _importProgress.value = 0f
        _importProgressText.value = ""
    }
    
    fun resetExportStatus() {
        _exportSuccess.value = null
    }
    
    fun resetImportStatus() {
        _importSuccess.value = null
    }
    
    fun getExportFilename(): String {
        return dataExportImportRepository.createExportFilename()
    }
    
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DataExportImportViewModel::class.java)) {
                return DataExportImportViewModel(
                    DataExportImportRepository.getInstance(context),
                    AccountRepository.getInstance(),
                    ExpenseRepository.getInstance(),
                    IncomeRepository.getInstance(),
                    EventRepository.getInstance(),
                    ListRepository.getInstance()
                ) as T
            }
            throw IllegalArgumentException("Непознати ViewModel класа")
        }
    }
} 