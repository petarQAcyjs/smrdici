package com.petar.smrdici.ui.screens.addAccount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.data.model.Account
import com.petar.smrdici.data.model.AccountType
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.screens.editAccount.AccountTypeChip
import com.petar.smrdici.ui.screens.editAccount.ColorChip
import com.petar.smrdici.ui.screens.editAccount.accountColors
import com.petar.smrdici.ui.screens.settings.AccountViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(
    navController: NavController,
    accountViewModel: AccountViewModel = viewModel(factory = AccountViewModel.Factory()),
    authViewModel: AuthViewModel = viewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    var name by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("0.0") }
    var selectedType by remember { mutableStateOf(AccountType.CASH) }
    var selectedColor by remember { mutableIntStateOf(0) }
    var isDefault by remember { mutableStateOf(false) }
    
    val authState by authViewModel.authState.collectAsState()
    val user = if (authState is AuthState.Authenticated) (authState as AuthState.Authenticated).user else null
    
    Scaffold(
        topBar = {
            AppHeader(
                title = "Додај рачун",
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
                label = { Text("Почетно стање") },
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
            
            Spacer(modifier = Modifier.height(8.dp))
            
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
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isDefault,
                    onCheckedChange = { isDefault = it }
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "Постави као подразумевани рачун",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Дугме за чување
            Button(
                onClick = {
                    val account = Account(
                        id = "",  // Empty string for new accounts, will be set by the repository
                        name = name,
                        balance = balance.toDoubleOrNull() ?: 0.0,
                        type = selectedType,
                        color = selectedColor,
                        isDefault = isDefault
                    )
                    
                    accountViewModel.addAccount(account)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Рачун успешно додат")
                        navController.navigateUp()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank()
            ) {
                Text("Додај рачун")
            }
        }
    }
} 