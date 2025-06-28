package com.petar.smrdici.ui.screens.home

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.petar.smrdici.data.model.Event
import com.petar.smrdici.data.model.Expense
import com.petar.smrdici.data.model.ExpenseCategory
import com.petar.smrdici.data.model.Transaction
import com.petar.smrdici.data.model.getExpenseCategoryColor
import com.petar.smrdici.data.model.CategoryManager
import com.petar.smrdici.data.repository.EventRepository
import com.petar.smrdici.data.repository.TransactionRepository
import com.petar.smrdici.data.repository.SettingsRepository
import com.petar.smrdici.ui.screens.settings.Period
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlin.math.abs
import org.threeten.bp.LocalDate
import org.threeten.bp.YearMonth
import org.threeten.bp.format.DateTimeFormatter

// Data class for expense category summaries
data class CategorySummary(
    val categoryName: String,
    val iconName: String,
    val color: Color,
    val amount: Double,
    val percentage: Double
)

class HomeViewModel(
    private val context: Context,
    private val settingsRepository: SettingsRepository = SettingsRepository.getInstance(context)
) : ViewModel() {
    // Лења иницијализација EventRepository
    private val eventRepository = EventRepository.getInstance()
    
    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events
    
    private val _todayEvents = MutableStateFlow<List<Event>>(emptyList())
    val todayEvents: StateFlow<List<Event>> = _todayEvents
    
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Initial)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus
    
    private val auth = FirebaseAuth.getInstance()
    
    // Кеш за учитане догађаје
    private val loadedEventsCache = mutableMapOf<String, List<Event>>()
    
    // Застава за спречавање вишеструких истовремених учитавања
    var isLoadingEvents by mutableStateOf(false)
        private set
    
    // Job за праћење текућег учитавања
    private var currentLoadJob: Job? = null
    
    // Expense chart data
    private val _expenseChartData = MutableStateFlow<List<CategorySummary>>(emptyList())
    val expenseChartData: StateFlow<List<CategorySummary>> = _expenseChartData
    
    private val _isLoadingExpenseData = MutableStateFlow(false)
    val isLoadingExpenseData: StateFlow<Boolean> = _isLoadingExpenseData
    
    // Lazy initialization of TransactionRepository
    private val transactionRepository = TransactionRepository.getInstance()
    
    // Predefined colors for category cards - same as in FinanceViewModel
    private val categoryColors = listOf(
        Color(0xFFFF5252),  // Red
        Color(0xFFFF9800),  // Orange
        Color(0xFFFFEB3B),  // Yellow
        Color(0xFF4CAF50),  // Green
        Color(0xFF2196F3),  // Blue
        Color(0xFF673AB7),  // Purple
        Color(0xFFE91E63),  // Pink
        Color(0xFF009688),  // Teal
        Color(0xFF795548),  // Brown
        Color(0xFF607D8B),  // Blue Grey
        Color(0xFFFFA000),  // Amber
        Color(0xFF00BCD4),  // Cyan
        Color(0xFF3F51B5)   // Indigo
    )
    
    // Category icon mapping - same as in FinanceViewModel
    private val categoryIconMapping = mapOf(
        "GROCERIES" to "LocalGroceryStore",
        "UTILITIES" to "Receipt",
        "RENT" to "Home",
        "TRANSPORTATION" to "DirectionsCar",
        "ENTERTAINMENT" to "SportsEsports",
        "HEALTH" to "LocalHospital",
        "EDUCATION" to "School",
        "CLOTHING" to "Checkroom",
        "TRAVEL" to "Flight",
        "FOOD" to "Restaurant",
        "COFFEE" to "LocalCafe",
        "ALCOHOL" to "LocalBar",
        "CIGARETTES" to "SmokingRooms",
        "GIFTS" to "CardGiftcard",
        "SUBSCRIPTIONS" to "Subscriptions",
        "ELECTRONICS" to "Devices",
        "HOME" to "Home",
        "BEAUTY" to "Face",
        "PETS" to "Pets",
        "SPORTS" to "FitnessCenter",
        "INVESTMENTS" to "TrendingUp",
        "DEBT" to "CreditCard",
        "INSURANCE" to "Security",
        "TAXES" to "Receipt",
        "CHARITY" to "Favorite",
        "BUSINESS" to "BusinessCenter",
        "CHILDREN" to "ChildCare",
        "PERSONAL_CARE" to "Face",
        "SHOPPING" to "ShoppingCart",
        "MAINTENANCE" to "Handyman",
        "SERVICES" to "Receipt",
        "SAVINGS" to "Savings",
        "LOAN" to "CreditCard",
        "RAMPA" to "DirectionsCar",
        "PARKING" to "DirectionsCar",
        "OTHER" to "Receipt"
    )
    
    companion object {
        private const val TAG = "HomeViewModel"
    }
    
    init {
        loadTodayEvents()
        loadExpenseData()
    }
    
    // Function to refresh expense data (similar to FinanceViewModel's refreshData)
    fun refreshExpenseData() {
        loadExpenseData()
    }
    
    private fun loadTodayEvents() {
        // Cancel any previous job
        currentLoadJob?.cancel()
        
        isLoadingEvents = true
        
        currentLoadJob = viewModelScope.launch {
            try {
                // Постављамо почетак и крај дана
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
                
                Log.d("HomeViewModel", """
                    Тражим догађаје за данас:
                    - Почетак дана: ${formatDate(today)}
                    - Крај дана: ${formatDate(endOfDay)}
                """.trimIndent())
                
                // Проверавамо кеш
                val cacheKey = "${formatDate(today)}_${formatDate(endOfDay)}"
                if (loadedEventsCache.containsKey(cacheKey)) {
                    Log.d("HomeViewModel", "Користим кеширане догађаје")
                    val cachedEvents = loadedEventsCache[cacheKey]!!
                    updateTodayEvents(cachedEvents)
                    return@launch
                }
                
                // Учитавамо све догађаје за данас
                eventRepository.getEvents(today, endOfDay)
                    .collect { events ->
                        Log.d("HomeViewModel", "Учитано ${events.size} догађаја")
                        
                        // Кеширамо учитане догађаје
                        loadedEventsCache[cacheKey] = events
                        
                        // Филтрирамо и приказујемо само данашње догађаје
                        updateTodayEvents(events)
                    }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    Log.d("HomeViewModel", "Учитавање догађаја отказано")
                } else {
                    Log.e("HomeViewModel", "Грешка при учитавању догађаја", e)
                }
            } finally {
                isLoadingEvents = false
            }
        }.also { currentLoadJob = it }
    }
    
    private fun updateTodayEvents(allEvents: List<Event>) {
        val currentTime = Calendar.getInstance().time
        
        // Постављамо почетак и крај дана
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
            
            // Додајемо детаљно логовање за сваки догађај
            Log.d("HomeViewModel", """
                Проверавам догађај:
                - Наслов: ${event.title}
                - Време почетка: ${formatDate(eventDate)}
                - Време краја: ${formatDate(eventEndTime)}
                - Почетак дана: ${formatDate(startOfDay)}
                - Крај дана: ${formatDate(endOfDay)}
                - Тренутно време: ${formatDate(currentTime)}
                - Целодневни догађај: ${event.allDay}
            """.trimIndent())
            
            // Проверавамо да ли је догађај данас
            val isToday = !eventDate.before(startOfDay) && !eventDate.after(endOfDay)
            
            // За целодневне догађаје, проверавамо само да ли су данас
            // За остале догађаје, проверавамо да ли су данас и још нису завршени
            val isValid = if (event.allDay) {
                isToday
            } else {
                isToday && eventEndTime >= currentTime
            }
            
            Log.d("HomeViewModel", """
                Резултат провере за догађај ${event.title}:
                - Је данас: $isToday
                - Је активан: ${eventEndTime >= currentTime}
                - Је валидан: $isValid
            """.trimIndent())
            
            isValid
        }.sortedBy { it.startTime?.toDate() }
        
        Log.d("HomeViewModel", """
            Филтрирање догађаја:
            - Укупно догађаја: ${allEvents.size}
            - Данашњих активних догађаја: ${todayEvents.size}
            - Тренутно време: ${formatDate(currentTime)}
            - Детаљи догађаја:
            ${todayEvents.joinToString("\n") { "- ${it.title} (${formatDate(it.startTime?.toDate())})" }}
        """.trimIndent())
        
        // Обавезно проверавамо да ли је листа заиста различита пре ажурирања
        if (_todayEvents.value != todayEvents) {
            _todayEvents.value = todayEvents
        }
    }
    
    private fun formatDate(date: Date?): String {
        return date?.let { 
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(it) 
        } ?: "null"
    }
    
    // Функција за синхронизацију догађаја
    fun syncEvents() {
        if (_syncStatus.value == SyncStatus.Syncing) {
            return
        }
        
        _syncStatus.value = SyncStatus.Syncing
        
        viewModelScope.launch {
            try {
                // Sync events from the event repository
                loadTodayEvents()
                
                // Also refresh expense data when syncing
                loadExpenseData()
                
                _syncStatus.value = SyncStatus.Success("Подаци успешно синхронизовани")
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.Error("Грешка при синхронизацији: ${e.message}")
            }
        }
    }
    
    // Function to load expense data for the current period
    fun loadExpenseData() {
        viewModelScope.launch {
            try {
                _isLoadingExpenseData.value = true
                
                // Get the period setting from SettingsRepository
                val configuredPeriod = settingsRepository.period.first()
                val now = LocalDate.now()
                
                // Calculate start and end dates based on the configured period
                val (startDate, endDate) = when (configuredPeriod) {
                    Period.DAILY -> {
                        // Daily - just today
                        Pair(now, now)
                    }
                    Period.WEEKLY -> {
                        // Weekly - current week (starting Monday)
                        val currentWeekStart = now.minusDays(now.dayOfWeek.value.toLong() - 1)
                        Pair(currentWeekStart, currentWeekStart.plusDays(6))
                    }
                    Period.MONTHLY -> {
                        // Monthly - current month
                        val currentMonth = YearMonth.from(now)
                        Pair(currentMonth.atDay(1), currentMonth.atEndOfMonth())
                    }
                    Period.YEARLY -> {
                        // Yearly - current year
                        Pair(LocalDate.of(now.year, 1, 1), LocalDate.of(now.year, 12, 31))
                    }
                    Period.CUSTOM -> {
                        // Custom period based on start day setting
                        val startDay = settingsRepository.customPeriodStartDay.first()
                        val currentMonth = YearMonth.from(now)
                        
                        // Calculate the current period's start date
                        val periodStartDate = if (now.dayOfMonth >= startDay) {
                            currentMonth.atDay(startDay)
                        } else {
                            currentMonth.minusMonths(1).atDay(startDay)
                        }
                        
                        // End date is the day before start day in the next month
                        val periodEndDate = periodStartDate.plusMonths(1).minusDays(1)
                        
                        Pair(periodStartDate, periodEndDate)
                    }
                    else -> {
                        // Default to current month for "ALL" or any other case
                        val currentMonth = YearMonth.from(now)
                        Pair(currentMonth.atDay(1), currentMonth.atEndOfMonth())
                    }
                }
                
                Log.d(TAG, "Loading expenses from $startDate to $endDate with period setting: $configuredPeriod")
                
                // Convert LocalDate to Calendar for the repository
                val startCalendar = Calendar.getInstance().apply {
                    set(startDate.year, startDate.monthValue - 1, startDate.dayOfMonth, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                
                val endCalendar = Calendar.getInstance().apply {
                    set(endDate.year, endDate.monthValue - 1, endDate.dayOfMonth, 23, 59, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                
                // Get transactions from the unified TransactionRepository
                val allTransactions = transactionRepository.getTransactionsBetween(startCalendar.time, endCalendar.time)
                
                // Filter to only include expenses
                val expenses = allTransactions.filterIsInstance<Expense>()
                
                // Calculate total for percentages - use absolute values for expenses
                val totalAmount = expenses.sumOf { abs(it.amount) }
                
                Log.d(TAG, "Found ${expenses.size} expense transactions with total amount: $totalAmount")
                
                // Group by category and create summaries (exactly as in FinanceViewModel)
                val categorySummaries = expenses
                    .groupBy { it.category ?: "OTHER" }
                    .map { (category, categoryTransactions) ->
                        val categoryAmount = categoryTransactions.sumOf { abs(it.amount) }
                        val percentage = if (totalAmount > 0) (categoryAmount / totalAmount) * 100 else 0.0
                        
                        // Get a consistent color for this category using CategoryManager
                        val categoryManager = CategoryManager.getInstance(context)
                        val savedColorValue = categoryManager.getCategoryColor(category, true)
                        
                        // Use the saved color or fall back to the hash-based approach
                        val color = if (savedColorValue != null) {
                            Color(savedColorValue)
                        } else {
                            // Use a color based on category hash code for consistency
                            val colorIndex = abs(category.hashCode()) % categoryColors.size
                            categoryColors[colorIndex]
                        }
                        
                        // Get icon name for this category using the mapping
                        val iconName = categoryIconMapping[category] ?: "Receipt"
                        
                        // Get display name for the category - IMPORTANT: Use the exact same logic as FinanceViewModel
                        val displayName = try {
                            // Try to get the enum value and its display name
                            ExpenseCategory.valueOf(category).getDisplayName()
                        } catch (e: Exception) {
                            // If not a standard category, just use the category string directly
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
                    .sortedByDescending { it.amount }
                
                Log.d(TAG, "Created ${categorySummaries.size} category summaries: ${categorySummaries.map { "${it.categoryName}: ${it.amount}" }}")
                
                _expenseChartData.value = categorySummaries
                _isLoadingExpenseData.value = false
            } catch (e: Exception) {
                Log.e(TAG, "Error loading expense data", e)
                _expenseChartData.value = emptyList()
                _isLoadingExpenseData.value = false
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        currentLoadJob?.cancel()
    }
    
    // Додајемо Factory класу за креирање HomeViewModel са Context параметром
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

// Класа за праћење статуса синхронизације
sealed class SyncStatus {
    data object Initial : SyncStatus()
    data object Idle : SyncStatus()
    data object Syncing : SyncStatus()
    data class Success(val message: String) : SyncStatus()
    data class Error(val message: String) : SyncStatus()
} 