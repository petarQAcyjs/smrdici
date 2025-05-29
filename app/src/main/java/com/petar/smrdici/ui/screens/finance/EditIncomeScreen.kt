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
import com.petar.smrdici.data.model.Income
import com.petar.smrdici.data.model.IncomeCategory
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
fun EditIncomeScreen(
    navController: NavController,
    incomeId: String,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    val user = if (authState is AuthState.Authenticated) (authState as AuthState.Authenticated).user else null
    
    // State variables
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<IncomeCategory?>(null) }
    var selectedDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedAccountId by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    
    // Initialize transaction repository
    val transactionRepository = remember { TransactionRepository.getInstance() }
    
    // Current income state
    var income by remember { mutableStateOf<Income?>(null) }
    
    // Load income data using incomeId
    LaunchedEffect(incomeId) {
        income = transactionRepository.getIncomeById(incomeId).first()
        
        income?.let {
            amount = it.amount.toString()
            description = it.description
            selectedCategory = IncomeCategory.valueOf(it.category)
            selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it.date)?.time ?: System.currentTimeMillis()
            selectedAccountId = it.accountId
        }
    }
    
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
                categories = IncomeCategory.entries.toList(),
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
                            val updatedIncome = income?.copy(
                                amount = amountValue,
                                description = description,
                                category = selectedCategory?.name ?: "",
                                date = dateFormat.format(Date(selectedDate)),
                                accountId = selectedAccountId,
                                userId = user?.uid ?: ""
                            )
                            
                            if (updatedIncome != null) {
                                transactionRepository.updateIncome(updatedIncome)
                                snackbarHostState.showSnackbar("Income updated successfully")
                                onNavigateBack()
                            }
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
                onDateSelected = { selectedDate = it.time },
                initialDate = Date(selectedDate)
            )
        }
    }
} 