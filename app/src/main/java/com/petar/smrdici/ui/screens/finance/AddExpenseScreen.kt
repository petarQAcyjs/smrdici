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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.data.model.CategoryManager
import com.petar.smrdici.data.model.Expense
import com.petar.smrdici.data.model.ExpenseCategory
import com.petar.smrdici.data.repository.TransactionRepository
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.components.CategoryDropdown
import com.petar.smrdici.ui.components.SuccessOverlay
import com.petar.smrdici.ui.screens.settings.AccountViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun AddExpenseScreen(
    onNavigateBack: () -> Unit,
    navController: NavController,
    selectedDate: String? = null,
    authViewModel: AuthViewModel = viewModel(),
    accountViewModel: AccountViewModel = viewModel()
) {
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ExpenseCategory?>(null) }
    var customCategoryName by remember { mutableStateOf("") }

    var selectedDateMillis by remember {
        mutableLongStateOf(
            if (selectedDate != null) {
                try {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    dateFormat.timeZone = TimeZone.getTimeZone("UTC")
                    val parsedDate = dateFormat.parse(selectedDate) ?: Date()
                    val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                    calendar.time = parsedDate
                    calendar.set(Calendar.HOUR_OF_DAY, 12)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    calendar.timeInMillis
                } catch (_: Exception) {
                    System.currentTimeMillis()
                }
            } else {
                System.currentTimeMillis()
            }
        )
    }

    var selectedAccountId by remember { mutableStateOf("") }

    var amountError by remember { mutableStateOf("") }
    var categoryError by remember { mutableStateOf("") }
    var accountError by remember { mutableStateOf("") }

    var accountMenuExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    var isLoading by remember { mutableStateOf(false) }
    var showSuccessAnimation by remember { mutableStateOf(false) }

    val authState by authViewModel.authState.collectAsState()
    val user = if (authState is AuthState.Authenticated) {
        (authState as AuthState.Authenticated).user
    } else null
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle

    LaunchedEffect(savedStateHandle) {
        val scannedMerchant = savedStateHandle?.get<String>("scanned_merchant")
        val scannedAmount = savedStateHandle?.get<String>("scanned_amount")
        val scannedCategory = savedStateHandle?.get<String>("scanned_category")

        if (!scannedAmount.isNullOrEmpty()) {
            amount = scannedAmount // Već zaokružen iznos na sledeću desetinu!
            savedStateHandle.remove<String>("scanned_amount")
        }

        if (!scannedMerchant.isNullOrEmpty()) {
            description = scannedMerchant // U opis upisujemo naziv trgovca (npr. MAXI)
            savedStateHandle.remove<String>("scanned_merchant")
        }

        if (!scannedCategory.isNullOrEmpty()) {
            try {
                selectedCategory = ExpenseCategory.valueOf(scannedCategory)
            } catch (_: Exception) {
                customCategoryName = scannedCategory
            }
            savedStateHandle.remove<String>("scanned_category")
        }
    }
    val accounts by accountViewModel.accounts.collectAsState()
    val transactionRepository = remember { TransactionRepository.getInstance() }

    val context = LocalContext.current
    val categoryManager = remember { CategoryManager.getInstance(context) }
    var isLoadingCategories by remember { mutableStateOf(true) }
    val expenseCategories = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        isLoadingCategories = true
        val categories = categoryManager.getExpenseCategoriesWithFallback()
        expenseCategories.clear()
        expenseCategories.addAll(categories)
        isLoadingCategories = false
    }

    if (user == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Морате бити пријављени да бисте додали расход")
        }
        return
    }

    LaunchedEffect(Unit) {
        accountViewModel.refreshAccounts()
    }

    LaunchedEffect(accounts) {
        if (accounts.isNotEmpty() && selectedAccountId.isEmpty()) {
            val defaultAccount = accounts.find { account -> account.isDefault }
            selectedAccountId = defaultAccount?.id ?: (if (accounts.isNotEmpty()) accounts.first().id else "")
        }
    }

    fun validateForm(): Boolean {
        var isValid = true

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
            } catch (_: NumberFormatException) {
                amountError = "Невалидан формат износа"
                isValid = false
            }
        }

        if (selectedCategory == null && customCategoryName.isEmpty()) {
            categoryError = "Изаберите категорију"
            isValid = false
        } else {
            categoryError = ""
        }

        if (selectedAccountId.isEmpty()) {
            accountError = "Изаберите рачун"
            isValid = false
        } else {
            accountError = ""
        }

        return isValid
    }

    fun saveExpense() {
        if (!validateForm()) return
        isLoading = true
    }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            try {
                val amountValue = amount.toDouble()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                dateFormat.timeZone = TimeZone.getTimeZone("UTC")

                val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                calendar.timeInMillis = selectedDateMillis
                val dateStr = dateFormat.format(calendar.time)

                val expense = Expense(
                    id = UUID.randomUUID().toString(),
                    userId = user.uid,
                    amount = amountValue,
                    description = description,
                    category = selectedCategory?.name ?: customCategoryName,
                    date = dateStr,
                    accountId = selectedAccountId
                )

                transactionRepository.addExpense(expense)
                showSuccessAnimation = true
                delay(1200.milliseconds)
                onNavigateBack()
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Грешка при чувању расхода: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    val dateFormatter = remember {
        SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    }

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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Box(modifier = Modifier.statusBarsPadding()) {
                    AppHeader(
                        title = "Додај расход",
                        user = user,
                        navController = navController,
                        showBackButton = true,
                        showProfileIcon = false
                    )
                }
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

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Опис") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )

                Column {
                    if (isLoadingCategories) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        CategoryDropdown(
                            selectedCategory = selectedCategory?.name ?: customCategoryName,
                            onCategorySelected = { name ->
                                try {
                                    selectedCategory = ExpenseCategory.valueOf(name)
                                    customCategoryName = ""
                                } catch (_: Exception) {
                                    selectedCategory = null
                                    customCategoryName = name
                                }
                            },
                            categories = expenseCategories,
                            getDisplayName = { name ->
                                try {
                                    ExpenseCategory.valueOf(name).getDisplayName()
                                } catch (_: Exception) {
                                    name
                                }
                            }
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
                }

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
                                text = accounts.find { account -> account.id == selectedAccountId }?.name ?: "Изабери рачун",
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

                Button(
                    onClick = { saveExpense() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Text("Сачувај")
                }
            }
        }

        SuccessOverlay(
            visible = showSuccessAnimation,
            message = "Расход успешно додат"
        )
    }
}