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
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import androidx.compose.ui.platform.LocalContext

@Composable
fun ListsScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(),
    listsViewModel: ListsViewModel = viewModel(factory = ListsViewModel.Factory(LocalContext.current))
) {
    val authState by authViewModel.authState.collectAsState()
    val user = if (authState is com.petar.smrdici.ui.auth.AuthState.Authenticated) {
        (authState as com.petar.smrdici.ui.auth.AuthState.Authenticated).user
    } else null
    
    val uiState by listsViewModel.uiState.collectAsState()
    val selectedList by listsViewModel.selectedList.collectAsState()
    val listFormState by listsViewModel.listFormState.collectAsState()
    var showAddListDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        AppHeader(
            title = "Листе за куповину",
            user = user,
            navController = navController,
            showBackButton = true
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Листа за куповину
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
                    
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(successState.lists) { list ->
                            ShoppingListItem(
                                list = list,
                                onClick = { listsViewModel.selectList(list) },
                                onDelete = { listsViewModel.deleteShoppingList(list.id) }
                            )
                        }
                    }
                }
            }
            
            // Дугме за додавање нове листе
            FloatingActionButton(
                onClick = { showAddListDialog = true },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Додај листу"
                )
            }
        }
    }
    
    // Дијалог за додавање нове листе
    if (showAddListDialog) {
        AlertDialog(
            onDismissRequest = { showAddListDialog = false },
            title = { Text("Нова листа за куповину") },
            text = {
                Column {
                    OutlinedTextField(
                        value = listFormState.title,
                        onValueChange = { listsViewModel.updateListForm { it.copy(title = it.title) } },
                        label = { Text("Назив листе") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        listsViewModel.addShoppingList()
                        showAddListDialog = false
                    },
                    enabled = listFormState.isValid
                ) {
                    Text("Додај")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddListDialog = false }) {
                    Text("Откажи")
                }
            }
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