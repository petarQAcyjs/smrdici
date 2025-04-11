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
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.R
import com.petar.smrdici.data.model.ShoppingList
import com.petar.smrdici.ui.navigation.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.components.StandardPullRefreshIndicator

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ListsScreen(
    navController: NavController,
    listsViewModel: ListsViewModel = viewModel(factory = ListsViewModel.Factory())
) {
    val listsUiState by listsViewModel.uiState.collectAsState()
    // Пратимо тренутне листе које се бришу
    val deletingListIds by listsViewModel.deletingListIds.collectAsState()
    val isDeletionInProgress = deletingListIds.isNotEmpty()
    
    // Додајемо корутински опсег за Compose компоненту
    val coroutineScope = rememberCoroutineScope()
    
    var showAddListDialog by remember { mutableStateOf(false) }
    
    // Стање освежавања
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            coroutineScope.launch {
                isRefreshing = true
                // Једноставно позивамо функцију без провере
                listsViewModel.loadLists()
                delay(500)
                isRefreshing = false
            }
        }
    )
    
    // Додајемо стање за Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Чувамо последњу обрисану листу за повраћај
    var lastDeletedList by remember { mutableStateOf<ShoppingList?>(null) }
    
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
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Заглавље са дугметом за повратак
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { navController.navigateUp() }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = "Листе",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
                
                // Приказујемо садржај екрана са подршком за освежавање
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    userScrollEnabled = !isDeletionInProgress
                ) {
                    // Предефинисане листе (2 у реду)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Листа за продавницу
                            PredefinedListCard(
                                title = "Spisak za prodavnicu",
                                iconResId = R.drawable.ic_shopping,
                                backgroundColor = Color(0xFF30C9C9),
                                onClick = {
                                    // Креирамо предефинисану листу ако не постоји и навигирамо на њу
                                    listsViewModel.getOrCreatePredefinedList(
                                        title = "Spisak za prodavnicu",
                                        onSuccess = { listId ->
                                            navController.navigate(Screen.ListDetails.createRoute(listId))
                                        },
                                        onError = { errorMsg ->
                                            // Можемо приказати поруку о грешци или обрадити грешку на други начин
                                            listsViewModel.updateUiState(ListsUiState.Error(errorMsg))
                                        }
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                            
                            // Кућни послови
                            PredefinedListCard(
                                title = "Kućni poslovi",
                                iconResId = R.drawable.ic_home,
                                backgroundColor = Color(0xFF9ED36A),
                                onClick = {
                                    // Креирамо предефинисану листу ако не постоји и навигирамо на њу
                                    listsViewModel.getOrCreatePredefinedList(
                                        title = "Kućni poslovi",
                                        onSuccess = { listId ->
                                            navController.navigate(Screen.ListDetails.createRoute(listId))
                                        },
                                        onError = { errorMsg ->
                                            // Можемо приказати поруку о грешци или обрадити грешку на други начин
                                            listsViewModel.updateUiState(ListsUiState.Error(errorMsg))
                                        }
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    
                    // Прилагођене листе
                    when (listsUiState) {
                        is ListsUiState.Loading -> {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Индикатор учитавања је већ присутан у SwipeRefresh
                                    if (!isRefreshing) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        }
                        is ListsUiState.Success -> {
                            val customLists = (listsUiState as ListsUiState.Success).lists
                            items(
                                items = customLists,
                                key = { list -> 
                                    // Додајемо временски печат уз ID да осигурамо јединственост
                                    "${list.id}_${System.currentTimeMillis()}"
                                }
                            ) { list ->
                                list.id?.let { listId ->
                                    // Не приказујемо листе које су у процесу брисања
                                    if (!deletingListIds.contains(listId)) {
                                        SwipeToDeleteListItem(
                                            list = list,
                                            onClick = {
                                                if (!isDeletionInProgress) {
                                                    navController.navigate(Screen.ListDetails.createRoute(listId))
                                                }
                                            },
                                            onDelete = {
                                                // Чувамо листу за поништавање
                                                lastDeletedList = list
                                                
                                                // Обришимо листу
                                                listsViewModel.deleteShoppingList(listId)
                                                
                                                // Приказујемо Snackbar са опцијом за повраћај и откључавамо брисање након што се снекбар затвори
                                                coroutineScope.launch {
                                                    val result = snackbarHostState.showSnackbar(
                                                        message = "Листа \"${list.title}\" је обрисана",
                                                        actionLabel = "Поништи",
                                                        duration = SnackbarDuration.Short
                                                    )
                                                    
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        // Поново додајемо листу ако је корисник тражио поништавање
                                                        lastDeletedList?.let { deletedList ->
                                                            listsViewModel.restoreList(deletedList)
                                                        }
                                                    }
                                                }
                                            },
                                            isDeletionLocked = isDeletionInProgress,
                                            listsViewModel = listsViewModel
                                        )
                                    }
                                }
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
    
    // Дијалог за додавање нове листе
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
                
                // Приказујемо поруку о грешци ако постоји
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

    // Проверавамо да ли је ова листа већ у процесу брисања
    val deletingListIds by listsViewModel.deletingListIds.collectAsState()
    val isBeingDeleted = deletingListIds.contains(listId) || isDeletionLocked
    
    // Постављамо дебаг лог да пратимо прави статус сваке листе
    LaunchedEffect(listId, isBeingDeleted) {
        Log.d("SwipeToDeleteListItem", "Листа $listId, наслов: $title, статус брисања: $isBeingDeleted")
    }
    
    // Ако је листа у процесу брисања, одмах прекидамо композицију и не приказујемо ништа
    if (isBeingDeleted) {
        Log.d("SwipeToDeleteListItem", "Прескачемо рендеровање листе $listId јер је у процесу брисања")
        return
    }
    
    // Бележимо да ли је компонента видљива
    var show by remember { mutableStateOf(true) }
    
    // Бележимо хоризонтално померање при превлачењу
    var offsetX by remember { mutableFloatStateOf(0f) }
    
    // Бележимо да ли је потврђено брисање (једном када је true, избегавамо дупло брисање)
    var confirmDelete by remember { mutableStateOf(false) }
    
    // Бележимо да ли је листа већ обрисана (спречава дупло брисање)
    var isDeleted by remember { mutableStateOf(false) }
    
    val view = LocalView.current
    
    // Додајемо стање за праћење клика на чекбокс
    var isCheckboxClicked by remember { mutableStateOf(false) }
    
    // Локално стање за праћење статуса комплетности
    var localCompletedState by remember { mutableStateOf(isCompleted) }
    
    // Ажурирамо локално стање само при првој композицији или када се промени извори параметар
    LaunchedEffect(list.id, isCompleted) {
        localCompletedState = isCompleted
    }
    
    // Додајемо дебаг лог за праћење брисања
    LaunchedEffect(list.id) {
        Log.d("SwipeToDeleteListItem", "Компонента креирана/рекомпонована за листу: ${list.id}")
    }
    
    // Израчунавамо праг за брисање - повећавамо праг на 200dp
    val density = LocalDensity.current
    val deleteThreshold = with(density) { 200.dp.toPx() }
    
    // Стање за превлачење
    val draggableState = rememberDraggableState { delta ->
        // Само дозвољавамо превлачење ако брисање није већ потврђено и ако елемент није већ избрисан
        if (!confirmDelete && !isDeleted && !isDeletionLocked) {
            offsetX += delta
            
            // Хаптичка повратна информација када пређемо први праг
            if (offsetX > 100f && offsetX < 110f) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
            
            // Друга хаптичка повратна информација када пређемо праг за брисање
            if (offsetX > deleteThreshold && !confirmDelete) {
                confirmDelete = true
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
    }
    
    // Када је елемент потпуно одбачен, позивамо onDelete само једном
    LaunchedEffect(confirmDelete) {
        if (confirmDelete && !isDeleted && !isDeletionLocked) {
            // Бележимо дебаг информацију
            Log.d("SwipeToDeleteListItem", "Брисање листе: ${list.id}")
            
            // Означавамо да је листа обрисана да бисмо избегли дупло брисање
            isDeleted = true
            
            // Сакривамо елемент
            show = false
            
            // Мала пауза за анимацију
            delay(300)
            
            // Позивамо функцију брисања само једном
            onDelete()
        }
    }
    
    // Када је чекбокс кликнут, приказујемо анимацију и затим позивамо toggleListStatus
    LaunchedEffect(isCheckboxClicked) {
        if (isCheckboxClicked && !isDeletionLocked) {
            try {
                // Oдмах ажурирамо локално стање за бољи UX
                localCompletedState = !localCompletedState
                
                Log.d("SwipeToDeleteListItem", "Променили смо чекбокс за листу: ${list.id}, ново стање: $localCompletedState")
                
                // Мала пауза за анимацију
                delay(100)
                
                // Ажурирамо статус у бази
                list.id?.let { listId ->
                    // Користимо нову корутину да не блокирамо UI ефекте
                    try {
                        listsViewModel.toggleListStatus(listId)
                    } catch (e: Exception) {
                        Log.e("SwipeToDeleteListItem", "Грешка при ажурирању: ${e.message}")
                    }
                }
            } finally {
                // Ресетујемо стање клика без обзира на исход операције
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
                            enabled = !isDeletionLocked, // Онемогућавамо превлачење ако је брисање закључано
                            onDragStopped = {
                                // Ако не пређемо праг, враћамо елемент назад
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
                    // Икона за статус (чекирано или не) - сада кликабилна
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (localCompletedState) Color(0xFF4CAF50) 
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable(
                                enabled = !isDeletionLocked,
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
                    
                    // Наслов листе - прецртан ако је завршен
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
                            .clickable(
                                enabled = !isDeletionLocked && !isDeleted && !confirmDelete,
                                onClick = onClick
                            )
                    )
                }
            }
        }
    }
} 