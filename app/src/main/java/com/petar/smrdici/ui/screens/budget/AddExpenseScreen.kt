package com.petar.smrdici.ui.screens.budget

import android.util.Log
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.google.firebase.Timestamp
import com.petar.smrdici.data.model.Expense
import com.petar.smrdici.data.model.ExpenseCategory
import com.petar.smrdici.data.repository.ExpenseRepository
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.screens.settings.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(),
    accountViewModel: AccountViewModel = viewModel(factory = AccountViewModel.Factory()),
    budgetViewModel: BudgetViewModel = viewModel(factory = BudgetViewModel.Factory(LocalContext.current))
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
    var selectedDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
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
    val scope = rememberCoroutineScope()
    
    // Стање за учитавање
    var isLoading by remember { mutableStateOf(false) }
    
    // Стање за аутентификацију
    val authState by authViewModel.authState.collectAsState()
    val user = if (authState is AuthState.Authenticated) {
        (authState as AuthState.Authenticated).user
    } else null
    
    // Учитавање рачуна
    val accounts by accountViewModel.accounts.collectAsState()
    
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
            val defaultAccount = accounts.find { it.isDefault }
            if (defaultAccount != null) {
                selectedAccountId = defaultAccount.id
            } else {
                selectedAccountId = accounts.first().id
            }
        }
    }
    
    // Функција за валидацију форме
    fun validateForm(): Boolean {
        var isValid = true
        
        // Валидација износа
        if (amount.isEmpty()) {
            amountError = "Износ је обавезан"
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
            } catch (e: Exception) {
                amountError = "Неисправан износ"
                isValid = false
            }
        }
        
        // Валидација категорије
        if (selectedCategory == null) {
            categoryError = "Категорија је обавезна"
            isValid = false
        } else {
            categoryError = ""
        }
        
        // Валидација рачуна
        if (selectedAccountId.isEmpty()) {
            accountError = "Рачун је обавезан"
            isValid = false
        } else {
            accountError = ""
        }
        
        return isValid
    }
    
    // Функција за чување расхода
    fun saveExpense() {
        if (!validateForm()) {
            return
        }
        
        isLoading = true
        
        // Креирамо нови објекат расхода
        val expense = Expense(
            amount = amount.toDouble(),
            description = description,
            category = selectedCategory?.name ?: ExpenseCategory.OTHER.name,
            date = Timestamp(Date(selectedDate)),
            accountId = selectedAccountId
        )
        
        Log.d("AddExpenseScreen", "Чувам расход: $expense")
        
        // Користимо viewModelScope уместо локалног scope-а из композиције
        // Ово спречава отказивање корутине када се композиција промени
        budgetViewModel.viewModelScope.launch {
            try {
                // Дефинишемо променљиву резултата пре NonCancellable контекста
                val result = withContext(NonCancellable) {
                    // Користимо NonCancellable контекст да спречимо отказивање операције чувања
                    // Ово је важно за операције које морају да се заврше и не смеју бити прекинуте
                    // чак и ако се корутина отказује (нпр. због навигације)
                    
                    // Користимо budgetViewModel уместо директног приступа репозиторијуму
                    val saveResult = budgetViewModel.addExpense(expense)
                    
                    if (saveResult.isSuccess) {
                        Log.d("AddExpenseScreen", "Расход је успешно сачуван")
                    } else {
                        Log.e("AddExpenseScreen", "Грешка при чувању расхода", saveResult.exceptionOrNull())
                    }
                    
                    // Враћамо резултат из NonCancellable блока
                    saveResult
                }
                
                // UI ажурирања извршавамо на главној нити, ван NonCancellable контекста
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        snackbarHostState.showSnackbar("Расход је успешно сачуван")
                        // Враћамо се на претходни екран
                        navController.popBackStack()
                    } else {
                        snackbarHostState.showSnackbar("Грешка при чувању расхода: ${result.exceptionOrNull()?.message}")
                    }
                    
                    isLoading = false
                }
            } catch (e: Exception) {
                // Обрађујемо изузетке, али игноришемо JobCancellationException који се нормално дешава при навигацији
                if (e is kotlinx.coroutines.CancellationException) {
                    // Само логујемо, не приказујемо грешку кориснику јер је успешно сачувано
                    Log.d("AddExpenseScreen", "Корутина је отказана након успешног чувања")
                } else {
                    // За остале грешке показујемо поруку
                    Log.e("AddExpenseScreen", "Грешка при чувању расхода", e)
                    
                    withContext(Dispatchers.Main + NonCancellable) {
                        snackbarHostState.showSnackbar("Грешка при чувању расхода: ${e.message}")
                        isLoading = false
                    }
                }
            }
        }
    }
    
    // Форматер за датум
    val dateFormatter = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    
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
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isLoading) { categoryMenuExpanded = true },
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = if (categoryError.isNotEmpty()) {
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
                            text = selectedCategory?.getDisplayName() ?: "Изабери категорију",
                            color = if (selectedCategory == null) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Изабери категорију"
                        )
                    }
                }
                
                if (categoryError.isNotEmpty()) {
                    Text(
                        text = categoryError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
                
                DropdownMenu(
                    expanded = categoryMenuExpanded,
                    onDismissRequest = { categoryMenuExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    ExpenseCategory.entries.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.getDisplayName()) },
                            onClick = {
                                selectedCategory = category
                                categoryMenuExpanded = false
                            }
                        )
                    }
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
                        text = dateFormatter.format(Date(selectedDate)),
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
                            text = accounts.find { it.id == selectedAccountId }?.name ?: "Изабери рачун",
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
                    accounts.forEach { account ->
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
                Text("Сачувај расход")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    
    // Дијалог за избор датума
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate)
        
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            selectedDate = it
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
                    Text("Отказжи")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
} 