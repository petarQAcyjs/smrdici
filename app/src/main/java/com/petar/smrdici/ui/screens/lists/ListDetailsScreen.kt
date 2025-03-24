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
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.fadeIn
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult

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
    
    // Додајемо стање за Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Чувамо последњу обрисану ставку за повраћај
    var lastDeletedItem by remember { mutableStateOf<ShoppingItem?>(null) }
    
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
    
    // Основни изглед екрана
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = selectedList?.title ?: "Детаљи листе") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            selectedList?.id?.let { id ->
                                showDeleteConfirmDialog = true
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Обриши листу"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Приказ листе са подршком за освежавање
            SwipeRefresh(
                state = swipeRefreshState,
                onRefresh = { refreshList() },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Садржај листе
                when (val listState = selectedList) {
                    null -> {
                        // Приказ индикатора учитавања
                        if (!isRefreshing) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    else -> {
                        // Приказивање ставки користећи LazyColumn
                        listState.items?.let { items ->
                            ShoppingItemsList(
                                items = items,
                                listId = listId,
                                onCheckedChange = { item, isChecked ->
                                    listsViewModel.updateItemCompletionStatus(listId, item.id!!, isChecked)
                                },
                                onDeleteItem = { item ->
                                    listsViewModel.deleteItem(listId, item.id!!)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } ?: EmptyState(message = "Грешка при учитавању ставки.")
                    }
                }
            }
            
            // Доњи део са пољем за унос
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Поље за унос и дугме за додавање
                if (isAddingNewItem) {
                    // Приказивање поља за унос
                    OutlinedTextField(
                        value = newItemText,
                        onValueChange = { newItemText = it },
                        label = { Text("Унесите ставку") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onKeyEvent { keyEvent ->
                                // Ако је притиснуто Enter, додајемо ставку
                                if (keyEvent.key == Key.Enter && newItemText.isNotBlank()) {
                                    listsViewModel.addItemToList(listId, newItemText.trim())
                                    newItemText = ""
                                    isAddingNewItem = false
                                    true
                                } else {
                                    false
                                }
                            },
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (newItemText.isNotBlank()) {
                                    listsViewModel.addItemToList(listId, newItemText.trim())
                                    newItemText = ""
                                    isAddingNewItem = false
                                    keyboardController?.hide()
                                }
                            }
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (newItemText.isNotBlank()) {
                                        listsViewModel.addItemToList(listId, newItemText.trim())
                                        newItemText = ""
                                        // Останите у режиму додавања са фокусом на пољу за унос
                                        shouldRefocus = true
                                    } else {
                                        isAddingNewItem = false
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Додај ставку")
                            }
                        }
                    )
                } else {
                    // Приказивање дугмета за додавање нове ставке
                    Button(
                        onClick = { isAddingNewItem = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Додај нову ставку")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Додај нову ставку")
                    }
                }
            }
        }
        
        // Дијалог за потврду брисања листе
        if (showDeleteConfirmDialog) {
            DeleteConfirmationDialog(
                onConfirm = {
                    selectedList?.id?.let { id ->
                        listsViewModel.deleteShoppingList(id)
                        navController.navigateUp()
                    }
                    showDeleteConfirmDialog = false
                },
                onDismiss = {
                    showDeleteConfirmDialog = false
                }
            )
        }
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
    var confirmDelete by remember { mutableStateOf(false) }
    val view = LocalView.current
    
    // Израчунавамо праг за брисање - повећавамо праг на 200dp
    val density = LocalDensity.current
    val deleteThreshold = with(density) { 200.dp.toPx() }
    
    // Стање за превлачење
    val draggableState = rememberDraggableState { delta ->
        offsetX += delta
        
        // Хаптичка повратна информација када пређемо први праг
        if (offsetX > 100f && offsetX < 110f && !confirmDelete) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
        
        // Друга хаптичка повратна информација када пређемо праг за брисање
        if (offsetX > deleteThreshold && !confirmDelete) {
            confirmDelete = true
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }
    
    // Када је елемент потпуно одбачен, позовите onDelete
    LaunchedEffect(confirmDelete) {
        if (confirmDelete) {
            show = false
            delay(300) // Мала пауза за анимацију
            onDelete(item)
        }
    }
    
    androidx.compose.animation.AnimatedVisibility(
        visible = show,
        exit = slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }) + fadeOut()
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Box {
                // Позадина која се приказује при превлачењу
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(if (offsetX < deleteThreshold) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.error)
                        .padding(start = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Обриши",
                            tint = if (offsetX < deleteThreshold) MaterialTheme.colorScheme.onErrorContainer else Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (offsetX < deleteThreshold) "Превуците више за брисање" else "Отпустите за брисање",
                            color = if (offsetX < deleteThreshold) MaterialTheme.colorScheme.onErrorContainer else Color.White
                        )
                    }
                }
                
                // Садржај који се може превлачити
                Row(
                    modifier = modifier
                        .fillMaxWidth()
                        .draggable(
                            state = draggableState,
                            orientation = Orientation.Horizontal,
                            onDragStopped = {
                                // Ако не пређемо праг, враћамо елемент назад
                                if (offsetX <= deleteThreshold) {
                                    offsetX = 0f
                                    confirmDelete = false
                                }
                            }
                        )
                        .offset { IntOffset(offsetX.roundToInt(), 0) }
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
}

@Composable
fun ShoppingItemsList(
    items: List<ShoppingItem>,
    listId: String,
    onCheckedChange: (ShoppingItem, Boolean) -> Unit,
    onDeleteItem: (ShoppingItem) -> Unit,
    modifier: Modifier = Modifier
) {
    // Креирамо локално стање за Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Чувамо последњу обрисану ставку за повраћај
    var lastDeletedItem by remember { mutableStateOf<ShoppingItem?>(null) }
    
    // Корутински обим за приказивање снекбара
    val coroutineScope = rememberCoroutineScope()
    
    // Креирамо референцу на ViewModel
    val listsViewModel: ListsViewModel = viewModel(factory = ListsViewModel.Factory(LocalContext.current))
    
    // Ажурирамо функцију за брисање
    val handleDelete: (ShoppingItem) -> Unit = { item ->
        // Чувамо ставку за потенцијални повраћај
        lastDeletedItem = item
        
        // Позивамо оригиналну функцију за брисање
        onDeleteItem(item)
        
        // Приказујемо снекбар са опцијом за повраћај
        coroutineScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Ставка \"${item.name}\" је обрисана",
                actionLabel = "Поништи",
                duration = SnackbarDuration.Short
            )
            
            if (result == SnackbarResult.ActionPerformed) {
                // Враћамо последњу обрисану ставку
                lastDeletedItem?.let { deletedItem ->
                    listsViewModel.restoreItem(listId, deletedItem)
                }
            }
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            EmptyState(message = "Нема ставки у листи. Додајте нове ставке користећи поље за унос испод.")
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items) { item ->
                        ShoppingItemRow(
                            item = item,
                            onCheckedChange = onCheckedChange,
                            onDelete = handleDelete,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        
        // Додајемо Snackbar host у Box
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun DeleteConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Брисање") },
        text = { Text("Да ли сте сигурни да желите да обришете ову ставку?") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Обриши")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Откажи")
            }
        }
    )
}

@Composable
fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Додајемо иконицу за празно стање
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .padding(bottom = 16.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}