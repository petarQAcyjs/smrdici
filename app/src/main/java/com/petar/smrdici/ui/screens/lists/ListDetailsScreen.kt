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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.data.model.ShoppingItem
import com.petar.smrdici.data.model.ShoppingList
import kotlinx.coroutines.delay
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.rememberDismissState
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.fadeIn
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

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
    
    // Приказ инструкција за превлачење
    var showSwipeInstruction by remember { mutableStateOf(true) }
    LaunchedEffect(key1 = showSwipeInstruction) {
        if (showSwipeInstruction) {
            delay(5000) // Приказ инструкција 5 секунди
            showSwipeInstruction = false
        }
    }
    
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                        
                        // Инструкције за превлачење
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showSwipeInstruction && list.items.isNotEmpty(),
                            enter = androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.fadeOut()
                        ) {
                            Text(
                                text = "Превуците ставке удесно за брисање",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(list.items) { item ->
                                ShoppingItemRow(
                                    item = item,
                                    onDelete = { itemToDelete -> 
                                        listsViewModel.deleteItemFromList(list.id ?: "", itemToDelete.id)
                                    },
                                    onCheckedChange = { itemToToggle, isChecked ->
                                        listsViewModel.toggleItemStatus(list.id ?: "", itemToToggle.id)
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

@Composable
fun ShoppingItemRow(
    item: ShoppingItem,
    onDelete: (ShoppingItem) -> Unit,
    onCheckedChange: (ShoppingItem, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var show by remember { mutableStateOf(true) }
    var offsetX by remember { mutableStateOf(0f) }
    val view = LocalView.current
    
    // Израчунавамо праг за брисање
    val density = LocalDensity.current
    val deleteThreshold = with(density) { 100.dp.toPx() }
    
    // Стање за превлачење
    val draggableState = rememberDraggableState { delta ->
        offsetX += delta
        
        // Ако је прелазимо праг први пут, додајемо хаптичку повратну информацију
        if (offsetX > 50f && offsetX < 60f) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
    
    // Када је елемент потпуно одбачен, позовите onDelete
    LaunchedEffect(offsetX) {
        if (offsetX > deleteThreshold) {
            show = false
            delay(300) // Мала пауза за анимацију
            onDelete(item)
        }
    }
    
    androidx.compose.animation.AnimatedVisibility(
        visible = show,
        exit = slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }) + fadeOut()
    ) {
        Box {
            // Позадина која се приказује при превлачењу
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.error)
                    .padding(start = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Обриши",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Обриши ставку",
                        color = Color.White
                    )
                }
            }
            
            // Садржај који се може превлачити
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .draggable(
                        state = draggableState,
                        orientation = Orientation.Horizontal,
                        onDragStopped = {
                            // Ако не пређемо праг, враћамо елемент назад
                            if (offsetX <= deleteThreshold) {
                                offsetX = 0f
                            }
                        }
                    )
                    .offset { IntOffset(offsetX.roundToInt(), 0) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                val styleText = if (item.isCompleted) {
                    MaterialTheme.typography.bodyLarge.copy(
                        textDecoration = TextDecoration.LineThrough
                    )
                } else {
                    MaterialTheme.typography.bodyLarge
                }

                Checkbox(
                    checked = item.isCompleted,
                    onCheckedChange = { isChecked -> onCheckedChange(item, isChecked) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.primary
                    )
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = item.name,
                    style = styleText,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}