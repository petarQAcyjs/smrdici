package com.petar.smrdici.ui.screens.lists

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectTapGestures

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterialApi::class)
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
    
    // Додајемо стање за LazyListState
    val lazyListState = rememberLazyListState()
    
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
                // Skrolujemo do poslednje stavke
                selectedList?.let { list ->
                    if (list.items.isNotEmpty()) {
                        try {
                            delay(150) // Kratko odlaganje za bolji UX
                            lazyListState.animateScrollToItem(
                                index = list.items.size,
                                scrollOffset = -200
                            )
                        } catch (_: Exception) {
                            // Ignorišemo greške pri skrolovanju
                        }
                    }
                }
                
                delay(100) // Kratko odlaganje za bolji UX
                addEmptyItem()
            }
        }
    }
    
    // Додајемо ефекат који прати када се тастатура појави/нестане
    LaunchedEffect(currentEditingItemId) {
        if (currentEditingItemId != null) {
            keyboardVisible = true
        }
    }
    
    // Poboljšavamo logiku za skrolovanje - fokusiramo se na to da poslednja stavka bude vidljiva
    LaunchedEffect(selectedList?.items?.size, isKeyboardVisible) {
        selectedList?.let { list ->
            if (list.items.isEmpty()) {
                // Ako je lista prazna, skrolujemo na vrh
                lazyListState.scrollToItem(0)
            } else {
                // Dodajemo odlaganje da bi animacija bila glatka
                delay(150)
                
                try {
                    // Uvek skrolujemo do poslednje stavke
                    // Koristimo veći negativni ofset kada je tastatura vidljiva
                    val offset = if (isKeyboardVisible) -200 else -50
                    
                    // Skrolujemo do poslednje stavke
                    lazyListState.animateScrollToItem(
                        index = list.items.size - 1,
                        scrollOffset = offset
                    )
                } catch (_: Exception) {
                    // Ignorišemo greške pri skrolovanju
                }
            }
        }
    }
    
    // Dodajemo novi LaunchedEffect koji će se izvršiti kada se doda nova stavka
    LaunchedEffect(selectedList?.items?.lastOrNull()?.id) {
        selectedList?.let { list ->
            if (list.items.isNotEmpty() && isKeyboardVisible) {
                delay(100)
                try {
                    // Skrolujemo do poslednje stavke sa većim ofsetom kada je tastatura vidljiva
                    lazyListState.animateScrollToItem(
                        index = list.items.size - 1,
                        scrollOffset = -200
                    )
                } catch (_: Exception) {
                    // Ignorišemo greške pri skrolovanju
                }
            }
        }
    }
    
    // Основни изглед екрана
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppHeader(
                title = selectedList?.title ?: "Детаљи листе",
                navController = navController,
                showBackButton = true,
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
                        DropdownMenuItem(
                            text = { Text("Означи све као завршено") },
                            onClick = {
                                showMenu = false
                                selectedList?.let { list ->
                                    listsViewModel.updateAllItemsCompletionStatus(listId, true)
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Означи све као завршено"
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
                            } catch (_: Exception) {
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
                        onValueChange = { newItemText = it },
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
                                .padding(horizontal = 16.dp),
                            state = lazyListState,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = if (isKeyboardVisible) 56.dp else 0.dp)
                        ) {
                            // Додајемо празан простор на врху листе када је празна
                            if (isListEmpty) {
                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                            
                            // Приказујемо ставке
                            items(
                                items = listState.items,
                                key = { item -> item.id }
                            ) { item ->
                                ShoppingItemRow(
                                    item = item,
                                    onDelete = { item ->
                                        // Čuvamo samo poslednju obrisanu stavku
                                        lastDeletedItem = item
                                        listsViewModel.deleteItem(item.id)
                                        
                                        // Prikazujemo Snackbar sa opcijom za povraćaj
                                        coroutineScope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "Ставка обрисана",
                                                actionLabel = "Поништи",
                                                duration = SnackbarDuration.Short
                                            )
                                            
                                            if (result == SnackbarResult.ActionPerformed) {
                                                // Vraćamo samo poslednju obrisanu stavku
                                                lastDeletedItem?.let { deletedItem ->
                                                    if (deletedItem.id.isNotEmpty()) {
                                                        listsViewModel.restoreItem(deletedItem.id, deletedItem)
                                                        // Nakon vraćanja, postavljamo lastDeletedItem na null
                                                        lastDeletedItem = null
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onCheckedChange = { shoppingItem, isChecked ->
                                        listState.id?.let { id ->
                                            listsViewModel.updateItemCompletionStatus(id, shoppingItem.id, isChecked)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    isKucniPoslovi = listState.title == "Kućni poslovi",
                                    listsViewModel = listsViewModel,
                                    listId = listId
                                )
                                
                                if (item != listState.items.lastOrNull()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                            
                            // Додајемо већи простор на дну када је тастатура видљива
                            item {
                                Spacer(
                                    modifier = Modifier.height(
                                        if (isKeyboardVisible) 300.dp else 100.dp
                                    )
                                )
                            }
                        }
                    }
                }
                
                // Индикатор освежавања
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
    listId: String
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var confirmDelete by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf(item.name) }
    val view = LocalView.current
    val density = LocalDensity.current
    val deleteThreshold = with(density) { 200.dp.toPx() }
    val keyboardController = LocalSoftwareKeyboardController.current

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    if (!confirmDelete) {
                        offsetX += delta
                        if (offsetX > deleteThreshold && !confirmDelete) {
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
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isKucniPoslovi) {
                // Calculate and show age indicator only for Kucni poslovi list
                val ageInDays = remember(item.createdAt) {
                    val now = Timestamp.now()
                    val diffInMillis = now.seconds - item.createdAt.seconds
                    (diffInMillis / (24 * 60 * 60)).toInt() // Convert seconds to days
                }

                // Age indicator dot
                val indicatorColor = when {
                    ageInDays >= 14 -> Color(0xFFE57373) // Red for more than 2 weeks
                    ageInDays >= 7 -> Color(0xFFFFB74D)  // Orange for 1-2 weeks
                    else -> Color(0xFF81C784) // Green for less than a week
                }

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(indicatorColor, CircleShape)
                        .padding(end = 8.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))
            }

            // Custom circular checkbox matching ListsScreen style
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
                TextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (editedName.isNotBlank() && editedName != item.name) {
                            listsViewModel.updateItemName(listId, item.id, editedName)
                        }
                        isEditing = false
                        keyboardController?.hide()
                    }),
                    modifier = Modifier.weight(1f)
                )
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
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    isEditing = true
                                }
                            )
                        }
                )
            }

            if (isKucniPoslovi) {
                // Show age in days for items older than 7 days in Kucni poslovi list
                val ageInDays = remember(item.createdAt) {
                    val now = Timestamp.now()
                    val diffInMillis = now.seconds - item.createdAt.seconds
                    (diffInMillis / (24 * 60 * 60)).toInt()
                }
                if (ageInDays >= 7) {
                    Text(
                        text = "${ageInDays}д",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
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