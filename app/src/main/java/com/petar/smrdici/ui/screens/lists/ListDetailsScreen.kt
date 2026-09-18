package com.petar.smrdici.ui.screens.lists

import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.firebase.Timestamp
import com.petar.smrdici.data.model.ShoppingItem
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.ReorderableLazyListState
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ListDetailsScreen(
    navController: NavController,
    listId: String,
    listsViewModel: ListsViewModel = viewModel(factory = ListsViewModel.Factory()),
    authViewModel: AuthViewModel = viewModel()
) {
    val selectedList by listsViewModel.selectedList.collectAsState()
    var newItemText by remember { mutableStateOf("") }
    var currentEditingItemId by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Use rememberSaveable to persist scroll state across recompositions
    val lazyListState = rememberLazyListState()
    
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
    
    // Додајемо стање за праћење видљивости тастатуре
    var keyboardVisible by remember { mutableStateOf(false) }
    
    // Додајемо WindowInsets за праћење тастатуре
    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    
    // Додајемо стање за праћење да ли је листа празна
    val isListEmpty = selectedList?.items?.isEmpty() == true
    
    // Додајемо стање за дијалог за потврду брисања листе
    var showClearListDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var clearType by remember { mutableStateOf<ClearType?>(null) }
    var lastClearedItems by remember { mutableStateOf<List<ShoppingItem>?>(null) }
    
    val view = LocalView.current
    
    // Add reorderable state
    val reorderableState = rememberReorderableLazyListState(
        onMove = { from, to ->
            selectedList?.let { list ->
                val items = list.items.toMutableList()
                val item = items.removeAt(from.index)
                items.add(to.index, item)
                
                // Update positions in the database using the correct function name
                listsViewModel.updateItemPositions(listId, items)
            }
        },
        canDragOver = { draggedOver, dragging -> true } // Allow dragging over all items
    )

    // Watch for drag state changes and provide haptic feedback
    LaunchedEffect(reorderableState.draggingItemKey) {
        if (reorderableState.draggingItemKey != null) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
    
    // Учитавање листе при првом рендеровању
    LaunchedEffect(listId) {
        isRefreshing = true
        listsViewModel.loadListById(listId)
        delay(500) // Кратко одлагање за иницијално учитавање
        isRefreshing = false
        // Scroll to top after initial load
        lazyListState.scrollToItem(0)
    }
    
    // Track keyboard visibility changes
    LaunchedEffect(isKeyboardVisible) {
        keyboardVisible = isKeyboardVisible
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
            } catch (_: Exception) {
                // Игноришемо грешку ако компонента још није спремна
            }
        }
    }
    
    // Функција за обраду уноса текста за нову ставку
    val handleItemTextSubmit = {
        if (newItemText.isNotBlank()) {
            // Додајемо ставку са текстом у листу
            listsViewModel.addItemToList(listId, newItemText.trim())
            
            // Чистимо текст
            newItemText = ""
            
            // Додајемо кратко одлагање пре додавања нове празне ставке
            coroutineScope.launch {
                delay(100) // Kratko odlaganje za bolji UX
                addEmptyItem()
            }
        }
    }
    
    // COMPLETELY REPLACE all three LaunchedEffect blocks for scrolling with this single one
    LaunchedEffect(selectedList?.items?.size, isKeyboardVisible, currentEditingItemId) {
        selectedList?.let { list ->
            val itemCount = list.items.size
            
            // Only perform auto-scrolling for lists with 5 or more items
            if (itemCount >= 5 && (currentEditingItemId != null || isKeyboardVisible)) {
                delay(150)
                try {
                    // Scroll to the last item with appropriate offset
                    val lastIndex = itemCount - 1
                    val offset = if (isKeyboardVisible) -200 else -50
                    
                    // Use reorderableState for consistency
                    reorderableState.listState.animateScrollToItem(
                        index = lastIndex,
                        scrollOffset = offset
                    )
                } catch (_: Exception) {
                    // Ignore scrolling errors
                }
            } else if (itemCount == 0) {
                // For empty lists, scroll to top
                reorderableState.listState.scrollToItem(0)
            }
        }
    }
    
    // Основни изглед екрана
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Box(modifier = Modifier.statusBarsPadding()) {
            AppHeader(
                title = selectedList?.title ?: "Детаљи листе",
                navController = navController,
                showBackButton = true,
                showProfileIcon = false,
                user = if (authViewModel.authState.collectAsState().value is AuthState.Authenticated) (authViewModel.authState.collectAsState().value as AuthState.Authenticated).user else null,
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Мени"
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        val allCompleted = selectedList?.items?.all { it.isCompleted } == true
                        
                        DropdownMenuItem(
                            text = { Text(if (allCompleted) "Поништи све" else "Означи све као завршено") },
                            onClick = {
                                showMenu = false
                                selectedList?.id?.let { id ->
                                    listsViewModel.updateAllItemsCompletionStatus(id, !allCompleted)
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (allCompleted) Icons.Default.Clear else Icons.Default.Check,
                                    contentDescription = if (allCompleted) "Поништи све" else "Означи све као завршено"
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Обриши завршене ставке") },
                            onClick = {
                                showMenu = false
                                clearType = ClearType.COMPLETED
                                showClearListDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Обриши завршене ставке"
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Обриши све ставке") },
                            onClick = {
                                showMenu = false
                                clearType = ClearType.ALL
                                showClearListDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Обриши све ставке"
                                )
                            }
                        )
                    }
                }
            )
            }
        },
        floatingActionButton = {
            if (currentEditingItemId == null) {
                FloatingActionButton(
                    onClick = {
                        addEmptyItem()
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Додај нову ставку"
                    )
                }
            }
        },
        bottomBar = {
            if (currentEditingItemId != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .navigationBarsPadding() // Додајемо padding за навигациону траку
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.TextField(
                        value = newItemText,
                        onValueChange = { text -> 
                            // Capitalize the first letter if the text is not empty
                            newItemText = if (text.isNotEmpty()) {
                                text.replaceFirstChar { it.uppercase() }
                            } else {
                                text
                            }
                        },
                        placeholder = { Text("Унесите назив ставке") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .onKeyEvent { keyEvent ->
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
                        )
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    IconButton(
                        onClick = {
                            handleItemTextSubmit()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Додај ставку"
                        )
                    }
                }
            }
        },
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
        contentWindowInsets = WindowInsets(0, 0, 0, 0) // Искључујемо подразумеване инсете
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Приказ листе са подршком за освежавање
            Box(
                modifier = Modifier
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
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                                .reorderable(reorderableState),
                            state = reorderableState.listState,
                            contentPadding = PaddingValues(
                                top = 8.dp,
                                bottom = if (isKeyboardVisible) 160.dp else 80.dp
                            )
                        ) {
                            // Add large spacer at the top for short lists when keyboard is visible
                            if (isKeyboardVisible && listState.items.size < 5) {
                                item {
                                    // This spacer pushes content down so it's visible above the keyboard
                                    Spacer(modifier = Modifier.padding(top = 200.dp))
                                }
                            }
                            
                            items(
                                items = listState.items,
                                key = { item -> item.id }
                            ) { item ->
                                ReorderableItem(
                                    reorderableState = reorderableState,
                                    key = item.id,
                                    modifier = Modifier.animateItem()
                                ) { isDragging ->
                                    ShoppingItemRow(
                                        item = item,
                                        onDelete = { deletedItem ->
                                            listsViewModel.deleteItem(deletedItem.id)
                                            coroutineScope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = "Ставка обрисана",
                                                    actionLabel = "Поништи",
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    lastDeletedItem = deletedItem
                                                    if (deletedItem.id.isNotEmpty()) {
                                                        listsViewModel.restoreItem(deletedItem.id, deletedItem)
                                                    }
                                                }
                                            }
                                        },
                                        onCheckedChange = { shoppingItem, isChecked ->
                                            listState.id?.let { id ->
                                                listsViewModel.updateItemCompletionStatus(id, shoppingItem.id, isChecked)
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isDragging) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surface
                                            ),
                                        isKucniPoslovi = listState.title == "Kućni poslovi",
                                        listsViewModel = listsViewModel,
                                        listId = listId,
                                        isDragging = isDragging,
                                        reorderableState = reorderableState
                                    )

                                    if (item != listState.items.lastOrNull()) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Индикатор освежавања
                PullRefreshIndicator(
                    refreshing = isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    scale = true
                )
            }
        }
        
        // Дијалог за потврду брисања листе
        if (showDeleteConfirmDialog) {
            DeleteConfirmationDialog(
                onConfirm = {
                    selectedList?.id?.let { id ->
                        // Log when user confirms deletion from the dialog
                        Log.d("ListDetailsScreen", "Корисник потврдио брисање листе \"${selectedList?.title}\" (ID: $id) из дијалога")
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
        
        if (showClearListDialog) {
            AlertDialog(
                onDismissRequest = { showClearListDialog = false },
                title = { Text("Обриши ставке") },
                text = { 
                    Text(
                        when (clearType) {
                            ClearType.COMPLETED -> "Да ли желите да обришете све завршене ставке?"
                            ClearType.ALL -> "Да ли желите да обришете све ставке из листе?"
                            null -> ""
                        }
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            when (clearType) {
                                ClearType.COMPLETED -> {
                                    selectedList?.let { list ->
                                        lastClearedItems = list.items.filter { it.isCompleted }
                                        listsViewModel.clearCompletedItems(listId)
                                    }
                                }
                                ClearType.ALL -> {
                                    selectedList?.let { list ->
                                        lastClearedItems = list.items
                                        listsViewModel.clearAllItems(listId)
                                    }
                                }
                                null -> {}
                            }
                            showClearListDialog = false
                            
                            // Show undo snackbar
                            coroutineScope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Ставке обрисане",
                                    actionLabel = "Поништи",
                                    duration = SnackbarDuration.Short
                                )
                                
                                if (result == SnackbarResult.ActionPerformed) {
                                    // Restore cleared items
                                    lastClearedItems?.forEach { item ->
                                        listsViewModel.restoreItem(item.id, item)
                                    }
                                    lastClearedItems = null
                                }
                            }
                        }
                    ) {
                        Text("Потврди")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearListDialog = false }) {
                        Text("Откажи")
                    }
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
    modifier: Modifier = Modifier,
    isKucniPoslovi: Boolean = false,
    listsViewModel: ListsViewModel,
    listId: String,
    isDragging: Boolean = false,
    reorderableState: ReorderableLazyListState? = null
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var confirmDelete by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf(item.name) }
    val view = LocalView.current
    val density = LocalDensity.current
    val deleteThreshold = with(density) { 200.dp.toPx() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    // Effect to update editedName when item.name changes
    LaunchedEffect(item.name) {
        editedName = item.name
    }

    // Effect to handle editing state
    LaunchedEffect(isEditing) {
        if (isEditing) {
            // When this item starts editing, notify the parent to cancel other items' editing
            listsViewModel.setEditingItemId(item.id)
            // Request focus and scroll to this item
            coroutineScope.launch {
                delay(100) // Short delay to ensure the layout is ready
                focusRequester.requestFocus()
            }
        }
    }

    // Effect to handle external editing cancellation
    LaunchedEffect(listsViewModel.editingItemId.collectAsState().value) {
        if (listsViewModel.editingItemId.value != item.id) {
            isEditing = false
        }
    }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    if (!confirmDelete) {
                        offsetX += delta
                        if (offsetX > deleteThreshold) {
                            confirmDelete = true
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                    }
                },
                onDragStarted = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                },
                onDragStopped = {
                    if (confirmDelete) {
                        onDelete(item)
                    }
                    offsetX = 0f
                    confirmDelete = false
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = if (isEditing) 4.dp else 8.dp), // Reduce padding when editing
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag Handle - make it more prominent
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .then(
                        if (reorderableState != null) {
                            Modifier.detectReorderAfterLongPress(reorderableState)
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DragIndicator,
                    contentDescription = "Превуци за промену редоследа",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDragging) 1f else 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }

            if (isKucniPoslovi) {
                // Age indicator with number
                val ageInDays = remember(item.createdAt) {
                    val now = Timestamp.now()
                    val diffInSeconds = now.seconds - item.createdAt.seconds
                    val days = diffInSeconds / (24 * 60 * 60)
                    days.toInt()
                }

                val indicatorColor = when {
                    ageInDays >= 14 -> Color(0xFFE57373)
                    ageInDays >= 7 -> Color(0xFFFFB74D)
                    else -> Color(0xFF81C784)
                }

                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(indicatorColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$ageInDays",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
            }

            // Custom circular checkbox
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (item.isCompleted) Color(0xFF4CAF50)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onCheckedChange(item, !item.isCompleted)
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    },
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

            Spacer(modifier = Modifier.width(16.dp))

            if (isEditing) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = editedName,
                        onValueChange = { text -> 
                            editedName = if (text.isNotEmpty()) {
                                text.replaceFirstChar { it.uppercase() }
                            } else {
                                text
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (editedName.isNotBlank() && editedName != item.name) {
                                listsViewModel.updateItemName(listId, item.id, editedName)
                            }
                            isEditing = false
                            keyboardController?.hide()
                        }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                    )
                    
                    // Compact the buttons when editing
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (editedName.isNotBlank() && editedName != item.name) {
                                    listsViewModel.updateItemName(listId, item.id, editedName)
                                }
                                isEditing = false
                                keyboardController?.hide()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Потврди",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        IconButton(
                            onClick = {
                                editedName = item.name
                                isEditing = false
                                keyboardController?.hide()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Откажи",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = item.name,
                    style = if (item.isCompleted) {
                        MaterialTheme.typography.bodyLarge.copy(
                            textDecoration = TextDecoration.LineThrough,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            isEditing = true
                            listsViewModel.setEditingItemId(item.id)
                        }
                )
            }
        }
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

enum class ClearType {
    COMPLETED,
    ALL
}