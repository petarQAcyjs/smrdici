package com.petar.smrdici.ui.screens.finance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.data.model.Account
import com.petar.smrdici.data.model.Expense
import com.petar.smrdici.data.model.ExpenseCategory
import com.petar.smrdici.data.repository.TransactionRepository
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.components.CategoryDropdown
import com.petar.smrdici.ui.screens.settings.AccountViewModel
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    onNavigateBack: () -> Unit,
    navController: NavController,
    selectedDate: String? = null,
    authViewModel: AuthViewModel = viewModel(),
    accountViewModel: AccountViewModel = viewModel()
) {
    // Спречавамо непотребно учитавање EventRepository-а
    // DisposableEffect(Unit) {
    //    onDispose { }
    // }
    
    // Нема потребе директно приступати репозиторијуму, користимо budgetViewModel
    // val expenseRepository = ExpenseRepository.getInstance()
    
    // Стање за форму
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ExpenseCategory?>(null) }
    
    // Initialize date with the passed date parameter if available, otherwise use current time
    var selectedDateMillis by remember { 
        mutableLongStateOf(
            if (selectedDate != null) {
                try {
                    LocalDate.parse(selectedDate).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }
            } else {
                System.currentTimeMillis()
            }
        ) 
    }
    
    var selectedAccountId by remember { mutableStateOf("") }
    
    // Стање за грешке
    var amountError by remember { mutableStateOf("") }
    var categoryError by remember { mutableStateOf("") }
    var accountError by remember { mutableStateOf("") }
    
    // Стање за падајуће меније
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    
    // Стање за снекбар
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Стање за учитавање
    var isLoading by remember { mutableStateOf(false) }
    
    // Стање за аутентификацију
    val authState by authViewModel.authState.collectAsState()
    val user = if (authState is AuthState.Authenticated) {
        (authState as AuthState.Authenticated).user
    } else null
    
    // Учитавање рачуна
    val accounts by accountViewModel.accounts.collectAsState()
    
    // Initialize transaction repository
    val transactionRepository = remember { TransactionRepository.getInstance() }
    
    // Приказ ако корисник није пријављен
    if (user == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Морате бити пријављени да бисте додали расход")
        }
        return
    }
    
    // Учитавамо рачуне при иницијализацији
    LaunchedEffect(Unit) {
        accountViewModel.refreshAccounts()
    }
    
    // Аутоматски постављамо подразумевани рачун ако постоји
    LaunchedEffect(accounts) {
        if (accounts.isNotEmpty() && selectedAccountId.isEmpty()) {
            val defaultAccount = accounts.find { account: Account -> account.isDefault }
            selectedAccountId = defaultAccount?.id ?: (if (accounts.isNotEmpty()) accounts.first().id else "")
        }
    }
    
    // Функција за валидацију форме
    fun validateForm(): Boolean {
        var isValid = true
        
        // Валидација износа
        if (amount.isEmpty()) {
            amountError = "Унесите износ"
            isValid = false
        } else {
            try {
                val amountValue = amount.toDouble()
                if (amountValue <= 0) {
                    amountError = "Износ мора бити већи од 0"
                    isValid = false
                } else {
                    amountError = ""
                }
            } catch (e: NumberFormatException) {
                amountError = "Невалидан формат износа"
                isValid = false
            }
        }
        
        // Валидација категорије
        if (selectedCategory == null) {
            categoryError = "Изаберите категорију"
            isValid = false
        } else {
            categoryError = ""
        }
        
        // Валидација рачуна
        if (selectedAccountId.isEmpty()) {
            accountError = "Изаберите рачун"
            isValid = false
        } else {
            accountError = ""
        }
        
        return isValid
    }
    
    // Функција за чување расхода
    fun saveExpense() {
        if (!validateForm()) return
        
        isLoading = true
    }
    
    // Handle expense saving in a LaunchedEffect
    LaunchedEffect(isLoading) {
        if (isLoading) {
            try {
                val amountValue = amount.toDouble()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                dateFormat.timeZone = TimeZone.getTimeZone("UTC")
                val dateStr = dateFormat.format(Date(selectedDateMillis))
                
                val expense = Expense(
                    id = UUID.randomUUID().toString(),
                    userId = user?.uid ?: "",
                    amount = amountValue,
                    description = description,
                    category = selectedCategory!!.name,
                    date = dateStr,
                    accountId = selectedAccountId
                )
                
                // Save expense using TransactionRepository
                transactionRepository.addExpense(expense)
                snackbarHostState.showSnackbar("Расход је успешно сачуван")
                onNavigateBack()
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Грешка при чувању расхода: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }
    
    // Формат датума
    val dateFormatter = remember {
        SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    }
    
    // DatePicker дијалог
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateMillis
        )
        
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            selectedDateMillis = it
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false }
                ) {
                    Text("Откажи")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppHeader(
                title = "Додај расход",
                user = user,
                navController = navController,
                showBackButton = true
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Поље за износ
            Column {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Износ") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = amountError.isNotEmpty(),
                    enabled = !isLoading
                )
                
                if (amountError.isNotEmpty()) {
                    Text(
                        text = amountError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
            }
            
            // Поље за опис
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Опис") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )
            
            // Избор категорије
            Column {
                CategoryDropdown(
                    selectedCategory = selectedCategory,
                    onCategorySelected = { category -> selectedCategory = category },
                    categories = ExpenseCategory.entries.toList(),
                    getDisplayName = { category -> category.getDisplayName() }
                )
                
                if (categoryError.isNotEmpty()) {
                    Text(
                        text = categoryError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
            }
            
            // Избор датума
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isLoading) { showDatePicker = true },
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateFormatter.format(Date(selectedDateMillis)),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Изабери датум"
                    )
                }
            }
            
            // Избор рачуна
            Column {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isLoading) { accountMenuExpanded = true },
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = if (accountError.isNotEmpty()) {
                        androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = accounts.find { account: Account -> account.id == selectedAccountId }?.name ?: "Изабери рачун",
                            color = if (selectedAccountId.isEmpty()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Изабери рачун"
                        )
                    }
                }
                
                if (accountError.isNotEmpty()) {
                    Text(
                        text = accountError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
                
                DropdownMenu(
                    expanded = accountMenuExpanded,
                    onDismissRequest = { accountMenuExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    accounts.forEach { account: Account ->
                        DropdownMenuItem(
                            text = { Text(account.name) },
                            onClick = {
                                selectedAccountId = account.id
                                accountMenuExpanded = false
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Дугме за чување
            Button(
                onClick = { saveExpense() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text("Сачувај")
            }
        }
    }
} 