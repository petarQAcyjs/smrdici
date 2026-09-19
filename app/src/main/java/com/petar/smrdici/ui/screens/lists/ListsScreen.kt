package com.petar.smrdici.ui.screens.lists

import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.R
import com.petar.smrdici.data.model.ShoppingList
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.components.StandardPullRefreshIndicator
import com.petar.smrdici.ui.navigation.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectTapGestures
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * Generates a unique key for a list that won't conflict even after deletion and restoration
 */
private fun generateUniqueListKey(listId: String?): String {
    if (listId == null) return "null_${Random.nextLong()}"
    return "list_${listId}_${System.currentTimeMillis()}_${Random.nextLong(0, 10000)}"
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ListsScreen(
    navController: NavController,
    listsViewModel: ListsViewModel = viewModel(factory = ListsViewModel.Factory()),
    authViewModel: AuthViewModel = viewModel()
) {
    val listsUiState by listsViewModel.uiState.collectAsState()
    val deletingListIds by listsViewModel.deletingListIds.collectAsState()
    val restoringListIds by listsViewModel.restoringListIds.collectAsState()
    val isDeletionInProgress = deletingListIds.isNotEmpty()
    
    val coroutineScope = rememberCoroutineScope()
    
    var showAddListDialog by remember { mutableStateOf(false) }
    
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            coroutineScope.launch {
                isRefreshing = true
                listsViewModel.loadLists()
                delay(500.milliseconds)
                isRefreshing = false
            }
        }
    )
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    var lastDeletedList by remember { mutableStateOf<ShoppingList?>(null) }
    
    val authState by authViewModel.authState.collectAsState()
    val user = if (authState is AuthState.Authenticated) {
        (authState as AuthState.Authenticated).user
    } else null
    
    LaunchedEffect(isDeletionInProgress) {
        if (isDeletionInProgress) {
            Log.d("ListsScreen", "Брисање је у току: ${deletingListIds.joinToString()}")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddListDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Додај нову листу"
                    )
                }
            },
            topBar = {
                AppHeader(
                    title = "Листе",
                    user = user,
                    navController = navController,
                    showProfileIcon = false
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    userScrollEnabled = !isDeletionInProgress
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            PredefinedListCard(
                                title = "Списак за продавницу",
                                iconResId = R.drawable.ic_shopping,
                                backgroundColor = Color(0xFF30C9C9),
                                onClick = {
                                    listsViewModel.getOrCreatePredefinedList(
                                        title = "Списак за продавницу",
                                        onSuccess = { listId ->
                                            navController.navigate(Screen.ListDetails.createRoute(listId))
                                        },
                                        onError = { errorMsg ->
                                            listsViewModel.updateUiState(ListsUiState.Error(errorMsg))
                                        }
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                            
                            PredefinedListCard(
                                title = "Кућни послови",
                                iconResId = R.drawable.ic_home,
                                backgroundColor = Color(0xFF9ED36A),
                                onClick = {
                                    listsViewModel.getOrCreatePredefinedList(
                                        title = "Kućni poslovi",
                                        onSuccess = { listId ->
                                            navController.navigate(Screen.ListDetails.createRoute(listId))
                                        },
                                        onError = { errorMsg ->
                                            listsViewModel.updateUiState(ListsUiState.Error(errorMsg))
                                        }
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    
                    when (listsUiState) {
                        is ListsUiState.Loading -> {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!isRefreshing) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        }
                        is ListsUiState.Success -> {
                            val customLists = (listsUiState as ListsUiState.Success).lists
                            
                            // Filter out lists that are being deleted or restored before passing to items()
                            val filteredLists = customLists.filter { list ->
                                val listId = list.id ?: return@filter false
                                !deletingListIds.contains(listId) && !restoringListIds.contains(listId)
                            }
                            
                            items(
                                items = filteredLists,
                                // Use our function to generate unique keys
                                key = { list -> generateUniqueListKey(list.id) }
                            ) { list ->
                                SwipeToDeleteListItem(
                                    list = list,
                                    onClick = {
                                        if (!isDeletionInProgress) {
                                            list.id?.let { listId ->
                                                navController.navigate(Screen.ListDetails.createRoute(listId))
                                            }
                                        }
                                    },
                                    onDelete = {
                                        lastDeletedList = list
                                        
                                        // Log local deletion
                                        Log.d("ListsScreen", "Листа \"${list.title}\" (ID: ${list.id}) је обрисана локално")
                                        
                                        // Actually delete the list from Firestore
                                        list.id?.let { listId ->
                                            listsViewModel.deleteShoppingList(listId)
                                        }
                                        
                                        coroutineScope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "Листа \"${list.title}\" је обрисана",
                                                actionLabel = "Поништи",
                                                duration = SnackbarDuration.Short
                                            )
                                            
                                            if (result == SnackbarResult.ActionPerformed) {
                                                lastDeletedList?.let { deletedList ->
                                                    // Add a short delay before restoring
                                                    coroutineScope.launch {
                                                        delay(300.milliseconds) // 300ms delay
                                                        listsViewModel.restoreList(deletedList)
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    isDeletionLocked = isDeletionInProgress,
                                    listsViewModel = listsViewModel
                                )
                            }
                        }
                        is ListsUiState.Error -> {
                            item {
                                Text(
                                    text = (listsUiState as ListsUiState.Error).message,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        StandardPullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
    
    if (showAddListDialog) {
        AddListDialog(
            onDismiss = { showAddListDialog = false },
            onListAdded = {
                showAddListDialog = false
                listsViewModel.loadLists()
            },
            listsViewModel = listsViewModel
        )
    }
}

@Composable
fun PredefinedListCard(
    title: String,
    iconResId: Int,
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(120.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Image(
                painter = painterResource(id = iconResId),
                contentDescription = title,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.End),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
fun AddListDialog(
    onDismiss: () -> Unit,
    onListAdded: () -> Unit,
    listsViewModel: ListsViewModel
) {
    var title by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Додај нову листу") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Наслов листе") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        listsViewModel.addList(
                            title = title,
                            onSuccess = {
                                onListAdded()
                            },
                            onError = { error ->
                                errorMessage = error
                            }
                        )
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("Додај")
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
fun SwipeToDeleteListItem(
    list: ShoppingList,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    isDeletionLocked: Boolean = false,
    listsViewModel: ListsViewModel
) {
    val title = list.title
    val isCompleted = list.isCompleted
    val listId = list.id ?: return

    val deletingListIds by listsViewModel.deletingListIds.collectAsState()
    val isBeingDeleted = deletingListIds.contains(listId) || isDeletionLocked
    
    var isEditing by remember { mutableStateOf(false) }
    var editedTitle by remember { mutableStateOf(title) }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    LaunchedEffect(listId, isBeingDeleted) {
        Log.d("SwipeToDeleteListItem", "Листа $listId, наслов: $title, статус брисања: $isBeingDeleted")
    }
    
    var show by remember { mutableStateOf(true) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var confirmDelete by remember { mutableStateOf(false) }
    var isDeleted by remember { mutableStateOf(false) }
    val view = LocalView.current
    var isCheckboxClicked by remember { mutableStateOf(false) }
    var localCompletedState by remember { mutableStateOf(isCompleted) }
    
    LaunchedEffect(list.id, isCompleted) {
        localCompletedState = isCompleted
    }
    
    LaunchedEffect(list.id) {
        Log.d("SwipeToDeleteListItem", "Компонента креирана/рекомпонована за листу: ${list.id}")
    }
    
    val density = LocalDensity.current
    val deleteThreshold = with(density) { 200.dp.toPx() }
    
    val draggableState = rememberDraggableState { delta ->
        if (!confirmDelete && !isDeleted && !isDeletionLocked) {
            offsetX += delta
            
            if (offsetX > 100f && offsetX < 110f) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
            
            if (offsetX > deleteThreshold && !confirmDelete) {
                confirmDelete = true
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
    }
    
    LaunchedEffect(confirmDelete) {
        if (confirmDelete && !isDeleted && !isDeletionLocked) {
            Log.d("SwipeToDeleteListItem", "Корисник потврдио брисање листе: ${list.id}, наслов: ${list.title}")
            
            isDeleted = true
            show = false
            delay(300.milliseconds)
            onDelete()
        }
    }
    
    LaunchedEffect(isCheckboxClicked) {
        if (isCheckboxClicked && !isDeletionLocked) {
            try {
                localCompletedState = !localCompletedState
                Log.d("SwipeToDeleteListItem", "Променили смо чекбокс за листу: ${list.id}, ново стање: $localCompletedState")
                delay(100.milliseconds)
                list.id?.let { listId ->
                    try {
                        listsViewModel.toggleListStatus(listId)
                    } catch (e: Exception) {
                        Log.e("SwipeToDeleteListItem", "Грешка при ажурирању: ${e.message}")
                    }
                }
            } finally {
                isCheckboxClicked = false
            }
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
                
                Row(
                    modifier = modifier
                        .fillMaxWidth()
                        .draggable(
                            state = draggableState,
                            orientation = Orientation.Horizontal,
                            enabled = !isDeletionLocked && !isEditing,
                            onDragStopped = {
                                if (offsetX <= deleteThreshold && !isDeleted) {
                                    offsetX = 0f
                                    confirmDelete = false
                                }
                            }
                        )
                        .offset { IntOffset(offsetX.roundToInt(), 0) }
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (localCompletedState) Color(0xFF4CAF50) 
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable(
                                enabled = !isDeletionLocked && !isEditing,
                                onClick = {
                                    if (!isDeleted && !confirmDelete) {
                                        isCheckboxClicked = true
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (localCompletedState) {
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
                            value = editedTitle,
                            onValueChange = { editedTitle = it },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (editedTitle.isNotBlank() && editedTitle != title) {
                                    listsViewModel.updateListTitle(listId, editedTitle)
                                }
                                isEditing = false
                                keyboardController?.hide()
                            }),
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        val textStyle = if (localCompletedState) {
                            MaterialTheme.typography.titleMedium.copy(
                                textDecoration = TextDecoration.LineThrough,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        } else {
                            MaterialTheme.typography.titleMedium
                        }
                        
                        Text(
                            text = title,
                            style = textStyle,
                            modifier = Modifier
                                .weight(1f)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { if (!isDeletionLocked && !isDeleted && !confirmDelete) onClick() },
                                        onLongPress = {
                                            if (!isDeletionLocked && !isDeleted && !confirmDelete) {
                                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                                isEditing = true
                                            }
                                        }
                                    )
                                }
                        )
                    }
                }
            }
        }
    }
} 