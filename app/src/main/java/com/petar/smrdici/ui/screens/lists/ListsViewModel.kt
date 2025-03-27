package com.petar.smrdici.ui.screens.lists

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.petar.smrdici.data.model.ShoppingList
import com.petar.smrdici.data.model.ShoppingItem
import com.petar.smrdici.data.repository.ListsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*
import kotlinx.coroutines.delay

class ListsViewModel(
    private val listsRepository: ListsRepository
) : ViewModel() {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private val _uiState = MutableStateFlow<ListsUiState>(ListsUiState.Loading)
    val uiState: StateFlow<ListsUiState> = _uiState
    
    private val _selectedList = MutableStateFlow<ShoppingList?>(null)
    val selectedList: StateFlow<ShoppingList?> = _selectedList
    
    // Стање за праћење листа које су тренутно у процесу брисања
    private val _deletingListIds = MutableStateFlow<Set<String>>(emptySet())
    val deletingListIds: StateFlow<Set<String>> = _deletingListIds
    
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
    
    // Функција за брисање ставке из листе
    fun deleteItem(listId: String, itemId: String) {
        viewModelScope.launch {
            try {
                // Проверавамо да ли је ставка већ у процесу брисања
                if (_deletingListIds.value.contains(itemId)) {
                    Log.w("ListsViewModel", "Ставка $itemId је већ у процесу брисања")
                    return@launch
                }
                
                // Додајемо ID ставке у листу оних које се бришу
                _deletingListIds.value += itemId
                
                // Чувамо тренутно стање за случај поништавања
                val currentList = _selectedList.value ?: return@launch
                val originalItems = currentList.items
                
                // Проналазимо ставку коју треба обрисати
                val itemToDelete = originalItems.find { it.id == itemId } ?: run {
                    Log.e("ListsViewModel", "Ставка са ID-ем $itemId није пронађена")
                    // Уклањамо ID ставке из листе оних које се бришу
                    _deletingListIds.value -= itemId
                    return@launch
                }
                
                // Креирамо нову листу ставки без обрисане ставке
                val updatedItems = originalItems.filter { it.id != itemId }
                
                // Проактивно ажурирамо UI пре завршетка операције у бази
                _selectedList.value = currentList.copy(items = updatedItems)
                
                // Ажурирамо листу у Firestore-у
                firestore.collection("shopping_lists").document(listId)
                    .update("items", updatedItems)
                    .addOnSuccessListener {
                        Log.d("ListsViewModel", "Ставка $itemId успешно обрисана")
                        
                        // Уклањамо ID ставке из листе оних које се бришу
                        _deletingListIds.value -= itemId
                    }
                    .addOnFailureListener { e ->
                        Log.e("ListsViewModel", "Грешка приликом брисања ставке: ${e.message}")
                        
                        // Враћамо првобитно стање у случају грешке
                        _selectedList.value = currentList.copy(items = originalItems)
                        
                        // Уклањамо ID ставке из листе оних које се бришу
                        _deletingListIds.value -= itemId
                    }
            } catch (e: Exception) {
                Log.e("ListsViewModel", "Општа грешка приликом брисања ставке: ${e.message}")
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
    
    // Функција за враћање избрисане ставке
    fun restoreItem(itemId: String, listId: String, originalItem: ShoppingItem) {
        viewModelScope.launch {
            try {
                // Проверавамо да ли је ставка већ у процесу брисања или враћања
                if (_deletingListIds.value.contains(itemId)) {
                    Log.w("ListsViewModel", "Ставка $itemId је у процесу брисања, не можемо је вратити")
                    return@launch
                }
                
                // Чувамо тренутни UI стање
                val currentState = _selectedList.value
                val currentItems = currentState?.items ?: emptyList()
                
                // Правимо копију ставке са истим ID-ем
                val itemToRestore = originalItem
                
                // Проактивно ажурирамо UI - додајемо ставку назад у листу
                if (currentState != null) {
                    val updatedItems = currentItems + listOf(itemToRestore)
                    _selectedList.value = currentState.copy(items = updatedItems)
                }
                
                // Добављамо најновију верзију листе из Firestore-а
                firestore.collection("shopping_lists").document(listId).get()
                    .addOnSuccessListener { document ->
                        if (document != null && document.exists()) {
                            val list = document.toObject(ShoppingList::class.java)
                            if (list != null) {
                                // Додајемо ставку назад у листу
                                val updatedItems = list.items.toMutableList()
                                updatedItems.add(itemToRestore)
                                
                                // Ажурирамо листу у Firestore-у
                                firestore.collection("shopping_lists").document(listId)
                                    .update("items", updatedItems)
                                    .addOnSuccessListener {
                                        Log.d("ListsViewModel", "Ставка $itemId успешно враћена")
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("ListsViewModel", "Грешка приликом враћања ставке ${itemId}: ${e.message}")
                                        
                                        // У случају грешке, враћамо оригинално стање UI-а
                                        if (currentState != null) {
                                            _selectedList.value = currentState
                                        }
                                    }
                            } else {
                                Log.e("ListsViewModel", "Листа постоји али није могла бити претворена у објекат")
                                
                                // У случају грешке, враћамо оригинално стање UI-а
                                if (currentState != null) {
                                    _selectedList.value = currentState
                                }
                            }
                        } else {
                            Log.e("ListsViewModel", "Листа не постоји")
                            
                            // У случају грешке, враћамо оригинално стање UI-а
                            if (currentState != null) {
                                _selectedList.value = currentState
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("ListsViewModel", "Грешка приликом добављања листе: ${e.message}")
                        
                        // У случају грешке, враћамо оригинално стање UI-а
                        if (currentState != null) {
                            _selectedList.value = currentState
                        }
                    }
            } catch (e: Exception) {
                Log.e("ListsViewModel", "Општа грешка приликом враћања ставке: ${e.message}")
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
                return ListsViewModel(
                    ListsRepository(context)
                ) as T
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

}

// Стање корисничког интерфејса
sealed class ListsUiState {
    data object Loading : ListsUiState()
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