package com.petar.smrdici.ui.screens.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.data.model.ShoppingItem
import com.petar.smrdici.data.model.ShoppingList
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(
    navController: NavController,
    viewModel: ListsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedList by viewModel.selectedList.collectAsState()
    val listFormState by viewModel.listFormState.collectAsState()
    val itemFormState by viewModel.itemFormState.collectAsState()
    
    var showAddListDialog by remember { mutableStateOf(false) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Приказ листа или детаља изабране листе
        if (selectedList == null) {
            // Приказ свих листа
            TopAppBar(
                title = { Text("Листе за куповину") },
                actions = {
                    IconButton(onClick = { showAddListDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Додај листу")
                    }
                }
            )
            
            when (uiState) {
                is ListsUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                
                is ListsUiState.Error -> {
                    val errorState = uiState as ListsUiState.Error
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = errorState.message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                is ListsUiState.Success -> {
                    val successState = uiState as ListsUiState.Success
                    
                    if (successState.lists.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Нема листа за куповину. Додајте нову листу.")
                        }
                    } else {
                        LazyColumn {
                            items(successState.lists) { list ->
                                ShoppingListItem(
                                    list = list,
                                    onClick = { viewModel.selectList(list) },
                                    onDelete = { viewModel.deleteShoppingList(list.id) }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Приказ детаља изабране листе
            TopAppBar(
                title = { Text(selectedList!!.title) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.clearSelectedList() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddItemDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Додај ставку")
                    }
                }
            )
            
            // Приказ ставки листе
            if (selectedList!!.items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Нема ставки у листи. Додајте нову ставку.")
                }
            } else {
                LazyColumn {
                    items(selectedList!!.items) { item ->
                        ShoppingItemRow(
                            item = item,
                            onToggle = { viewModel.toggleItemStatus(item.id) },
                            onDelete = { viewModel.removeItemFromList(item.id) }
                        )
                    }
                }
            }
        }
    }
    
    // Дијалог за додавање нове листе
    if (showAddListDialog) {
        AddListDialog(
            formState = listFormState,
            onFormChanged = { updatedForm -> viewModel.updateListForm { updatedForm } },
            onAddList = {
                viewModel.addShoppingList()
                showAddListDialog = false
            },
            onDismiss = { showAddListDialog = false }
        )
    }
    
    // Дијалог за додавање нове ставке
    if (showAddItemDialog) {
        AddItemDialog(
            formState = itemFormState,
            onFormChanged = { updatedForm -> viewModel.updateItemForm { updatedForm } },
            onAddItem = {
                viewModel.addItemToList()
                showAddItemDialog = false
            },
            onDismiss = { showAddItemDialog = false }
        )
    }
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
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = list.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Ставки: ${list.items.size}",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Креирано: ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(list.createdAt.toDate())}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Обриши листу",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun ShoppingItemRow(
    item: ShoppingItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = item.isCompleted,
            onCheckedChange = { onToggle() }
        )
        
        Text(
            text = "${item.name} (${item.quantity})",
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )
        
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Обриши ставку",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddListDialog(
    formState: ListFormState,
    onFormChanged: (ListFormState) -> Unit,
    onAddList: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Додај нову листу") },
        text = {
            Column {
                OutlinedTextField(
                    value = formState.title,
                    onValueChange = { onFormChanged(formState.copy(title = it)) },
                    label = { Text("Назив листе") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAddList,
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