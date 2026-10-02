package com.petar.smrdici.ui.screens.home

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.petar.smrdici.data.model.CategoryManager
import com.petar.smrdici.data.model.Event
import com.petar.smrdici.data.model.Expense
import com.petar.smrdici.data.model.ExpenseCategory
import com.petar.smrdici.data.repository.AccountRepository
import com.petar.smrdici.data.repository.EventRepository
import com.petar.smrdici.data.repository.SettingsRepository
import com.petar.smrdici.data.repository.TransactionRepository
import com.petar.smrdici.ui.screens.settings.Period
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.threeten.bp.LocalDate
import org.threeten.bp.YearMonth
import org.threeten.bp.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// Data class for expense category summaries
data class CategorySummary(
    val categoryName: String,
    val iconName: String,
    val color: Color,
    val amount: Double,
    val percentage: Double
)

// Cache key data class for expense data
data class ExpenseCacheKey(
    val periodType: Period,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val customStartDay: Int = 1
)

sealed class SyncStatus {
    data object Initial : SyncStatus()
    data object Syncing : SyncStatus()
    data class Success(val message: String) : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

class HomeViewModel(
    context: Context,
    private val settingsRepository: SettingsRepository = SettingsRepository.getInstance(context)
) : ViewModel() {

    @SuppressLint("StaticFieldLeak")
    private val context: Context = context.applicationContext

    private val eventRepository = EventRepository.getInstance()
    private val transactionRepository = TransactionRepository.getInstance()
    private val accountRepository = AccountRepository.getInstance()

    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events

    private val _todayEvents = MutableStateFlow<List<Event>>(emptyList())
    val todayEvents: StateFlow<List<Event>> = _todayEvents

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Initial)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus

    private val loadedEventsCache = mutableMapOf<String, List<Event>>()
    private val expenseChartCache = mutableMapOf<ExpenseCacheKey, List<CategorySummary>>()
    private val expenseHistoryCache = mutableMapOf<ExpenseCacheKey, List<PeriodExpenses>>()

    private val cacheExpirationTime = 10 * 60 * 1000L
    private val cacheTimestamps = mutableMapOf<ExpenseCacheKey, Long>()
    private val cacheMutex = Mutex()

    var isLoadingEvents by mutableStateOf(false)
        private set

    private var currentLoadJob: Job? = null

    private val _expenseChartData = MutableStateFlow<List<CategorySummary>>(emptyList())
    val expenseChartData: StateFlow<List<CategorySummary>> = _expenseChartData

    private val _expenseHistoryData = MutableStateFlow<List<PeriodExpenses>>(emptyList())
    val expenseHistoryData: StateFlow<List<PeriodExpenses>> = _expenseHistoryData

    private val _isLoadingExpenseData = MutableStateFlow(false)
    val isLoadingExpenseData: StateFlow<Boolean> = _isLoadingExpenseData

    private val _isLoadingHistoryData = MutableStateFlow(false)
    val isLoadingHistoryData: StateFlow<Boolean> = _isLoadingHistoryData

    private val freshPeriodsCount = 2

    private val prefs = context.getSharedPreferences("expense_history_cache", Context.MODE_PRIVATE)

