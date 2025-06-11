package com.petar.smrdici.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SmokingRooms
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.data.model.CategoryManager
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import kotlinx.coroutines.launch

class CategoryMigrationViewModel(context: Context) : ViewModel() {
    private val categoryManager = CategoryManager.getInstance(context)
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    fun migrateIfNeeded() {
        val migrated = prefs.getBoolean("categories_migrated", false)
        if (!migrated) {
            viewModelScope.launch {
                categoryManager.migrateEnumCategoriesToFirestore()
                prefs.edit().putBoolean("categories_migrated", true).apply()
            }
        }
    }
}

// Funkcija za generisanje boje na osnovu imena kategorije
fun getExpenseCategoryColor(categoryName: String): Color {
    val colors = listOf(
        Color(0xFFE57373), // Red
        Color(0xFF64B5F6), // Blue
        Color(0xFFFFD54F), // Yellow
        Color(0xFF81C784), // Green
        Color(0xFFBA68C8), // Purple
        Color(0xFF4FC3F7), // Light Blue
        Color(0xFFFF8A65), // Orange
        Color(0xFF9575CD), // Deep Purple
        Color(0xFF4DB6AC), // Teal
        Color(0xFFF06292), // Pink
        Color(0xFF7986CB), // Indigo
        Color(0xFFA1887F), // Brown
        Color(0xFF90A4AE), // Blue Grey
        Color(0xFFFFB74D), // Amber
        Color(0xFFAED581), // Light Green
    )
    
    // Koristimo hash kod imena kategorije za odabir boje
    val index = Math.abs(categoryName.hashCode()) % colors.size
    return colors[index]
}

// Funkcija za dobijanje odgovarajuće ikone za kategoriju
fun getCategoryIcon(categoryName: String): ImageVector {
    return when (categoryName.lowercase()) {
        "food", "храна", "hrana" -> Icons.Default.Fastfood
        "home", "кућа", "kuća" -> Icons.Default.Home
        "health", "здравље", "zdravlje" -> Icons.Default.LocalHospital
        "car", "ауто", "auto" -> Icons.Default.DirectionsCar
        "education", "образовање", "obrazovanje" -> Icons.Default.School
        "children", "деца", "deca" -> Icons.Default.ChildCare
        "gifts", "поклони", "pokloni" -> Icons.Default.CardGiftcard
        "cafe", "кафа", "kafa" -> Icons.Default.LocalCafe
        "electronics", "електроника", "elektronika" -> Icons.Default.Devices
        "entertainment", "забава", "zabava" -> Icons.Default.SportsEsports
        "pets", "љубимци", "ljubimci" -> Icons.Default.Pets
        "restaurant", "ресторан", "restoran" -> Icons.Default.Restaurant
        "cigarettes", "цигарете", "cigarete" -> Icons.Default.SmokingRooms
        "groceries", "намирнице", "namirnice" -> Icons.Default.LocalGroceryStore
        "debt", "дуг", "dug" -> Icons.Default.CreditCard
        "celebration", "прослава", "proslava" -> Icons.Default.Celebration
        else -> Icons.Default.ShoppingCart
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseCategoriesScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // Lista kategorija rashoda
    val expenseCategories = remember { mutableStateListOf<String>() }
    
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
        val categories = categoryManager.getExpenseCategoriesWithFallback()
        expenseCategories.clear()
        expenseCategories.addAll(categories)
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
                title = "Категорије расхода",
                navController = navController,
                showBackButton = true,
                user = user
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (isLoading.value) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Tab switcher for EXPENSES/INCOME
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = "EXPENSES",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        // Underline for active tab
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(MaterialTheme.colorScheme.primary)
                                .align(Alignment.BottomCenter)
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(4.dp)
                            .clickable {
                                navController.navigate("income_categories") {
                                    popUpTo("expense_categories") { inclusive = true }
                                }
                            }
                    ) {
                        Text(
                            text = "INCOME",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Grid of categories
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    contentPadding = PaddingValues(6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    items(expenseCategories) { category ->
                        CategoryItem(
                            name = category,
                            color = getExpenseCategoryColor(category),
                            onEdit = {
                                selectedCategory = category
                                newCategoryName = category
                                showEditDialog = true
                            },
                            onDelete = {
                                selectedCategory = category
                                showDeleteDialog = true
                            }
                        )
                    }
                    
                    // Add "Create" item at the end
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(Color(0xFF9E9E9E)) // Grey color for create button
                                .clickable {
                                    newCategoryName = ""
                                    showAddDialog = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Create",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
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
                            if (!categoryManager.hasExpenseCategory(newCategoryName)) {
                                isLoading.value = true
                                categoryManager.addExpenseCategoryBoth(newCategoryName)
                                val categories = categoryManager.getExpenseCategoriesWithFallback()
                                expenseCategories.clear()
                                expenseCategories.addAll(categories)
                                isLoading.value = false
                                showAddDialog = false
                            } else {
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
                    scope.launch {
                        if (newCategoryName.isNotBlank() && selectedCategory.isNotBlank()) {
                            if (!categoryManager.hasExpenseCategory(newCategoryName) || newCategoryName == selectedCategory) {
                                isLoading.value = true
                                categoryManager.updateExpenseCategoryBoth(selectedCategory, newCategoryName)
                                val categories = categoryManager.getExpenseCategoriesWithFallback()
                                expenseCategories.clear()
                                expenseCategories.addAll(categories)
                                isLoading.value = false
                                showEditDialog = false
                            } else {
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
                    scope.launch {
                        if (selectedCategory.isNotBlank()) {
                            isLoading.value = true
                            categoryManager.deleteExpenseCategoryBoth(selectedCategory)
                            val categories = categoryManager.getExpenseCategoriesWithFallback()
                            expenseCategories.clear()
                            expenseCategories.addAll(categories)
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

@Composable
fun CategoryItem(
    name: String,
    color: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(bottom = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            // Circle background with icon
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .clickable { onEdit() },
                color = color
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = getCategoryIcon(name),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            // Edit button - small white circle in top-right
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { onEdit() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = color,
                    modifier = Modifier.size(8.dp)
                )
            }
            
            // Delete button - small white circle in bottom-right
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = color,
                    modifier = Modifier.size(8.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(1.dp))
        
        // Category name
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth()
        )
    }
} 