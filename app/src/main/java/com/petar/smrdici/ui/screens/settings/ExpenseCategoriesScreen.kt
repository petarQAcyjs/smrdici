package com.petar.smrdici.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.petar.smrdici.data.model.ExpenseCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseCategoriesScreen(
    navController: NavController
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    // Lista kategorija rashoda
    val expenseCategories = remember { mutableStateListOf<String>() }
    
    // Inicijalno popunimo listu svim dostupnim kategorijama iz enumeracije
    LaunchedEffect(Unit) {
        expenseCategories.clear()
        ExpenseCategory.entries.forEach { 
            expenseCategories.add(it.getDisplayName()) 
        }
    }
    
    // Stanje za dijaloge
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("") }
    var newCategoryName by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Категорије расхода") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        newCategoryName = ""
                        showAddDialog = true 
                    }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Додај категорију"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(expenseCategories) { category ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    
                    IconButton(onClick = { 
                        selectedCategory = category
                        newCategoryName = category
                        showEditDialog = true 
                    }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Измени категорију"
                        )
                    }
                    
                    IconButton(onClick = { 
                        selectedCategory = category
                        showDeleteDialog = true 
                    }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Обриши категорију",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                HorizontalDivider()
            }
        }
    }
    
    // Dijalog za dodavanje nove kategorije
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Додај нову категорију") },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("Назив категорије") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newCategoryName.isNotBlank() && !expenseCategories.contains(newCategoryName)) {
                            expenseCategories.add(newCategoryName)
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Сачувај")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Откажи")
                }
            }
        )
    }
    
    // Dijalog za izmenu kategorije
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Измени категорију") },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("Назив категорије") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newCategoryName.isNotBlank()) {
                            val index = expenseCategories.indexOf(selectedCategory)
                            if (index != -1) {
                                expenseCategories[index] = newCategoryName
                            }
                            showEditDialog = false
                        }
                    }
                ) {
                    Text("Сачувај")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Откажи")
                }
            }
        )
    }
    
    // Dijalog za brisanje kategorije
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Обриши категорију") },
            text = { Text("Да ли сте сигурни да желите да обришете категорију \"$selectedCategory\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        expenseCategories.remove(selectedCategory)
                        showDeleteDialog = false
                    }
                ) {
                    Text("Обриши")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Откажи")
                }
            }
        )
    }
} 