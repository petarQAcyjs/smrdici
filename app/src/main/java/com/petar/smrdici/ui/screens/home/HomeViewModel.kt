package com.petar.smrdici.ui.screens.home

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
import com.petar.smrdici.data.repository.EventRepository
import com.petar.smrdici.data.repository.TransactionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlin.math.abs

// Data class for expense category summaries
data class CategorySummary(
    val categoryName: String,
    val iconName: String,
    val color: Color,
    val amount: Double,
    val percentage: Double
)

class HomeViewModel() : ViewModel() {
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
    
    companion object {
        private const val TAG = "HomeViewModel"
    }
    
    init {
        loadTodayEvents()
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
                loadTodayEvents()
                _syncStatus.value = SyncStatus.Success("Догађаји успешно синхронизовани")
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.Error("Грешка при синхронизацији: ${e.message}")
            }
        }
    }
    
    // Function to load expense data for the current period
    fun loadCurrentPeriodExpenses() {
        viewModelScope.launch {
            _isLoadingExpenseData.value = true
            try {
                // Get current month start and end dates
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val startDate = calendar.time
                
                calendar.add(Calendar.MONTH, 1)
                calendar.add(Calendar.DAY_OF_MONTH, -1)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val endDate = calendar.time
                
                Log.d("HomeViewModel", "Loading expenses from ${formatDate(startDate)} to ${formatDate(endDate)}")
                
                // Get transactions for the current period
                val transactions = transactionRepository.getTransactionsBetween(startDate, endDate)
                
                // Filter expenses only
                val expenses = transactions.filter { it is Expense }
                
                // Calculate total amount
                val totalAmount = expenses.sumOf { abs(it.amount) }
                
                // Group by category and create summaries
                val categorySummaries = expenses
                    .groupBy { it.category ?: "Other" }
                    .map { (category, categoryTransactions) ->
                        val amount = categoryTransactions.sumOf { abs(it.amount) }
                        val percentage = if (totalAmount > 0) (amount / totalAmount) * 100 else 0.0
                        
                        // Find appropriate icon name for the category
                        val iconName = try {
                            // Try to match with enum category
                            val enumCategory = ExpenseCategory.valueOf(category)
                            when (enumCategory) {
                                ExpenseCategory.FOOD -> "Restaurant"
                                ExpenseCategory.TRANSPORTATION -> "DirectionsCar"
                                ExpenseCategory.ENTERTAINMENT -> "SportsEsports"
                                ExpenseCategory.UTILITIES -> "Receipt"
                                ExpenseCategory.RENT -> "Home"
                                ExpenseCategory.SHOPPING -> "ShoppingCart"
                                ExpenseCategory.HEALTH -> "LocalHospital"
                                ExpenseCategory.EDUCATION -> "School"
                                ExpenseCategory.TRAVEL -> "Flight"
                                else -> "Receipt"
                            }
                        } catch (e: IllegalArgumentException) {
                            // Default icon for custom categories
                            "Receipt"
                        }
                        
                        CategorySummary(
                            categoryName = category,
                            iconName = iconName,
                            color = getExpenseCategoryColor(category),
                            amount = amount,
                            percentage = percentage
                        )
                    }
                    .sortedByDescending { it.amount }
                
                Log.d("HomeViewModel", "Loaded expenses by category: ${categorySummaries.size} categories")
                
                _expenseChartData.value = categorySummaries
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading expense data", e)
                _expenseChartData.value = emptyList()
            } finally {
                _isLoadingExpenseData.value = false
            }
        }
    }
    
    // Load expense data for the current period
    fun loadExpenseData() {
        viewModelScope.launch {
            try {
                _isLoadingExpenseData.value = true
                
                // Get start and end dates for the current month
                val calendar = Calendar.getInstance()
                val startDate = calendar.apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }.time
                
                val endDate = calendar.apply {
                    set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                }.time
                
                // Get transactions for the current month
                val transactions = transactionRepository.getTransactionsBetween(startDate, endDate)
                
                // Filter expense transactions
                val expenses = transactions.filter { it is Expense }
                
                // Calculate total for percentages
                val totalAmount = expenses.sumOf { Math.abs(it.amount) }
                
                // Group by category and create summaries
                val categorySummaries = expenses
                    .groupBy { it.category ?: "Other" }
                    .map { (category, categoryTransactions) ->
                        val amount = categoryTransactions.sumOf { Math.abs(it.amount) }
                        val percentage = if (totalAmount > 0) (amount / totalAmount) * 100 else 0.0
                        
                        // Find appropriate icon name for the category
                        val iconName = try {
                            // Try to match with enum category
                            val enumCategory = ExpenseCategory.valueOf(category)
                            when (enumCategory) {
                                ExpenseCategory.FOOD -> "Restaurant"
                                ExpenseCategory.TRANSPORTATION -> "DirectionsCar"
                                ExpenseCategory.ENTERTAINMENT -> "SportsEsports"
                                ExpenseCategory.UTILITIES -> "Receipt"
                                ExpenseCategory.RENT -> "Home"
                                ExpenseCategory.SHOPPING -> "ShoppingCart"
                                ExpenseCategory.HEALTH -> "LocalHospital"
                                ExpenseCategory.EDUCATION -> "School"
                                ExpenseCategory.TRAVEL -> "Flight"
                                else -> "Receipt"
                            }
                        } catch (e: IllegalArgumentException) {
                            // Default icon for custom categories
                            "Receipt"
                        }
                        
                        CategorySummary(
                            categoryName = category,
                            iconName = iconName,
                            color = getExpenseCategoryColor(category),
                            amount = amount,
                            percentage = percentage
                        )
                    }
                    .sortedByDescending { it.amount }
                
                _expenseChartData.value = categorySummaries
                _isLoadingExpenseData.value = false
                
                Log.d(TAG, "Loaded expense data: ${categorySummaries.size} categories")
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
    class Factory() : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                return HomeViewModel() as T
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