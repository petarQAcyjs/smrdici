package com.petar.smrdici.ui.screens.profile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.components.AppHeader
import com.petar.smrdici.ui.navigation.Screen
import com.petar.smrdici.ui.screens.home.HomeViewModel
import com.petar.smrdici.ui.screens.home.SyncStatus
import com.petar.smrdici.ui.theme.ThemeMode
import com.petar.smrdici.ui.theme.ThemeViewModel
import com.petar.smrdici.ui.theme.ThemeViewModelFactory
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(),
    homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory()),
    themeViewModel: ThemeViewModel = viewModel(factory = ThemeViewModelFactory(LocalContext.current))
) {
    val authState by authViewModel.authState.collectAsState()
    val themeMode by themeViewModel.themeMode.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Додајемо coroutineScope и snackbarHostState
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Функција за приказивање Snackbar-а
    fun showSnackbar(message: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    // Inicijalizujemo Google Sign-In klijenta за slučaj да се одјави
    LaunchedEffect(Unit) {
        authViewModel.initGoogleSignIn(context)
    }

    val user = if (authState is AuthState.Authenticated) {
        (authState as AuthState.Authenticated).user
    } else null

    val syncStatus by homeViewModel.syncStatus.collectAsState()
    
    // Пратимо промене у статусу синхронизације
    LaunchedEffect(syncStatus) {
        Log.d("ProfileScreen", "Промена статуса синхронизације: $syncStatus")
        when (syncStatus) {
            is SyncStatus.Success -> {
                showSnackbar("Синхронизација успешна")
            }
            is SyncStatus.Error -> {
                val errorMsg = (syncStatus as SyncStatus.Error).message
                Log.e("ProfileScreen", "Грешка при синхронизацији: $errorMsg")
                showSnackbar("Грешка: $errorMsg")
            }
            else -> {}
        }
    }

    // Pratimo stanje autentifikacije i navigiramo na Login kad korisnik nije autentifikovan
    LaunchedEffect(authState) {
        android.util.Log.d("ProfileScreen", "LaunchedEffect(authState): тренутно стање = $authState")
        if (authState is AuthState.NotAuthenticated) {
            android.util.Log.d("ProfileScreen", "Детектована промена на NotAuthenticated, навигирам на Login")
            navController.navigate(Screen.Login.route) {
                popUpTo(Screen.Home.route) { inclusive = true }
            }
        }
    }

    // Користимо само један Scaffold
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppHeader(
                title = "Профил",
                user = user,
                navController = navController,
                showBackButton = true,
                showProfileIcon = false
            )
        }
    ) { paddingValues ->
        // Садржај екрана...
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (user != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .padding(bottom = 80.dp), // Додајемо додатни padding на дну да направимо места за дугме
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        // Профилна слика
                        Surface(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Box(
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = user.displayName?.firstOrNull()?.toString() ?:
                                    user.email?.firstOrNull()?.toString() ?: "?",
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Име корисника
                        Text(
                            text = user.displayName ?: "Корисник",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // Имејл
                        Text(
                            text = user.email ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // Подешавања
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
                                    text = "Подешавања",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )

                                // Дугме за синхронизацију
                                SettingsItem(
                                    icon = if (syncStatus is SyncStatus.Syncing) 
                                        Icons.Default.Refresh 
                                    else 
                                        Icons.Default.Refresh,
                                    title = "Синхронизуј податке",
                                    subtitle = when (syncStatus) {
                                        is SyncStatus.Syncing -> "Синхронизација у току..."
                                        is SyncStatus.Success -> "Синхронизација успешна"
                                        is SyncStatus.Error -> "Грешка: ${(syncStatus as SyncStatus.Error).message}"
                                        else -> null
                                    },
                                    onClick = { 
                                        Log.d("ProfileScreen", "Клик на дугме за синхронизацију")
                                        homeViewModel.syncEvents() 
                                    },
                                    isLoading = syncStatus is SyncStatus.Syncing
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )

                                // Тема
                                SettingsItem(
                                    icon = when (themeMode) {
                                        ThemeMode.LIGHT -> Icons.Default.LightMode
                                        ThemeMode.DARK -> Icons.Default.DarkMode
                                        ThemeMode.SYSTEM -> Icons.Default.Settings
                                    },
                                    title = "Тема",
                                    subtitle = when (themeMode) {
                                        ThemeMode.LIGHT -> "Светла"
                                        ThemeMode.DARK -> "Тамна"
                                        ThemeMode.SYSTEM -> "Систем"
                                    },
                                    onClick = { showThemeDialog = true }
                                )

                                // За подешавања буџета
                                SettingsItem(
                                    icon = Icons.Default.AccountBalance,
                                    title = "Подешавања финансија",
                                    onClick = {
                                        navController.navigate(Screen.FinanceSettings.route)
                                    }
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )

                                // За подешавања обавештења
                                SettingsItem(
                                    icon = Icons.Default.Notifications,
                                    title = "Подешавања обавештења",
                                    onClick = {
                                        navController.navigate(Screen.NotificationSettings.route)
                                    }
                                )
                            }
                        }
                    }
                    
                    // Дугме за одјаву у посебном Box-у који је увек на дну екрана
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 32.dp)
                    ) {
                        Button(
                            onClick = {
                                android.util.Log.d("ProfileScreen", "Клик на дугме за одјаву")
                                try {
                                    authViewModel.signOut()
                                    android.util.Log.d("ProfileScreen", "Позив signOut() успешан")
                                    
                                    // Додајемо директну навигацију
                                    navController.navigate(Screen.Login.route) {
                                        popUpTo(Screen.Home.route) { inclusive = true }
                                    }
                                    android.util.Log.d("ProfileScreen", "Директна навигација на Login екран")
                                } catch (e: Exception) {
                                    android.util.Log.e("ProfileScreen", "Грешка при одјави: ${e.message}", e)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp), // Фиксна висина дугмета
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(
                                text = "Одјави се",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            } else {
                // Корисник није пријављен
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Нисте пријављени",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Изаберите тему") },
            text = {
                Column {
                    ThemeOption(
                        title = "Светла",
                        icon = Icons.Default.LightMode,
                        selected = themeMode == ThemeMode.LIGHT,
                        onClick = {
                            themeViewModel.setThemeMode(ThemeMode.LIGHT)
                            showThemeDialog = false
                            recreateActivity(context)
                        }
                    )
                    ThemeOption(
                        title = "Тамна",
                        icon = Icons.Default.DarkMode,
                        selected = themeMode == ThemeMode.DARK,
                        onClick = {
                            themeViewModel.setThemeMode(ThemeMode.DARK)
                            showThemeDialog = false
                            recreateActivity(context)
                        }
                    )
                    ThemeOption(
                        title = "Систем",
                        icon = Icons.Default.Settings,
                        selected = themeMode == ThemeMode.SYSTEM,
                        onClick = {
                            themeViewModel.setThemeMode(ThemeMode.SYSTEM)
                            showThemeDialog = false
                            recreateActivity(context)
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Откажи")
                }
            }
        )
    }
}

@Composable
private fun ThemeOption(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.weight(1f))

        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Изабрано",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailingText: String? = null,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp) // Смањујемо висину на 64dp за све ставке
            .clickable(onClick = onClick, enabled = !isLoading)
            .padding(horizontal = 16.dp), // Уклањамо вертикални падинг
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLoading) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.Center // Центрирамо садржај вертикално
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        if (trailingText != null) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.width(8.dp))
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

// Funkcija za ponovno kreiranje aktivnosti
private fun recreateActivity(context: Context) {
    (context as? Activity)?.let { activity ->
        val intent = Intent(activity, com.petar.smrdici.ui.MainActivity::class.java)
        activity.finish()
        activity.startActivity(intent)
    }
}