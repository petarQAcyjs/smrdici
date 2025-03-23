package com.petar.smrdici.ui.screens.lists

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.data.model.ShoppingItem
import com.petar.smrdici.data.model.ShoppingList
import kotlinx.coroutines.delay
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ListDetailsScreen(
    navController: NavController,
    listId: String,
    listsViewModel: ListsViewModel = viewModel(factory = ListsViewModel.Factory(LocalContext.current))
) {
    val selectedList by listsViewModel.selectedList.collectAsState()
    var newItemText by remember { mutableStateOf("") }
    var isAddingNewItem by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Додајемо корутински опсег за Compose компоненту
    val coroutineScope = rememberCoroutineScope()
    
    // Додајемо стање за праћење када треба поново фокусирати поље
    var shouldRefocus by remember { mutableStateOf(false) }
    
    // Додајемо стање за дијалог за потврду брисања
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    
    // Додајемо стање за освежавање
    var isRefreshing by remember { mutableStateOf(false) }
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = isRefreshing)
    
    // Функција за освежавање листе
    val refreshList = {
        coroutineScope.launch {
            isRefreshing = true
            listsViewModel.loadListById(listId)
            delay(1000) // Минимално трајање анимације освежавања
            isRefreshing = false
        }
    }
    
    // Учитавање листе при првом рендеровању
    LaunchedEffect(listId) {
        isRefreshing = true
        listsViewModel.loadListById(listId)
        delay(500) // Кратко одлагање за иницијално учитавање
        isRefreshing = false
    }
    
    // Фокусирамо поље за унос када се активира или када треба поново фокусирати
    LaunchedEffect(isAddingNewItem, shouldRefocus) {
        if (isAddingNewItem) {
            try {
                // Мало одлагање да би се осигурало да је компонента рендерована
                delay(100)
                focusRequester.requestFocus()
                // Ресетујемо стање за поновно фокусирање
                if (shouldRefocus) {
                    shouldRefocus = false
                }
            } catch (e: Exception) {
                // Игноришемо грешку ако компонента још није спремна
            }
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Заглавље са дугметом за повратак
            TopAppBar(
                title = { 
                    Text(
                        text = selectedList?.title ?: "Листа",
                        style = MaterialTheme.typography.headlineMedium
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                actions = {
                    // Дугме за промену статуса листе (завршено/незавршено)
                    selectedList?.id?.let { id ->
                        IconButton(
                            onClick = { listsViewModel.toggleListStatus(id) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Промени статус",
                                tint = if (selectedList?.isCompleted == true) 
                                    Color(0xFF4CAF50) else Color.Gray
                            )
                        }
                        
                        // Дугме за брисање листе
                        IconButton(
                            onClick = { showDeleteConfirmDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Обриши листу",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
            
            // Информације о листи са подршком за освежавање превлачењем
            SwipeRefresh(
                state = swipeRefreshState,
                onRefresh = { refreshList() },
                modifier = Modifier.fillMaxSize()
            ) {
                selectedList?.let { list ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Статус листе
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Статус: ",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (list.isCompleted) "Завршено" else "У току",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Број ставки
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Број ставки: ",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${list.items.size}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        
                        Divider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        )
                        
                        // Листа ставки
                        Text(
                            text = "Ставке",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(list.items) { item ->
                                ShoppingItemRow(
                                    item = item,
                                    onToggle = { 
                                        listsViewModel.toggleItemStatus(list.id ?: "", item.id)
                                    },
                                    onDelete = {
                                        listsViewModel.deleteItemFromList(list.id ?: "", item.id)
                                    }
                                )
                            }
                            
                            // Поље за унос нове ставке
                            if (isAddingNewItem) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = false,
                                            onCheckedChange = null
                                        )
                                        
                                        OutlinedTextField(
                                            value = newItemText,
                                            onValueChange = { newItemText = it },
                                            placeholder = { Text("Унесите назив ставке") },
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                                .focusRequester(focusRequester),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                            keyboardActions = KeyboardActions(
                                                onDone = {
                                                    if (newItemText.isNotBlank()) {
                                                        listsViewModel.addItemToList(newItemText)
                                                        newItemText = ""
                                                        // Постављамо заставицу да треба поново фокусирати поље
                                                        shouldRefocus = true
                                                    }
                                                }
                                            )
                                        )
                                        
                                        IconButton(
                                            onClick = {
                                                if (newItemText.isNotBlank()) {
                                                    listsViewModel.addItemToList(newItemText)
                                                    newItemText = ""
                                                    // Постављамо заставицу да треба поново фокусирати поље
                                                    shouldRefocus = true
                                                } else {
                                                    isAddingNewItem = false
                                                    keyboardController?.hide()
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Додај ставку"
                                            )
                                        }
                                    }
                                }
                            }
                            
                            if (list.items.isEmpty() && !isAddingNewItem) {
                                item {
                                    Text(
                                        text = "Нема ставки у листи",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(vertical = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                } ?: run {
                    // Приказ учитавања ако листа још није учитана
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!isRefreshing) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
        
        // Плутајуће дугме за додавање нове ставке
        FloatingActionButton(
            onClick = { 
                isAddingNewItem = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Додај нову ставку"
            )
        }
    }
    
    // Дијалог за потврду брисања листе
    if (showDeleteConfirmDialog) {
        // Проверавамо да ли је листа предефинисана
        val isPredefinedList = selectedList?.title == "Spisak za prodavnicu" || selectedList?.title == "Kućni poslovi"
        
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(if (isPredefinedList) "Није могуће обрисати" else "Брисање листе") },
            text = { 
                Text(
                    if (isPredefinedList) 
                        "Предефинисане листе не могу бити обрисане." 
                    else 
                        "Да ли сте сигурни да желите да обришете ову листу?"
                ) 
            },
            confirmButton = {
                if (!isPredefinedList) {
                    Button(
                        onClick = {
                            selectedList?.id?.let { id ->
                                listsViewModel.deleteShoppingList(id)
                                navController.navigateUp()
                            }
                            showDeleteConfirmDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Обриши")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(if (isPredefinedList) "У реду" else "Откажи")
                }
            }
        )
    }
}