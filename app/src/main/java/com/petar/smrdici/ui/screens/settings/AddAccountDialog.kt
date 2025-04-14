package com.petar.smrdici.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.petar.smrdici.data.model.Account
import com.petar.smrdici.data.model.AccountType
import com.petar.smrdici.ui.screens.editAccount.accountColors

@Composable
@Suppress("unused")
fun AddAccountDialog(
    onDismiss: () -> Unit,
    onAddAccount: (Account) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("0.0") }
    var selectedType by remember { mutableStateOf(AccountType.CASH) }
    var selectedColor by remember { mutableIntStateOf(0) }
    var isDefault by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Додај нови рачун") },
        confirmButton = {
            Button(
                onClick = {
                    val account = Account(
                        name = name,
                        balance = balance.toDoubleOrNull() ?: 0.0,
                        type = selectedType,
                        color = selectedColor,
                        isDefault = isDefault
                    )
                    onAddAccount(account)
                },
                enabled = name.isNotBlank()
            ) {
                Text("Додај")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Откажи")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Назив рачуна") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it },
                    label = { Text("Почетно стање") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Тип рачуна", style = MaterialTheme.typography.titleMedium)
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    com.petar.smrdici.ui.screens.editAccount.AccountTypeChip(
                        type = AccountType.CASH,
                        isSelected = selectedType == AccountType.CASH,
                        onClick = { selectedType = AccountType.CASH }
                    )
                    
                    com.petar.smrdici.ui.screens.editAccount.AccountTypeChip(
                        type = AccountType.BANK,
                        isSelected = selectedType == AccountType.BANK,
                        onClick = { selectedType = AccountType.BANK }
                    )
                    
                    com.petar.smrdici.ui.screens.editAccount.AccountTypeChip(
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
                    com.petar.smrdici.ui.screens.editAccount.AccountTypeChip(
                        type = AccountType.SAVINGS,
                        isSelected = selectedType == AccountType.SAVINGS,
                        onClick = { selectedType = AccountType.SAVINGS }
                    )
                    
                    com.petar.smrdici.ui.screens.editAccount.AccountTypeChip(
                        type = AccountType.INVESTMENT,
                        isSelected = selectedType == AccountType.INVESTMENT,
                        onClick = { selectedType = AccountType.INVESTMENT }
                    )
                    
                    com.petar.smrdici.ui.screens.editAccount.AccountTypeChip(
                        type = AccountType.OTHER,
                        isSelected = selectedType == AccountType.OTHER,
                        onClick = { selectedType = AccountType.OTHER }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Боја", style = MaterialTheme.typography.titleMedium)
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    accountColors.forEachIndexed { index, color ->
                        com.petar.smrdici.ui.screens.editAccount.ColorChip(
                            color = color,
                            isSelected = selectedColor == index,
                            onClick = { selectedColor = index }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = isDefault,
                        onCheckedChange = { isDefault = it }
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text("Постави као подразумевани рачун")
                }
            }
        }
    )
} 