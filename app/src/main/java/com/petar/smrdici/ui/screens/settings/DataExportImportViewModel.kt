package com.petar.smrdici.ui.screens.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.petar.smrdici.data.model.Account
import com.petar.smrdici.data.model.AccountType
import com.petar.smrdici.data.model.Expense
import com.petar.smrdici.data.model.Income
import com.petar.smrdici.data.repository.AccountRepository
import com.petar.smrdici.data.repository.DataExportImportRepository
import com.petar.smrdici.data.repository.EventRepository
import com.petar.smrdici.data.repository.ExpenseRepository
import com.petar.smrdici.data.repository.ExportData
import com.petar.smrdici.data.repository.IncomeRepository
import com.petar.smrdici.data.repository.ListRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID

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
                    
                    // Учитавамо постојеће податке у зависности од режима
                    val existingAccounts = if (_importMode.value == ImportMode.ADD_NEW) {
                        accountRepository.getAllAccounts()
                    } else {
                        emptyList()
                    }
                    
                    val existingExpenses = if (_importMode.value == ImportMode.ADD_NEW) {
                        expenseRepository.getExpenses().first()
                    } else {
                        emptyList()
                    }
                    
                    val existingIncomes = if (_importMode.value == ImportMode.ADD_NEW) {
                        incomeRepository.getIncomes().first()
                    } else {
                        emptyList()
                    }
                    
                    // Обрађујемо рачуне
                    _importProgressText.value = "Увоз рачуна..."
                    
                    // Ако је режим REPLACE_ALL, бришемо све постојеће рачуне
                    if (_importMode.value == ImportMode.REPLACE_ALL) {
                        val currentAccounts = accountRepository.getAllAccounts()
                        currentAccounts.forEach { account ->
                            accountRepository.deleteAccount(account.id)
                        }
                    }
                    
                    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: throw IllegalStateException("User not authenticated")
                    
                    accounts.forEach { accountData ->
                        try {
                            // Сигурна конверзија Account објекта
                            val account = when (accountData) {
                                is Account -> accountData.copy(userId = currentUserId)  // Always use current user's ID
                                is Map<*, *> -> {
                                    val id = (accountData["id"] as? String) ?: UUID.randomUUID().toString()
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
                                    val isDefault = when (val defaultValue = accountData["isDefault"]) {
                                        is Boolean -> defaultValue
                                        is Number -> defaultValue.toInt() != 0
                                        is String -> defaultValue.toBoolean()
                                        else -> false
                                    }
                                    val type = try {
                                        val typeStr = (accountData["type"] as? String) ?: "CASH"
                                        AccountType.valueOf(typeStr)
                                    } catch (e: Exception) {
                                        AccountType.CASH
                                    }
                                    Account(
                                        id = id,
                                        userId = currentUserId,  // Always use current user's ID
                                        name = name,
                                        balance = balance,
                                        currency = currency,
                                        color = color,
                                        isDefault = isDefault,
                                        type = type
                                    )
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
                    
                    // Ako je režim REPLACE_ALL, brišemo sve postojeće rashode
                    if (_importMode.value == ImportMode.REPLACE_ALL) {
                        val currentExpenses = expenseRepository.getExpenses().first()
                        currentExpenses.forEach { expense ->
                            expenseRepository.deleteExpense(expense.id)
                        }
                    }
                    
                    // Dohvatimo Firebase instancu za direktan pristup
                    val firestore = FirebaseFirestore.getInstance()
                    
                    expenses.forEach { expenseData ->
                        try {
                            // Сигурна конверзија Expense објекта
                            val expense = when (expenseData) {
                                is Expense -> expenseData.copy(userId = currentUserId)  // Always use current user's ID
                                is Map<*, *> -> {
                                    val id = (expenseData["id"] as? String) ?: UUID.randomUUID().toString()
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
                                                // Pokušaj izvući seconds i nanoseconds ako postoje
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
                                                
                                                // Pretvorimo timestamp u string datum
                                                val date = Date(seconds * 1000 + nanoseconds / 1000000)
                                                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                                dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                                dateFormat.format(date)
                                            } catch (e: Exception) {
                                                Log.e("ImportData", "Грешка при парсирању датума расхода", e)
                                                // Današnji datum kao fallback
                                                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                                dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                                dateFormat.format(Date())
                                            }
                                        }
                                        is String -> {
                                            // Proveri da li je string već u ispravnom formatu
                                            if (dateValue.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                                                dateValue
                                            } else {
                                                try {
                                                    // Pokušaj parsirati string u datum
                                                    val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                                    dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                                    val parsedDate = dateFormat.parse(dateValue)
                                                    if (parsedDate != null) {
                                                        dateFormat.format(parsedDate)
                                                    } else {
                                                        dateFormat.format(Date())
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("ImportData", "Greška pri parsiranju string datuma: ${e.message}")
                                                    // Današnji datum kao fallback
                                                    val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                                    dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                                    dateFormat.format(Date())
                                                }
                                            }
                                        }
                                        is Number -> {
                                            // Pretvorimo long timestamp u string datum
                                            val date = Date(dateValue.toLong())
                                            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                            dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                            dateFormat.format(date)
                                        }
                                        else -> {
                                            // Današnji datum kao fallback
                                            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                            dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                            dateFormat.format(Date())
                                        }
                                    }
                                    
                                    Expense(
                                        id = id,
                                        userId = currentUserId,  // Always use current user's ID
                                        amount = amount,
                                        description = description,
                                        category = category,
                                        date = date,
                                        accountId = accountId
                                    )
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
                                    
                                    // Kreiramo podatke za direktan upis u Firebase
                                    val expenseId = expense.id.ifEmpty { java.util.UUID.randomUUID().toString() }
                                    
                                    // Osiguravamo da datum bude u ispravnom formatu
                                    val validDate = if (expense.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                                        expense.date
                                    } else {
                                        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                        dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                        dateFormat.format(Date())
                                    }
                                    
                                    val expenseMap = hashMapOf(
                                        "id" to expenseId,
                                        "userId" to currentUserId,
                                        "amount" to expense.amount,
                                        "description" to expense.description,
                                        "category" to expense.category,
                                        "date" to validDate,
                                        "accountId" to expense.accountId,
                                        "createdAt" to System.currentTimeMillis(),
                                        "updatedAt" to System.currentTimeMillis()
                                    )
                                    
                                    // Direktno upisujemo u Firebase
                                    if (currentUserId.isNotEmpty()) {
                                        firestore.collection("users").document(currentUserId)
                                            .collection("expenses").document(expenseId)
                                            .set(expenseMap)
                                            .addOnSuccessListener {
                                                Log.d("ImportData", "Uspešno dodat trošak: $expenseId")
                                            }
                                            .addOnFailureListener { e ->
                                                Log.e("ImportData", "Greška pri dodavanju troška: ${e.message}")
                                            }
                                    } else {
                                        // Ako nije dostupan direktan pristup Firebase-u, koristimo standardni način
                                        expenseRepository.addExpense(expense.copy(id = expenseId), updateAccountBalance = false)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ImportData", "Грешка при увозу расхода: ${e.message}", e)
                        }
                        processedItems++
                        _importProgress.value = processedItems.toFloat() / totalItems
                    }
                    
                    _importProgressText.value = "Увоз прихода..."
                    
                    // Ako je režim REPLACE_ALL, brišemo sve postojeće prihode
                    if (_importMode.value == ImportMode.REPLACE_ALL) {
                        val currentIncomes = incomeRepository.getIncomes().first()
                        currentIncomes.forEach { income ->
                            incomeRepository.deleteIncome(income.id)
                        }
                    }
                    
                    incomes.forEach { incomeData ->
                        try {
                            // Сигурна конверзија Income објекта
                            val income = when (incomeData) {
                                is Income -> incomeData.copy(userId = currentUserId)  // Always use current user's ID
                                is Map<*, *> -> {
                                    val id = (incomeData["id"] as? String) ?: UUID.randomUUID().toString()
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
                                                // Pokušaj izvući seconds i nanoseconds ako postoje
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
                                                
                                                // Pretvorimo timestamp u string datum
                                                val date = Date(seconds * 1000 + nanoseconds / 1000000)
                                                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                                dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                                dateFormat.format(date)
                                            } catch (e: Exception) {
                                                Log.e("ImportData", "Грешка при парсирању датума прихода", e)
                                                // Današnji datum kao fallback
                                                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                                dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                                dateFormat.format(Date())
                                            }
                                        }
                                        is String -> {
                                            // Proveri da li je string već u ispravnom formatu
                                            if (dateValue.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                                                dateValue
                                            } else {
                                                try {
                                                    // Pokušaj parsirati string u datum
                                                    val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                                    dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                                    val parsedDate = dateFormat.parse(dateValue)
                                                    if (parsedDate != null) {
                                                        dateFormat.format(parsedDate)
                                                    } else {
                                                        dateFormat.format(Date())
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("ImportData", "Greška pri parsiranju string datuma: ${e.message}")
                                                    // Današnji datum kao fallback
                                                    val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                                    dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                                    dateFormat.format(Date())
                                                }
                                            }
                                        }
                                        is Number -> {
                                            // Pretvorimo long timestamp u string datum
                                            val date = Date(dateValue.toLong())
                                            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                            dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                            dateFormat.format(date)
                                        }
                                        else -> {
                                            // Današnji datum kao fallback
                                            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                            dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                            dateFormat.format(Date())
                                        }
                                    }
                                    
                                    Income(
                                        id = id,
                                        userId = currentUserId,  // Always use current user's ID
                                        amount = amount,
                                        description = description,
                                        category = category,
                                        date = date,
                                        accountId = accountId
                                    )
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
                                    
                                    // Kreiramo podatke za direktan upis u Firebase
                                    val incomeId = income.id.ifEmpty { java.util.UUID.randomUUID().toString() }
                                    
                                    // Osiguravamo da datum bude u ispravnom formatu
                                    val validDate = if (income.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                                        income.date
                                    } else {
                                        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                        dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                        dateFormat.format(Date())
                                    }
                                    
                                    val incomeMap = hashMapOf(
                                        "id" to incomeId,
                                        "userId" to currentUserId,
                                        "amount" to income.amount,
                                        "description" to income.description,
                                        "category" to income.category,
                                        "date" to validDate,
                                        "accountId" to income.accountId,
                                        "createdAt" to System.currentTimeMillis(),
                                        "updatedAt" to System.currentTimeMillis()
                                    )
                                    
                                    // Direktno upisujemo u Firebase
                                    if (currentUserId.isNotEmpty()) {
                                        firestore.collection("users").document(currentUserId)
                                            .collection("incomes").document(incomeId)
                                            .set(incomeMap)
                                            .addOnSuccessListener {
                                                Log.d("ImportData", "Uspešno dodat prihod: $incomeId")
                                            }
                                            .addOnFailureListener { e ->
                                                Log.e("ImportData", "Greška pri dodavanju prihoda: ${e.message}")
                                            }
                                    } else {
                                        // Ako nije dostupan direktan pristup Firebase-u, koristimo standardni način
                                        incomeRepository.addIncome(income.copy(id = incomeId), updateAccountBalance = false)
                                    }
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
                        val importedBalances = mutableMapOf<String, Double>()
                        
                        // Проверавамо и додајемо баланс за сваки рачун
                        accounts.forEach {
                            when (it) {
                                is Account -> {
                                    if (it.id.isNotEmpty()) {
                                        importedBalances[it.id] = it.balance
                                    }
                                }
                                is Map<*, *> -> {
                                    val id = (it["id"] as? String) ?: ""
                                    val balance = when (val balanceValue = it["balance"]) {
                                        is Number -> balanceValue.toDouble()
                                        is String -> balanceValue.toDoubleOrNull() ?: 0.0
                                        else -> 0.0
                                    }
                                    
                                    if (id.isNotEmpty()) {
                                        importedBalances[id] = balance
                                    }
                                }
                                else -> {
                                    // Игноришемо нетипизирани објекте
                                    Log.e("ImportData", "Непознат тип рачуна: $it")
                                }
                            }
                        }
                        
                        // Поправљамо баланс рачуна
                        for (account in currentAccounts) {
                            val importedBalance = importedBalances[account.id]
                            if (importedBalance != null) {
                                // Ажурирамо рачун директно да има баланс из JSON-а
                                val correctedAccount = account.copy(balance = importedBalance)
                                accountRepository.updateAccount(correctedAccount)
                            }
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