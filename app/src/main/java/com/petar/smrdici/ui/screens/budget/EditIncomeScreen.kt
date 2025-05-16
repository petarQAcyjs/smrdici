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
import com.petar.smrdici.data.model.Income
import com.petar.smrdici.ui.components.CategoryDropdown
import com.petar.smrdici.ui.components.DatePickerDialog
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.auth.AuthState
import androidx.navigation.NavController
import androidx.compose.runtime.collectAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditIncomeScreen(
    navController: NavController,
    income: Income,
    onNavigateBack: () -> Unit,
    budgetViewModel: BudgetViewModel,
    authViewModel: AuthViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    val user = if (authState is AuthState.Authenticated) (authState as AuthState.Authenticated).user else null
    var amount by remember { mutableStateOf(income.amount.toString()) }
    var description by remember { mutableStateOf(income.description) }
    var selectedCategory by remember { mutableStateOf(income.category) }
    var selectedDate by remember { mutableStateOf(income.getDateObject() ?: Date()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            AppHeader(
                title = "Edit Income",
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
                categories = budgetViewModel.incomeCategories.collectAsState().value
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
                            
                            val updatedIncome = income.copy(
                                amount = amountValue,
                                description = description,
                                category = selectedCategory,
                                date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(selectedDate)
                            )
                            
                            budgetViewModel.updateIncome(updatedIncome)
                            snackbarHostState.showSnackbar("Income updated successfully")
                            onNavigateBack()
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("Failed to update income: ${e.message}")
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