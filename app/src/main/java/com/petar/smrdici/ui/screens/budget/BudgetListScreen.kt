package com.petar.smrdici.ui.screens.budget

import android.util.Log
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.data.model.Expense
import com.petar.smrdici.data.model.Income
import com.petar.smrdici.data.model.DisplayBudget
import com.petar.smrdici.data.model.Account
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.components.BudgetBattery
import com.petar.smrdici.ui.components.StandardPullRefreshIndicator
import com.petar.smrdici.ui.navigation.Screen
import com.petar.smrdici.ui.screens.settings.Period
import kotlinx.coroutines.launch
import com.petar.smrdici.data.model.CategoryManager
import com.petar.smrdici.utils.LogUtils
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import kotlinx.coroutines.delay

/**
 * BudgetListScreen - Ekran za prikaz budžeta i transakcija
 * 
 * Napomena o logovanju:
 * ---------------------
 * Ova komponenta koristi optimizovani sistem logovanja putem LogUtils klase.
 * Umesto direktnog korišćenja Android Log API-ja, koristimo LogUtils koji:
 * 
 * 1. Filtrira logove po nivou detaljnosti (MINIMAL, NORMAL, VERBOSE)
 * 2. Ograničava količinu logova za velike kolekcije (do 5 stavki po default-u)
 * 3. Uključuje ili isključuje logove po kategorijama (expense, income, budget, itd.)
 * 4. Može generisati statistiku umesto detaljnih logova za velike kolekcije
 * 
 * Za uključivanje/isključivanje detaljnog logovanja, pogledajte 
 * SmrdiciApplication.initLogging() metodu.
 * 
 * Ako treba dodati nove logove, koristite:
 * - LogUtils.i() za osnovne informacije (uvek vidljivo)
 * - LogUtils.d() za detalje (vidljivo samo u debug režimu)
 * - LogUtils.w() za upozorenja
 * - LogUtils.e() za greške
 * - LogUtils.logCollection() za logovanje kolekcija
 * - LogUtils.logNumericStats() za statistike numeričkih vrednosti
 */

/**
 * Враћа валуту за приказ на основу изабраног рачуна
 */
private fun getCurrencyForAccount(accounts: List<Account>, selectedAccountId: String?): String {
    return if (selectedAccountId != null) {
        accounts.find { it.id == selectedAccountId }?.currency ?: "RSD"
    } else {
        val currencies = accounts.mapNotNull { it.currency }.distinct()
        if (currencies.isEmpty()) "RSD" else currencies.first()
    }
}

/**
 * Komponenta za prikaz i navigaciju kroz periode
 */
