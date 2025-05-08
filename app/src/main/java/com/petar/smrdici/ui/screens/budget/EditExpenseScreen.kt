package com.petar.smrdici.ui.screens.budget

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.petar.smrdici.data.model.Expense
import com.petar.smrdici.ui.components.CategoryDropdown
import com.petar.smrdici.ui.components.DatePickerDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseScreen(
    expense: Expense,
    onNavigateBack: () -> Unit,
    budgetViewModel: BudgetViewModel
) {
    var amount by remember { mutableStateOf(expense.amount.toString()) }
    var description by remember { mutableStateOf(expense.description) }
    var selectedCategory by remember { mutableStateOf(expense.category) }
    var selectedDate by remember { mutableStateOf(expense.getDateObject() ?: Date()) }
    var showDatePicker by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Expense") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
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
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth()
            )

            CategoryDropdown(
                selectedCategory = selectedCategory,
                onCategorySelected = { selectedCategory = it },
                categories = budgetViewModel.expenseCategories.collectAsState().value
            )

            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Date: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(selectedDate)}")
            }

            Button(
                onClick = {
                    scope.launch {
                        try {
                            val amountValue = amount.toDoubleOrNull()
                            if (amountValue == null) {
                                snackbarHostState.showSnackbar("Please enter a valid amount")
                                return@launch
                            }
                            
                            val updatedExpense = expense.copy(
                                amount = amountValue,
                                description = description,
                                category = selectedCategory,
                                date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(selectedDate)
                            )
                            
                            budgetViewModel.updateExpense(updatedExpense)
                            snackbarHostState.showSnackbar("Expense updated successfully")
                            onNavigateBack()
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("Failed to update expense: ${e.message}")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Changes")
            }
        }

        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                onDateSelected = { selectedDate = it },
                initialDate = selectedDate
            )
        }
    }
} 