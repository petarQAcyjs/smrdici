package com.petar.smrdici.ui.screens.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.data.model.Expense
import com.petar.smrdici.data.model.ExpenseCategory
import com.petar.smrdici.data.repository.TransactionRepository
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.components.CategoryDropdown
import com.petar.smrdici.ui.components.DatePickerDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseScreen(
    navController: NavController,
    expenseId: String,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    val user = if (authState is AuthState.Authenticated) (authState as AuthState.Authenticated).user else null
    
    // State variables
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ExpenseCategory?>(null) }
    var selectedDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedAccountId by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    
    // Initialize transaction repository
    val transactionRepository = remember { TransactionRepository.getInstance() }
    
    // Current expense state
    var expense by remember { mutableStateOf<Expense?>(null) }
    
    // Load expense data using expenseId
    LaunchedEffect(expenseId) {
        expense = transactionRepository.getExpenseById(expenseId).first()
        
        expense?.let {
            amount = it.amount.toString()
            description = it.description
            selectedCategory = ExpenseCategory.valueOf(it.category)
            selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it.date)?.time ?: System.currentTimeMillis()
            selectedAccountId = it.accountId
        }
    }
    
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            AppHeader(
                title = "Edit Expense",
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
                categories = ExpenseCategory.entries.toList(),
                getDisplayName = { it.getDisplayName() }
            )

            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Date: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(selectedDate))}")
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
                            
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            val updatedExpense = expense?.copy(
                                amount = amountValue,
                                description = description,
                                category = selectedCategory?.name ?: "",
                                date = dateFormat.format(Date(selectedDate)),
                                accountId = selectedAccountId,
                                userId = user?.uid ?: ""
                            )
                            
                            if (updatedExpense != null) {
                                transactionRepository.updateExpense(updatedExpense)
                                snackbarHostState.showSnackbar("Expense updated successfully")
                                onNavigateBack()
                            }
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
                onDateSelected = { selectedDate = it.time },
                initialDate = Date(selectedDate)
            )
        }
    }
} 