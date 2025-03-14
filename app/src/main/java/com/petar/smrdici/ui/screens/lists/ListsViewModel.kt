package com.petar.smrdici.ui.screens.lists

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.petar.smrdici.data.model.ShoppingItem
import com.petar.smrdici.data.model.ShoppingList
import com.petar.smrdici.data.repository.ShoppingListRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.UUID

class ListsViewModel(private val context: Context) : ViewModel() {
    private val repository = ShoppingListRepository(context)
    
    private val _uiState = MutableStateFlow<ListsUiState>(ListsUiState.Loading)
    val uiState: StateFlow<ListsUiState> = _uiState
    
    private val _selectedList = MutableStateFlow<ShoppingList?>(null)
    val selectedList: StateFlow<ShoppingList?> = _selectedList
    
    // Форма за унос нове листе
    private val _listFormState = MutableStateFlow(ListFormState())
    val listFormState: StateFlow<ListFormState> = _listFormState
    
    // Форма за унос нове ставке
    private val _itemFormState = MutableStateFlow(ItemFormState())
    val itemFormState: StateFlow<ItemFormState> = _itemFormState
    
    init {
        loadShoppingLists()
    }
    
    private fun loadShoppingLists() {
        viewModelScope.launch {
            repository.getShoppingListsForCurrentUser()
                .catch { e ->
                    _uiState.value = ListsUiState.Error(e.message ?: "Грешка при учитавању листа")
                }
                .collect { lists ->
                    _uiState.value = ListsUiState.Success(lists)
                }
        }
    }
    
    // Избор листе
    fun selectList(list: ShoppingList) {
        _selectedList.value = list
    }
    
    // Поништавање избора листе
    fun clearSelectedList() {
        _selectedList.value = null
    }
    
    // Ажурирање форме за унос листе
    fun updateListForm(update: (ListFormState) -> ListFormState) {
        _listFormState.value = update(_listFormState.value)
    }
    
    // Ажурирање форме за унос ставке
    fun updateItemForm(update: (ItemFormState) -> ItemFormState) {
        _itemFormState.value = update(_itemFormState.value)
    }
    
    // Додавање нове листе
    fun addShoppingList() {
        viewModelScope.launch {
            val form = _listFormState.value
            
            if (!form.isValid) {
                return@launch
            }
            
            val newList = ShoppingList(
                title = form.title,
                createdAt = Timestamp.now()
            )
            
            repository.addShoppingList(newList)
                .onSuccess {
                    _listFormState.value = ListFormState()
                }
                .onFailure { e ->
                    _uiState.value = ListsUiState.Error(e.message ?: "Грешка при додавању листе")
                }
        }
    }
    
    // Брисање листе
    fun deleteShoppingList(listId: String) {
        viewModelScope.launch {
            repository.deleteShoppingList(listId)
                .onFailure { e ->
                    _uiState.value = ListsUiState.Error(e.message ?: "Грешка при брисању листе")
                }
        }
    }
    
    // Додавање нове ставке у листу
    fun addItemToList() {
        viewModelScope.launch {
            val form = _itemFormState.value
            val currentList = _selectedList.value
            
            if (!form.isValid || currentList == null) {
                return@launch
            }
            
            val newItem = ShoppingItem(
                id = UUID.randomUUID().toString(),
                name = form.name,
                quantity = form.quantity,
                addedAt = Timestamp.now()
            )
            
            val updatedItems = currentList.items + newItem
            val updatedList = currentList.copy(items = updatedItems)
            
            repository.updateShoppingList(updatedList)
                .onSuccess {
                    _selectedList.value = updatedList
                    _itemFormState.value = ItemFormState()
                }
                .onFailure { e ->
                    _uiState.value = ListsUiState.Error(e.message ?: "Грешка при додавању ставке")
                }
        }
    }
    
    // Ажурирање статуса ставке (завршено/незавршено)
    fun toggleItemStatus(itemId: String) {
        viewModelScope.launch {
            val currentList = _selectedList.value ?: return@launch
            
            val updatedItems = currentList.items.map { item ->
                if (item.id == itemId) {
                    item.copy(isCompleted = !item.isCompleted)
                } else {
                    item
                }
            }
            
            val updatedList = currentList.copy(items = updatedItems)
            
            repository.updateShoppingList(updatedList)
                .onSuccess {
                    _selectedList.value = updatedList
                }
                .onFailure { e ->
                    _uiState.value = ListsUiState.Error(e.message ?: "Грешка при ажурирању ставке")
                }
        }
    }
    
    // Брисање ставке из листе
    fun removeItemFromList(itemId: String) {
        viewModelScope.launch {
            val currentList = _selectedList.value ?: return@launch
            
            val updatedItems = currentList.items.filter { it.id != itemId }
            val updatedList = currentList.copy(items = updatedItems)
            
            repository.updateShoppingList(updatedList)
                .onSuccess {
                    _selectedList.value = updatedList
                }
                .onFailure { e ->
                    _uiState.value = ListsUiState.Error(e.message ?: "Грешка при брисању ставке")
                }
        }
    }
    
    // Додајемо Factory класу за креирање ListsViewModel са Context параметром
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ListsViewModel::class.java)) {
                return ListsViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

// Стање корисничког интерфејса
sealed class ListsUiState {
    object Loading : ListsUiState()
    data class Success(val lists: List<ShoppingList>) : ListsUiState()
    data class Error(val message: String) : ListsUiState()
}

// Стање форме за унос листе
data class ListFormState(
    val title: String = ""
) {
    val isValid: Boolean
        get() = title.isNotBlank()
}

// Стање форме за унос ставке
data class ItemFormState(
    val name: String = "",
    val quantity: Int = 1
) {
    val isValid: Boolean
        get() = name.isNotBlank() && quantity > 0
} 