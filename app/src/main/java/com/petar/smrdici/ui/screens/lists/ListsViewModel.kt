package com.petar.smrdici.ui.screens.lists

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.petar.smrdici.data.model.ShoppingList
import com.petar.smrdici.data.model.ShoppingItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*
import kotlinx.coroutines.delay

class ListsViewModel(private val context: Context) : ViewModel() {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
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
        loadLists()
    }
    
    fun loadLists() {
        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: return@launch
                val familyId = "default" // Подразумевана породица за дељење
                
                _uiState.value = ListsUiState.Loading
                
                // Користимо два упита - један за све листе које је креирао корисник
                // и један за све листе у породици којој припада корисник
                val userListsQuery = firestore.collection("shopping_lists")
                    .whereEqualTo("createdBy", userId)
                
                val familyListsQuery = firestore.collection("shopping_lists")
                    .whereEqualTo("familyId", familyId)
                
                // Извршавамо први упит за листе корисника
                userListsQuery.get().addOnSuccessListener { userSnapshot ->
                    // Затим извршавамо други упит за породичне листе
                    familyListsQuery.get().addOnSuccessListener { familySnapshot ->
                        viewModelScope.launch {
                            // Комбинујемо резултате оба упита у једну листу
                            val allDocs = userSnapshot.documents + familySnapshot.documents
                            
                            // Креирамо мапу где је кључ ID документа да избегнемо дупликате
                            val uniqueListsMap = mutableMapOf<String, ShoppingList>()
                            
                            // Обрађујемо све документе и додајемо их у мапу
                            allDocs.forEach { doc ->
                                try {
                                    val list = doc.toObject(ShoppingList::class.java)
                                    list?.id = doc.id
                                    if (list != null) {
                                        uniqueListsMap[doc.id] = list
                                    }
                                } catch (e: Exception) {
                                    Log.e("ListsViewModel", "Грешка при обради листе", e)
                                }
                            }
                            
                            // Конвертујемо мапу у листу и сортирамо по времену креирања (опадајуће)
                            val lists = uniqueListsMap.values.toList()
                                .sortedByDescending { it.createdAt.seconds }
                            
                            // Филтрирамо листе да искључимо предефинисане листе из главног приказа
                            val filteredLists = lists.filter { list ->
                                list.title != "Spisak za prodavnicu" && list.title != "Kućni poslovi"
                            }
                            
                            _uiState.value = ListsUiState.Success(filteredLists)
                        }
                    }.addOnFailureListener { e ->
                        Log.e("ListsViewModel", "Грешка при учитавању породичних листа", e)
                        _uiState.value = ListsUiState.Error(e.message ?: "Грешка при учитавању листа")
                    }
                }.addOnFailureListener { e ->
                    Log.e("ListsViewModel", "Грешка при учитавању корисничких листа", e)
                    _uiState.value = ListsUiState.Error(e.message ?: "Грешка при учитавању листа")
                }
            } catch (e: Exception) {
                Log.e("ListsViewModel", "Општа грешка", e)
                _uiState.value = ListsUiState.Error(e.message ?: "Непозната грешка")
            }
        }
    }
    
    fun addList(title: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid
                if (userId == null) {
                    onError("Корисник није пријављен")
                    return@launch
                }

                // Креирамо нову инстанцу листе са подразумеваним familyId за дељење
                val newList = ShoppingList(
                    title = title.trim(),
                    createdBy = userId,
                    familyId = "default", // Подразумевана породица
                    createdAt = Timestamp.now(),
                    items = emptyList(),
                    isCompleted = false
                )

                firestore.collection("shopping_lists")
                    .add(newList)
                    .addOnSuccessListener { documentReference ->
                        Log.d("ListsViewModel", "Листа успешно додата: ${documentReference.id}")
                        onSuccess()
                        loadLists() // Ажурирамо листе да бисмо приказали нову листу
                    }
                    .addOnFailureListener { e ->
                        Log.e("ListsViewModel", "Грешка приликом додавања листе", e)
                        onError(e.message ?: "Непозната грешка при додавању листе")
                    }
            } catch (e: Exception) {
                Log.e("ListsViewModel", "Општа грешка при додавању листе", e)
                onError(e.message ?: "Непозната грешка при додавању листе")
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
            
            val userId = auth.currentUser?.uid ?: return@launch
            val familyId = "default" // Подразумевана породица за дељење
            
            val newList = ShoppingList(
                title = form.title,
                createdBy = userId,
                familyId = familyId,
                createdAt = Timestamp.now(),
                items = emptyList(),
                isCompleted = false
            )
            
            firestore.collection("shopping_lists")
                .add(newList)
                .addOnSuccessListener { documentReference ->
                    Log.d("ListsViewModel", "Листа додата са ID: ${documentReference.id}")
                    loadLists() // Поново учитавамо листе
                }
                .addOnFailureListener { e ->
                    Log.e("ListsViewModel", "Грешка при додавању листе", e)
                    _uiState.value = ListsUiState.Error(e.message ?: "Грешка при додавању листе")
                }
        }
    }
    
    // Брисање листе
    fun deleteShoppingList(listId: String) {
        viewModelScope.launch {
            try {
                // Прво проверавамо да ли је листа предефинисана
                firestore.collection("shopping_lists").document(listId)
                    .get()
                    .addOnSuccessListener { document ->
                        val list = document.toObject(ShoppingList::class.java)
                        if (list != null && (list.title == "Spisak za prodavnicu" || list.title == "Kućni poslovi")) {
                            // Не дозвољавамо брисање предефинисаних листа
                            _uiState.value = ListsUiState.Error("Предефинисане листе не могу бити обрисане")
                            return@addOnSuccessListener
                        }
                        
                        // Ако није предефинисана, бришемо је
                        firestore.collection("shopping_lists").document(listId).delete()
                            .addOnSuccessListener {
                                // Ресетујемо изабрану листу
                                _selectedList.value = null
                                // Поново учитавамо листе
                                loadLists()
                            }
                            .addOnFailureListener { e ->
                                _uiState.value = ListsUiState.Error(e.message ?: "Грешка при брисању листе")
                            }
                    }
                    .addOnFailureListener { e ->
                        _uiState.value = ListsUiState.Error(e.message ?: "Грешка при провери листе")
                    }
            } catch (e: Exception) {
                _uiState.value = ListsUiState.Error(e.message ?: "Непозната грешка")
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
            
            // Креирамо нову ставку
            val newItem = ShoppingItem(
                id = UUID.randomUUID().toString(),
                name = form.name,
                quantity = form.quantity,
                isCompleted = false,
                note = ""
            )
            
            // Додајемо нову ставку у листу постојећих ставки
            val updatedItems = currentList.items + newItem
            
            // Креирамо ажурирану листу
            val updatedList = currentList.copy(
                items = updatedItems
            )
            
            // Ажурирамо листу у Firestore-у
            firestore.collection("shopping_lists").document(currentList.id ?: "")
                .set(updatedList)
                .addOnSuccessListener {
                    _selectedList.value = updatedList
                    _itemFormState.value = ItemFormState()
                }
                .addOnFailureListener { e ->
                    _uiState.value = ListsUiState.Error(e.message ?: "Грешка при додавању ставке")
                }
        }
    }
    
    // Функција за додавање ставке у листу - ажурирана да прихвата listId
    fun addItemToList(listId: String, name: String, quantity: Int = 1) {
        viewModelScope.launch {
            try {
                // Добијамо тренутну листу
                loadListById(listId)
                val currentList = _selectedList.value ?: return@launch
                
                // Креирамо нову ставку
                val newItem = ShoppingItem(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    quantity = quantity,
                    isCompleted = false,
                    note = ""
                )
                
                // Додајемо нову ставку у листу постојећих ставки
                val updatedItems = currentList.items + newItem
                
                // Креирамо ажурирану листу
                val updatedList = currentList.copy(items = updatedItems)
                
                // Ажурирамо листу у Firestore-у
                firestore.collection("shopping_lists").document(listId)
                    .set(updatedList)
                    .addOnSuccessListener {
                        _selectedList.value = updatedList
                    }
                    .addOnFailureListener { e ->
                        _uiState.value = ListsUiState.Error(e.message ?: "Грешка при додавању ставке")
                    }
            } catch (e: Exception) {
                _uiState.value = ListsUiState.Error(e.message ?: "Грешка при додавању ставке")
            }
        }
    }
    
    // Стара верзија за компатибилност ако је потребно
    fun addItemToList(name: String, quantity: Int = 1) {
        viewModelScope.launch {
            val currentList = _selectedList.value ?: return@launch
            
            // Прослеђујемо на нову функцију
            addItemToList(currentList.id ?: return@launch, name, quantity)
        }
    }
    
    // Ажурирање статуса листе (завршено/незавршено)
    fun toggleListStatus(listId: String) {
        viewModelScope.launch {
            val currentList = _selectedList.value ?: return@launch
            
            val updatedList = currentList.copy(isCompleted = !currentList.isCompleted)
            
            firestore.collection("shopping_lists").document(listId).set(updatedList)
                .addOnSuccessListener {
                    _selectedList.value = updatedList
                }
                .addOnFailureListener { e ->
                    _uiState.value = ListsUiState.Error(e.message ?: "Грешка при ажурирању листе")
                }
        }
    }
    
    // Додајемо нову функцију за учитавање листе по ID-у
    fun loadListById(listId: String) {
        viewModelScope.launch {
            try {
                _uiState.value = ListsUiState.Loading
                
                firestore.collection("shopping_lists").document(listId)
                    .get()
                    .addOnSuccessListener { document ->
                        try {
                            val list = document.toObject(ShoppingList::class.java)
                            list?.id = document.id
                            if (list != null) {
                                _selectedList.value = list
                            } else {
                                _uiState.value = ListsUiState.Error("Листа није пронађена")
                            }
                        } catch (e: Exception) {
                            Log.e("ListsViewModel", "Грешка при обради листе", e)
                            _uiState.value = ListsUiState.Error(e.message ?: "Грешка при обради листе")
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("ListsViewModel", "Грешка при учитавању листе", e)
                        _uiState.value = ListsUiState.Error(e.message ?: "Грешка при учитавању листе")
                    }
            } catch (e: Exception) {
                Log.e("ListsViewModel", "Општа грешка", e)
                _uiState.value = ListsUiState.Error(e.message ?: "Непозната грешка")
            }
        }
    }
    
    // Додајемо функцију за промену статуса ставке
    fun toggleItemStatus(listId: String, itemId: String) {
        viewModelScope.launch {
            val currentList = _selectedList.value ?: return@launch
            
            // Ажурирамо статус ставке
            val updatedItems = currentList.items.map { item ->
                if (item.id == itemId) {
                    item.copy(isCompleted = !item.isCompleted)
                } else {
                    item
                }
            }
            
            // Креирамо ажурирану листу
            val updatedList = currentList.copy(items = updatedItems)
            
            // Ажурирамо листу у Firestore-у
            firestore.collection("shopping_lists").document(listId)
                .set(updatedList)
                .addOnSuccessListener {
                    _selectedList.value = updatedList
                }
                .addOnFailureListener { e ->
                    _uiState.value = ListsUiState.Error(e.message ?: "Грешка при ажурирању ставке")
                }
        }
    }
    
    // Додајемо функцију за брисање ставке из листе
    fun deleteItemFromList(listId: String, itemId: String) {
        viewModelScope.launch {
            val currentList = _selectedList.value ?: return@launch
            
            // Филтрирамо ставке да уклонимо ону коју желимо да обришемо
            val updatedItems = currentList.items.filter { it.id != itemId }
            
            // Креирамо ажурирану листу
            val updatedList = currentList.copy(items = updatedItems)
            
            // Ажурирамо листу у Firestore-у
            firestore.collection("shopping_lists").document(listId)
                .set(updatedList)
                .addOnSuccessListener {
                    _selectedList.value = updatedList
                }
                .addOnFailureListener { e ->
                    _uiState.value = ListsUiState.Error(e.message ?: "Грешка при брисању ставке")
                }
        }
    }
    
    // Функција за добијање или креирање предефинисане листе
    fun getOrCreatePredefinedList(title: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid
                if (userId == null) {
                    onError("Корисник није пријављен")
                    return@launch
                }
                
                val familyId = "default" // Подразумевана породица за дељење
                
                // Прво проверавамо да ли листа већ постоји
                firestore.collection("shopping_lists")
                    .whereEqualTo("title", title)
                    .whereEqualTo("familyId", familyId)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        if (!snapshot.isEmpty) {
                            // Листа постоји, враћамо њен ID
                            val listId = snapshot.documents[0].id
                            onSuccess(listId)
                        } else {
                            // Листа не постоји, креирамо нову
                            val newList = ShoppingList(
                                title = title,
                                createdBy = userId,
                                familyId = familyId,
                                createdAt = Timestamp.now(),
                                items = emptyList(),
                                isCompleted = false
                            )
                            
                            firestore.collection("shopping_lists")
                                .add(newList)
                                .addOnSuccessListener { documentReference ->
                                    Log.d("ListsViewModel", "Предефинисана листа креирана: ${documentReference.id}")
                                    onSuccess(documentReference.id)
                                }
                                .addOnFailureListener { e ->
                                    Log.e("ListsViewModel", "Грешка при креирању предефинисане листе", e)
                                    onError(e.message ?: "Грешка при креирању листе")
                                }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("ListsViewModel", "Грешка при провери предефинисане листе", e)
                        onError(e.message ?: "Грешка при провери листе")
                    }
            } catch (e: Exception) {
                Log.e("ListsViewModel", "Општа грешка", e)
                onError(e.message ?: "Непозната грешка")
            }
        }
    }
    
    // Функција за враћање избрисане листе
    fun restoreList(list: ShoppingList) {
        viewModelScope.launch {
            try {
                // Правимо копију листе без ID-а да бисмо је додали у Firestore
                val listToRestore = list.copy(id = null)
                
                // Додајемо листу назад у Firestore
                firestore.collection("shopping_lists")
                    .add(listToRestore)
                    .addOnSuccessListener { documentReference ->
                        // Ажурирамо UI након успешног враћања
                        loadLists()
                    }
                    .addOnFailureListener { e ->
                        _uiState.value = ListsUiState.Error("Грешка приликом враћања листе: ${e.message}")
                    }
            } catch (e: Exception) {
                // У случају грешке, ажурирамо UI стање
                _uiState.value = ListsUiState.Error("Грешка приликом враћања листе: ${e.message}")
            }
        }
    }
    
    // Функција за враћање избрисане ставке у листу
    fun restoreItem(listId: String, item: ShoppingItem) {
        viewModelScope.launch {
            try {
                // Добијамо тренутну листу
                val currentList = _selectedList.value ?: return@launch
                
                // Креирамо нову ставку (копирамо је да бисмо имали нови ID)
                val newItem = item.copy(id = UUID.randomUUID().toString())
                
                // Ажурирамо листу ставки
                val updatedItems = currentList.items + newItem
                
                // Креирамо ажурирану листу
                val updatedList = currentList.copy(items = updatedItems)
                
                // Ажурирамо листу у Firestore-у
                firestore.collection("shopping_lists").document(listId)
                    .set(updatedList)
                    .addOnSuccessListener {
                        // Ажурирамо локални податак
                        _selectedList.value = updatedList
                    }
                    .addOnFailureListener { e ->
                        _uiState.value = ListsUiState.Error("Грешка приликом враћања ставке: ${e.message}")
                    }
            } catch (e: Exception) {
                // У случају грешке, ажурирамо UI стање
                _uiState.value = ListsUiState.Error("Грешка приликом враћања ставке: ${e.message}")
            }
        }
    }
    
    // Функција за директно ажурирање UI стања
    fun updateUiState(newState: ListsUiState) {
        _uiState.value = newState
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

    // Ажурирање статуса ставке (завршено/незавршено) користећи нову функцију
    fun updateItemCompletionStatus(listId: String, itemId: String, isCompleted: Boolean) {
        viewModelScope.launch {
            val currentList = _selectedList.value ?: return@launch
            
            // Ажурирамо статус ставке
            val updatedItems = currentList.items.map { item ->
                if (item.id == itemId) {
                    item.copy(isCompleted = isCompleted)
                } else {
                    item
                }
            }
            
            // Креирамо ажурирану листу
            val updatedList = currentList.copy(items = updatedItems)
            
            // Ажурирамо листу у Firestore-у
            firestore.collection("shopping_lists").document(listId)
                .set(updatedList)
                .addOnSuccessListener {
                    _selectedList.value = updatedList
                }
                .addOnFailureListener { e ->
                    _uiState.value = ListsUiState.Error(e.message ?: "Грешка при ажурирању ставке")
                }
        }
    }
    
    // Брисање ставке из листе са новим именом
    fun deleteItem(listId: String, itemId: String) {
        viewModelScope.launch {
            val currentList = _selectedList.value ?: return@launch
            
            // Филтрирамо ставке да уклонимо ону коју желимо да обришемо
            val updatedItems = currentList.items.filter { it.id != itemId }
            
            // Креирамо ажурирану листу
            val updatedList = currentList.copy(items = updatedItems)
            
            // Ажурирамо листу у Firestore-у
            firestore.collection("shopping_lists").document(listId)
                .set(updatedList)
                .addOnSuccessListener {
                    _selectedList.value = updatedList
                }
                .addOnFailureListener { e ->
                    _uiState.value = ListsUiState.Error(e.message ?: "Грешка при брисању ставке")
                }
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