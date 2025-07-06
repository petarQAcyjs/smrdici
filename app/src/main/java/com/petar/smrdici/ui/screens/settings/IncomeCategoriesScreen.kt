package com.petar.smrdici.ui.screens.settings

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Copyright
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.House
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Work
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.data.model.CategoryManager
import com.petar.smrdici.data.model.CategoryIcons
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.navigation.Screen
import kotlinx.coroutines.launch
import kotlin.math.abs

// Funkcija za generisanje boje na osnovu imena kategorije
private fun getIncomeCategoryColor(categoryName: String): Color {
    val context = com.petar.smrdici.utils.AppGlobals.getAppContext()
    
    // If context is available, check for saved color
    if (context != null) {
        val categoryManager = CategoryManager.getInstance(context)
        
        // First check if we have a saved color
        val savedColor = categoryManager.getCategoryColor(categoryName, false)
        Log.d("IncomeCategories", "Getting color for $categoryName: savedColor=$savedColor")
        if (savedColor != null) {
            try {
                // Convert the long value to a Color
                val alpha = (savedColor shr 24 and 0xFF).toInt()
                val red = (savedColor shr 16 and 0xFF).toInt()
                val green = (savedColor shr 8 and 0xFF).toInt()
                val blue = (savedColor and 0xFF).toInt()
                
                val color = Color(red, green, blue, alpha)
                Log.d("IncomeCategories", "Using saved color for $categoryName: ARGB($alpha,$red,$green,$blue)")
                return color
            } catch (e: Exception) {
                Log.e("IncomeCategories", "Error converting color value: $savedColor", e)
                // Fall through to default color
            }
        }
    }
    
    // If no saved color, use the hash-based approach
    val colors = listOf(
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
        Color(0xFFE57373), // Red
        Color(0xFF64B5F6), // Blue
        Color(0xFFFFD54F), // Yellow
        Color(0xFF81C784), // Green
        Color(0xFFBA68C8), // Purple
    )
    
    // Koristimo hash kod imena kategorije za odabir boje
    val index = abs(categoryName.hashCode()) % colors.size
    val defaultColor = colors[index]
    Log.d("IncomeCategories", "Using default color for $categoryName: ${defaultColor.value}")
    return defaultColor
}

// Funkcija za dobijanje odgovarajuće ikone za kategoriju
private fun getIncomeCategoryIcon(categoryName: String): ImageVector {
    val context = com.petar.smrdici.utils.AppGlobals.getAppContext()
    
    // If context is available, check for saved icon
    if (context != null) {
        val categoryManager = CategoryManager.getInstance(context)
        
        // First check if we have a saved icon
        val savedIconName = categoryManager.getCategoryIcon(categoryName, false)
        Log.d("IncomeCategories", "Getting icon for $categoryName: savedIconName=$savedIconName")
        
        if (savedIconName != null) {
            // Try to find the icon by name
            try {
                Log.d("IncomeCategories", "Attempting to find icon with name: $savedIconName")
                
                // Use the shared icon finder
                val foundIcon = CategoryIcons.findIconByName(savedIconName)
                if (foundIcon != null) {
                    Log.d("IncomeCategories", "Found icon for name: $savedIconName")
                    return foundIcon
                }
                
                Log.e("IncomeCategories", "Could not find icon with name: $savedIconName")
            } catch (e: Exception) {
                Log.e("IncomeCategories", "Error finding icon $savedIconName: ${e.message}")
                // Fall through to default icon selection
            }
        }
    }
    
    // If no saved icon or couldn't find it, use the default mapping
    Log.d("IncomeCategories", "Using default icon mapping for $categoryName")
    
    // If no saved icon or couldn't find it, use the default mapping
    return when (categoryName.lowercase()) {
        "paycheck", "плата", "plata" -> Icons.Default.Payments
        "gift", "поклон", "poklon" -> Icons.Default.CardGiftcard
        "interest", "камата", "kamata" -> Icons.Default.AccountBalance
        "refund", "повраћај", "povraćaj" -> Icons.Default.MoneyOff
        "salary", "зарада", "zarada" -> Icons.Default.Work
        "savings", "уштеђевина", "ušteđevina" -> Icons.Default.Savings
        "cash", "готовина", "gotovina" -> Icons.Default.LocalAtm
        "bonus", "бонус" -> Icons.Default.EmojiEvents
        "dividend", "дивиденда", "dividenda" -> Icons.Default.PieChart
        "rental", "рентал" -> Icons.Default.House
        "investment", "инвестиција", "investicija" -> Icons.AutoMirrored.Filled.TrendingUp
        "freelance", "фриленс", "frilens" -> Icons.Default.Computer
        "side hustle", "додатни посао", "dodatni posao" -> Icons.Default.BusinessCenter
        "commission", "провизија", "provizija" -> Icons.Default.Handshake
        "royalty", "ројалти", "rojalty" -> Icons.Default.Copyright
        "pension", "пензија", "penzija" -> Icons.Default.EventSeat
        "alimony", "алиментација", "alimentacija" -> Icons.Default.FamilyRestroom
        "child support", "издржавање детета", "izdržavanje deteta" -> Icons.Default.ChildCare
        "tax return", "повраћај пореза", "povraćaj poreza" -> Icons.Default.Receipt
        "inheritance", "наследство", "nasledstvo" -> Icons.Default.AutoAwesome
        "lottery", "лутрија", "lutrija" -> Icons.Default.Casino
        "scholarship", "стипендија", "stipendija" -> Icons.Default.School
        "grant", "грант" -> Icons.Default.Gavel
        "social security", "социјална помоћ" -> Icons.Default.HealthAndSafety
        "unemployment", "накнада за незапосленост" -> Icons.Default.PersonOff
        else -> Icons.Default.AttachMoney
    }
}

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
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("") }
    
    val authState by authViewModel.authState.collectAsState()
    val user = (authState as? AuthState.Authenticated)?.user
    
    Scaffold(
        topBar = {
            AppHeader(
                title = "Категорије прихода",
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
                            .clickable {
                                navController.navigate("expense_categories") {
                                    popUpTo("income_categories") { inclusive = true }
                                }
                            }
                    ) {
                        Text(
                            text = "EXPENSES",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = "INCOME",
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
                    items(incomeCategories) { category ->
                        IncomeCategoryItem(
                            name = category,
                            color = getIncomeCategoryColor(category),
                            onEdit = {
                                navController.navigate(Screen.EditCategory.createRoute(category, "INCOME"))
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
                                    navController.navigate(Screen.EditCategory.createRoute("", "INCOME"))
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

@Composable
fun IncomeCategoryItem(
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
                        imageVector = getIncomeCategoryIcon(name),
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