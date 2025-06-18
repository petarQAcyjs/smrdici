package com.petar.smrdici.ui.screens.settings

import android.util.Log
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
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
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
import com.petar.smrdici.data.model.CategoryIcons
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
            
            Log.d("EditCategoryScreen", "Initialized with icon: ${selectedIcon?.toString() ?: "null"} for category: $categoryName")
            
            selectedColor = when (categoryType) {
                CategoryType.EXPENSE -> getExpenseCategoryColor(categoryName)
                CategoryType.INCOME -> getIncomeCategoryColor(categoryName)
            }
            
            Log.d("EditCategoryScreen", "Initialized with color: ${selectedColor?.toString() ?: "null"} for category: $categoryName")
        } else {
            // Default selections for new category
            selectedIcon = if (categoryType == CategoryType.EXPENSE) 
                Icons.Default.ShoppingCart else Icons.Default.AttachMoney
            selectedColor = predefinedColors[0]
            
            Log.d("EditCategoryScreen", "New category initialized with default icon: ${selectedIcon?.toString() ?: "null"} and color: ${selectedColor?.toString() ?: "null"}")
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
                                
                                Log.d("EditCategoryScreen", "Saving color: ARGB($alpha,$red,$green,$blue) = $colorValue for category: $currentCategoryName, isExpense: $isExpense")
                                
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
                            } ?: Log.e("EditCategoryScreen", "No color selected!")
                            
                            // Save the selected icon
                            selectedIcon?.let { icon ->
                                // Extract icon name from the icon
                                val iconFullName = icon.toString()
                                Log.d("EditCategoryScreen", "Icon full name: $iconFullName")
                                
                                // Extract the simple class name of the icon
                                val iconName = try {
                                    // Use reflection to get the actual field name
                                    var foundName: String? = null
                                    
                                    // Try Icons.Default
                                    if (foundName == null) {
                                        val fields = Icons.Default::class.java.declaredFields
                                        for (field in fields) {
                                            field.isAccessible = true
                                            if (field.get(Icons.Default) == icon) {
                                                foundName = field.name
                                                Log.d("EditCategoryScreen", "Found icon in Icons.Default: $foundName")
                                                break
                                            }
                                        }
                                    }
                                    
                                    // Try Icons.AutoMirrored.Filled
                                    if (foundName == null) {
                                        val fields = Icons.AutoMirrored.Filled::class.java.declaredFields
                                        for (field in fields) {
                                            field.isAccessible = true
                                            if (field.get(Icons.AutoMirrored.Filled) == icon) {
                                                foundName = field.name
                                                Log.d("EditCategoryScreen", "Found icon in Icons.AutoMirrored.Filled: $foundName")
                                                break
                                            }
                                        }
                                    }
                                    
                                    // Try Icons.Filled
                                    if (foundName == null) {
                                        val fields = Icons.Filled::class.java.declaredFields
                                        for (field in fields) {
                                            field.isAccessible = true
                                            if (field.get(Icons.Filled) == icon) {
                                                foundName = field.name
                                                Log.d("EditCategoryScreen", "Found icon in Icons.Filled: $foundName")
                                                break
                                            }
                                        }
                                    }
                                    
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
                                    Log.e("EditCategoryScreen", "Error extracting icon name: ${e.message}")
                                    // Fallback
                                    if (categoryType == CategoryType.EXPENSE) "ShoppingCart" else "AttachMoney"
                                }
                                
                                val isExpense = categoryType == CategoryType.EXPENSE
                                
                                Log.d("EditCategoryScreen", "Saving icon: $iconName for category: $currentCategoryName, isExpense: $isExpense (from $iconFullName)")
                                
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
                            } ?: Log.e("EditCategoryScreen", "No icon selected!")
                            
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