package com.petar.smrdici.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.data.model.Account
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.navigation.Screen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetSettingsScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(),
    budgetSettingsViewModel: BudgetSettingsViewModel = viewModel(factory = BudgetSettingsViewModel.Factory(LocalContext.current)),
    accountViewModel: AccountViewModel = viewModel(factory = AccountViewModel.Factory()),
    dataExportImportViewModel: DataExportImportViewModel = viewModel(factory = DataExportImportViewModel.Factory(LocalContext.current))
) {
    // Спречавамо непотребно учитавање EventRepository-а
    DisposableEffect(Unit) {
        // Ништа не радимо, само спречавамо непотребно учитавање
        onDispose { }
    }
    
    val authState by authViewModel.authState.collectAsState()
    val user = if (authState is AuthState.Authenticated) {
        (authState as AuthState.Authenticated).user
    } else null
    
    // Стање за снекбар
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    // Пратимо стање процеса извоза/увоза
    val isExporting by dataExportImportViewModel.isExporting.collectAsState()
    val isImporting by dataExportImportViewModel.isImporting.collectAsState()
    val exportSuccess by dataExportImportViewModel.exportSuccess.collectAsState()
    val importSuccess by dataExportImportViewModel.importSuccess.collectAsState()
    
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var selectedImportUri by remember { mutableStateOf<Uri?>(null) }
    
    // Launcher за бирање локације за чување извезених података
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            dataExportImportViewModel.exportData(it)
        }
    }
    
    // Launcher за бирање локације за увоз података
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedImportUri = uri
            showImportConfirmDialog = true
        }
    }
    
    // Observer зa успешан извоз
    LaunchedEffect(exportSuccess) {
        exportSuccess?.let { success ->
            if (success) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Подаци су успешно извезени")
                }
            } else {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Грешка при извозу података")
                }
            }
            dataExportImportViewModel.resetExportStatus()
        }
    }
    
    // Observer зa успешан увоз
    LaunchedEffect(importSuccess) {
        importSuccess?.let { success ->
            if (success) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Подаци су успешно увезени")
                }
            } else {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Грешка при увозу података")
                }
            }
            dataExportImportViewModel.resetImportStatus()
        }
    }
    
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
    val accounts by accountViewModel.accounts.collectAsState()
    
    // Мапирамо Period енумерацију на стрингове за приказ
    val periodStrings = mapOf(
        Period.DAILY to "Дневно",
        Period.WEEKLY to "Недељно",
        Period.MONTHLY to "Месечно",
        Period.YEARLY to "Годишње",
        Period.CUSTOM to "Прилагођено",
        Period.ALL to "Све"
    )
    
    // Мапирамо Currency енумерацију на стрингове за приказ
    val currencyStrings = mapOf(
        Currency.RSD to "Динар (RSD)",
        Currency.EUR to "Евро (EUR)",
        Currency.USD to "Долар (USD)"
    )
    
    // Стање за падајуће меније
    var currencyExpanded by remember { mutableStateOf(false) }
    var periodExpanded by remember { mutableStateOf(false) }
    var customPeriodExpanded by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        accountViewModel.refreshAccounts()
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppHeader(
                title = "Подешавања буџета",
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
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Картица за валуту
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Валута",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { currencyExpanded = true }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currency.value,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Промени валуту"
                            )
                        }
                        
                        DropdownMenu(
                            expanded = currencyExpanded,
                            onDismissRequest = { currencyExpanded = false }
                        ) {
                            Currency.entries.forEach { currencyOption ->
                                DropdownMenuItem(
                                    text = { Text(currencyOption.value) },
                                    onClick = {
                                        budgetSettingsViewModel.setCurrency(currencyOption)
                                        currencyExpanded = false
                                        showSnackbar("Валута промењена на ${currencyOption.value}")
                                    }
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Картица за период
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Период буџета",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { periodExpanded = true }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                        
                        DropdownMenu(
                            expanded = periodExpanded,
                            onDismissRequest = { periodExpanded = false }
                        ) {
                            Period.entries.forEach { periodOption ->
                                DropdownMenuItem(
                                    text = { Text(periodOption.value) },
                                    onClick = {
                                        budgetSettingsViewModel.setPeriod(periodOption)
                                        periodExpanded = false
                                        showSnackbar("Период промењен на ${periodOption.value}")
                                    }
                                )
                            }
                        }
                    }
                }
                
                // Ако је изабран прилагођени период, приказујемо додатне опције
                if (period == Period.CUSTOM) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Дан почетка периода",
                                style = MaterialTheme.typography.titleMedium
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "Изаберите дан у месецу када почиње ваш буџетски период:",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = "Приказ трансакција биће од изабраног дана једног месеца до дан пре тог датума следећег месеца. На пример, ако изаберете 15, период ће бити од 15. у месецу до 14. следећег месеца.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        if (customPeriodStartDay > 1) {
                                            budgetSettingsViewModel.setCustomPeriodStartDay(customPeriodStartDay - 1)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Смањи",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$customPeriodStartDay",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                
                                IconButton(
                                    onClick = {
                                        if (customPeriodStartDay < 28) {
                                            budgetSettingsViewModel.setCustomPeriodStartDay(customPeriodStartDay + 1)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Повећај",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Додајемо брзе изборе за уобичајене дане
                            Text(
                                text = "Брзи избор:",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                for (day in listOf(1, 5, 10, 15, 20, 25)) {
                                    QuickDateButton(
                                        day = day,
                                        selectedDay = customPeriodStartDay,
                                        onClick = {
                                            budgetSettingsViewModel.setCustomPeriodStartDay(day)
                                        }
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "Тренутни период: од ${customPeriodStartDay}. дана овог месеца до ${customPeriodStartDay - 1}. дана следећег месеца.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Картица за управљање рачунима
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
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
                                // Додајте логовање
                                logAccountDetails(account)
                                
                                AccountItem(
                                    account = account,
                                    onClick = {
                                        // Додајте логовање
                                        android.util.Log.d("BudgetSettings", "Navigating to edit account: ${account.id}")
                                        
                                        if (account.id.isNotEmpty()) {
                                            navController.navigate("edit_account/${account.id}")
                                        } else {
                                            showSnackbar("Рачун нема валидан ID")
                                        }
                                    },
                                    onSetDefault = {
                                        accountViewModel.setDefaultAccount(account.id) { success ->
                                            if (success) {
                                                showSnackbar("${account.name} је сада подразумевани рачун")
                                            }
                                        }
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
                                navController.navigate(Screen.AddAccount.route)
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Додај рачун")
                        }
                    }
                }

                // Додајемо картицу за категорије
                Spacer(modifier = Modifier.height(16.dp))
                CategoriesCard(
                    onIncomeClick = { 
                        navController.navigate("income_categories")
                    },
                    onExpenseClick = { 
                        navController.navigate("expense_categories")
                    }
                )

                // Додајемо картицу за извоз/увоз података
                Spacer(modifier = Modifier.height(16.dp))
                ExportImportCard(
                    onExportClick = { 
                        exportLauncher.launch(dataExportImportViewModel.getExportFilename())
                    },
                    onImportClick = {
                        importLauncher.launch("application/json")
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
    
    // Дијалог за потврду увоза
    if (showImportConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showImportConfirmDialog = false },
            title = { Text("Увоз података") },
            text = { 
                Text("Упозорење: Увоз ће заменити све постојеће податке! Да ли сте сигурни да желите да наставите?") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportConfirmDialog = false
                        selectedImportUri?.let { uri ->
                            dataExportImportViewModel.importData(uri)
                        }
                    }
                ) {
                    Text("Увези")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showImportConfirmDialog = false }
                ) {
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
    // Додајте логовање
    android.util.Log.d("AccountItem", "Account ID: ${account.id}")
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
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

// Додајте ову функцију за дебаговање
private fun logAccountDetails(account: Account) {
    android.util.Log.d("BudgetSettings", "Account: ${account.name}, ID: ${account.id}, Default: ${account.isDefault}")
} 