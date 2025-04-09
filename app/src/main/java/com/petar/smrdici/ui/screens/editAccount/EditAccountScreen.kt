package com.petar.smrdici.ui.screens.editAccount

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.data.model.AccountType
import com.petar.smrdici.ui.screens.settings.AccountViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAccountScreen(
    navController: NavController,
    accountId: String,
    accountViewModel: AccountViewModel = viewModel(factory = AccountViewModel.Factory())
) {
    LaunchedEffect(accountId) {
        accountViewModel.getAccountById(accountId)
    }
    val account by accountViewModel.currentAccount.collectAsState()
    val isLoading by accountViewModel.isLoading.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    var name by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("0.0") }
    var selectedType by remember { mutableStateOf(AccountType.CASH) }
    var selectedColor by remember { mutableIntStateOf(0) }
    var isDefault by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // Учитавамо податке о рачуну када се компонента први пут прикаже
    LaunchedEffect(account) {
        account?.let {
            name = it.name
            balance = it.balance.toString()
            selectedType = it.type
            selectedColor = it.color
            isDefault = it.isDefault
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Уреди рачун") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showDeleteDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Обриши рачун",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Поље за назив рачуна
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Назив рачуна") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Поље за стање рачуна
                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it },
                    label = { Text("Стање рачуна") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Избор типа рачуна
                Text(
                    text = "Тип рачуна",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AccountTypeChip(
                        type = AccountType.CASH,
                        isSelected = selectedType == AccountType.CASH,
                        onClick = { selectedType = AccountType.CASH }
                    )
                    
                    AccountTypeChip(
                        type = AccountType.BANK,
                        isSelected = selectedType == AccountType.BANK,
                        onClick = { selectedType = AccountType.BANK }
                    )
                    
                    AccountTypeChip(
                        type = AccountType.CREDIT_CARD,
                        isSelected = selectedType == AccountType.CREDIT_CARD,
                        onClick = { selectedType = AccountType.CREDIT_CARD }
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AccountTypeChip(
                        type = AccountType.SAVINGS,
                        isSelected = selectedType == AccountType.SAVINGS,
                        onClick = { selectedType = AccountType.SAVINGS }
                    )
                    
                    AccountTypeChip(
                        type = AccountType.INVESTMENT,
                        isSelected = selectedType == AccountType.INVESTMENT,
                        onClick = { selectedType = AccountType.INVESTMENT }
                    )
                    
                    AccountTypeChip(
                        type = AccountType.OTHER,
                        isSelected = selectedType == AccountType.OTHER,
                        onClick = { selectedType = AccountType.OTHER }
                    )
                }
                
                // Избор боје рачуна
                Text(
                    text = "Боја рачуна",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    accountColors.forEachIndexed { index, color ->
                        ColorChip(
                            color = color,
                            isSelected = selectedColor == index,
                            onClick = { selectedColor = index }
                        )
                    }
                }
                
                // Опција за подразумевани рачун
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isDefault = !isDefault }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDefault) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDefault) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Text(
                        text = "Подразумевани рачун",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Дугме за чување
                Button(
                    onClick = {
                        val updatedAccount = account?.copy(
                            name = name,
                            balance = balance.toDoubleOrNull() ?: 0.0,
                            type = selectedType,
                            color = selectedColor,
                            isDefault = isDefault
                        )
                        
                        updatedAccount?.let {
                            accountViewModel.updateAccount(it) { success ->
                                coroutineScope.launch {
                                    if (success) {
                                        snackbarHostState.showSnackbar("Рачун успешно ажуриран")
                                        navController.navigateUp()
                                    } else {
                                        snackbarHostState.showSnackbar("Грешка при ажурирању рачуна")
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Сачувај")
                }
            }
        }
    }
    
    // Дијалог за потврду брисања
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Брисање рачуна") },
            text = { Text("Да ли сте сигурни да желите да обришете овај рачун?") },
            confirmButton = {
                Button(
                    onClick = {
                        accountViewModel.deleteAccount(accountId) { success ->
                            coroutineScope.launch {
                                if (success) {
                                    snackbarHostState.showSnackbar("Рачун успешно обрисан")
                                    navController.navigateUp()
                                } else {
                                    snackbarHostState.showSnackbar("Грешка при брисању рачуна")
                                }
                            }
                        }
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Обриши")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Откажи")
                }
            }
        )
    }
}

@Composable
fun AccountTypeChip(
    type: AccountType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = when (type) {
                AccountType.CASH -> "Готовина"
                AccountType.BANK -> "Банка"
                AccountType.CREDIT_CARD -> "Кредитна картица"
                AccountType.SAVINGS -> "Штедња"
                AccountType.INVESTMENT -> "Инвестиција"
                AccountType.OTHER -> "Остало"
            },
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun ColorChip(
    color: androidx.compose.ui.graphics.Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Изабрано",
                tint = androidx.compose.ui.graphics.Color.White
            )
        }
    }
}

// Листа боја за рачуне
val accountColors = listOf(
    androidx.compose.ui.graphics.Color(0xFF4285F4), // Плава
    androidx.compose.ui.graphics.Color(0xFF34A853), // Зелена
    androidx.compose.ui.graphics.Color(0xFFFBBC05), // Жута
    androidx.compose.ui.graphics.Color(0xFFEA4335), // Црвена
    androidx.compose.ui.graphics.Color(0xFF9C27B0), // Љубичаста
    androidx.compose.ui.graphics.Color(0xFF00BCD4), // Тиркизна
    androidx.compose.ui.graphics.Color(0xFFFF9800), // Наранџаста
    androidx.compose.ui.graphics.Color(0xFF795548)  // Браон
) 