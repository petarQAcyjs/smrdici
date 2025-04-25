package com.petar.smrdici.ui.screens.transfer

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.petar.smrdici.data.model.Account
import com.petar.smrdici.data.repository.AccountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel за управљање трансферима између рачуна
 */
class TransferViewModel(
    private val accountRepository: AccountRepository
) : ViewModel() {
    
    // UI стање
    private val _uiState = MutableStateFlow(TransferUiState())
    val uiState: StateFlow<TransferUiState> = _uiState.asStateFlow()
    
    // Листа рачуна
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()
    
    // Селектовани рачуни
    private val _sourceAccountId = mutableStateOf<String?>(null)
    val sourceAccountId: String? get() = _sourceAccountId.value
    
    private val _destinationAccountId = mutableStateOf<String?>(null)
    val destinationAccountId: String? get() = _destinationAccountId.value
    
    // Износ за трансфер
    private val _amount = mutableStateOf("")
    val amount: String get() = _amount.value
    
    // Опис трансфера
    private val _description = mutableStateOf("Трансфер новца")
    val description: String get() = _description.value
    
    // Историја трансфера
    private val _transferHistory = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val transferHistory: StateFlow<List<Map<String, Any>>> = _transferHistory.asStateFlow()
    
    // Статус операције
    private val _isLoading = mutableStateOf(false)
    val isLoading: Boolean get() = _isLoading.value
    
    init {
        loadAccounts()
        loadTransferHistory()
    }
    
    // Учитавање рачуна
    private fun loadAccounts() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val accountList = accountRepository.getAllAccounts()
                _accounts.value = accountList
                
                // Постављамо подразумеване рачуне ако их има
                if (accountList.isNotEmpty() && _sourceAccountId.value == null) {
                    val defaultAccount = accountList.find { it.isDefault }
                    _sourceAccountId.value = defaultAccount?.id ?: accountList.first().id
                }
                
                _uiState.value = _uiState.value.copy(error = null)
            } catch (e: Exception) {
                Log.e(TAG, "Грешка при учитавању рачуна", e)
                _uiState.value = _uiState.value.copy(error = "Грешка при учитавању рачуна: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    // Учитавање историје трансфера
    private fun loadTransferHistory() {
        viewModelScope.launch {
            try {
                _transferHistory.value = accountRepository.getTransferHistory()
            } catch (e: Exception) {
                Log.e(TAG, "Грешка при учитавању историје трансфера", e)
            }
        }
    }
    
    // Постављање изворног рачуна
    fun setSourceAccount(accountId: String) {
        _sourceAccountId.value = accountId
        
        // Ресетујемо дестинациони рачун ако је исти као изворни
        if (_destinationAccountId.value == accountId) {
            _destinationAccountId.value = null
        }
    }
    
    // Постављање дестинационог рачуна
    fun setDestinationAccount(accountId: String) {
        _destinationAccountId.value = accountId
        
        // Ресетујемо изворни рачун ако је исти као дестинациони
        if (_sourceAccountId.value == accountId) {
            _sourceAccountId.value = null
        }
    }
    
    // Ажурирање износа
    fun setAmount(amount: String) {
        _amount.value = amount
    }
    
    // Ажурирање описа
    fun setDescription(description: String) {
        _description.value = description
    }
    
    // Извршавање трансфера
    fun executeTransfer(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Валидација улазних података
                val sourceId = _sourceAccountId.value
                    ?: throw IllegalStateException("Морате изабрати изворни рачун")
                
                val destId = _destinationAccountId.value
                    ?: throw IllegalStateException("Морате изабрати циљни рачун")
                
                if (sourceId == destId) {
                    throw IllegalArgumentException("Изворни и циљни рачун не могу бити исти")
                }
                
                val amountValue = _amount.value.toDoubleOrNull()
                    ?: throw IllegalArgumentException("Унесите валидан износ")
                
                if (amountValue <= 0) {
                    throw IllegalArgumentException("Износ мора бити већи од нуле")
                }
                
                // Извршавамо трансфер
                val result = accountRepository.transferFunds(
                    sourceAccountId = sourceId,
                    destinationAccountId = destId,
                    amount = amountValue,
                    description = _description.value
                )
                
                if (result.isSuccess) {
                    // Ресетујемо поља након успешног трансфера
                    _amount.value = ""
                    _description.value = "Трансфер новца"
                    
                    // Освежавамо податке
                    loadAccounts()
                    loadTransferHistory()
                    
                    // Обавештавамо о успеху
                    _uiState.value = _uiState.value.copy(
                        error = null,
                        successMessage = "Трансфер успешно обављен"
                    )
                    
                    // Позивамо callback за успех
                    onSuccess()
                } else {
                    // Обрађујемо грешку
                    val exception = result.exceptionOrNull()
                    Log.e(TAG, "Грешка при трансферу", exception)
                    _uiState.value = _uiState.value.copy(
                        error = "Грешка при трансферу: ${exception?.message ?: "Непозната грешка"}",
                        successMessage = null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Грешка при трансферу", e)
                _uiState.value = _uiState.value.copy(
                    error = "Грешка при трансферу: ${e.message}",
                    successMessage = null
                )
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    // Добављање рачуна по ID-у
    fun getAccountById(accountId: String?): Account? {
        if (accountId == null) return null
        return _accounts.value.find { it.id == accountId }
    }
    
    // Чишћење порука о успеху/грешци
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            error = null,
            successMessage = null
        )
    }
    
    // Фабрика за креирање ViewModel-а
    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TransferViewModel::class.java)) {
                val accountRepository = AccountRepository.getInstance()
                return TransferViewModel(accountRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
    
    companion object {
        private const val TAG = "TransferViewModel"
    }
}

/**
 * UI стање за трансфер новца
 */
data class TransferUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
) 