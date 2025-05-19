package com.petar.smrdici.ui.screens.lists

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.petar.smrdici.data.model.ShoppingList
import com.petar.smrdici.data.model.ShoppingItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*
import kotlinx.coroutines.delay

class ListsViewModel : ViewModel() {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private val _uiState = MutableStateFlow<ListsUiState>(ListsUiState.Loading)
    val uiState: StateFlow<ListsUiState> = _uiState
    
    private val _selectedList = MutableStateFlow<ShoppingList?>(null)
    val selectedList: StateFlow<ShoppingList?> = _selectedList
    
    // Стање за праћење листа које су тренутно у процесу брисања
    private val _deletingListIds = MutableStateFlow<Set<String>>(emptySet())
    val deletingListIds: StateFlow<Set<String>> = _deletingListIds

    init {
        loadLists()
    }
    
    fun loadLists() {
        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: return@launch
                val familyId = "default" // Подразумевана породица за дељење
                
                // Узимамо тренутно стање пре учитавања новог
                val currentState = _uiState.value
                val currentLists = if (currentState is ListsUiState.Success) currentState.lists else emptyList()
                
                // Постављамо стање учитавања само ако немамо претходне податке
                if (currentLists.isEmpty()) {
                    _uiState.value = ListsUiState.Loading
                }
                
                // Додајемо логер за праћење
                Log.d("ListsViewModel", "Започињем учитавање листа...")
                
                // Користимо два упита - један за све листе које је креирао корисник
                // и један за све листе у породици којој припада корисник
                val userListsQuery = firestore.collection("shopping_lists")
                    .whereEqualTo("createdBy", userId)
                
                val familyListsQuery = firestore.collection("shopping_lists")
                    .whereEqualTo("familyId", familyId)
                
                // Припремамо мапу за чување параметара комплетираних ставки из тренутног UI стања
                val completedStatusMap = currentLists.associate { it.id to it.isCompleted }
                
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
                                    if (list != null) {
                                        list.id = doc.id
                                        
                                        // Важно: очувајмо локално стање комплетираности
                                        // Ово избегава бесконачну петљу ако преузети подаци још увек немају најновије стање
                                        if (completedStatusMap.containsKey(doc.id)) {
                                            val localStatus = completedStatusMap[doc.id]
                                            // Ако локално стање постоји, користимо га
                                            if (localStatus != null && localStatus != list.isCompleted) {
                                                Log.d("ListsViewModel", "Очували смо локални статус за листу ${doc.id}: $localStatus (уместо ${list.isCompleted})")
                                                list.isCompleted = localStatus
                                            }
                                        }
                                        
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
                            
                            // Дебаг лог за праћење ажурирања
                            Log.d("ListsViewModel", "loadLists: Учитано ${filteredLists.size} листа")
                            
                            // Дебаг лог за статусе учитаних листа
                            filteredLists.forEach { list ->
                                Log.d("ListsViewModel", "Листа ${list.id}: ${list.title}, isCompleted: ${list.isCompleted}")
                            }
                            
                            _uiState.value = ListsUiState.Success(filteredLists)
                        }
                    }.addOnFailureListener { e ->
                        Log.e("ListsViewModel", "Грешка при учитавању породичних листа", e)
                        
                        // Ако имамо претходне податке, задржавамо их уместо да прикажемо грешку
                        if (currentLists.isNotEmpty()) {
                            _uiState.value = ListsUiState.Success(currentLists)
                        } else {
                            _uiState.value = ListsUiState.Error(e.message ?: "Грешка при учитавању листа")
                        }
                    }
                }.addOnFailureListener { e ->
                    Log.e("ListsViewModel", "Грешка при учитавању корисничких листа", e)
                    
                    // Ако имамо претходне податке, задржавамо их уместо да прикажемо грешку
                    if (currentLists.isNotEmpty()) {
                        _uiState.value = ListsUiState.Success(currentLists)
                    } else {
                        _uiState.value = ListsUiState.Error(e.message ?: "Грешка при учитавању листа")
                    }
                }
            } catch (e: Exception) {
                Log.e("ListsViewModel", "Општа грешка", e)
                
                // Узимамо тренутно стање за случај грешке
                val currentState = _uiState.value
                val currentLists = if (currentState is ListsUiState.Success) currentState.lists else emptyList()
                
                // Ако имамо претходне податке, задржавамо их уместо да прикажемо грешку
                if (currentLists.isNotEmpty()) {
                    _uiState.value = ListsUiState.Success(currentLists)
                } else {
                    _uiState.value = ListsUiState.Error(e.message ?: "Непозната грешка")
                }
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

    // Брисање листе
    fun deleteShoppingList(listId: String) {
        viewModelScope.launch {
            try {
                // Проверавамо да ли је листа већ у процесу брисања
                if (_deletingListIds.value.contains(listId)) {
                    Log.w("ListsViewModel", "Листа $listId је већ у процесу брисања")
                    return@launch
                }
                
                // Додајемо ID у сет листа које се бришу
                _deletingListIds.value += listId
                
                // Спремамо тренутно стање за случај грешке
                val currentState = _uiState.value
                
                firestore.collection("shopping_lists")
                    .document(listId)
                    .delete()
                    .addOnSuccessListener {
                        viewModelScope.launch {
                            Log.d("ListsViewModel", "Листа $listId успешно обрисана")
                            
                            // Уклањамо ID из сета листа које се бришу
                            _deletingListIds.value -= listId
                            
                            // Ажурирамо UI стање ако је потребно
                            if (currentState is ListsUiState.Success) {
                                val updatedLists = currentState.lists.filter { it.id != listId }
                                _uiState.value = ListsUiState.Success(updatedLists)
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        viewModelScope.launch {
                            Log.e("ListsViewModel", "Грешка при брисању листе $listId: ${e.message}")
                            
                            // Уклањамо ID из сета листа које се бришу
                            _deletingListIds.value -= listId
                            
                            // Враћамо претходно стање у случају грешке
                            if (currentState is ListsUiState.Success) {
                                _uiState.value = currentState
                            }
                            
                            // Постављамо стање грешке без бацања изузетка
                            _uiState.value = ListsUiState.Error(e.message ?: "Грешка при брисању листе")
                        }
                    }
            } catch (e: Exception) {
                // Уклањамо ID из сета листа које се бришу
                _deletingListIds.value -= listId
                
                Log.e("ListsViewModel", "Општа грешка при брисању листе $listId", e)
                _uiState.value = ListsUiState.Error(e.message ?: "Грешка при брисању листе")
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
                    note = "",
                    createdAt = Timestamp.now()
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
    
    // Ажурирање статуса листе (завршено/незавршено)
    fun toggleListStatus(listId: String) {
        viewModelScope.launch {
            try {
                // Директно добављамо најсвежију верзију листе из Firestore-а
                firestore.collection("shopping_lists").document(listId)
                    .get()
                    .addOnSuccessListener { document ->
                        val list = document.toObject(ShoppingList::class.java)
                        if (list != null) {
                            // Постављамо ID јер Firebase то не чини аутоматски
                            list.id = document.id
                            
                            // Бележимо нови статус (обрнуто од тренутног)
                            val newIsCompleted = !list.isCompleted
                            
                            // Креирамо ажурирану листу са обрнутим статусом
                            val updatedList = list.copy(isCompleted = newIsCompleted)
                            
                            // Одмах ажурирамо UI стање (оптимистички)
                            val currentUiState = _uiState.value
                            if (currentUiState is ListsUiState.Success) {
                                val updatedLists = currentUiState.lists.map { uiList ->
                                    if (uiList.id == listId) {
                                        uiList.copy(isCompleted = newIsCompleted)
                                    } else {
                                        uiList
                                    }
                                }
                                _uiState.value = ListsUiState.Success(updatedLists)
                            }
                            
                            // Ажурирамо листу у Firestore-у
                            firestore.collection("shopping_lists").document(listId)
                                .set(updatedList)
                                .addOnSuccessListener {
                                    Log.d("ListsViewModel", "Успешно ажуриран статус листе на: $newIsCompleted")
                                    
                                    // Ако је ова листа тренутно изабрана, ажурирамо и њено стање
                                    if (_selectedList.value?.id == listId) {
                                        _selectedList.value = updatedList
                                    }
                                    
                                    // НЕ позивамо loadLists() - оптимистичко ажурирање је довољно
                                }
                                .addOnFailureListener { e ->
                                    Log.e("ListsViewModel", "Грешка при ажурирању листе: ${e.message}")
                                    // У случају грешке, враћамо првобитно стање са кратким одлагањем
                                    viewModelScope.launch {
                                        delay(500)
                                        loadLists()
                                    }
                                }
                        } else {
                            Log.e("ListsViewModel", "Листа није пронађена: $listId")
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("ListsViewModel", "Грешка при учитавању листе: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.e("ListsViewModel", "Општа грешка: ${e.message}")
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
    
    // Функција за брисање ставке из листе
    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            try {
                // Чувамо копију ставке пре брисања за случај да корисник жели да је врати
                val currentList = _selectedList.value ?: return@launch
                val itemToDelete = currentList.items.find { it.id == itemId }
                
                if (itemToDelete != null) {
                    // Креирамо нову листу ставки без обрисане ставке
                    val updatedItems = currentList.items.filter { it.id != itemId }
                    
                    // Ажурирамо локалну листу одмах
                    _selectedList.value = currentList.copy(items = updatedItems)
                    
                    // Затим бришемо из базе података
                    // Додајемо проверу да ли је ID листе нулабилан
                    val listId = currentList.id
                    if (listId != null && listId.isNotEmpty()) {
                        firestore.collection("shopping_lists").document(listId)
                            .update("items", updatedItems)
                            .addOnSuccessListener {
                                Log.d("ListsViewModel", "Ставка $itemId успешно обрисана")
                            }
                            .addOnFailureListener { e ->
                                Log.e("ListsViewModel", "Грешка при брисању ставке", e)
                            }
                    } else {
                        Log.e("ListsViewModel", "Грешка: ID листе је null или празан")
                    }
                }
            } catch (e: Exception) {
                Log.e("ListsViewModel", "Грешка при брисању ставке", e)
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
                // Проверавамо да ли листа има ID
                val listId = list.id
                if (listId == null) {
                    Log.e("ListsViewModel", "Не можемо вратити листу без ID-a")
                    return@launch
                }
                
                // Проверавамо да ли је ова листа већ у процесу брисања или враћања
                if (_deletingListIds.value.contains(listId)) {
                    Log.w("ListsViewModel", "Листа $listId је у процесу брисања, не можемо је вратити")
                    return@launch
                }
                
                // Спремамо тренутне листе у случају да треба да вратимо претходно стање
                val currentState = _uiState.value
                val currentLists = if (currentState is ListsUiState.Success) currentState.lists else emptyList()
                
                // Правимо копију листе без ID-а да бисмо је додали у Firestore
                val listToRestore = list.copy()
                
                // Проактивно ажурирамо UI - додајемо листу назад у листе
                if (currentState is ListsUiState.Success) {
                    val updatedLists = currentLists + listOf(list)
                    _uiState.value = ListsUiState.Success(updatedLists.sortedByDescending { it.createdAt.seconds })
                }
                
                // Додајемо листу назад у Firestore
                firestore.collection("shopping_lists")
                    .document(listId) // Користимо исти ID
                    .set(listToRestore)
                    .addOnSuccessListener { documentReference ->
                        Log.d("ListsViewModel", "Листа $listId успешно враћена")
                    }
                    .addOnFailureListener { e ->
                        Log.e("ListsViewModel", "Грешка приликом враћања листе ${listId}: ${e.message}")
                        
                        // У случају грешке, враћамо оригинално стање UI-а
                        if (currentState is ListsUiState.Success) {
                            _uiState.value = currentState
                        } else {
                            _uiState.value = ListsUiState.Error("Грешка приликом враћања листе: ${e.message}")
                        }
                    }
            } catch (e: Exception) {
                // У случају грешке, ажурирамо UI стање
                Log.e("ListsViewModel", "Општа грешка приликом враћања листе: ${e.message}")
                _uiState.value = ListsUiState.Error("Грешка приликом враћања листе: ${e.message}")
            }
        }
    }
    
    // Функција за враћање обрисане ставке
    fun restoreItem(itemId: String, item: ShoppingItem) {
        viewModelScope.launch {
            try {
                val currentList = _selectedList.value ?: return@launch
                
                // Проверавамо да ли ставка са истим ID-ем већ постоји у листи
                val itemExists = currentList.items.any { it.id == itemId }
                if (itemExists) {
                    Log.w("ListsViewModel", "Ставка $itemId већ постоји у листи, не можемо је вратити")
                    return@launch
                }
                
                // Креирамо нову листу ставки са враћеном ставком
                val updatedItems = currentList.items.toMutableList()
                updatedItems.add(item)
                
                // Ажурирамо локалну листу одмах
                _selectedList.value = currentList.copy(items = updatedItems)
                
                // Затим ажурирамо базу података
                val listIdNonNull = currentList.id
                if (listIdNonNull != null && listIdNonNull.isNotEmpty()) {
                    firestore.collection("shopping_lists").document(listIdNonNull)
                        .update("items", updatedItems)
                        .addOnSuccessListener {
                            Log.d("ListsViewModel", "Ставка $itemId успешно враћена")
                        }
                        .addOnFailureListener { e ->
                            Log.e("ListsViewModel", "Грешка при враћању ставке", e)
                        }
                } else {
                    Log.e("ListsViewModel", "Грешка: ID листе је null или празан")
                }
            } catch (e: Exception) {
                Log.e("ListsViewModel", "Грешка при враћању ставке", e)
            }
        }
    }
    
    // Функција за директно ажурирање UI стања
    fun updateUiState(newState: ListsUiState) {
        _uiState.value = newState
    }
    
    // Додајемо Factory класу за креирање ListsViewModel
    @Suppress("UNUSED_PARAMETER")
    class Factory() : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ListsViewModel::class.java)) {
                return ListsViewModel() as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    // Ажурирање статуса ставке (завршено/незавршено) користећи нову функцију
    fun updateItemCompletionStatus(listId: String, itemId: String, isCompleted: Boolean) {
        viewModelScope.launch {
            try {
                val currentList = _selectedList.value ?: return@launch
                val updatedItems = currentList.items.map { item ->
                    if (item.id == itemId) item.copy(isCompleted = isCompleted) else item
                }
                
                val updatedList = currentList.copy(items = updatedItems)
                _selectedList.value = updatedList
                
                firestore.collection("shopping_lists").document(listId)
                    .update("items", updatedItems)
            } catch (e: Exception) {
                _uiState.value = ListsUiState.Error("Грешка при ажурирању ставке: ${e.message}")
            }
        }
    }

    fun updateAllItemsCompletionStatus(listId: String, isCompleted: Boolean) {
        viewModelScope.launch {
            try {
                val currentList = _selectedList.value ?: return@launch
                val updatedItems = currentList.items.map { item ->
                    item.copy(isCompleted = isCompleted)
                }
                
                val updatedList = currentList.copy(items = updatedItems)
                _selectedList.value = updatedList
                
                firestore.collection("shopping_lists").document(listId)
                    .update("items", updatedItems)
            } catch (e: Exception) {
                _uiState.value = ListsUiState.Error("Грешка при ажурирању ставки: ${e.message}")
            }
        }
    }

    fun updateItemName(listId: String, itemId: String, newName: String) {
        viewModelScope.launch {
            try {
                val currentList = _selectedList.value ?: return@launch
                
                // Find and update the item
                val updatedItems = currentList.items.map { item ->
                    if (item.id == itemId) {
                        item.copy(name = newName)
                    } else {
                        item
                    }
                }
                
                // Create updated list
                val updatedList = currentList.copy(items = updatedItems)
                
                // Update in Firestore
                firestore.collection("shopping_lists").document(listId)
                    .set(updatedList)
                    .addOnSuccessListener {
                        _selectedList.value = updatedList
                    }
                    .addOnFailureListener { e ->
                        _uiState.value = ListsUiState.Error(e.message ?: "Грешка при ажурирању ставке")
                    }
            } catch (e: Exception) {
                _uiState.value = ListsUiState.Error(e.message ?: "Грешка при ажурирању ставке")
            }
        }
    }

    fun updateListTitle(listId: String, newTitle: String) {
        viewModelScope.launch {
            try {
                val currentList = _selectedList.value ?: return@launch
                
                // Create updated list with new title
                val updatedList = currentList.copy(title = newTitle)
                
                // Update in Firestore
                firestore.collection("shopping_lists").document(listId)
                    .set(updatedList)
                    .addOnSuccessListener {
                        _selectedList.value = updatedList
                        loadLists() // Refresh the lists to update UI
                    }
                    .addOnFailureListener { e ->
                        _uiState.value = ListsUiState.Error(e.message ?: "Грешка при ажурирању наслова")
                    }
            } catch (e: Exception) {
                _uiState.value = ListsUiState.Error(e.message ?: "Грешка при ажурирању наслова")
            }
        }
    }

    fun clearCompletedItems(listId: String) {
        viewModelScope.launch {
            try {
                val currentList = _selectedList.value ?: return@launch
                
                // Filter out completed items
                val updatedItems = currentList.items.filter { !it.isCompleted }
                
                // Create updated list
                val updatedList = currentList.copy(items = updatedItems)
                
                // Update in Firestore
                firestore.collection("shopping_lists").document(listId)
                    .set(updatedList)
                    .addOnSuccessListener {
                        _selectedList.value = updatedList
                    }
                    .addOnFailureListener { e ->
                        _uiState.value = ListsUiState.Error(e.message ?: "Грешка при брисању завршених ставки")
                    }
            } catch (e: Exception) {
                _uiState.value = ListsUiState.Error(e.message ?: "Грешка при брисању завршених ставки")
            }
        }
    }

    fun clearAllItems(listId: String) {
        viewModelScope.launch {
            try {
                val currentList = _selectedList.value ?: return@launch
                
                // Create updated list with empty items
                val updatedList = currentList.copy(items = emptyList())
                
                // Update in Firestore
                firestore.collection("shopping_lists").document(listId)
                    .set(updatedList)
                    .addOnSuccessListener {
                        _selectedList.value = updatedList
                    }
                    .addOnFailureListener { e ->
                        _uiState.value = ListsUiState.Error(e.message ?: "Грешка при брисању свих ставки")
                    }
            } catch (e: Exception) {
                _uiState.value = ListsUiState.Error(e.message ?: "Грешка при брисању свих ставки")
            }
        }
    }

    // Function to update list title
    fun updateListTitle(listId: String, newTitle: String) {
        viewModelScope.launch {
            try {
                val currentList = _selectedList.value ?: return@launch
                
                // Create updated list with new title
                val updatedList = currentList.copy(title = newTitle)
                
                // Update list in Firestore
                firestore.collection("shopping_lists").document(listId)
                    .set(updatedList)
                    .addOnSuccessListener {
                        _selectedList.value = updatedList
                        // Also update the list in the UI state if it exists there
                        val currentUiState = _uiState.value
                        if (currentUiState is ListsUiState.Success) {
                            val updatedLists = currentUiState.lists.map { list ->
                                if (list.id == listId) list.copy(title = newTitle) else list
                            }
                            _uiState.value = ListsUiState.Success(updatedLists)
                        }
                    }
                    .addOnFailureListener { e ->
                        _uiState.value = ListsUiState.Error(e.message ?: "Грешка при ажурирању наслова листе")
                    }
            } catch (e: Exception) {
                _uiState.value = ListsUiState.Error(e.message ?: "Грешка при ажурирању наслова листе")
            }
        }
    }

    // Function to update item name
    fun updateItemName(listId: String, itemId: String, newName: String) {
        viewModelScope.launch {
            try {
                val currentList = _selectedList.value ?: return@launch
                
                // Update the item name in the list
                val updatedItems = currentList.items.map { item ->
                    if (item.id == itemId) {
                        item.copy(name = newName)
                    } else {
                        item
                    }
                }
                
                // Create updated list with new items
                val updatedList = currentList.copy(items = updatedItems)
                
                // Update list in Firestore
                firestore.collection("shopping_lists").document(listId)
                    .set(updatedList)
                    .addOnSuccessListener {
                        _selectedList.value = updatedList
                    }
                    .addOnFailureListener { e ->
                        _uiState.value = ListsUiState.Error(e.message ?: "Грешка при ажурирању назива ставке")
                    }
            } catch (e: Exception) {
                _uiState.value = ListsUiState.Error(e.message ?: "Грешка при ажурирању назива ставке")
            }
        }
    }

}

// Стање корисничког интерфејса
sealed class ListsUiState {
    data object Loading : ListsUiState()
    data class Success(val lists: List<ShoppingList>) : ListsUiState()
    data class Error(val message: String) : ListsUiState()
} 