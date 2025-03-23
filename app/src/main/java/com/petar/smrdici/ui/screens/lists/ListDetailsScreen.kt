package com.petar.smrdici.ui.screens.lists

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.data.model.ShoppingItem
import com.petar.smrdici.data.model.ShoppingList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailsScreen(
    navController: NavController,
    listId: String,
    listsViewModel: ListsViewModel = viewModel(factory = ListsViewModel.Factory(LocalContext.current))
) {
    val selectedList by listsViewModel.selectedList.collectAsState()
    val itemFormState by listsViewModel.itemFormState.collectAsState()
    var showAddItemDialog by remember { mutableStateOf(false) }
    
    // Учитавање листе при првом рендеровању
    LaunchedEffect(listId) {
        listsViewModel.loadListById(listId)
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
                    }
                }
            )
            
            // Информације о листи
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
                        
                        if (list.items.isEmpty()) {
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
                    CircularProgressIndicator()
                }
            }
        }
        
        // Плутајуће дугме за додавање нове ставке
        FloatingActionButton(
            onClick = { showAddItemDialog = true },
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
    
    // Дијалог за додавање нове ставке
    if (showAddItemDialog) {
        AddItemDialog(
            formState = itemFormState,
            onFormChanged = { listsViewModel.updateItemForm { it } },
            onAddItem = {
                listsViewModel.addItemToList()
                showAddItemDialog = false
            },
            onDismiss = { showAddItemDialog = false }
        )
    }
} 