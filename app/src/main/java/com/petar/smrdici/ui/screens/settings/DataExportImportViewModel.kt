package com.petar.smrdici.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.petar.smrdici.data.model.Account
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
import com.petar.smrdici.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DataExportImportViewModel(
    private val context: Context,
    private val dataExportImportRepository: DataExportImportRepository,
    private val accountRepository: AccountRepository,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val eventRepository: EventRepository,
    private val listRepository: ListRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    
    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()
    
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()
    
    private val _exportSuccess = MutableStateFlow<Boolean?>(null)
    val exportSuccess: StateFlow<Boolean?> = _exportSuccess.asStateFlow()
    
    private val _importSuccess = MutableStateFlow<Boolean?>(null)
    val importSuccess: StateFlow<Boolean?> = _importSuccess.asStateFlow()
    
    fun exportData(uri: Uri) {
        viewModelScope.launch {
            _isExporting.value = true
            _exportSuccess.value = null
            
            try {
                // Prikupljanje podataka iz svih repozitorijuma
                val accounts = accountRepository.getAllAccounts()
                val expenses = expenseRepository.getExpenses().first()  // Koristi Flow.first() da dobijemo trenutnu vrednost
                val incomes = incomeRepository.getIncomes().first()     // Koristi Flow.first() da dobijemo trenutnu vrednost
                val events = eventRepository.getAllEvents()
                val lists = listRepository.getAllLists()
                
                // Kreiranje objekta sa svim podacima
                val exportData = ExportData(
                    accounts = accounts,
                    expenses = expenses,
                    incomes = incomes,
                    events = events,
                    lists = lists,
                    // Ovde bismo dodali podešavanja ako je potrebno
                    version = 1
                )
                
                // Izvoz podataka
                val success = dataExportImportRepository.exportData(uri, exportData)
                _exportSuccess.value = success
            } catch (e: Exception) {
                _exportSuccess.value = false
            } finally {
                _isExporting.value = false
            }
        }
    }
    
    fun importData(uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            _importSuccess.value = null
            
            try {
                // Uvoz podataka
                val importedData = dataExportImportRepository.importData(uri)
                
                if (importedData != null) {
                    // Brisanje postojećih podataka
                    accountRepository.deleteAllAccounts()
                    expenseRepository.deleteAllExpenses()
                    incomeRepository.deleteAllIncomes()
                    eventRepository.deleteAllEvents()
                    listRepository.deleteAllLists()
                    
                    // Dodavanje uvezenih podataka
                    importedData.accounts.forEach { account ->
                        accountRepository.addAccount(account as Account)
                    }
                    
                    importedData.expenses.forEach { expense ->
                        expenseRepository.addExpense(expense as Expense)
                    }
                    
                    importedData.incomes.forEach { income ->
                        incomeRepository.addIncome(income as Income)
                    }
                    
                    importedData.events.forEach { event ->
                        eventRepository.addEvent(event as Event)
                    }
                    
                    importedData.lists.forEach { list ->
                        listRepository.addList(list as ShoppingList)
                    }
                    
                    _importSuccess.value = true
                } else {
                    _importSuccess.value = false
                }
            } catch (e: Exception) {
                _importSuccess.value = false
            } finally {
                _isImporting.value = false
            }
        }
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
                    context,
                    DataExportImportRepository.getInstance(context),
                    AccountRepository.getInstance(context),
                    ExpenseRepository.getInstance(),
                    IncomeRepository.getInstance(),
                    EventRepository.getInstance(context),
                    ListRepository.getInstance(context),
                    SettingsRepository.getInstance(context)
                ) as T
            }
            throw IllegalArgumentException("Непознати ViewModel класа")
        }
    }
} 