package com.petar.smrdici.ui.screens.transfer

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.data.model.Account
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.theme.PurpleGrey40
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Екран за трансфер средстава између рачуна
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
fun TransferScreen(
    navController: NavController,
    viewModel: TransferViewModel = viewModel(factory = TransferViewModel.Factory()),
    user: com.google.firebase.auth.FirebaseUser? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val transferHistory by viewModel.transferHistory.collectAsState()
    
    // Стања за dropdown менија
    var sourceAccountExpanded by remember { mutableStateOf(false) }
    var destinationAccountExpanded by remember { mutableStateOf(false) }
    
    // Стање за историју трансфера
    var showHistory by remember { mutableStateOf(false) }
    
    // Обрађивање порука
    val errorMessage = uiState.error
    val successMessage = uiState.successMessage
    
    // Уклањамо поруке након што се прикажу
    LaunchedEffect(errorMessage, successMessage) {
        if (errorMessage != null || successMessage != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearMessages()
        }
    }
    
    Scaffold(
        topBar = {
            AppHeader(
                title = "Трансфер новца",
                user = user,
                navController = navController,
                showBackButton = true
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    // Карта за трансфер
                    TransferCard(
                        viewModel = viewModel,
                        accounts = accounts,
                        sourceAccountExpanded = sourceAccountExpanded,
                        onSourceAccountExpandedChange = { sourceAccountExpanded = it },
                        destinationAccountExpanded = destinationAccountExpanded,
                        onDestinationAccountExpandedChange = { destinationAccountExpanded = it },
                        onTransferClick = {
                            viewModel.executeTransfer {
                                // Овде можемо додати додатне акције након успешног трансфера
                            }
                        }
                    )
                }
                
                item {
                    // Картица за историју трансфера
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 4.dp
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showHistory = !showHistory }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Историја трансфера",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Icon(
                                imageVector = if (showHistory) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = if (showHistory) "Сакриј" else "Прикажи"
                            )
                        }
                        
                        AnimatedVisibility(visible = showHistory) {
                            if (transferHistory.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Нема историје трансфера",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray
                                    )
                                }
                            } else {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    transferHistory.forEach { transfer ->
                                        TransferHistoryItem(
                                            transfer = transfer,
                                            accounts = accounts
                                        )
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Приказ порука
            AnimatedVisibility(
                visible = errorMessage != null || successMessage != null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                val message = errorMessage ?: successMessage
                val backgroundColor = if (errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                
                Surface(
                    color = backgroundColor,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = message ?: "",
                        color = Color.White,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // Индикатор учитавања
            if (viewModel.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
fun TransferCard(
    viewModel: TransferViewModel,
    accounts: List<Account>,
    sourceAccountExpanded: Boolean,
    onSourceAccountExpandedChange: (Boolean) -> Unit,
    destinationAccountExpanded: Boolean,
    onDestinationAccountExpandedChange: (Boolean) -> Unit,
    onTransferClick: () -> Unit
) {
    val sourceAccount = viewModel.getAccountById(viewModel.sourceAccountId)
    val destinationAccount = viewModel.getAccountById(viewModel.destinationAccountId)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            // Наслов
            Text(
                text = "Трансфер средстава",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Изворни рачун
            Text(
                text = "Са рачуна",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = sourceAccountExpanded,
                    onExpandedChange = onSourceAccountExpandedChange
                ) {
                    TextField(
                        value = sourceAccount?.name ?: "Изаберите рачун",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceAccountExpanded)
                        },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = sourceAccountExpanded,
                        onDismissRequest = { onSourceAccountExpandedChange(false) }
                    ) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(account.name)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${account.balance} ${account.currency}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PurpleGrey40
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.setSourceAccount(account.id)
                                    onSourceAccountExpandedChange(false)
                                }
                            )
                        }
                    }
                }
            }
            
            // Дестинациони рачун
            Text(
                text = "На рачун",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = destinationAccountExpanded,
                    onExpandedChange = onDestinationAccountExpandedChange
                ) {
                    TextField(
                        value = destinationAccount?.name ?: "Изаберите рачун",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = destinationAccountExpanded)
                        },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = destinationAccountExpanded,
                        onDismissRequest = { onDestinationAccountExpandedChange(false) }
                    ) {
                        accounts.forEach { account ->
                            if (account.id != viewModel.sourceAccountId) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(account.name)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${account.balance} ${account.currency}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = PurpleGrey40
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.setDestinationAccount(account.id)
                                        onDestinationAccountExpandedChange(false)
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            // Износ
            Text(
                text = "Износ",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            TextField(
                value = viewModel.amount,
                onValueChange = { viewModel.setAmount(it) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                placeholder = { Text("Унесите износ") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
            
            // Опис
            Text(
                text = "Опис (опционо)",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            TextField(
                value = viewModel.description,
                onValueChange = { viewModel.setDescription(it) },
                placeholder = { Text("Унесите опис трансфера") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
            
            // Дугме за трансфер
            Button(
                onClick = onTransferClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = viewModel.sourceAccountId != null && viewModel.destinationAccountId != null && viewModel.amount.isNotEmpty(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SwapHoriz,
                        contentDescription = "Трансфер",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Изврши трансфер")
                }
            }
        }
    }
}

@Composable
fun TransferHistoryItem(
    transfer: Map<String, Any>,
    accounts: List<Account>
) {
    val sourceAccountId = transfer["sourceAccountId"] as? String ?: ""
    val destinationAccountId = transfer["destinationAccountId"] as? String ?: ""
    val amount = (transfer["amount"] as? Number)?.toDouble() ?: 0.0
    val description = transfer["description"] as? String ?: "Трансфер новца"
    val timestamp = transfer["timestamp"]
    
    val dateFormat = SimpleDateFormat("dd.MM.yyyy. HH:mm", Locale.getDefault())
    val date = if (timestamp is com.google.firebase.Timestamp) {
        dateFormat.format(timestamp.toDate())
    } else {
        "Непознат датум"
    }
    
    val sourceAccount = accounts.find { it.id == sourceAccountId }
    val destinationAccount = accounts.find { it.id == destinationAccountId }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = date,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            
            Text(
                text = "$amount РСД",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.AccountBalance,
                contentDescription = "Рачун",
                tint = Color.Gray,
                modifier = Modifier.size(16.dp)
            )
            
            Spacer(modifier = Modifier.width(4.dp))
            
            Text(
                text = "Са: ${sourceAccount?.name ?: sourceAccountId}",
                style = MaterialTheme.typography.bodySmall
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Icon(
                imageVector = Icons.Outlined.SwapHoriz,
                contentDescription = "Трансфер",
                tint = Color.Gray,
                modifier = Modifier.size(16.dp)
            )
            
            Spacer(modifier = Modifier.width(4.dp))
            
            Text(
                text = "На: ${destinationAccount?.name ?: destinationAccountId}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        
        if (description.isNotEmpty() && description != "Трансфер новца") {
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Опис",
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
                
                Spacer(modifier = Modifier.width(4.dp))
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
} 