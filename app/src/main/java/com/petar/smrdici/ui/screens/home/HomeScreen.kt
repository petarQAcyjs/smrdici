@file:OptIn(ExperimentalMaterialApi::class)
package com.petar.smrdici.ui.screens.home

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
                        .height(220.dp), // Increased height for larger chart
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Pie chart on the left - increased size
                    Box(
                        modifier = Modifier
                            .weight(1.2f) // Give more space to the chart
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
                            .weight(0.8f) // Less space for the legend
                            .fillMaxHeight()
                            .padding(start = 4.dp),
                        verticalArrangement = Arrangement.SpaceBetween // Changed to SpaceBetween
                    ) {
                        // Categories section
                        Column {
                            // Show categories in a compact layout
                            val topCategories = expenseData.take(5) // Show up to 5 categories
                            
                            // Create rows of 1 item each for better readability
                            topCategories.forEach { category ->
                                CompactCategoryLegendItem(
                                    category = category,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            
                            // Show "View more" if there are more categories
                            if (expenseData.size > 5) {
                                Text(
                                    text = "... и још ${expenseData.size - 5}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .padding(top = 4.dp)
                                )
                            }
                        }
                        
                        // Total section - always at the bottom
                        Column {
                            // Add divider before total
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
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