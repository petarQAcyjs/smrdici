package com.petar.smrdici.ui.screens.lists

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.R
import com.petar.smrdici.data.model.ShoppingItem
import com.petar.smrdici.data.model.ShoppingList
import java.text.SimpleDateFormat
import java.util.*
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.navigation.Screen
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.rememberDismissState
import androidx.compose.ui.input.pointer.pointerInput
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.fadeIn
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.offset

@Composable
fun ListsScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(),
    listsViewModel: ListsViewModel = viewModel(factory = ListsViewModel.Factory(LocalContext.current))
) {
    val authState by authViewModel.authState.collectAsState()
    val listsUiState by listsViewModel.uiState.collectAsState()
    val user = if (authState is AuthState.Authenticated) {
        (authState as AuthState.Authenticated).user
    } else null
    
    // Додајемо корутински опсег за Compose компоненту
    val coroutineScope = rememberCoroutineScope()
    
    var showAddListDialog by remember { mutableStateOf(false) }
    
    // Стање освежавања
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
    
    // Функција за освежавање листа
    val refreshLists = {
        coroutineScope.launch {
            isRefreshing = true
            listsViewModel.loadLists()
            delay(1000) // Минимално трајање анимације освежавања
            isRefreshing = false
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
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
            
            // Садржај екрана са подршком за освежавање
            SwipeRefresh(
                state = swipeRefreshState,
                onRefresh = { refreshLists() },
                modifier = Modifier.fillMaxSize()
            ) {
                // Инструкције за превлачење
                androidx.compose.animation.AnimatedVisibility(
                    visible = showSwipeInstruction && listsUiState is ListsUiState.Success && (listsUiState as ListsUiState.Success).lists.isNotEmpty(),
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut()
                ) {
                    Text(
                        text = "Превуците листе удесно за брисање",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
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
                            items(customLists) { list ->
                                list.id?.let { listId ->
                                    SwipeToDeleteListItem(
                                        title = list.title,
                                        isCompleted = list.isCompleted,
                                        onClick = {
                                            navController.navigate(Screen.ListDetails.createRoute(listId))
                                        },
                                        onDelete = {
                                            listsViewModel.deleteShoppingList(listId)
                                        }
                                    )
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
        
        // Плутајуће дугме за додавање нове листе
        FloatingActionButton(
            onClick = { showAddListDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Додај нову листу"
            )
        }
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
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
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
fun CustomListItem(
    title: String,
    isCompleted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Икона за статус (чекирано или не)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isCompleted) Color(0xFF4CAF50) else Color.LightGray),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Завршено",
                        tint = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Наслов листе
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
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
fun ShoppingListItem(
    list: ShoppingList,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = list.title,
                    style = MaterialTheme.typography.titleMedium
                )
                
                Text(
                    text = "${list.items.size} ставки",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            TextButton(
                onClick = onDelete
            ) {
                Text("Обриши")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemDialog(
    formState: ItemFormState,
    onFormChanged: (ItemFormState) -> Unit,
    onAddItem: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Додај нову ставку") },
        text = {
            Column {
                OutlinedTextField(
                    value = formState.name,
                    onValueChange = { onFormChanged(formState.copy(name = it)) },
                    label = { Text("Назив ставке") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = formState.quantity.toString(),
                    onValueChange = { 
                        val quantity = it.toIntOrNull() ?: 1
                        onFormChanged(formState.copy(quantity = quantity)) 
                    },
                    label = { Text("Количина") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAddItem,
                enabled = formState.isValid
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
    title: String,
    isCompleted: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var show by remember { mutableStateOf(true) }
    var offsetX by remember { mutableStateOf(0f) }
    val view = LocalView.current
    
    // Израчунавамо праг за брисање (30% екрана)
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
            onDelete()
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
                        text = "Обриши листу",
                        color = Color.White
                    )
                }
            }
            
            // Садржај који се може превлачити
            CustomListItem(
                title = title,
                isCompleted = isCompleted,
                onClick = onClick,
                modifier = modifier
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
                    .offset { IntOffset(offsetX.roundToInt(), 0) }
            )
        }
    }
} 