    private val categoryColors = listOf(
        Color(0xFFFF5252), Color(0xFFFF9800), Color(0xFFFFEB3B),
        Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFF673AB7),
        Color(0xFFE91E63), Color(0xFF009688), Color(0xFF795548),
        Color(0xFF607D8B), Color(0xFFFFA000), Color(0xFF00BCD4),
        Color(0xFF3F51B5)
    )

    private val categoryIconMapping = mapOf(
        "GROCERIES" to "LocalGroceryStore", "UTILITIES" to "Receipt", "RENT" to "Home",
        "TRANSPORTATION" to "DirectionsCar", "ENTERTAINMENT" to "SportsEsports",
        "HEALTH" to "LocalHospital", "EDUCATION" to "School", "CLOTHING" to "Checkroom",
        "TRAVEL" to "Flight", "FOOD" to "Restaurant", "COFFEE" to "LocalCafe",
        "ALCOHOL" to "LocalBar", "CIGARETTES" to "SmokingRooms", "GIFTS" to "CardGiftcard",
        "SUBSCRIPTIONS" to "Subscriptions", "ELECTRONICS" to "Devices", "HOME" to "Home",
        "BEAUTY" to "Face", "PETS" to "Pets", "SPORTS" to "FitnessCenter",
        "INVESTMENTS" to "TrendingUp", "DEBT" to "CreditCard", "INSURANCE" to "Security",
        "TAXES" to "Receipt", "CHARITY" to "Favorite", "BUSINESS" to "BusinessCenter",
        "CHILDREN" to "ChildCare", "PERSONAL_CARE" to "Face", "SHOPPING" to "ShoppingCart",
        "MAINTENANCE" to "Handyman", "SERVICES" to "Receipt", "SAVINGS" to "Savings",
        "LOAN" to "CreditCard", "RAMPA" to "DirectionsCar", "PARKING" to "DirectionsCar",
        "OTHER" to "Receipt"
    )

    companion object {
        private const val TAG = "HomeViewModel"
        private const val NUM_PERIODS_TO_SHOW = 8
    }

    init {
        loadDashboardDataSequentially()
    }

    private fun loadDashboardDataSequentially() {
        viewModelScope.launch {
            loadTodayEvents()
            loadExpenseData()
            loadExpenseHistoryData()
        }
    }

    fun refreshExpenseData() {
        viewModelScope.launch {
            cacheMutex.withLock {
                expenseChartCache.clear()
                expenseHistoryCache.clear()
                cacheTimestamps.clear()
            }
            prefs.edit { clear() }

            loadExpenseData()
            loadExpenseHistoryData()
        }
    }

    private fun loadTodayEvents() {
        currentLoadJob?.cancel()
        isLoadingEvents = true

        currentLoadJob = viewModelScope.launch {
            try {
                val calendar = Calendar.getInstance()
                val today = calendar.apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time

                val endOfDay = calendar.apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.time

                val cacheKey = "${formatDate(today)}_${formatDate(endOfDay)}"
                if (loadedEventsCache.containsKey(cacheKey)) {
                    val cachedEvents = loadedEventsCache[cacheKey]!!
                    updateTodayEvents(cachedEvents)
                    return@launch
                }

                eventRepository.getEvents(today, endOfDay)
                    .collect { events ->
                        loadedEventsCache[cacheKey] = events
                        updateTodayEvents(events)
                    }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e(TAG, "Error loading events", e)
                }
            } finally {
                isLoadingEvents = false
            }
        }.also { currentLoadJob = it }
    }

    private fun updateTodayEvents(allEvents: List<Event>) {
        val currentTime = Calendar.getInstance().time
        val calendar = Calendar.getInstance()

        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.time

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfDay = calendar.time

        val todayEvents = allEvents.filter { event ->
            val eventDate = event.startTime?.toDate() ?: return@filter false
            val eventEndTime = event.endTime?.toDate() ?: eventDate
            val isToday = !eventDate.before(startOfDay) && !eventDate.after(endOfDay)

            if (event.allDay) isToday else isToday && eventEndTime >= currentTime
        }.sortedBy { it.startTime?.toDate() }

        if (_todayEvents.value != todayEvents) {
            _todayEvents.value = todayEvents
        }
    }

    private fun formatDate(date: Date?): String {
        return date?.let {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(it)
        } ?: "null"
    }

    fun syncEvents() {
        if (_syncStatus.value == SyncStatus.Syncing) return
        _syncStatus.value = SyncStatus.Syncing

        viewModelScope.launch {
            try {
                loadTodayEvents()
                refreshExpenseData()
                _syncStatus.value = SyncStatus.Success("Подаци успешно синхронизовани")
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.Error("Грешка при синхронизацији: ${e.message}")
            }
        }
    }

    fun loadExpenseData() {
        viewModelScope.launch {
            try {
                _isLoadingExpenseData.value = true

                val configuredPeriod = settingsRepository.period.first()
                val now = LocalDate.now()
                val customStartDay = settingsRepository.customPeriodStartDay.first()

                val allAccounts = try {
                    accountRepository.accounts.first()
                } catch (_: Exception) {
                    emptyList()
                }

                val defaultAccount = allAccounts.find { acc -> acc.isDefault } ?: allAccounts.firstOrNull()
                val defaultAccountId = defaultAccount?.id

                val (startDate, endDate) = when (configuredPeriod) {
                    Period.DAILY -> Pair(now, now)
                    Period.WEEKLY -> {
                        val currentWeekStart = now.minusDays(now.dayOfWeek.value.toLong() - 1)
                        Pair(currentWeekStart, currentWeekStart.plusDays(6))
                    }
                    Period.MONTHLY -> {
                        val currentMonth = YearMonth.from(now)
                        Pair(currentMonth.atDay(1), currentMonth.atEndOfMonth())
                    }
                    Period.YEARLY -> Pair(LocalDate.of(now.year, 1, 1), LocalDate.of(now.year, 12, 31))
                    Period.CUSTOM -> {
                        val currentMonth = YearMonth.from(now)
                        val periodStartDate = if (now.dayOfMonth >= customStartDay) {
                            currentMonth.atDay(customStartDay)
                        } else {
                            currentMonth.minusMonths(1).atDay(customStartDay)
                        }
                        val periodEndDate = periodStartDate.plusMonths(1).minusDays(1)
                        Pair(periodStartDate, periodEndDate)
                    }
                    else -> {
                        val currentMonth = YearMonth.from(now)
                        Pair(currentMonth.atDay(1), currentMonth.atEndOfMonth())
                    }
                }

                val cacheKey = ExpenseCacheKey(
                    periodType = configuredPeriod,
                    startDate = startDate,
                    endDate = endDate,
                    customStartDay = if (configuredPeriod == Period.CUSTOM) customStartDay else 1
                )

                val currentTime = System.currentTimeMillis()
                var categorySummaries: List<CategorySummary>? = null

                cacheMutex.withLock {
                    val cacheTimestamp = cacheTimestamps[cacheKey] ?: 0L
                    if (currentTime - cacheTimestamp < cacheExpirationTime && expenseChartCache.containsKey(cacheKey)) {
                        categorySummaries = expenseChartCache[cacheKey]
                    }
                }

                if (categorySummaries == null) {
                    categorySummaries = withContext(Dispatchers.Default) {
                        // Ciljani opseg za Firestore upit uz tampon od 2 dana radi vremenskih zona
                        val queryStart = Calendar.getInstance().apply {
                            set(startDate.year, startDate.monthValue - 1, startDate.dayOfMonth, 0, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                            add(Calendar.DAY_OF_MONTH, -2)
                        }.time

                        val queryEnd = Calendar.getInstance().apply {
                            set(endDate.year, endDate.monthValue - 1, endDate.dayOfMonth, 23, 59, 59)
                            set(Calendar.MILLISECOND, 999)
                            add(Calendar.DAY_OF_MONTH, 2)
                        }.time

                        val allTransactions = transactionRepository.getTransactionsBetween(queryStart, queryEnd)

                        val expenses = allTransactions
                            .filterIsInstance<Expense>()
                            .filter { expense -> defaultAccountId == null || expense.accountId == defaultAccountId }
                            .filter { expense ->
                                val expDate = try {
                                    val parts = expense.date.split("-")
                                    if (parts.size == 3) {
                                        LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
                                    } else {
                                        LocalDate.parse(expense.date)
                                    }
                                } catch (_: Exception) {
                                    null
                                }
                                expDate != null && (expDate.isEqual(startDate) || expDate.isAfter(startDate)) &&
                                        (expDate.isEqual(endDate) || expDate.isBefore(endDate))
                            }

                        val totalAmount = expenses.sumOf { expense -> expense.amount }

                        val categoryManager = CategoryManager.getInstance(context)

                        val summaries = expenses
                            .groupBy { expense -> expense.category }
                            .map { (category, categoryTransactions) ->
                                val categoryAmount = categoryTransactions.sumOf { exp -> exp.amount }
                                val percentage = if (totalAmount > 0) (categoryAmount / totalAmount) * 100 else 0.0

                                val savedColorValue = categoryManager.getCategoryColor(category, true)
                                val color = if (savedColorValue != null) {
                                    Color(savedColorValue)
                                } else {
                                    val colorIndex = abs(category.hashCode()) % categoryColors.size
                                    categoryColors[colorIndex]
                                }

                                val iconName = categoryIconMapping[category] ?: "Receipt"
                                val displayName = try {
                                    ExpenseCategory.valueOf(category).getDisplayName()
                                } catch (_: Exception) {
                                    category
                                }

                                CategorySummary(
                                    categoryName = displayName,
                                    iconName = iconName,
                                    color = color,
                                    amount = categoryAmount,
                                    percentage = percentage
                                )
                            }
                            .sortedByDescending { summary -> summary.amount }

                        cacheMutex.withLock {
                            expenseChartCache[cacheKey] = summaries
                            cacheTimestamps[cacheKey] = currentTime
                        }

                        summaries
                    }
                }

                _expenseChartData.value = categorySummaries
                _isLoadingExpenseData.value = false
            } catch (e: Exception) {
                Log.e(TAG, "Error loading expense data", e)
                _expenseChartData.value = emptyList()
                _isLoadingExpenseData.value = false
            }
        }
    }

    fun loadExpenseHistoryData() {
        viewModelScope.launch {
            try {
                _isLoadingHistoryData.value = true

                val configuredPeriod = settingsRepository.period.first()
                val now = LocalDate.now()
                val customStartDay = settingsRepository.customPeriodStartDay.first()

                val allAccounts = try {
                    accountRepository.accounts.first()
                } catch (_: Exception) {
                    emptyList()
                }

                val defaultAccount = allAccounts.find { acc -> acc.isDefault } ?: allAccounts.firstOrNull()
                val defaultAccountId = defaultAccount?.id

                val cacheKey = ExpenseCacheKey(
                    periodType = configuredPeriod,
                    startDate = now.minusMonths((NUM_PERIODS_TO_SHOW - 1).toLong()),
                    endDate = now,
                    customStartDay = if (configuredPeriod == Period.CUSTOM) customStartDay else 1
                )

                val currentTime = System.currentTimeMillis()

                cacheMutex.withLock {
                    val cacheTimestamp = cacheTimestamps[cacheKey] ?: 0L
                    if (currentTime - cacheTimestamp < cacheExpirationTime && expenseHistoryCache.containsKey(cacheKey)) {
                        _expenseHistoryData.value = expenseHistoryCache[cacheKey]!!
                        _isLoadingHistoryData.value = false
                        return@launch
                    }
                }

                val periods = calculatePeriods(configuredPeriod, now, NUM_PERIODS_TO_SHOW, customStartDay)
                if (periods.isEmpty()) {
                    _isLoadingHistoryData.value = false
                    return@launch
                }

                val periodExpensesList = withContext(Dispatchers.Default) {
                    val freshPeriods = periods.take(freshPeriodsCount)
                    val olderPeriods = periods.drop(freshPeriodsCount)

                    val olderPeriodsToFetch = mutableListOf<Triple<LocalDate, LocalDate, String>>()
                    val olderResultsMap = mutableMapOf<String, Double>()

                    olderPeriods.forEach { (startDate, endDate, _) ->
                        val prefKey = "history_total_${configuredPeriod.name}_${startDate}_${endDate}"
                        if (prefs.contains(prefKey)) {
                            olderResultsMap[prefKey] = prefs.getFloat(prefKey, 0f).toDouble()
                        } else {
                            olderPeriodsToFetch.add(Triple(startDate, endDate, ""))
                        }
                    }

                    val periodsNeedingFetch = freshPeriods + olderPeriodsToFetch
                    val fetchedExpenses = if (periodsNeedingFetch.isNotEmpty()) {
                        val oldestStart = periodsNeedingFetch.minOfOrNull { triple -> triple.first }
                            ?: now
                        val newestEnd = periodsNeedingFetch.maxOfOrNull { triple -> triple.second }
                            ?: now

                        val startCalendar = Calendar.getInstance().apply {
                            set(oldestStart.year, oldestStart.monthValue - 1, oldestStart.dayOfMonth, 0, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        val endCalendar = Calendar.getInstance().apply {
                            set(newestEnd.year, newestEnd.monthValue - 1, newestEnd.dayOfMonth, 23, 59, 59)
                            set(Calendar.MILLISECOND, 999)
                        }

                        val allTransactions = transactionRepository.getTransactionsBetween(startCalendar.time, endCalendar.time)
                        allTransactions
                            .filterIsInstance<Expense>()
                            .filter { expense -> defaultAccountId == null || expense.accountId == defaultAccountId }
                    } else {
                        emptyList()
                    }

                    val newPeriodExpensesList = periods.map { (startDate, endDate, periodName) ->
                        val prefKey = "history_total_${configuredPeriod.name}_${startDate}_${endDate}"

                        val totalAmount = if (olderResultsMap.containsKey(prefKey)) {
                            olderResultsMap[prefKey] ?: 0.0
                        } else {
                            val expensesForPeriod = fetchedExpenses.filter { expense ->
                                val expDate = try {
                                    val parts = expense.date.split("-")
                                    if (parts.size == 3) {
                                        LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
                                    } else {
                                        LocalDate.parse(expense.date)
                                    }
                                } catch (_: Exception) {
                                    null
                                }
                                expDate != null && (expDate.isEqual(startDate) || expDate.isAfter(startDate)) &&
                                        (expDate.isEqual(endDate) || expDate.isBefore(endDate))
                            }

                            val calculatedSum = expensesForPeriod.sumOf { expense -> expense.amount }

                            if (olderPeriods.any { triple -> triple.first == startDate && triple.second == endDate }) {
                                prefs.edit { putFloat(prefKey, calculatedSum.toFloat()) }
                            }

                            calculatedSum
                        }

                        PeriodExpenses(
                            periodName = periodName,
                            totalAmount = totalAmount,
                            categoryExpenses = emptyList()
                        )
                    }

                    val reversed = newPeriodExpensesList.reversed()

                    cacheMutex.withLock {
                        expenseHistoryCache[cacheKey] = reversed
                        cacheTimestamps[cacheKey] = currentTime
                    }

                    reversed
                }

                _expenseHistoryData.value = periodExpensesList
                _isLoadingHistoryData.value = false

            } catch (e: Exception) {
                Log.e(TAG, "Error loading expense history data", e)
                _expenseHistoryData.value = emptyList()
                _isLoadingHistoryData.value = false
            }
        }
    }

    private fun calculatePeriods(
        periodType: Period,
        currentDate: LocalDate,
        count: Int,
        customStartDay: Int
    ): List<Triple<LocalDate, LocalDate, String>> {
        val periods = mutableListOf<Triple<LocalDate, LocalDate, String>>()
        val dateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
        val monthFormatter = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())

        for (i in 0 until count) {
            val (startDate, endDate, periodName) = when (periodType) {
                Period.DAILY -> {
                    val date = currentDate.minusDays(i.toLong())
                    val name = if (i == 0) "Данас" else if (i == 1) "Јуче" else date.format(dateFormatter)
                    Triple(date, date, name)
                }

                Period.WEEKLY -> {
                    val weekStart = currentDate.minusWeeks(i.toLong())
                        .minusDays((currentDate.dayOfWeek.value - 1).toLong())
                    val weekEnd = weekStart.plusDays(6)
                    val name = if (i == 0) "Ова недеља" else weekStart.format(dateFormatter)
                    Triple(weekStart, weekEnd, name)
                }

                Period.MONTHLY -> {
                    val month = YearMonth.from(currentDate).minusMonths(i.toLong())
                    val name = if (i == 0) "Овај месец" else month.format(monthFormatter)
                    Triple(month.atDay(1), month.atEndOfMonth(), name)
                }

                Period.YEARLY -> {
                    val year = currentDate.year - i
                    val name = if (i == 0) "Ова година" else year.toString()
                    Triple(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), name)
                }

                Period.CUSTOM -> {
                    val referenceDate = currentDate.minusMonths(i.toLong())
                    val currentMonth = YearMonth.from(referenceDate)

                    val periodStartDate = if (referenceDate.dayOfMonth >= customStartDay) {
                        currentMonth.atDay(customStartDay)
                    } else {
                        currentMonth.minusMonths(1).atDay(customStartDay)
                    }

                    val periodEndDate = periodStartDate.plusMonths(1).minusDays(1)
                    val name = if (i == 0) "Овај период" else periodStartDate.format(dateFormatter)

                    Triple(periodStartDate, periodEndDate, name)
                }

                else -> {
                    val month = YearMonth.from(currentDate).minusMonths(i.toLong())
                    Triple(month.atDay(1), month.atEndOfMonth(), month.format(monthFormatter))
                }
            }

            periods.add(Triple(startDate, endDate, periodName))
        }

        return periods
    }

    override fun onCleared() {
        super.onCleared()
        currentLoadJob?.cancel()

        viewModelScope.launch {
            cacheMutex.withLock {
                expenseChartCache.clear()
                expenseHistoryCache.clear()
                cacheTimestamps.clear()
            }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                return HomeViewModel(
                    context = context,
                    settingsRepository = SettingsRepository.getInstance(context)
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}