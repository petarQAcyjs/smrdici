package com.petar.smrdici.ui.screens.budget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.data.model.Transaction
import com.petar.smrdici.data.model.TransactionCategory
import com.petar.smrdici.data.model.TransactionType
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Locale

@Composable
fun BudgetScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    val user = if (authState is com.petar.smrdici.ui.auth.AuthState.Authenticated) {
        (authState as com.petar.smrdici.ui.auth.AuthState.Authenticated).user
    } else null
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        AppHeader(
            title = "Буџет",
            user = user,
            navController = navController,
            showBackButton = true
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Буџет екран у изради",
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetSummary(
    totalIncome: Double,
    totalExpense: Double,
    balance: Double
) {
    val currencyFormat = NumberFormat.getCurrencyInstance().apply {
        currency = Currency.getInstance("RSD")
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Укупно стање",
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = currencyFormat.format(balance),
                style = MaterialTheme.typography.headlineMedium,
                color = if (balance >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Приходи",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = currencyFormat.format(totalIncome),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF4CAF50)
                    )
                }
                
                Column {
                    Text(
                        text = "Расходи",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = currencyFormat.format(totalExpense),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFF44336)
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionItem(
    transaction: Transaction,
    onDelete: () -> Unit
) {
    val currencyFormat = NumberFormat.getCurrencyInstance().apply {
        currency = Currency.getInstance("RSD")
    }
    
    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    val date = transaction.date.toDate()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Опис и категорија
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = transaction.description,
                    style = MaterialTheme.typography.titleMedium
                )
                
                Text(
                    text = "${transaction.category} • ${dateFormat.format(date)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            // Износ
            Text(
                text = currencyFormat.format(transaction.amount),
                style = MaterialTheme.typography.titleMedium,
                color = if (transaction.type == TransactionType.INCOME) 
                    Color(0xFF4CAF50) else Color(0xFFF44336)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Дугме за брисање
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Обриши",
                    tint = Color.Gray
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    formState: TransactionFormState,
    onFormChanged: (TransactionFormState) -> Unit,
    onAddTransaction: () -> Unit,
    onDismiss: () -> Unit
) {
    val categories = if (formState.type == TransactionType.INCOME) {
        listOf(
            TransactionCategory.SALARY,
            TransactionCategory.BONUS,
            TransactionCategory.GIFT,
            TransactionCategory.OTHER_INCOME
        )
    } else {
        listOf(
            TransactionCategory.FOOD,
            TransactionCategory.UTILITIES,
            TransactionCategory.RENT,
            TransactionCategory.TRANSPORTATION,
            TransactionCategory.ENTERTAINMENT,
            TransactionCategory.HEALTH,
            TransactionCategory.EDUCATION,
            TransactionCategory.SHOPPING,
            TransactionCategory.OTHER_EXPENSE
        )
    }
    
    var showCategoryDropdown by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Додај нову трансакцију") },
        text = {
            Column {
                // Тип трансакције
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FilterChip(
                        selected = formState.type == TransactionType.EXPENSE,
                        onClick = {
                            onFormChanged(formState.copy(type = TransactionType.EXPENSE, category = ""))
                        },
                        label = { Text("Расход") }
                    )
                    
                    FilterChip(
                        selected = formState.type == TransactionType.INCOME,
                        onClick = {
                            onFormChanged(formState.copy(type = TransactionType.INCOME, category = ""))
                        },
                        label = { Text("Приход") }
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Износ
                OutlinedTextField(
                    value = formState.amount,
                    onValueChange = { onFormChanged(formState.copy(amount = it)) },
                    label = { Text("Износ") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Опис
                OutlinedTextField(
                    value = formState.description,
                    onValueChange = { onFormChanged(formState.copy(description = it)) },
                    label = { Text("Опис") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Категорија
                ExposedDropdownMenuBox(
                    expanded = showCategoryDropdown,
                    onExpandedChange = { showCategoryDropdown = it }
                ) {
                    OutlinedTextField(
                        value = categories.find { it.name == formState.category }?.displayName ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Категорија") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCategoryDropdown)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = showCategoryDropdown,
                        onDismissRequest = { showCategoryDropdown = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.displayName) },
                                onClick = {
                                    onFormChanged(formState.copy(category = category.name))
                                    showCategoryDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAddTransaction,
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