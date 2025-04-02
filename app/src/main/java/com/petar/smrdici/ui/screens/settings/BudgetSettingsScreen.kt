package com.petar.smrdici.ui.screens.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.data.model.Account
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import kotlinx.coroutines.launch

@Composable
fun BudgetSettingsScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(),
    budgetSettingsViewModel: BudgetSettingsViewModel = viewModel(factory = BudgetSettingsViewModel.Factory())
) {
    val authState by authViewModel.authState.collectAsState()
    val user = if (authState is AuthState.Authenticated) {
        (authState as AuthState.Authenticated).user
    } else null
    
    // Стање за снекбар
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    // Функција за приказивање снекбара
    fun showSnackbar(message: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }
    
    // Стање за дијалоге
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showPeriodDialog by remember { mutableStateOf(false) }
    var showCustomPeriodDialog by remember { mutableStateOf(false) }
    
    // Добијамо вредности из ViewModel-а
    val currency by budgetSettingsViewModel.currency.collectAsState()
    val period by budgetSettingsViewModel.period.collectAsState()
    val customPeriodStartDay by budgetSettingsViewModel.customPeriodStartDay.collectAsState()
    val accounts by budgetSettingsViewModel.accounts.collectAsState()
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AppHeader(
                title = "Подешавања буџета",
                user = user,
                navController = navController
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Картица за валуту
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Валута",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCurrencyDialog = true }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachMoney,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Text(
                                text = "${currency.code} (${currency.symbol})",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Промени валуту"
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Картица за период
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Период буџета",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPeriodDialog = true }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Text(
                                text = period.value,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Промени период"
                            )
                        }
                        
                        // Ако је изабран прилагођени период, приказујемо додатне опције
                        if (period == Period.CUSTOM) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showCustomPeriodDialog = true }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                Text(
                                    text = "Почиње ${customPeriodStartDay}. у месецу",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Промени датум почетка"
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Картица за управљање рачунима
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Управљање рачунима",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        // Приказујемо листу рачуна
                        if (accounts.isEmpty()) {
                            Text(
                                text = "Нема сачуваних рачуна",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        } else {
                            accounts.forEach { account ->
                                AccountItem(
                                    account = account,
                                    onClick = {
                                        // Овде ћемо додати навигацију на екран за уређивање рачуна
                                        showSnackbar("Уређивање рачуна ће бити доступно ускоро")
                                    },
                                    onSetDefault = {
                                        budgetSettingsViewModel.setDefaultAccount(account.id)
                                        showSnackbar("${account.name} је постављен као подразумевани рачун")
                                    }
                                )
                                
                                if (account != accounts.last()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }
                        
                        // Дугме за додавање новог рачуна
                        TextButton(
                            onClick = {
                                // Овде ћемо додати навигацију на екран за додавање новог рачуна
                                showSnackbar("Додавање новог рачуна ће бити доступно ускоро")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Додај рачун",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Text(
                                text = "Додај нови рачун",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Додајемо картицу за категорије
                Spacer(modifier = Modifier.height(16.dp))
                CategoriesCard(
                    onIncomeClick = { 
                        showSnackbar("Управљање категоријама прихода ће бити доступно ускоро") 
                    },
                    onExpenseClick = { 
                        showSnackbar("Управљање категоријама расхода ће бити доступно ускоро") 
                    }
                )

                // Додајемо картицу за извоз/увоз података
                Spacer(modifier = Modifier.height(16.dp))
                ExportImportCard(
                    onExportClick = { 
                        showSnackbar("Извоз трансакција ће бити доступан ускоро") 
                    },
                    onImportClick = { 
                        showSnackbar("Увоз трансакција ће бити доступан ускоро") 
                    }
                )
            }
        }
    }
    
    // Дијалог за избор валуте
    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text("Изаберите валуту") },
            text = {
                Column {
                    Currency.entries.forEach { currencyOption ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    budgetSettingsViewModel.setCurrency(currencyOption)
                                    showCurrencyDialog = false
                                    showSnackbar("Валута промењена на ${currencyOption.code}")
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${currencyOption.code} (${currencyOption.symbol})",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            
                            if (currencyOption == currency) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Изабрано",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrencyDialog = false }) {
                    Text("Откажи")
                }
            }
        )
    }
    
    // Дијалог за избор периода
    if (showPeriodDialog) {
        AlertDialog(
            onDismissRequest = { showPeriodDialog = false },
            title = { Text("Изаберите период буџета") },
            text = {
                Column {
                    Period.entries.forEach { periodOption ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    budgetSettingsViewModel.setPeriod(periodOption)
                                    showPeriodDialog = false
                                    showSnackbar("Период промењен на ${periodOption.value}")
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = periodOption.value,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            
                            if (periodOption == period) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Изабрано",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPeriodDialog = false }) {
                    Text("Откажи")
                }
            }
        )
    }
    
    // Дијалог за избор датума почетка прилагођеног периода
    if (showCustomPeriodDialog) {
        AlertDialog(
            onDismissRequest = { showCustomPeriodDialog = false },
            title = { Text("Изаберите датум почетка периода") },
            text = {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Изаберите дан у месецу када почиње ваш буџетски период:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Брзи избор популарних датума
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        QuickDateButton(1, customPeriodStartDay) { 
                            budgetSettingsViewModel.setCustomPeriodStartDay(1) 
                        }
                        QuickDateButton(5, customPeriodStartDay) { 
                            budgetSettingsViewModel.setCustomPeriodStartDay(5) 
                        }
                        QuickDateButton(10, customPeriodStartDay) { 
                            budgetSettingsViewModel.setCustomPeriodStartDay(10) 
                        }
                        QuickDateButton(15, customPeriodStartDay) { 
                            budgetSettingsViewModel.setCustomPeriodStartDay(15) 
                        }
                        QuickDateButton(20, customPeriodStartDay) { 
                            budgetSettingsViewModel.setCustomPeriodStartDay(20) 
                        }
                        QuickDateButton(25, customPeriodStartDay) { 
                            budgetSettingsViewModel.setCustomPeriodStartDay(25) 
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Ручни избор датума
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (customPeriodStartDay > 1) {
                                    budgetSettingsViewModel.setCustomPeriodStartDay(customPeriodStartDay - 1)
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Смањи",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Text(
                            text = "$customPeriodStartDay",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        
                        IconButton(
                            onClick = {
                                if (customPeriodStartDay < 28) {
                                    budgetSettingsViewModel.setCustomPeriodStartDay(customPeriodStartDay + 1)
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Повећај",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { 
                        showCustomPeriodDialog = false
                        showSnackbar("Датум почетка периода промењен на ${customPeriodStartDay}. у месецу")
                    }
                ) {
                    Text("Потврди")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomPeriodDialog = false }) {
                    Text("Откажи")
                }
            }
        )
    }
}

@Composable
fun QuickDateButton(day: Int, selectedDay: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = if (day == selectedDay) 
            MaterialTheme.colorScheme.primary 
        else 
            MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$day",
                color = if (day == selectedDay) 
                    MaterialTheme.colorScheme.onPrimary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AccountItem(
    account: Account,
    onClick: () -> Unit,
    onSetDefault: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Индикатор боје рачуна
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Color(account.color), CircleShape)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.bodyLarge
                )
                
                if (account.isDefault) {
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "Подразумевано",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            
            Text(
                text = "${account.balance} ${account.currency}",
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    account.balance > 0 -> Color(0xFF4CAF50) // Зелена за позитиван баланс
                    account.balance < 0 -> Color(0xFFF44336) // Црвена за негативан баланс
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                }
            )
        }
        
        // Ако рачун није подразумевани, приказујемо дугме за постављање као подразумевани
        if (!account.isDefault) {
            IconButton(onClick = onSetDefault) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Постави као подразумевани",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
        
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
} 