package com.petar.smrdici.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Commute
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Copyright
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.House
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SmokingRooms
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.petar.smrdici.data.model.CategoryManager
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import kotlinx.coroutines.launch

enum class CategoryType {
    EXPENSE, INCOME
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCategoryScreen(
    navController: NavController,
    categoryName: String,
    categoryType: CategoryType,
    authViewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val categoryManager = remember { CategoryManager.getInstance(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    var currentCategoryName by remember { mutableStateOf(categoryName) }
    var isNewCategory by remember { mutableStateOf(categoryName.isEmpty()) }
    var selectedIcon by remember { mutableStateOf<ImageVector?>(null) }
    var selectedColor by remember { mutableStateOf<Color?>(null) }
    
    // Search state for icons
    var iconSearchQuery by remember { mutableStateOf("") }
    
    // Initialize with existing category data if editing
    LaunchedEffect(categoryName) {
        if (!isNewCategory) {
            selectedIcon = when (categoryType) {
                CategoryType.EXPENSE -> getCategoryIcon(categoryName)
                CategoryType.INCOME -> getIncomeCategoryIcon(categoryName)
            }
            
            selectedColor = when (categoryType) {
                CategoryType.EXPENSE -> getExpenseCategoryColor(categoryName)
                CategoryType.INCOME -> getIncomeCategoryColor(categoryName)
            }
        } else {
            // Default selections for new category
            selectedIcon = if (categoryType == CategoryType.EXPENSE) 
                Icons.Default.ShoppingCart else Icons.Default.AttachMoney
            selectedColor = predefinedColors[0]
        }
    }
    
    val authState by authViewModel.authState.collectAsState()
    val user = (authState as? AuthState.Authenticated)?.user
    
    Scaffold(
        topBar = {
            AppHeader(
                title = if (isNewCategory) "Додај категорију" else "Измени категорију",
                navController = navController,
                showBackButton = true,
                user = user
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Category name field
            OutlinedTextField(
                value = currentCategoryName,
                onValueChange = { currentCategoryName = it },
                label = { Text("Category Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )
            
            // Type indicator (not editable)
            Text(
                text = "Type",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Text(
                text = if (categoryType == CategoryType.EXPENSE) "Expenses" else "Income",
                modifier = Modifier.padding(start = 4.dp)
            )
            
            // Icon search field
            OutlinedTextField(
                value = iconSearchQuery,
                onValueChange = { iconSearchQuery = it },
                label = { Text("Search Icons") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                },
                trailingIcon = {
                    if (iconSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { iconSearchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear"
                            )
                        }
                    }
                }
            )
            
            // Icon selection section
            Text(
                text = "Icons",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(4.dp),
                modifier = Modifier.height(240.dp)
            ) {
                val icons = if (categoryType == CategoryType.EXPENSE) {
                    expenseIcons
                } else {
                    incomeIcons
                }
                
                // Filter icons based on search query
                val filteredIcons = if (iconSearchQuery.isEmpty()) {
                    icons
                } else {
                    // Filter icons based on their name representation
                    icons.filter { icon ->
                        // Extract icon name from the icon's toString() representation
                        val iconName = icon.toString()
                            .substringAfterLast('.')
                            .replace("_", " ")
                            .lowercase()
                        
                        iconSearchQuery.lowercase() in iconName
                    }
                }
                
                items(filteredIcons) { icon ->
                    IconSelectionItem(
                        icon = icon,
                        isSelected = selectedIcon == icon,
                        onSelect = { selectedIcon = icon }
                    )
                }
            }
            
            // Color selection section
            Text(
                text = "Color",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(4.dp),
                modifier = Modifier.height(120.dp)
            ) {
                items(predefinedColors) { color ->
                    ColorSelectionItem(
                        color = color,
                        isSelected = selectedColor == color,
                        onSelect = { selectedColor = color }
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Save button
            Button(
                onClick = {
                    if (currentCategoryName.isBlank()) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Category name cannot be empty")
                        }
                        return@Button
                    }
                    
                    scope.launch {
                        try {
                            if (isNewCategory) {
                                // Add new category
                                if (categoryType == CategoryType.EXPENSE) {
                                    if (!categoryManager.hasExpenseCategory(currentCategoryName)) {
                                        categoryManager.addExpenseCategoryBoth(currentCategoryName)
                                    } else {
                                        snackbarHostState.showSnackbar("Category already exists")
                                        return@launch
                                    }
                                } else {
                                    if (!categoryManager.hasIncomeCategory(currentCategoryName)) {
                                        categoryManager.addIncomeCategoryBoth(currentCategoryName)
                                    } else {
                                        snackbarHostState.showSnackbar("Category already exists")
                                        return@launch
                                    }
                                }
                            } else {
                                // Update existing category
                                if (categoryType == CategoryType.EXPENSE) {
                                    if (!categoryManager.hasExpenseCategory(currentCategoryName) || currentCategoryName == categoryName) {
                                        categoryManager.updateExpenseCategoryBoth(categoryName, currentCategoryName)
                                    } else {
                                        snackbarHostState.showSnackbar("Category already exists")
                                        return@launch
                                    }
                                } else {
                                    if (!categoryManager.hasIncomeCategory(currentCategoryName) || currentCategoryName == categoryName) {
                                        categoryManager.updateIncomeCategoryBoth(categoryName, currentCategoryName)
                                    } else {
                                        snackbarHostState.showSnackbar("Category already exists")
                                        return@launch
                                    }
                                }
                            }
                            
                            // Navigate back after saving
                            navController.popBackStack()
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("Error: ${e.message}")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Save")
            }
        }
    }
}

@Composable
fun IconSelectionItem(
    icon: ImageVector,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClick = onSelect),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun ColorSelectionItem(
    color: Color,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClick = onSelect),
        contentAlignment = Alignment.Center
    ) {
        // Empty content
    }
}

// Predefined colors for selection
val predefinedColors = listOf(
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
    Color(0xFFAED581)  // Light Green
)

// Icons for expense categories
val expenseIcons = listOf(
    Icons.Default.Receipt,
    Icons.Default.DirectionsCar,
    Icons.Default.ShoppingCart,
    Icons.Default.Pets,
    Icons.Default.Home,
    Icons.Default.LocalHospital,
    Icons.Default.Restaurant,
    Icons.Default.School,
    Icons.Default.Devices,
    Icons.Default.Fastfood,
    Icons.Default.ChildCare,
    Icons.Default.LocalCafe,
    Icons.Default.SmokingRooms,
    Icons.Default.LocalGroceryStore,
    Icons.Default.CreditCard,
    Icons.Default.Celebration,
    Icons.Default.SportsEsports,
    Icons.Default.CardGiftcard,
    // Add many more icons for a comprehensive selection
    Icons.Default.Apartment,
    Icons.Default.Checkroom,
    Icons.Default.Commute,
    Icons.Default.Face,
    Icons.Default.Favorite,
    Icons.Default.FitnessCenter,
    Icons.Default.Flight,
    Icons.Default.Grass,
    Icons.Default.Handyman,
    Icons.Default.LocalBar,
    Icons.Default.Movie,
    Icons.Default.MusicNote,
    Icons.Default.Security,
    Icons.Default.Smartphone,
    Icons.Default.SportsBasketball,
    Icons.Default.Subscriptions,
    Icons.Default.WaterDrop,
    Icons.Default.Wifi,
    Icons.AutoMirrored.Filled.MenuBook,
    Icons.AutoMirrored.Filled.TrendingUp
)

// Icons for income categories
val incomeIcons = listOf(
    Icons.Default.AttachMoney,
    Icons.Default.Payments,
    Icons.Default.AccountBalance,
    Icons.Default.MoneyOff,
    Icons.Default.Work,
    Icons.Default.Savings,
    Icons.Default.LocalAtm,
    Icons.Default.CardGiftcard,
    // Add many more icons for a comprehensive selection
    Icons.Default.BusinessCenter,
    Icons.Default.Casino,
    Icons.Default.Copyright,
    Icons.Default.EmojiEvents,
    Icons.Default.EventSeat,
    Icons.Default.FamilyRestroom,
    Icons.Default.Gavel,
    Icons.Default.Handshake,
    Icons.Default.HealthAndSafety,
    Icons.Default.House,
    Icons.Default.PersonOff,
    Icons.Default.PieChart,
    Icons.Default.Receipt,
    Icons.Default.School,
    Icons.AutoMirrored.Filled.TrendingUp,
    Icons.Default.Computer,
    Icons.Default.AutoAwesome
) 