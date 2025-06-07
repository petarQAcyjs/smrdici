package com.petar.smrdici.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import com.petar.smrdici.ui.components.AppHeader
import kotlinx.coroutines.launch
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.auth.AuthState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.PaddingValues
import android.util.Log
import androidx.compose.material3.CircularProgressIndicator
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import androidx.lifecycle.ViewModelProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeCategoriesScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // Lista kategorija prihoda
    val incomeCategories = remember { mutableStateListOf<String>() }
    
    // Koristimo CategoryManager za dobavljanje svih kategorija
    val context = LocalContext.current
    val categoryManager = remember { CategoryManager.getInstance(context) }
    
    val isLoading = remember { mutableStateOf(true) }
    
    val migrationViewModel: CategoryMigrationViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return CategoryMigrationViewModel(context) as T
            }
        }
    )
    
    LaunchedEffect(Unit) {
        migrationViewModel.migrateIfNeeded()
        isLoading.value = true
        val categories = categoryManager.getIncomeCategoriesWithFallback()
        incomeCategories.clear()
        incomeCategories.addAll(categories)
        isLoading.value = false
    }
    
    // Stanje za dijaloge
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("") }
    var newCategoryName by remember { mutableStateOf("") }
    
    val authState by authViewModel.authState.collectAsState()
    val user = if (authState is AuthState.Authenticated) (authState as AuthState.Authenticated).user else null
    
    Scaffold(
        topBar = {
            AppHeader(
                title = "Категорије прихода",
                navController = navController,
                showBackButton = true,
                user = user
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            androidx.compose.material3.FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Додај категорију")
            }
        }
    ) { paddingValues ->
        if (isLoading.value) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
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
                            Log.d("IncomeCategoriesScreen", "Edit icon clicked for category: $category")
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
                    scope.launch {
                        if (newCategoryName.isNotBlank()) {
                            if (!categoryManager.hasIncomeCategory(newCategoryName)) {
                                isLoading.value = true
                                categoryManager.addIncomeCategoryBoth(newCategoryName)
                                val categories = categoryManager.getIncomeCategoriesWithFallback()
                                incomeCategories.clear()
                                incomeCategories.addAll(categories)
                                isLoading.value = false
                                showAddDialog = false
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Категорија већ постоји!")
                                }
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
                    scope.launch {
                        if (newCategoryName.isNotBlank() && selectedCategory.isNotBlank()) {
                            if (!categoryManager.hasIncomeCategory(newCategoryName) || newCategoryName == selectedCategory) {
                                isLoading.value = true
                                categoryManager.updateIncomeCategoryBoth(selectedCategory, newCategoryName)
                                val categories = categoryManager.getIncomeCategoriesWithFallback()
                                incomeCategories.clear()
                                incomeCategories.addAll(categories)
                                isLoading.value = false
                                showEditDialog = false
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Категорија већ постоји!")
                                }
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
                    scope.launch {
                        if (selectedCategory.isNotBlank()) {
                            isLoading.value = true
                            categoryManager.deleteIncomeCategoryBoth(selectedCategory)
                            val categories = categoryManager.getIncomeCategoriesWithFallback()
                            incomeCategories.clear()
                            incomeCategories.addAll(categories)
                            isLoading.value = false
                            showDeleteDialog = false
                        }
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