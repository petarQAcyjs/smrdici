package com.petar.smrdici.ui.screens.settings

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SmokingRooms
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Commute
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.petar.smrdici.data.model.CategoryIcons
import com.petar.smrdici.data.model.CategoryManager
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.utils.LogUtils
import kotlinx.coroutines.launch
import kotlin.math.abs

// Helper functions to get category icons and colors
@Composable
private fun getCategoryIcon(categoryName: String): ImageVector {
    val context = LocalContext.current
    val categoryManager = remember { CategoryManager.getInstance(context) }
    
    // First check if we have a saved icon
    val savedIconName = categoryManager.getCategoryIcon(categoryName, true)
    LogUtils.d("EditCategoryScreen", "Getting icon for $categoryName: savedIconName=$savedIconName")
    
    if (savedIconName != null) {
        // Try to find the icon by name
        try {
            LogUtils.d("EditCategoryScreen", "Attempting to find icon with name: $savedIconName")
            
            // Use the shared icon finder
            val foundIcon = CategoryIcons.findIconByName(savedIconName)
            if (foundIcon != null) {
                LogUtils.d("EditCategoryScreen", "Found icon for name: $savedIconName")
                return foundIcon
            }
            
            LogUtils.e("EditCategoryScreen", "Could not find icon with name: $savedIconName")
        } catch (e: Exception) {
            LogUtils.e("EditCategoryScreen", "Error finding icon $savedIconName: ${e.message}")
            // Fall through to default icon selection
        }
    }
    
    // If no saved icon or couldn't find it, use the default mapping
    LogUtils.d("EditCategoryScreen", "Using default icon mapping for $categoryName")
    
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

@Composable
private fun getIncomeCategoryIcon(categoryName: String): ImageVector {
    val context = LocalContext.current
    val categoryManager = remember { CategoryManager.getInstance(context) }
    
    // First check if we have a saved icon
    val savedIconName = categoryManager.getCategoryIcon(categoryName, false)
    LogUtils.d("EditCategoryScreen", "Getting income icon for $categoryName: savedIconName=$savedIconName")
    
    if (savedIconName != null) {
        // Try to find the icon by name
        try {
            LogUtils.d("EditCategoryScreen", "Attempting to find icon with name: $savedIconName")
            
            // Use the shared icon finder
            val foundIcon = CategoryIcons.findIconByName(savedIconName)
            if (foundIcon != null) {
                LogUtils.d("EditCategoryScreen", "Found icon for name: $savedIconName")
                return foundIcon
            }
            
            LogUtils.e("EditCategoryScreen", "Could not find icon with name: $savedIconName")
        } catch (e: Exception) {
            LogUtils.e("EditCategoryScreen", "Error finding icon $savedIconName: ${e.message}")
            // Fall through to default icon selection
        }
    }
    
    // If no saved icon or couldn't find it, use the default mapping
    LogUtils.d("EditCategoryScreen", "Using default icon mapping for income category: $categoryName")
    
    // If no saved icon, use the default mapping
    return when (categoryName.lowercase()) {
        "salary", "плата", "plata" -> Icons.Default.Work
        "bonus", "бонус" -> Icons.Default.MonetizationOn
        "gift", "поклон", "poklon" -> Icons.Default.CardGiftcard
        "investment", "инвестиција", "investicija" -> Icons.AutoMirrored.Filled.TrendingUp
        "rental", "рентал", "rental" -> Icons.Default.Apartment
        "business", "бизнис", "biznis" -> Icons.Default.Business
        "freelance", "фриленс", "frilens" -> Icons.Default.Payments
        "interest", "камата", "kamata" -> Icons.Default.AccountBalance
        "dividend", "дивиденда", "dividenda" -> Icons.Default.CurrencyExchange
        "refund", "повраћај", "povraćaj" -> Icons.Default.Savings
        "sale", "продаја", "prodaja" -> Icons.Default.LocalGroceryStore
        "other", "друго", "drugo" -> Icons.Default.AccountBalanceWallet
        else -> Icons.Default.AttachMoney
    }
}

@Composable
private fun getExpenseCategoryColor(categoryName: String): Color {
    val context = LocalContext.current
    val categoryManager = remember { CategoryManager.getInstance(context) }
    
    // First check if we have a saved color
    val savedColor = categoryManager.getCategoryColor(categoryName, true)
    LogUtils.d("EditCategoryScreen", "Getting color for $categoryName: savedColor=$savedColor")
    if (savedColor != null) {
        try {
            // Convert the long value to a Color
            val alpha = (savedColor shr 24 and 0xFF).toInt()
            val red = (savedColor shr 16 and 0xFF).toInt()
            val green = (savedColor shr 8 and 0xFF).toInt()
            val blue = (savedColor and 0xFF).toInt()
            
            val color = Color(red, green, blue, alpha)
            LogUtils.d("EditCategoryScreen", "Using saved color for $categoryName: ARGB($alpha,$red,$green,$blue)")
            return color
        } catch (e: Exception) {
            LogUtils.e("EditCategoryScreen", "Error converting color value: $savedColor", e)
            // Fall through to default color
        }
    }
    
    // If no saved color, use a diverse set of colors
    val colors = listOf(
        // Material Design colors
        Color(0xFFE91E63), // Pink
        Color(0xFF9C27B0), // Purple
        Color(0xFF3F51B5), // Indigo
        Color(0xFF2196F3), // Blue
        Color(0xFF00BCD4), // Cyan
        Color(0xFF009688), // Teal
        Color(0xFF4CAF50), // Green
        Color(0xFFCDDC39), // Lime
        Color(0xFFFFC107), // Amber
        Color(0xFFFF5722), // Deep Orange
        
        // Rich/Dark colors
        Color(0xFF6A1B9A), // Rich Purple
        Color(0xFF1A237E), // Deep Blue
        Color(0xFF1B5E20), // Forest Green
        Color(0xFFB71C1C), // Dark Red
        Color(0xFF880E4F), // Dark Pink
        
        // Bright colors
        Color(0xFF4285F4), // Google Blue
        Color(0xFFEA4335), // Google Red
        Color(0xFFFBBC05), // Google Yellow
        Color(0xFF34A853), // Google Green
        Color(0xFFFF9800)  // Orange
    )
    
    // Use hash code of category name to select color
    val index = abs(categoryName.hashCode()) % colors.size
    val defaultColor = colors[index]
    LogUtils.d("EditCategoryScreen", "Using default color for $categoryName: ${defaultColor.value}")
    return defaultColor
}

@Composable
private fun getIncomeCategoryColor(categoryName: String): Color {
    val context = LocalContext.current
    val categoryManager = remember { CategoryManager.getInstance(context) }
    
    // First check if we have a saved color
    val savedColor = categoryManager.getCategoryColor(categoryName, false)
    LogUtils.d("EditCategoryScreen", "Getting income color for $categoryName: savedColor=$savedColor")
    if (savedColor != null) {
        try {
            // Convert the long value to a Color
            val alpha = (savedColor shr 24 and 0xFF).toInt()
            val red = (savedColor shr 16 and 0xFF).toInt()
            val green = (savedColor shr 8 and 0xFF).toInt()
            val blue = (savedColor and 0xFF).toInt()
            
            val color = Color(red, green, blue, alpha)
            LogUtils.d("EditCategoryScreen", "Using saved income color for $categoryName: ARGB($alpha,$red,$green,$blue)")
            return color
        } catch (e: Exception) {
            LogUtils.e("EditCategoryScreen", "Error converting income color value: $savedColor", e)
            // Fall through to default color
        }
    }
    
    // If no saved color, use a diverse set of colors with an income-focused palette
    val colors = listOf(
        // Green/Blue tones (traditionally associated with income/money)
        Color(0xFF4CAF50), // Green
        Color(0xFF009688), // Teal
        Color(0xFF00BCD4), // Cyan
        Color(0xFF2196F3), // Blue
        Color(0xFF3F51B5), // Indigo
        
        // Warm colors for contrast
        Color(0xFFFF9800), // Orange
        Color(0xFFE91E63), // Pink
        Color(0xFF9C27B0), // Purple
        
        // Rich/Dark colors
        Color(0xFF1A237E), // Deep Blue
        Color(0xFF1B5E20), // Forest Green
        Color(0xFF006064), // Dark Cyan
        
        // Bright colors
        Color(0xFF4285F4), // Google Blue
        Color(0xFF34A853), // Google Green
        Color(0xFFFBBC05), // Google Yellow
        
        // Pastel colors for a softer look
        Color(0xFFBBDEFB), // Pastel Blue
        Color(0xFFC8E6C9), // Pastel Green
        Color(0xFFD1C4E9), // Pastel Purple
        Color(0xFFFFF9C4), // Pastel Yellow
        Color(0xFFFFE0B2), // Pastel Orange
        Color(0xFFFFCCBC)  // Pastel Red
    )
    
    // Use hash code of category name to select color
    val index = abs(categoryName.hashCode()) % colors.size
    val defaultColor = colors[index]
    LogUtils.d("EditCategoryScreen", "Using default income color for $categoryName: ${defaultColor.value}")
    return defaultColor
}

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
    var selectedIcon by remember { 
        mutableStateOf<ImageVector>(
            if (categoryType == CategoryType.EXPENSE) Icons.Default.ShoppingCart else Icons.Default.AttachMoney
        ) 
    }
    var selectedColor by remember { 
        mutableStateOf<Color>(
            categoryManager.getAllComposeColors()[0]
        ) 
    }
    
    // Dialog state for delete confirmation
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // Search state for icons
    var iconSearchQuery by remember { mutableStateOf("") }
    
    // Initialize with existing category data if editing
    if (!isNewCategory) {
        val icon = if (categoryType == CategoryType.EXPENSE) {
            getCategoryIcon(categoryName)
        } else {
            getIncomeCategoryIcon(categoryName)
        }
        
        val color = if (categoryType == CategoryType.EXPENSE) {
            getExpenseCategoryColor(categoryName)
        } else {
            getIncomeCategoryColor(categoryName)
        }
        
        // Update the state variables with the computed values
        LaunchedEffect(categoryName) {
            selectedIcon = icon
            selectedColor = color
            LogUtils.d("EditCategoryScreen", "Initialized with icon: ${selectedIcon?.toString() ?: "null"} for category: $categoryName")
            LogUtils.d("EditCategoryScreen", "Initialized with color: ${selectedColor?.toString() ?: "null"} for category: $categoryName")
        }
    }
    
    val authState by authViewModel.authState.collectAsState()
    val user = (authState as? AuthState.Authenticated)?.user
    
    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Обриши категорију") },
            text = { Text("Да ли сте сигурни да желите да обришете категорију '$categoryName'?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            LogUtils.i("EditCategoryScreen", "Deleting category: $categoryName, type: $categoryType")
                            if (categoryType == CategoryType.EXPENSE) {
                                categoryManager.deleteExpenseCategoryBoth(categoryName)
                            } else {
                                categoryManager.deleteIncomeCategoryBoth(categoryName)
                            }
                            LogUtils.i("EditCategoryScreen", "Successfully deleted category: $categoryName")
                            showDeleteDialog = false
                            navController.popBackStack()
                        } catch (e: Exception) {
                            LogUtils.e("EditCategoryScreen", "Failed to delete category: $categoryName", e)
                            snackbarHostState.showSnackbar("Error: ${e.message}")
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
    
    Scaffold(
        topBar = {
            Box(modifier = Modifier.statusBarsPadding()) {
            AppHeader(
                title = if (isNewCategory) "Додај категорију" else "Измени категорију",
                navController = navController,
                showBackButton = true,
                user = user,
                showProfileIcon = false,
                actions = {
                    // Add save button to the app bar
                    IconButton(onClick = {
                        if (currentCategoryName.isBlank()) {
                            scope.launch {
                                LogUtils.w("EditCategoryScreen", "Attempted to save with empty category name")
                                snackbarHostState.showSnackbar("Category name cannot be empty")
                            }
                            return@IconButton
                        }
                        
                        scope.launch {
                            try {
                                LogUtils.i("EditCategoryScreen", "Saving category: $currentCategoryName, type: $categoryType, isNew: $isNewCategory")
                                
                                // Save the selected color
                                selectedColor?.let { color ->
                                    // Convert Color to ARGB long value
                                    val alpha = (color.alpha * 255).toInt()
                                    val red = (color.red * 255).toInt()
                                    val green = (color.green * 255).toInt()
                                    val blue = (color.blue * 255).toInt()
                                    
                                    val colorValue = (alpha.toLong() shl 24) or
                                                    (red.toLong() shl 16) or
                                                    (green.toLong() shl 8) or
                                                    blue.toLong()
                                    
                                    val isExpense = categoryType == CategoryType.EXPENSE
                                    
                                    LogUtils.d("EditCategoryScreen", "Saving color: ARGB($alpha,$red,$green,$blue) = $colorValue for category: $currentCategoryName, isExpense: $isExpense")
                                    
                                    // For new category, save with new name
                                    if (isNewCategory) {
                                        categoryManager.saveCategoryColor(currentCategoryName, colorValue, isExpense)
                                    } else if (currentCategoryName != categoryName) {
                                        // For renamed category, update the color key
                                        categoryManager.updateCategoryColor(categoryName, currentCategoryName, isExpense)
                                        // Also save the color for the new name
                                        categoryManager.saveCategoryColor(currentCategoryName, colorValue, isExpense)
                                    } else {
                                        // For existing category without name change, just save the color
                                        categoryManager.saveCategoryColor(currentCategoryName, colorValue, isExpense)
                                    }
                                } ?: LogUtils.e("EditCategoryScreen", "No color selected!")
                                
                                // Save the selected icon
                                selectedIcon?.let { icon ->
                                    // Extract icon name from the icon
                                    val iconFullName = icon.toString()
                                    LogUtils.d("EditCategoryScreen", "Icon full name: $iconFullName")
                                    
                                    // Extract the simple class name of the icon
                                    val iconName = try {
                                        // Use reflection to get the actual field name
                                        var foundName: String? = null
                                        
                                        // Try Icons.Default
                                        val defaultFields = Icons.Default::class.java.declaredFields
                                        for (field in defaultFields) {
                                            field.isAccessible = true
                                            if (field.get(Icons.Default) == icon) {
                                                foundName = field.name
                                                LogUtils.d("EditCategoryScreen", "Found icon in Icons.Default: $foundName")
                                                break
                                            }
                                        }
                                        
                                        // Try Icons.AutoMirrored.Filled if not found in Default
                                        if (foundName == null) {
                                            val autoMirroredFields = Icons.AutoMirrored.Filled::class.java.declaredFields
                                            for (field in autoMirroredFields) {
                                                field.isAccessible = true
                                                if (field.get(Icons.AutoMirrored.Filled) == icon) {
                                                    foundName = field.name
                                                    LogUtils.d("EditCategoryScreen", "Found icon in Icons.AutoMirrored.Filled: $foundName")
                                                    break
                                                }
                                            }
                                        }
                                        
                                        // Try Icons.Filled if not found yet
                                        if (foundName == null) {
                                            val filledFields = Icons.Filled::class.java.declaredFields
                                            for (field in filledFields) {
                                                field.isAccessible = true
                                                if (field.get(Icons.Filled) == icon) {
                                                    foundName = field.name
                                                    LogUtils.d("EditCategoryScreen", "Found icon in Icons.Filled: $foundName")
                                                    break
                                                }
                                            }
                                        }
                                        
                                        // If still null, try fallback methods
                                        foundName ?: run {
                                            // Fallback to using index in our predefined lists
                                            val isExpense = categoryType == CategoryType.EXPENSE
                                            val icons = if (isExpense) CategoryIcons.expenseIcons else CategoryIcons.incomeIcons
                                            
                                            val index = icons.indexOf(icon)
                                            if (index != -1) {
                                                if (isExpense) {
                                                    "ExpenseIcon_$index"
                                                } else {
                                                    "IncomeIcon_$index"
                                                }
                                            } else {
                                                // Last resort - use a default icon name based on category type
                                                if (isExpense) "ShoppingCart" else "AttachMoney"
                                            }
                                        }
                                    } catch (e: Exception) {
                                        LogUtils.e("EditCategoryScreen", "Error extracting icon name", e)
                                        // Fallback
                                        if (categoryType == CategoryType.EXPENSE) "ShoppingCart" else "AttachMoney"
                                    }
                                    
                                    val isExpense = categoryType == CategoryType.EXPENSE
                                    
                                    LogUtils.d("EditCategoryScreen", "Saving icon: $iconName for category: $currentCategoryName, isExpense: $isExpense (from $iconFullName)")
                                    
                                    // For new category, save with new name
                                    if (isNewCategory) {
                                        categoryManager.saveCategoryIcon(currentCategoryName, iconName, isExpense)
                                    } else if (currentCategoryName != categoryName) {
                                        // For renamed category, update the icon key
                                        categoryManager.updateCategoryIcon(categoryName, currentCategoryName, isExpense)
                                        // Also save the icon for the new name
                                        categoryManager.saveCategoryIcon(currentCategoryName, iconName, isExpense)
                                    } else {
                                        // For existing category without name change, just save the icon
                                        categoryManager.saveCategoryIcon(currentCategoryName, iconName, isExpense)
                                    }
                                } ?: LogUtils.e("EditCategoryScreen", "No icon selected!")
                                
                                if (isNewCategory) {
                                    // Add new category
                                    if (categoryType == CategoryType.EXPENSE) {
                                        if (!categoryManager.hasExpenseCategory(currentCategoryName)) {
                                            LogUtils.i("EditCategoryScreen", "Adding new expense category: $currentCategoryName")
                                            categoryManager.addExpenseCategoryBoth(currentCategoryName)
                                            LogUtils.i("EditCategoryScreen", "Successfully added expense category: $currentCategoryName")
                                            navController.popBackStack()
                                        } else {
                                            LogUtils.w("EditCategoryScreen", "Category already exists: $currentCategoryName")
                                            snackbarHostState.showSnackbar("Category already exists")
                                        }
                                    } else {
                                        if (!categoryManager.hasIncomeCategory(currentCategoryName)) {
                                            LogUtils.i("EditCategoryScreen", "Adding new income category: $currentCategoryName")
                                            categoryManager.addIncomeCategoryBoth(currentCategoryName)
                                            LogUtils.i("EditCategoryScreen", "Successfully added income category: $currentCategoryName")
                                            navController.popBackStack()
                                        } else {
                                            LogUtils.w("EditCategoryScreen", "Category already exists: $currentCategoryName")
                                            snackbarHostState.showSnackbar("Category already exists")
                                        }
                                    }
                                } else {
                                    // Update existing category
                                    if (categoryType == CategoryType.EXPENSE) {
                                        if (!categoryManager.hasExpenseCategory(currentCategoryName) || currentCategoryName == categoryName) {
                                            try {
                                                LogUtils.i("EditCategoryScreen", "Updating expense category from $categoryName to $currentCategoryName")
                                                categoryManager.updateExpenseCategoryBoth(categoryName, currentCategoryName)
                                                LogUtils.i("EditCategoryScreen", "Successfully updated expense category to: $currentCategoryName")
                                                // Navigate back after saving
                                                navController.popBackStack()
                                            } catch (e: Exception) {
                                                LogUtils.e("EditCategoryScreen", "Error updating expense category", e)
                                                snackbarHostState.showSnackbar("Error updating category: ${e.message ?: "Unknown error"}")
                                            }
                                        } else {
                                            LogUtils.w("EditCategoryScreen", "Category already exists: $currentCategoryName")
                                            snackbarHostState.showSnackbar("Category already exists")
                                        }
                                    } else {
                                        if (!categoryManager.hasIncomeCategory(currentCategoryName) || currentCategoryName == categoryName) {
                                            try {
                                                LogUtils.i("EditCategoryScreen", "Updating income category from $categoryName to $currentCategoryName")
                                                categoryManager.updateIncomeCategoryBoth(categoryName, currentCategoryName)
                                                LogUtils.i("EditCategoryScreen", "Successfully updated income category to: $currentCategoryName")
                                                // Navigate back after saving
                                                navController.popBackStack()
                                            } catch (e: Exception) {
                                                LogUtils.e("EditCategoryScreen", "Error updating income category", e)
                                                snackbarHostState.showSnackbar("Error updating category: ${e.message ?: "Unknown error"}")
                                            }
                                        } else {
                                            LogUtils.w("EditCategoryScreen", "Category already exists: $currentCategoryName")
                                            snackbarHostState.showSnackbar("Category already exists")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                LogUtils.e("EditCategoryScreen", "Error saving category", e)
                                snackbarHostState.showSnackbar("Error: ${e.message}")
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Сачувај",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
            }
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
                    CategoryIcons.expenseIcons
                } else {
                    CategoryIcons.incomeIcons
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
                columns = GridCells.Adaptive(minSize = 48.dp),
                contentPadding = PaddingValues(4.dp),
                modifier = Modifier.height(180.dp)
            ) {
                items(categoryManager.getAllComposeColors()) { color ->
                    ColorSelectionItem(
                        color = color,
                        isSelected = selectedColor == color,
                        onSelect = { selectedColor = color }
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Delete button - only show for existing categories
            if (!isNewCategory) {
                Button(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(text = "Delete")
                }
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

// Get colors from CategoryManager instead of hardcoded list
@Composable
private fun getCategoryColors(): List<Color> {
    val context = LocalContext.current
    val categoryManager = remember { CategoryManager.getInstance(context) }
    return categoryManager.getAllComposeColors()
} 