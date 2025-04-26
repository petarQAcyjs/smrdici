package com.petar.smrdici.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.petar.smrdici.data.model.CategoryManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeCategoriesScreen(
    navController: NavController
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // Lista kategorija prihoda
    val incomeCategories = remember { mutableStateListOf<String>() }
    
    // Koristimo CategoryManager za dobavljanje svih kategorija
    val context = LocalContext.current
    val categoryManager = remember { CategoryManager.getInstance(context) }
    
    // Inicijalno popunimo listu svim dostupnim kategorijama
    LaunchedEffect(Unit) {
        incomeCategories.clear()
        // Koristimo getAllIncomeCategories umesto direktnog pristupa enumeraciji
        categoryManager.getAllIncomeCategories().forEach { categoryName ->
            // Dobavljamo display name za svaku kategoriju
            incomeCategories.add(categoryManager.getIncomeCategoryDisplayName(categoryName))
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
                title = { Text("Категорије прихода") },
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
            items(incomeCategories) { category ->
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
                    label = { Text("Назив категорије") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newCategoryName.isNotBlank()) {
                        // Proveravamo da li kategorija već postoji
                        if (!categoryManager.hasIncomeCategory(newCategoryName)) {
                            // Dodajemo novu kategoriju
                            categoryManager.addIncomeCategory(newCategoryName)
                            // Osvežavamo listu
                            incomeCategories.clear()
                            categoryManager.getAllIncomeCategories().forEach { categoryName ->
                                incomeCategories.add(categoryManager.getIncomeCategoryDisplayName(categoryName))
                            }
                            showAddDialog = false
                        } else {
                            // Prikazujemo poruku da kategorija već postoji
                            scope.launch {
                                snackbarHostState.showSnackbar("Категорија већ постоји!")
                            }
                        }
                    }
                }) {
                    Text("Додај")
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
                    label = { Text("Нови назив категорије") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newCategoryName.isNotBlank() && selectedCategory.isNotBlank()) {
                        // Proveravamo da li nova kategorija već postoji
                        if (!categoryManager.hasIncomeCategory(newCategoryName) || newCategoryName == selectedCategory) {
                            // Ažuriramo kategoriju
                            categoryManager.updateIncomeCategory(selectedCategory, newCategoryName)
                            // Osvežavamo listu
                            incomeCategories.clear()
                            categoryManager.getAllIncomeCategories().forEach { categoryName ->
                                incomeCategories.add(categoryManager.getIncomeCategoryDisplayName(categoryName))
                            }
                            showEditDialog = false
                        } else {
                            // Prikazujemo poruku da kategorija već postoji
                            scope.launch {
                                snackbarHostState.showSnackbar("Категорија већ постоји!")
                            }
                        }
                    }
                }) {
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
            text = { Text("Да ли сте сигурни да желите да обришете категорију '$selectedCategory'?") },
            confirmButton = {
                TextButton(onClick = {
                    if (selectedCategory.isNotBlank()) {
                        // Brišemo kategoriju
                        categoryManager.deleteIncomeCategory(selectedCategory)
                        // Osvežavamo listu
                        incomeCategories.clear()
                        categoryManager.getAllIncomeCategories().forEach { categoryName ->
                            incomeCategories.add(categoryManager.getIncomeCategoryDisplayName(categoryName))
                        }
                        showDeleteDialog = false
                    }
                }) {
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