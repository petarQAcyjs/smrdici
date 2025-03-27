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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.petar.smrdici.R
import com.petar.smrdici.data.model.Event
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.navigation.Screen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(),
    homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory(LocalContext.current))
) {
    val coroutineScope = rememberCoroutineScope()
    val authState by authViewModel.authState.collectAsState()
    val todayEvents by homeViewModel.todayEvents.collectAsState()
    val syncStatus by homeViewModel.syncStatus.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            coroutineScope.launch {
                isRefreshing = true
                homeViewModel.syncEvents()
                delay(1000)
                isRefreshing = false
            }
        }
    )
    
    val user = if (authState is AuthState.Authenticated) {
        (authState as AuthState.Authenticated).user
    } else null

    var testRefresh by remember { mutableStateOf(false) }
    
    // Додајемо стање за ручно праћење гестова
    var dragStartY by remember { mutableStateOf(0f) }
    var dragCurrentY by remember { mutableStateOf(0f) }

    Scaffold(
        topBar = {
            AppHeader(
                title = "Почетна",
                user = user,
                navController = navController
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pullRefresh(pullRefreshState)
                // Додајемо експлицитну подршку за гест повлачења
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { dragStartY = it.y },
                        onDragEnd = {
                            if (dragCurrentY - dragStartY > 100f) {
                                // Повлачење надоле
                                coroutineScope.launch {
                                    isRefreshing = true
                                    homeViewModel.syncEvents()
                                    delay(1000)
                                    isRefreshing = false
                                }
                            }
                            dragStartY = 0f
                            dragCurrentY = 0f
                        },
                        onDragCancel = {
                            dragStartY = 0f
                            dragCurrentY = 0f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            dragCurrentY = change.position.y
                        }
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TodayActivitiesCard(
                    events = todayEvents,
                    onEventClick = { event ->
                        event.id?.let { eventId ->
                            navController.navigate(Screen.Calendar.createRoute(eventId))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .padding(bottom = 16.dp)
                )
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        NavigationCard(
                            title = "Листе",
                            iconResId = R.drawable.ic_list,
                            onClick = { navController.navigate(Screen.Lists.route) }
                        )
                    }
                    
                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        NavigationCard(
                            title = "Календар",
                            iconResId = R.drawable.ic_calendar,
                            onClick = { navController.navigate(Screen.Calendar.route) }
                        )
                    }
                    
                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        NavigationCard(
                            title = "Буџет",
                            iconResId = R.drawable.ic_budget,
                            onClick = { navController.navigate(Screen.Budget.route) }
                        )
                    }
                }
            }
            
            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
fun NavigationCard(
    title: String,
    iconResId: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            
            Image(
                painter = painterResource(id = iconResId),
                contentDescription = title,
                modifier = Modifier.size(48.dp)
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
                .background(Color(android.graphics.Color.parseColor(event.color)))
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