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
import com.petar.smrdici.data.model.Income
import com.petar.smrdici.data.model.IncomeCategory
import com.petar.smrdici.data.repository.TransactionRepository
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.components.SuccessOverlay
import com.petar.smrdici.ui.screens.settings.AccountViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

@Suppress("UNUSED_PARAMETER", "KotlinRedundantDiagnosticSuppress", "NAME_SHADOWING")
@Composable
fun AddIncomeScreen(
    onNavigateBack: () -> Unit,
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(),
    accountViewModel: AccountViewModel = viewModel()
) {
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<IncomeCategory?>(null) }
    var customCategoryName by remember { mutableStateOf("") }
    var selectedDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedAccountId by remember { mutableStateOf("") }

    var amountError by remember { mutableStateOf("") }
    var categoryError by remember { mutableStateOf("") }
    var accountError by remember { mutableStateOf("") }

    var accountMenuExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    var isLoading by remember { mutableStateOf(false) }
    var showSuccessAnimation by remember { mutableStateOf(false) }

    val authState by authViewModel.authState.collectAsState()
    @Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
    val user = if (authState is AuthState.Authenticated) {
        (authState as AuthState.Authenticated).user
    } else null

    val accounts by accountViewModel.accounts.collectAsState()
    val transactionRepository = remember { TransactionRepository.getInstance() }

    val context = LocalContext.current
    val categoryManager = remember { CategoryManager.getInstance(context) }
    var isLoadingCategories by remember { mutableStateOf(true) }
    val incomeCategories = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        isLoadingCategories = true
        val categories = categoryManager.getIncomeCategoriesWithFallback()
        incomeCategories.clear()
        incomeCategories.addAll(categories)
        isLoadingCategories = false
    }

    if (user == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Морате бити пријављени да бисте додали приход")
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
            } catch (e: NumberFormatException) {
                amountError = "Невалидан формат износа"
                isValid = false
            }
        }

        if (selectedCategory == null) {
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

    fun saveIncome() {
        if (!validateForm()) return
        isLoading = true
    }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            try {
                val amountValue = amount.toDouble()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                dateFormat.timeZone = TimeZone.getTimeZone("UTC")
                val dateStr = dateFormat.format(Date(selectedDate))

                val income = Income(
                    id = UUID.randomUUID().toString(),
                    userId = user.uid,
                    amount = amountValue,
                    description = description,
                    category = if (customCategoryName.isNotEmpty() && selectedCategory == IncomeCategory.OTHER)
                        customCategoryName else selectedCategory!!.name,
                    date = dateStr,
                    accountId = selectedAccountId
                )

                transactionRepository.addIncome(income)
                showSuccessAnimation = true
                delay(1200.milliseconds)
                onNavigateBack()
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Грешка при чувању прихода: ${e.message}")
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
            initialSelectedDateMillis = selectedDate
        )

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
                    Text("Откажи")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (isLoadingCategories) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    Box(modifier = Modifier.statusBarsPadding()) {
                        AppHeader(
                            title = "Додај приход",
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
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { categoryMenuExpanded = true },
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
                                    text = if (customCategoryName.isNotEmpty() && selectedCategory == IncomeCategory.OTHER)
                                        customCategoryName
                                    else selectedCategory?.getDisplayName() ?: "Изаберите категорију",
                                    color = if (selectedCategory == null) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Изаберите категорију"
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = categoryMenuExpanded,
                            onDismissRequest = { categoryMenuExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            IncomeCategory.entries.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.getDisplayName()) },
                                    onClick = {
                                        selectedCategory = category
                                        categoryMenuExpanded = false
                                    }
                                )
                            }

                            incomeCategories.filter { categoryName ->
                                try {
                                    IncomeCategory.valueOf(categoryName)
                                    false
                                } catch (_: IllegalArgumentException) {
                                    true
                                }
                            }.forEach { customCategory ->
                                DropdownMenuItem(
                                    text = { Text(customCategory) },
                                    onClick = {
                                        selectedCategory = IncomeCategory.OTHER
                                        customCategoryName = customCategory
                                        categoryMenuExpanded = false
                                    }
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
                                text = dateFormatter.format(Date(selectedDate)),
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
                        onClick = { saveIncome() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    ) {
                        Text("Сачувај")
                    }
                }
            }

            SuccessOverlay(
                visible = showSuccessAnimation,
                message = "Приход успешно додат"
            )
        }
    }
}