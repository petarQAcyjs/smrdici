package com.petar.smrdici.ui.screens.settings

import android.content.Context
import android.util.Log
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Commute
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SmokingRooms
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.data.model.CategoryIcons
import com.petar.smrdici.data.model.CategoryManager
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.navigation.Screen
import kotlinx.coroutines.launch
import kotlin.math.abs

class CategoryMigrationViewModel(context: Context) : ViewModel() {
    private val categoryManager = CategoryManager.getInstance(context)
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    fun migrateIfNeeded() {
        val migrated = prefs.getBoolean("categories_migrated", false)
        if (!migrated) {
            viewModelScope.launch {
                categoryManager.migrateEnumCategoriesToFirestore()
                prefs.edit {
                    putBoolean("categories_migrated", true)
                }
            }
        }
    }
}

// Funkcija za generisanje boje na osnovu imena kategorije
private fun getExpenseCategoryColor(categoryName: String): Color {
    val context = com.petar.smrdici.utils.AppGlobals.getAppContext()
    
    // If context is available, check for saved color
    if (context != null) {
        val categoryManager = CategoryManager.getInstance(context)
        
        // First check if we have a saved color
        val savedColor = categoryManager.getCategoryColor(categoryName, true)
        Log.d("ExpenseCategories", "Getting color for $categoryName: savedColor=$savedColor")
        if (savedColor != null) {
            try {
                // Convert the long value to a Color
                val alpha = (savedColor shr 24 and 0xFF).toInt()
                val red = (savedColor shr 16 and 0xFF).toInt()
                val green = (savedColor shr 8 and 0xFF).toInt()
                val blue = (savedColor and 0xFF).toInt()
                
                val color = Color(red, green, blue, alpha)
                Log.d("ExpenseCategories", "Using saved color for $categoryName: ARGB($alpha,$red,$green,$blue)")
                return color
            } catch (e: Exception) {
                Log.e("ExpenseCategories", "Error converting color value: $savedColor", e)
                // Fall through to default color
            }
        }
    }
    
    // If no saved color, use the hash-based approach
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
    val index = abs(categoryName.hashCode()) % colors.size
    val defaultColor = colors[index]
    Log.d("ExpenseCategories", "Using default color for $categoryName: ${defaultColor.value}")
    return defaultColor
}

// Funkcija za dobijanje odgovarajuće ikone za kategoriju
private fun getCategoryIcon(categoryName: String): ImageVector {
    val context = com.petar.smrdici.utils.AppGlobals.getAppContext()
    
    // If context is available, check for saved icon
    if (context != null) {
        val categoryManager = CategoryManager.getInstance(context)
        
        // First check if we have a saved icon
        val savedIconName = categoryManager.getCategoryIcon(categoryName, true)
        Log.d("ExpenseCategories", "Getting icon for $categoryName: savedIconName=$savedIconName")
        
        if (savedIconName != null) {
            // Try to find the icon by name
            try {
                Log.d("ExpenseCategories", "Attempting to find icon with name: $savedIconName")
                
                // Use the shared icon finder
                val foundIcon = CategoryIcons.findIconByName(savedIconName)
                if (foundIcon != null) {
                    Log.d("ExpenseCategories", "Found icon for name: $savedIconName")
                    return foundIcon
                }
                
                Log.e("ExpenseCategories", "Could not find icon with name: $savedIconName")
            } catch (e: Exception) {
                Log.e("ExpenseCategories", "Error finding icon $savedIconName: ${e.message}")
                // Fall through to default icon selection
            }
        }
    }
    
    // If no saved icon or couldn't find it, use the default mapping
    Log.d("ExpenseCategories", "Using default icon mapping for $categoryName")
    
    // If no saved icon, use the default mapping
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
        "entertainment", "забава", "zabava" -> Icons.Filled.SportsEsports
        "pets", "љубимци", "ljubimci" -> Icons.Filled.Pets
        "restaurant", "ресторан", "restoran" -> Icons.Filled.Restaurant
        "cigarettes", "цигарете", "cigarete" -> Icons.Filled.SmokingRooms
        "groceries", "намирнице", "namirnice" -> Icons.Filled.LocalGroceryStore
        "debt", "дуг", "dug" -> Icons.Filled.CreditCard
        "celebration", "прослава", "proslava" -> Icons.Filled.Celebration
        "bills", "рачуни", "računi" -> Icons.Filled.Receipt
        "clothing", "одећа", "odeća" -> Icons.Filled.Checkroom
        "travel", "путовање", "putovanje" -> Icons.Filled.Flight
        "sports", "спорт", "sport" -> Icons.Filled.SportsBasketball
        "fitness", "фитнес", "fitnes" -> Icons.Filled.FitnessCenter
        "beauty", "лепота", "lepota" -> Icons.Filled.Face
        "transport", "превоз", "prevoz" -> Icons.Filled.Commute
        "books", "књиге", "knjige" -> Icons.AutoMirrored.Filled.MenuBook
        "games", "игре", "igre" -> Icons.Filled.Casino
        "music", "музика", "muzika" -> Icons.Filled.MusicNote
        "movies", "филмови", "filmovi" -> Icons.Filled.Movie
        "subscriptions", "претплате", "pretplate" -> Icons.Filled.Subscriptions
        "insurance", "осигурање", "osiguranje" -> Icons.Filled.Security
        "taxes", "порези", "porezi" -> Icons.Filled.Receipt
        "maintenance", "одржавање", "održavanje" -> Icons.Filled.Handyman
        "internet", "интернет" -> Icons.Filled.Wifi
        "phone", "телефон", "telefon" -> Icons.Filled.Smartphone
        "utilities", "комуналије", "komunalije" -> Icons.Filled.WaterDrop
        "rent", "кирија", "kirija" -> Icons.Filled.Apartment
        "mortgage", "хипотека", "hipoteka" -> Icons.Filled.AccountBalance
        "investments", "инвестиције", "investicije" -> Icons.AutoMirrored.Filled.TrendingUp
        "charity", "добротворно", "dobrotvorno" -> Icons.Filled.Favorite
        "furniture", "намештај", "nameštaj" -> Icons.Filled.Chair
        "garden", "башта", "bašta" -> Icons.Filled.Grass
        "alcohol", "алкохол", "alkohol" -> Icons.Filled.LocalBar
        else -> Icons.Default.ShoppingCart
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseCategoriesScreen(
    navController: NavController
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
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            Box(modifier = Modifier.statusBarsPadding()) {
                AppHeader(
                    title = "Категорије расхода",
                    navController = navController,
                    showBackButton = true,
                    showProfileIcon = false
                )
            }
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
                                navController.navigate(Screen.EditCategory.createRoute(category, "EXPENSE"))
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
                                .background(Color(0xFF9E9E9E)) // Gray color for create button
                                .clickable {
                                    navController.navigate(Screen.EditCategory.createRoute("", "EXPENSE"))
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
    onEdit: () -> Unit
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
            
            // Remove the edit and delete buttons
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