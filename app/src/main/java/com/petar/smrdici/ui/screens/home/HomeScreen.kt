@file:OptIn(ExperimentalMaterialApi::class)
package com.petar.smrdici.ui.screens.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.petar.smrdici.R
import com.petar.smrdici.data.model.Event
import com.petar.smrdici.data.model.ExpenseCategory
import com.petar.smrdici.data.model.getExpenseCategoryColor
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.components.PieChart
import com.petar.smrdici.ui.components.PieChartData
import com.petar.smrdici.ui.navigation.Screen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

// Data classes for stacked bar chart
data class CategoryExpense(
    val categoryName: String,
    val color: Color,
    val amount: Double,
    val percentage: Double
)

data class PeriodExpenses(
    val periodName: String,
    val totalAmount: Double,
    val categoryExpenses: List<CategoryExpense>
)

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel()
) {
    val authState = authViewModel.authState.collectAsState().value
    val context = LocalContext.current
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory(context))
    val todayEvents = homeViewModel.todayEvents.collectAsState().value
    val syncStatus = homeViewModel.syncStatus.collectAsState().value
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            homeViewModel.syncEvents() // This already calls loadExpenseData() in the updated syncEvents function
        }
    )
    
    // Observe sync status and show messages
    LaunchedEffect(syncStatus) {
        when (syncStatus) {
            is SyncStatus.Success -> {
                scope.launch {
                    snackbarHostState.showSnackbar(syncStatus.message)
                }
                isRefreshing = false
            }
            is SyncStatus.Error -> {
                scope.launch {
                    snackbarHostState.showSnackbar(syncStatus.message)
                }
                isRefreshing = false
            }
            else -> {}
        }
    }
    
    Scaffold(
        topBar = {
            AppHeader(
                title = "Почетна",
                showLogoutButton = false
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pullRefresh(pullRefreshState)
        ) {
            // Check if user is authenticated
            if (authState !is AuthState.Authenticated) {
                // Show login prompt
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Пријавите се да бисте видели садржај",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                // Show content for authenticated user
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Today's activities card
                    TodayActivitiesCard(
                        events = todayEvents,
                        onEventClick = { event ->
                            event.id?.let { _ ->
                                navController.navigate(Screen.Calendar.route)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .padding(vertical = 8.dp)
                    )
                    
                    // Add Expense Pie Chart Card
                    ExpensePieChartCard(
                        expenseData = homeViewModel.expenseChartData.collectAsState().value,
                        isLoading = homeViewModel.isLoadingExpenseData.collectAsState().value,
                        onCardClick = {
                            navController.navigate(Screen.Finance.route)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp)
                            .padding(vertical = 8.dp)
                    )
                    
                    // Add Expense History Card with stacked bar chart
                    ExpenseHistoryCard(
                        historyData = homeViewModel.expenseHistoryData.collectAsState().value,
                        isLoading = homeViewModel.isLoadingHistoryData.collectAsState().value,
                        onCardClick = {
                            navController.navigate(Screen.Finance.route)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp)
                            .padding(vertical = 8.dp)
                    )
                    
                    // Refresh expense data when returning to home screen
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                homeViewModel.refreshExpenseData()
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            
            // Pull to refresh indicator
            androidx.compose.material.pullrefresh.PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
fun TodayActivitiesCard(
    events: List<Event>,
    onEventClick: (Event) -> Unit,
    modifier: Modifier = Modifier,
    lottieResId: Int = R.raw.homeanimation
) {
    val cardColor = Color(0xFF3F8CFF)
    val textColor = Color.White
    
    val lottieComposition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(lottieResId)
    )
    
    val lottieAnimationState by animateLottieCompositionAsState(
        composition = lottieComposition,
        iterations = LottieConstants.IterateForever,
        isPlaying = true,
        speed = 1.0f,
        restartOnPlay = false
    )

    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
            ) {
                Text(
                    text = "Данашње активности",
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                if (events.isEmpty()) {
                    Text(
                        text = "Нема активности за данас",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor.copy(alpha = 0.9f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    val eventsToShow = events.take(3)
                    eventsToShow.forEach { event ->
                        EventItemCompact(
                            event = event,
                            onClick = { onEventClick(event) },
                            textColor = textColor
                        )
                    }
                    
                    if (events.size > 3) {
                        Text(
                            text = "Још ${events.size - 3} догађаја...",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.9f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            
            Box(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                LottieAnimation(
                    composition = lottieComposition,
                    progress = { lottieAnimationState },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    enableMergePaths = true
                )
            }
        }
    }
}

@Composable
fun EventItemCompact(
    event: Event,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(Color(event.color.toColorInt()))
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            event.startTime?.let { startTime ->
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val timeText = if (event.allDay) {
                    "Цео дан"
                } else {
                    val startTimeText = timeFormat.format(Date(startTime.seconds * 1000))
                    val endTimeText = event.endTime?.let {
                        timeFormat.format(Date(it.seconds * 1000))
                    } ?: ""
                    
                    if (endTimeText.isNotEmpty()) {
                        "$startTimeText - $endTimeText"
                    } else {
                        startTimeText
                    }
                }
                
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun ExpensePieChartCard(
    expenseData: List<CategorySummary>,
    isLoading: Boolean,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedCategories by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Updated title
            Text(
                text = "Трошкови у тренутном периоду",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (expenseData.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Нема података за приказ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Calculate total expenses
                val totalExpenses = expenseData.sumOf { it.amount }
                
                // Create pie chart data
                val pieChartData = expenseData.map { category ->
                    PieChartData.Slice(
                        value = category.amount.toFloat(),
                        color = category.color,
                        label = category.categoryName
                    )
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f), // Use weight to fill available space
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Pie chart on the left
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        PieChart(
                            data = pieChartData,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    // Compact legend on the right with percentages
                    Column(
                        modifier = Modifier
                            .weight(0.8f)
                            .fillMaxHeight()
                            .padding(start = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp) // Evenly space items
                    ) {
                        // Categories section in a scrollable column
                        Column(
                            modifier = Modifier
                                .weight(1f) // Take all available space
                                .verticalScroll(rememberScrollState()) // Make scrollable if needed
                        ) {
                            // Show categories
                            val categoriesToShow = if (expandedCategories) expenseData else expenseData.take(5)
                            
                            categoriesToShow.forEach { category ->
                                CompactCategoryLegendItem(
                                    category = category,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            
                            // Show "View more" if there are more categories
                            if (expenseData.size > 5 && !expandedCategories) {
                                Text(
                                    text = "... и још ${expenseData.size - 5}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .padding(top = 4.dp)
                                        .clickable { expandedCategories = true }
                                )
                            } else if (expandedCategories && expenseData.size > 5) {
                                Text(
                                    text = "Прикажи мање",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .padding(top = 4.dp)
                                        .clickable { expandedCategories = false }
                                )
                            }
                        }
                    }
                }
                
                // Total section - outside of Row but inside the main Column
                Column {
                    // Add divider before total
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Total expenses
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Укупно:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            text = String.format("%.2f", totalExpenses),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactCategoryLegendItem(
    category: CategorySummary,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Color indicator
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = category.color,
                    shape = CircleShape
                )
        )
        
        Spacer(modifier = Modifier.width(4.dp))
        
        // Category name and percentage
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = category.categoryName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format("%.1f%%", category.percentage),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.width(4.dp))
                
                Text(
                    text = String.format("%.0f", category.amount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ExpenseHistoryCard(
    historyData: List<PeriodExpenses>,
    isLoading: Boolean,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Card title
            Text(
                text = "Историја трошкова",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (historyData.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Нема података за приказ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Main chart area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    // Simple stacked bar chart implementation
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val availableWidth = size.width - (historyData.size * 20f)
                        val barWidth = min(40f, availableWidth / historyData.size)
                        val startX = (size.width - ((historyData.size - 1) * 20f + historyData.size * barWidth)) / 2
                        
                        // Find maximum value for scaling
                        val maxValue = historyData.maxOfOrNull { it.totalAmount } ?: 0.0
                        
                        historyData.forEachIndexed { index, periodData ->
                            val x = startX + index * (barWidth + 20f)
                            var yOffset = size.height
                            val scaleFactor = if (maxValue > 0) size.height / maxValue else 0.0
                            
                            // Draw each category segment in the bar
                            periodData.categoryExpenses.forEach { category ->
                                val segmentHeight = (category.amount * scaleFactor).toFloat()
                                
                                // Don't draw segments that are too small to be visible
                                if (segmentHeight >= 1f) {
                                    drawRect(
                                        color = category.color,
                                        topLeft = Offset(x, yOffset - segmentHeight),
                                        size = Size(barWidth, segmentHeight)
                                    )
                                    
                                    yOffset -= segmentHeight
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Period labels
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    historyData.forEach { periodData ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = periodData.periodName,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            Text(
                                text = String.format("%.0f", periodData.totalAmount),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Legend for top categories
                val allCategories = mutableListOf<CategoryExpense>()
                
                // Collect all unique categories
                historyData.forEach { period ->
                    period.categoryExpenses.forEach { category ->
                        if (allCategories.none { it.categoryName == category.categoryName }) {
                            allCategories.add(category)
                        }
                    }
                }
                
                // Sort by total amount across all periods and take top 5
                val topCategories = allCategories.sortedByDescending { category ->
                    historyData.sumOf { period ->
                        period.categoryExpenses
                            .find { it.categoryName == category.categoryName }?.amount ?: 0.0
                    }
                }.take(5)
                
                // Display legend in rows of 3 items
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    val rows = topCategories.chunked(3)
                    rows.forEach { rowCategories ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            rowCategories.forEach { category ->
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(category.color)
                                    )
                                    
                                    Spacer(modifier = Modifier.width(4.dp))
                                    
                                    Text(
                                        text = category.categoryName,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            
                            // Add empty spacers if row is not full
                            repeat(3 - rowCategories.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
} 