@Composable
fun PeriodNavigationControls(
    currentPeriod: Period,
    currentPeriodText: String,
    periodOffset: Int,
    onNavigateBack: () -> Unit,
    onNavigateForward: () -> Unit,
    onResetPeriod: () -> Unit,
    onSelectDate: () -> Unit
) {
    // Za Period.ALL ne prikazujemo kontrole za navigaciju, samo naslov
    if (currentPeriod == Period.ALL) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currentPeriodText.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    } else {
        // Za ostale periode prikazujemo pune kontrole
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Dugme za navigaciju unazad
            androidx.compose.material3.IconButton(
                onClick = onNavigateBack
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Prethodni period"
                )
            }
            
            // Tekst perioda (klikabilan za izbor datuma u dnevnom režimu)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (currentPeriod == Period.DAILY) {
                            Modifier.clickable { onSelectDate() }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = currentPeriodText.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    
                    // Ako nismo u trenutnom periodu, prikazujemo dugme za povratak
                    if (periodOffset > 0) {
                        androidx.compose.material3.TextButton(
                            onClick = onResetPeriod,
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Vrati se na trenutni period")
                        }
                    }
                }
            }
            
            // Dugme za navigaciju unapred (onemogućeno ako smo u trenutnom periodu)
            androidx.compose.material3.IconButton(
                onClick = onNavigateForward,
                enabled = periodOffset > 0
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Sledeći period"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER")
@Composable
fun BudgetListScreen(
    navController: NavController,
    budgetsViewModel: BudgetsViewModel = viewModel(factory = BudgetsViewModel.Factory(LocalContext.current)),
    budgetViewModel: BudgetViewModel = viewModel(factory = BudgetViewModel.Factory(LocalContext.current))
) {
    // Za snackbar poruke
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // Stanje ScrollView-a
    val scrollState = rememberScrollState()
    
    // Dodajemo varijable za stanja koja su nedostajala
    var showPeriodDropdown by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showAccountsDropdown by remember { mutableStateOf(false) }
    
    // LaunchedEffect za inicialno učitavanje podataka
    LaunchedEffect(Unit) {
        LogUtils.i("BudgetListScreen", "Inicijalno učitavanje podataka")
        budgetsViewModel.loadBudgets()
        budgetViewModel.reloadTransactions()
    }
    
    // Učitavamo podatke iz ViewModela
    val isLoading by budgetsViewModel.isLoading.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }
    val totalExpenseSpent by budgetsViewModel.totalExpenseSpent.collectAsState()
    val totalIncomeReceived by budgetsViewModel.totalIncomeReceived.collectAsState()
    val errorMessage by budgetsViewModel.errorMessage.collectAsState()
    val displayExpenseBudgets by budgetsViewModel.displayExpenseBudgets.collectAsState()
    val displayIncomeBudgets by budgetsViewModel.displayIncomeBudgets.collectAsState()
    val selectedPeriodIndex by budgetsViewModel.selectedPeriodIndex.collectAsState()
    
    // Dobijamo transakcije iz BudgetViewModel
    val expenses by budgetViewModel.expenses.collectAsState(initial = emptyList())
    val incomes by budgetViewModel.incomes.collectAsState(initial = emptyList())
    
    // Dodatno - direktno pratimo UI stanje iz BudgetViewModel za dijagnostiku
    val uiState by budgetViewModel.uiState.collectAsState()
    
    // Dodajemo logiku za osvežavanje podataka na pull-to-refresh
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            LogUtils.i("BudgetListScreen", "Korisnik je zatražio osvežavanje podataka (pull-to-refresh)")
            isRefreshing = true
            
            // Koristimo coroutineScope za osvežavanje
            scope.launch {
                try {
                    // Pozivamo učitavanje podataka
                    budgetsViewModel.loadBudgets()
                    budgetViewModel.reloadTransactions()
                    
                    // Čekamo kratko da se podaci učitaju
                    delay(500)
                } finally {
                    // Osiguravamo da se isRefreshing uvek resetuje
                    isRefreshing = false
                }
            }
        }
    )
    
    // Sigurnosni mehanizam za slučaj da isLoading ostane 'zaglavljen' na true
    LaunchedEffect(Unit) {
        // Ovo je globalni sigurnosni tajmer koji će isključiti indikator učitavanja
        // ako ostane aktivan predugo (15 sekundi je više nego dovoljno za učitavanje)
        while (true) {
            if (isLoading) {
                // Počinjemo brojanje vremena kada je isLoading true
                val startTime = System.currentTimeMillis()
                LogUtils.i("BudgetListScreen", "SIGURNOSNI TAJMER: Počinjem praćenje učitavanja")
                
                // Čekamo 15 sekundi
                kotlinx.coroutines.delay(15000)
                
                // Ako je i dalje isLoading nakon 15 sekundi, forsirano ga isključujemo
                if (budgetsViewModel.isLoading.value) {
                    LogUtils.e("BudgetListScreen", "SIGURNOSNI TAJMER: Forsiram isključivanje indikatora nakon ${(System.currentTimeMillis() - startTime) / 1000} sekundi")
                    
                    // Izvršavamo ovo u main thread-u jer modifikujemo UI stanje
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        try {
                            // Forsirano isključujemo indikator učitavanja
                            budgetsViewModel.forceStopLoading()
                            
                            // Takođe osiguravamo da se ne prikazuje isRefreshing
                            isRefreshing = false
                            
                            // Prikazujemo snackbar korisniku da je došlo do problema
                            scope.launch {
                                snackbarHostState.showSnackbar("Učitavanje je trajalo predugo i automatski je zaustavljeno.")
                            }
                        } catch (e: Exception) {
                            LogUtils.e("BudgetListScreen", "Greška pri forsiranom zaustavljanju učitavanja: ${e.message}")
                        }
                    }
                }
            }
            
            // Čekamo pre sledeće provere (1 sekunda)
            kotlinx.coroutines.delay(1000)
        }
    }
    
    // Stanje za tabove
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Расходи", "Приходи")
    
    // Pratimo promenu taba
    LaunchedEffect(selectedTabIndex) {
        LogUtils.i("BudgetListScreen", "Promenjen tab na: ${tabs[selectedTabIndex]}")
    }
    
    // Vrednosti budžeta
    val budgetLimit by budgetsViewModel.budgetLimit.collectAsState()
    
    // Konvertujemo period u čitljiv tekst
    val periodText = when(selectedPeriodIndex) {
        0 -> "danas"
        1 -> "ove sedmice"
        2 -> "ovog meseca"
        3 -> "ove godine"
        4 -> "u ovom periodu"
        else -> ""
    }
    
    // Pratimo offset perioda
    val periodOffset by budgetViewModel.periodOffset.collectAsState()
    
    // Pratimo izabrani datum (za dnevni prikaz)
    val selectedDate by budgetViewModel.selectedDate.collectAsState()
    
    // Dinamično pratimo prikaz perioda koristeći remember i derivedStateOf
    // Ovo će automatski osvežiti vrednost kada se promene zavisni parametri (selectedPeriod, periodOffset, selectedDate)
    val periodDisplayText by remember(
        budgetViewModel.selectedPeriod.collectAsState().value,
        periodOffset,
        selectedDate
    ) {
        derivedStateOf { budgetViewModel.getPeriodDisplayText() }
    }
    
    // LaunchedEffect za reagovanje na promene u periodu (za dijagnostiku)
    LaunchedEffect(periodOffset, budgetViewModel.selectedPeriod.collectAsState().value, selectedDate) {
        LogUtils.i("BudgetListScreen", "Period promenjen: offset=$periodOffset, period=${budgetViewModel.selectedPeriod.value}, periodText=${budgetViewModel.getPeriodDisplayText()}")
    }
    
    // Efekat za prikazivanje greške
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            budgetsViewModel.errorMessage.value = null
        }
    }
    
    // Stanje učitavanja povezujemo sa isRefreshing
    LaunchedEffect(isLoading) {
        if (!isLoading) {
            isRefreshing = false
        }
    }
    
    // Lista perioda
    val periodStrings = listOf("Дан", "Недеља", "Месец", "Година", "Период", "Све")
    
    val accounts by budgetsViewModel.accounts.collectAsState(initial = emptyList())
    val selectedAccountId by budgetsViewModel.selectedAccountId.collectAsState()
    
    // Učitava ime odabranog računa
    var selectedAccount by remember { mutableStateOf("") }
    LaunchedEffect(selectedAccountId) {
        selectedAccount = budgetsViewModel.getSelectedAccountName()
    }
    
    // Sortiranje
    var sortOrder by remember { mutableStateOf(SortOrder.DATE_DESC) }
    
    // Stanje za dialog za postavljanje budžetskog limit
    var showBudgetLimitDialog by remember { mutableStateOf(false) }
    var budgetLimitInput by remember { mutableStateOf("") }
    
    // Komponente UI
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF1A1C1E),
        topBar = {
            AppHeader(
                title = "Budžet",
                navController = navController,
                showBackButton = true,
                user = null
            )
        },
        floatingActionButton = {
            Row {
                // FAB za dodavanje transakcije
                FloatingActionButton(
                    onClick = { 
                        when (selectedTabIndex) {
                            0 -> navController.navigate(Screen.AddExpense.route)
                            1 -> navController.navigate(Screen.AddIncome.route)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Dodaj transakciju"
                    )
                }
            }
        }
    ) { paddingValues ->
        // Box sa pullRefresh modifikatorom oko celog sadržaja
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pullRefresh(pullRefreshState)
        ) {
            // Glavni sadržaj je Column sa scrollState
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // Dodajemo komponente za izbor perioda i kretanje kroz periode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Period:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(
                                        color = Color(0xFF2E2E2E),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                    )
                                    .clickable { showPeriodDropdown = true }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = when (selectedPeriodIndex) {
                                        0 -> "Dan"
                                        1 -> "Nedelja"
                                        2 -> "Mesec"
                                        3 -> "Godina"
                                        4 -> "Prilagođeno"
                                        5 -> "Sve"
                                        else -> "Mesec"
                                    },
                                    color = Color.White,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Izbor perioda",
                                    tint = Color.White
                                )
                            }
                            
                            androidx.compose.material3.DropdownMenu(
                                expanded = showPeriodDropdown,
                                onDismissRequest = { showPeriodDropdown = false },
                                modifier = Modifier.background(Color(0xFF2E2E2E))
                            ) {
                                listOf("Dan", "Nedelja", "Mesec", "Godina", "Prilagođeno", "Sve").forEachIndexed { index, title ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(title, color = Color.White) },
                                        onClick = {
                                            // Ovde ažuriramo period
                                            budgetViewModel.updatePeriodIndex(index)
                                            showPeriodDropdown = false
                                        },
                                        trailingIcon = {
                                            if (selectedPeriodIndex == index) {
                                                Icon(
                                                    imageVector = androidx.compose.material.icons.Icons.Default.Check,
                                                    contentDescription = "Odabrano",
                                                    tint = androidx.compose.material3.MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Kontrole za navigaciju kroz periode
                PeriodNavigationControls(
                    currentPeriod = budgetViewModel.selectedPeriod.value,
                    currentPeriodText = periodDisplayText,
                    periodOffset = periodOffset,
                    onNavigateBack = { budgetViewModel.movePeriodBackward() },
                    onNavigateForward = { budgetViewModel.movePeriodForward() },
                    onResetPeriod = { budgetViewModel.resetToCurrentPeriod() },
                    onSelectDate = {
                        if (budgetViewModel.selectedPeriod.value == Period.DAILY) {
                            showDatePicker = true
                        }
                    }
                )
                
                // Tabovi za rashode i prihode
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    contentColor = Color.White
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Prikaz podataka za odabrani tab
                when (selectedTabIndex) {
                    0 -> {
                        // Rashodi
                        // Prikaz ukupnog budžeta
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF303436)
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 4.dp
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                // Naslov sa dropdown filterom za račun
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showAccountsDropdown = true },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = selectedAccount,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Одабери рачун",
                                        tint = Color.White
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                // Стање рачуна уместо буџета
                                val selectedAccountBalance = if (selectedAccountId != null) {
                                    accounts.find { it.id == selectedAccountId }?.balance ?: 0.0
                                } else {
                                    accounts.sumOf { it.balance }
                                }
                                
                                Text(
                                    text = "Тренутно стање: ${budgetsViewModel.formatAmount(selectedAccountBalance)}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Додајемо прогрес бар буџета који се може кликнути
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Наслов прогрес бара
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Буџет за ${periodDisplayText}:",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                    
                                    Text(
                                        text = if (budgetLimit > 0.0) budgetsViewModel.formatAmount(budgetLimit) else "Није подешено",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Прогрес бар буџета
                                val progress = if (budgetLimit > 0.0) (totalExpenseSpent / budgetLimit).coerceIn(0.0, 1.0) else 0.0
                                val progressColor = when {
                                    progress >= 1.0 -> Color.Red
                                    progress >= 0.75 -> Color(0xFFFF9800) // Наранџаста
                                    else -> Color(0xFF4CAF50) // Зелена
                                }
                                
                                // Заменимо обичан прогрес бар са BudgetBattery компонентом
                                BudgetBattery(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp, bottom = 8.dp)
                                        .clickable { showBudgetLimitDialog = true },
                                    expenses = totalExpenseSpent,
                                    budget = budgetLimit,
                                    currency = getCurrencyForAccount(accounts, selectedAccountId),
                                    backgroundNotFilled = Color(0xFF303436),
                                    onClick = { showBudgetLimitDialog = true }
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Opcije za sortiranje
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { 
                                    sortOrder = when(sortOrder) {
                                        SortOrder.DATE_DESC -> SortOrder.AMOUNT_DESC
                                        SortOrder.AMOUNT_DESC -> SortOrder.AMOUNT_ASC
                                        SortOrder.AMOUNT_ASC -> SortOrder.DATE_DESC
                                    }
                                }
                            ) {
                                val sortText = when(sortOrder) {
                                    SortOrder.DATE_DESC -> "Сортирај по: Датуму"
                                    SortOrder.AMOUNT_DESC -> "Сортирај по: Износу (↓)"
                                    SortOrder.AMOUNT_ASC -> "Сортирај по: Износу (↑)"
                                }
                                Text(
                                    text = sortText,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Prikazujemo listu TRANSAKCIJA, ne budžeta
                        val transactions = if (selectedTabIndex == 0) {
                            // Sortiranje troškova
                            LogUtils.i("BudgetListScreen", "Pripremam troškove za prikaz, ukupno: ${expenses.size}")
                            
                            // Loguj detaljno samo ako je potrebno i samo prvih nekoliko stavki
                            LogUtils.logCollection(
                                tag = "BudgetListScreen",
                                collection = expenses,
                                prefix = "EXPENSES",
                                transform = { expense -> 
                                    "id=${expense.id}, amount=${expense.amount}, date=${expense.date}, category=${expense.category}" 
                                }
                            )
                            
                            val sortedExpenses = when(sortOrder) {
                                SortOrder.DATE_DESC -> expenses.sortedByDescending { it.getDateObject()?.time ?: 0L }
                                SortOrder.AMOUNT_DESC -> expenses.sortedByDescending { it.amount }
                                SortOrder.AMOUNT_ASC -> expenses.sortedBy { it.amount }
                            }
                            
                            LogUtils.i("BudgetListScreen", "Sortirano ${sortedExpenses.size} troškova po ${sortOrder.name}")
                            
                            // Loguj statistiku umesto svake stavke
                            LogUtils.logNumericStats(
                                tag = "BudgetListScreen",
                                values = sortedExpenses.map { it.amount },
                                prefix = "RASHODI:"
                            )
                            
                            sortedExpenses
                        } else {
                            // Sortiranje prihoda
                            LogUtils.i("BudgetListScreen", "Pripremam prihode za prikaz, ukupno: ${incomes.size}")
                            
                            // Loguj detaljno samo ako je potrebno i samo prvih nekoliko stavki
                            LogUtils.logCollection(
                                tag = "BudgetListScreen",
                                collection = incomes,
                                prefix = "INCOMES",
                                transform = { income -> 
                                    "id=${income.id}, amount=${income.amount}, date=${income.date}, category=${income.category}" 
                                }
                            )
                            
                            val sortedIncomes = when(sortOrder) {
                                SortOrder.DATE_DESC -> incomes.sortedByDescending { it.getDateObject()?.time ?: 0L }
                                SortOrder.AMOUNT_DESC -> incomes.sortedByDescending { it.amount }
                                SortOrder.AMOUNT_ASC -> incomes.sortedBy { it.amount }
                            }
                            
                            LogUtils.i("BudgetListScreen", "Sortirano ${sortedIncomes.size} prihoda po ${sortOrder.name}")
                            
                            // Loguj statistiku umesto svake stavke
                            LogUtils.logNumericStats(
                                tag = "BudgetListScreen",
                                values = sortedIncomes.map { it.amount },
                                prefix = "PRIHODI:"
                            )
                            
                            sortedIncomes
                        }
                        
                        if (transactions.isNotEmpty()) {
                            LogUtils.i("BudgetListScreen", "Prikazujem ${transactions.size} transakcija")
                            
                            // Ne loguj svaku pojedinačnu transakciju pri prikazu
                            // samo prikaži komponente
                            transactions.forEach { transaction ->
                                when (transaction) {
                                    is Expense -> ExpenseListItem(
                                        expense = transaction,
                                        formatAmount = { amount -> budgetsViewModel.formatAmount(amount) },
                                        accounts = accounts
                                    )
                                    is Income -> IncomeListItem(
                                        income = transaction,
                                        formatAmount = { amount -> budgetsViewModel.formatAmount(amount) },
                                        accounts = accounts
                                    )
                                    else -> {
                                        LogUtils.e("BudgetListScreen", "Nepoznat tip transakcije: ${transaction.javaClass.name}")
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        } else {
                            LogUtils.i("BudgetListScreen", "Nema transakcija za prikaz za tab: $selectedTabIndex")
                            // Prikaz za prazan ekran
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val emptyMessage = if (selectedTabIndex == 0) {
                                        "Нема евидентираних трошкова у овом периоду"
                                    } else {
                                        "Нема евидентираних прихода у овом периоду"
                                    }
                                    
                                    val emptyDetailsMessage = if (selectedTabIndex == 0) {
                                        "Кликните на + дугме да додате трошак"
                                    } else {
                                        "Кликните на + дугме да додате приход"
                                    }
                                    
                                    Text(
                                        text = emptyMessage,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Text(
                                        text = emptyDetailsMessage,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.7f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                    1 -> {
                        // Prihodi
                        // Prikaz ukupnog budžeta
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF303436)
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 4.dp
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                // Naslov sa dropdown filterom za račun
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showAccountsDropdown = true },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = selectedAccount,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Одабери рачун",
                                        tint = Color.White
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                // Стање рачуна уместо буџета
                                val selectedAccountBalance = if (selectedAccountId != null) {
                                    accounts.find { it.id == selectedAccountId }?.balance ?: 0.0
                                } else {
                                    accounts.sumOf { it.balance }
                                }
                                
                                Text(
                                    text = "Тренутно стање: ${budgetsViewModel.formatAmount(selectedAccountBalance)}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Додајемо прогрес бар буџета који се може кликнути
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // За приходе не приказујемо прогрес бар, већ само информацију
                                Text(
                                    text = "Укупно примљено за ${periodDisplayText}: ${budgetsViewModel.formatAmount(totalIncomeReceived)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Opcije za sortiranje
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { 
                                    sortOrder = when(sortOrder) {
                                        SortOrder.DATE_DESC -> SortOrder.AMOUNT_DESC
                                        SortOrder.AMOUNT_DESC -> SortOrder.AMOUNT_ASC
                                        SortOrder.AMOUNT_ASC -> SortOrder.DATE_DESC
                                    }
                                }
                            ) {
                                val sortText = when(sortOrder) {
                                    SortOrder.DATE_DESC -> "Сортирај по: Датуму"
                                    SortOrder.AMOUNT_DESC -> "Сортирај по: Износу (↓)"
                                    SortOrder.AMOUNT_ASC -> "Сортирај по: Износу (↑)"
                                }
                                Text(
                                    text = sortText,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Prikazujemo listu TRANSAKCIJA, ne budžeta
                        val transactions = if (selectedTabIndex == 0) {
                            // Sortiranje troškova
                            LogUtils.i("BudgetListScreen", "Pripremam troškove za prikaz, ukupno: ${expenses.size}")
                            
                            // Loguj detaljno samo ako je potrebno i samo prvih nekoliko stavki
                            LogUtils.logCollection(
                                tag = "BudgetListScreen",
                                collection = expenses,
                                prefix = "EXPENSES",
                                transform = { expense -> 
                                    "id=${expense.id}, amount=${expense.amount}, date=${expense.date}, category=${expense.category}" 
                                }
                            )
                            
                            val sortedExpenses = when(sortOrder) {
                                SortOrder.DATE_DESC -> expenses.sortedByDescending { it.getDateObject()?.time ?: 0L }
                                SortOrder.AMOUNT_DESC -> expenses.sortedByDescending { it.amount }
                                SortOrder.AMOUNT_ASC -> expenses.sortedBy { it.amount }
                            }
                            
                            LogUtils.i("BudgetListScreen", "Sortirano ${sortedExpenses.size} troškova po ${sortOrder.name}")
                            
                            // Loguj statistiku umesto svake stavke
                            LogUtils.logNumericStats(
                                tag = "BudgetListScreen",
                                values = sortedExpenses.map { it.amount },
                                prefix = "RASHODI:"
                            )
                            
                            sortedExpenses
                        } else {
                            // Sortiranje prihoda
                            LogUtils.i("BudgetListScreen", "Pripremam prihode za prikaz, ukupno: ${incomes.size}")
                            
                            // Loguj detaljno samo ako je potrebno i samo prvih nekoliko stavki
                            LogUtils.logCollection(
                                tag = "BudgetListScreen",
                                collection = incomes,
                                prefix = "INCOMES",
                                transform = { income -> 
                                    "id=${income.id}, amount=${income.amount}, date=${income.date}, category=${income.category}" 
                                }
                            )
                            
                            val sortedIncomes = when(sortOrder) {
                                SortOrder.DATE_DESC -> incomes.sortedByDescending { it.getDateObject()?.time ?: 0L }
                                SortOrder.AMOUNT_DESC -> incomes.sortedByDescending { it.amount }
                                SortOrder.AMOUNT_ASC -> incomes.sortedBy { it.amount }
                            }
                            
                            LogUtils.i("BudgetListScreen", "Sortirano ${sortedIncomes.size} prihoda po ${sortOrder.name}")
                            
                            // Loguj statistiku umesto svake stavke
                            LogUtils.logNumericStats(
                                tag = "BudgetListScreen",
                                values = sortedIncomes.map { it.amount },
                                prefix = "PRIHODI:"
                            )
                            
                            sortedIncomes
                        }
                        
                        if (transactions.isNotEmpty()) {
                            LogUtils.i("BudgetListScreen", "Prikazujem ${transactions.size} transakcija")
                            
                            // Ne loguj svaku pojedinačnu transakciju pri prikazu
                            // samo prikaži komponente
                            transactions.forEach { transaction ->
                                when (transaction) {
                                    is Expense -> ExpenseListItem(
                                        expense = transaction,
                                        formatAmount = { amount -> budgetsViewModel.formatAmount(amount) },
                                        accounts = accounts
                                    )
                                    is Income -> IncomeListItem(
                                        income = transaction,
                                        formatAmount = { amount -> budgetsViewModel.formatAmount(amount) },
                                        accounts = accounts
                                    )
                                    else -> {
                                        LogUtils.e("BudgetListScreen", "Nepoznat tip transakcije: ${transaction.javaClass.name}")
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        } else {
                            LogUtils.i("BudgetListScreen", "Nema transakcija za prikaz za tab: $selectedTabIndex")
                            // Prikaz za prazan ekran
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val emptyMessage = if (selectedTabIndex == 0) {
                                        "Нема евидентираних трошкова у овом периоду"
                                    } else {
                                        "Нема евидентираних прихода у овом периоду"
                                    }
                                    
                                    val emptyDetailsMessage = if (selectedTabIndex == 0) {
                                        "Кликните на + дугме да додате трошак"
                                    } else {
                                        "Кликните на + дугме да додате приход"
                                    }
                                    
                                    Text(
                                        text = emptyMessage,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Text(
                                        text = emptyDetailsMessage,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.7f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Prikazujemo DatePicker za izbor datuma u dnevnom režimu
            if (showDatePicker) {
                // Dodajem suppress anotaciju za eksperimentalni API
                @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                val datePickerState = androidx.compose.material3.rememberDatePickerState(
                    initialSelectedDateMillis = selectedDate.time
                )
                
                @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                androidx.compose.material3.DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    budgetViewModel.setSelectedDate(java.util.Date(millis))
                                }
                                showDatePicker = false
                            }
                        ) {
                            Text("Potvrdi")
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { showDatePicker = false }
                        ) {
                            Text("Odustani")
                        }
                    }
                ) {
                    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                    androidx.compose.material3.DatePicker(
                        state = datePickerState
                    )
                }
            }
            
            // Indikator za učitavanje - prikazuje se kada je isLoading=true, a nije u stanju isRefreshing
            if (isLoading && !isRefreshing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x80000000)), // Polu-providna tamna pozadina
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // Indikator osvežavanja
            StandardPullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
    
    // Dialog za podešavanje budžetskog limita
    if (showBudgetLimitDialog) {
        AlertDialog(
            onDismissRequest = { showBudgetLimitDialog = false },
            title = { Text("Подесите буџетски лимит") },
            text = {
                Column {
                    Text("Унесите максимални износ који желите да потрошите за ${periodDisplayText}.")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = budgetLimitInput,
                        onValueChange = { budgetLimitInput = it },
                        label = { Text("Износ") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            val limitValue = budgetLimitInput.toDoubleOrNull()
                            if (limitValue != null && limitValue > 0) {
                                budgetViewModel.saveBudgetLimit(limitValue)
                                showBudgetLimitDialog = false
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Унесите валидан износ већи од нуле")
                                }
                            }
                        } catch (_: Exception) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Грешка при чувању буџетског лимита")
                            }
                        }
                    }
                ) {
                    Text("Сачувај")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBudgetLimitDialog = false }) {
                    Text("Откажи")
                }
            }
        )
    }
}

// Dodajemo enum za sortiranje
enum class SortOrder {
    DATE_DESC, AMOUNT_DESC, AMOUNT_ASC
}

@Composable
fun ExpenseListItem(
    expense: Expense,
    formatAmount: (Double) -> String,
    accounts: List<Account>
) {
    val account = accounts.find { it.id == expense.accountId }
    // Koristimo getFormattedDate metodu za prikazivanje datuma
    val dateFormatted = expense.getFormattedDate()
    
    // Koristimo CategoryManager za dobijanje imena kategorije
    val context = LocalContext.current
    val categoryManager = CategoryManager.getInstance(context)
    val categoryName = categoryManager.getExpenseCategoryDisplayName(expense.category)
    
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF303436)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Levi deo - kategorija i opis
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                
                if (expense.description.isNotEmpty()) {
                    Text(
                        text = expense.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                
                Text(
                    text = dateFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
                
                if (accounts.size > 1) {
                    Text(
                        text = account?.name ?: "Непознат рачун",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
            
            // Desni deo - iznos
            Text(
                text = formatAmount(expense.amount),
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFFF9800), // Narandžasta za troškove
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun IncomeListItem(
    income: Income,
    formatAmount: (Double) -> String,
    accounts: List<Account>
) {
    val account = accounts.find { it.id == income.accountId }
    // Koristimo getFormattedDate metodu za prikazivanje datuma
    val dateFormatted = income.getFormattedDate()
    
    // Koristimo CategoryManager za dobijanje imena kategorije
    val context = LocalContext.current
    val categoryManager = CategoryManager.getInstance(context)
    val categoryName = categoryManager.getIncomeCategoryDisplayName(income.category)
    
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF303436)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Levi deo - kategorija i opis
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                
                if (income.description.isNotEmpty()) {
                    Text(
                        text = income.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                
                Text(
                    text = dateFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
                
                if (accounts.size > 1) {
                    Text(
                        text = account?.name ?: "Непознат рачун",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
            
            // Desni deo - iznos
            Text(
                text = formatAmount(income.amount),
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF4CAF50), // Zelena za prihode
                fontWeight = FontWeight.Bold
            )
        }
    }
} 