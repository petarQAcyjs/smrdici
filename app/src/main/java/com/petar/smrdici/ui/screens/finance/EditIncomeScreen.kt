package com.petar.smrdici.ui.screens.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
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
import com.petar.smrdici.ui.components.CategoryDropdown
import com.petar.smrdici.ui.components.DatePickerDialog
import com.petar.smrdici.ui.components.SuccessOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun EditIncomeScreen(
    navController: NavController,
    incomeId: String,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    val user = if (authState is AuthState.Authenticated) (authState as AuthState.Authenticated).user else null

    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("") }
    var selectedDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedAccountId by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showSuccessAnimation by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val categoryManager = remember { CategoryManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    var isLoadingCategories by remember { mutableStateOf(true) }
    val incomeCategories = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        isLoadingCategories = true
        val categories = categoryManager.getIncomeCategoriesWithFallback()
        incomeCategories.clear()
        incomeCategories.addAll(categories)
        isLoadingCategories = false
    }

    val transactionRepository = remember { TransactionRepository.getInstance() }
    var income by remember { mutableStateOf<Income?>(null) }

    LaunchedEffect(incomeId) {
        income = transactionRepository.getIncomeById(incomeId).first()

        income?.let {
            amount = it.amount.toString()
            description = it.description
            selectedCategory = it.category
            selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it.date)?.time ?: System.currentTimeMillis()
            selectedAccountId = it.accountId
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Box(modifier = Modifier.statusBarsPadding()) {
                    AppHeader(
                        title = "Измени приход",
                        navController = navController,
                        showBackButton = true,
                        showProfileIcon = false,
                        user = user
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
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
                    label = { Text("Износ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Опис") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (isLoadingCategories) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    CategoryDropdown(
                        selectedCategory = selectedCategory,
                        onCategorySelected = { selectedCategory = it },
                        categories = incomeCategories,
                        getDisplayName = { name ->
                            try { IncomeCategory.valueOf(name).getDisplayName() } catch (_: Exception) { name }
                        }
                    )
                }

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Датум: ${SimpleDateFormat("dd/MM/yyyy", LocalLocale.current.platformLocale).format(Date(selectedDate))}")
                }

                Button(
                    onClick = {
                        scope.launch {
                            try {
                                val amountValue = amount.toDoubleOrNull()
                                if (amountValue == null) {
                                    snackbarHostState.showSnackbar("Унесите валидан износ")
                                    return@launch
                                }

                                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                val updatedIncome = income?.copy(
                                    amount = amountValue,
                                    description = description,
                                    category = selectedCategory,
                                    date = dateFormat.format(Date(selectedDate)),
                                    accountId = selectedAccountId,
                                    userId = user?.uid ?: ""
                                )

                                if (updatedIncome != null) {
                                    transactionRepository.updateIncome(updatedIncome)
                                    showSuccessAnimation = true
                                    delay(1200.milliseconds)
                                    onNavigateBack()
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Грешка при чувању измена: ${e.message}")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Сачувај измене")
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

        SuccessOverlay(
            visible = showSuccessAnimation,
            message = "Приход успешно измењен"
        )
    }
}