package com.petar.smrdici.ui.screens.lists

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.petar.smrdici.data.model.ShoppingItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.ExperimentalMaterial3Api as Material3ExperimentalApi

@OptIn(Material3ExperimentalApi::class, ExperimentalComposeUiApi::class, ExperimentalMaterialApi::class)
@Composable
fun ListDetailsScreen(
    navController: NavController,
    listId: String,
    listsViewModel: ListsViewModel = viewModel(factory = ListsViewModel.Factory(LocalContext.current))
) {
    val selectedList by listsViewModel.selectedList.collectAsState()
    var newItemText by remember { mutableStateOf("") }
    var currentEditingItemId by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Додајемо корутински опсег за Compose компоненту
    val coroutineScope = rememberCoroutineScope()
    
    // Додајемо стање за дијалог за потврду брисања
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    
    // Додајемо стање за освежавање
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            coroutineScope.launch {
                isRefreshing = true
                listsViewModel.loadListById(listId)
                delay(1000) // Минимално трајање анимације освежавања
                isRefreshing = false
            }
        }
    )
    
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
    
    // Функција за додавање нове празне ставке у листу
    val addEmptyItem = {
        // Генеришемо привремени ID за нову ставку
        val tempId = "temp_${System.currentTimeMillis()}"
        currentEditingItemId = tempId
        
        // Фокусирамо поље за унос након кратког одлагања
        coroutineScope.launch {
            delay(100)
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (e: Exception) {
                // Игноришемо грешку ако компонента још није спремна
            }
        }
    }
    
    // Функција за обраду уноса текста за нову ставку
    val handleItemTextSubmit = {
        if (newItemText.isNotBlank()) {
            // Додајемо ставку са текстом у листу
            listsViewModel.addItemToList(listId, newItemText.trim())
            
            // Чистимо текст и додајемо нову празну ставку
            newItemText = ""
            
            // Одмах додајемо нову празну ставку за даљи унос
            addEmptyItem()
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    // Ако нема активне ставке за унос, додајемо празну
                    if (currentEditingItemId == null) {
                        addEmptyItem()
                    } else {
                        // Фокусирамо тренутну ставку за унос
                        coroutineScope.launch {
                            try {
                                focusRequester.requestFocus()
                                keyboardController?.show()
                            } catch (e: Exception) {
                                // Игноришемо грешку ако компонента још није спремна
                            }
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Додај нову ставку"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Приказ листе са подршком за освежавање
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pullRefresh(pullRefreshState)
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
                        listState.items.let { items ->
                            Column(modifier = Modifier.fillMaxSize()) {
                                ShoppingItemsList(
                                    items = items,
                                    listId = listId,
                                    onCheckedChange = { item, isChecked ->
                                        listsViewModel.updateItemCompletionStatus(listId,
                                            item.id, isChecked)
                                    },
                                    onDeleteItem = { item ->
                                        listsViewModel.deleteItem(listId, item.id)
                                    },
                                    modifier = Modifier.weight(1f)
                                )

                                // Приказујемо поље за унос нове ставке ако постоји активна ставка за унос
                                if (currentEditingItemId != null) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Кружни индикатор (неактиван) уместо чекбокса
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            // Празан садржај, само приказујемо кружни индикатор
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Поље за унос
                                        TextField(
                                            value = newItemText,
                                            onValueChange = { newItemText = it },
                                            placeholder = { Text("Унесите ставку") },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .focusRequester(focusRequester)
                                                .onKeyEvent { keyEvent ->
                                                    // Ако је притиснуто Enter, додајемо ставку и припремамо нови ред
                                                    if (keyEvent.key == Key.Enter) {
                                                        handleItemTextSubmit()
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
                                                    handleItemTextSubmit()
                                                }
                                            ),
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = Color.Transparent,
                                                unfocusedContainerColor = Color.Transparent,
                                                disabledContainerColor = Color.Transparent,
                                                focusedIndicatorColor = Color.Transparent,
                                                unfocusedIndicatorColor = Color.Transparent
                                            ),
                                            singleLine = true
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                PullRefreshIndicator(
                    refreshing = isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
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
    var offsetX by remember { mutableFloatStateOf(0f) }
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
    
    AnimatedVisibility(
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

                    // Заменили смо стандардни Checkbox са кружним индикатором
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (item.isCompleted) Color(0xFF4CAF50) 
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { onCheckedChange(item, !item.isCompleted) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.isCompleted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Завршено",
                                tint = Color.White
                            )
                        }
                    }
                    
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

@OptIn(ExperimentalMaterialApi::class)
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
                    // Проверавамо да ли ставка има валидан ID (није празан стринг)
                    if (deletedItem.id.isNotEmpty()) {
                        listsViewModel.restoreItem(deletedItem.id, listId, deletedItem)
                    } else {
                        // Логујемо грешку ако ставка нема валидан ID
                        println("Не можемо вратити ставку са празним ID-ем")
                    }
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