package com.petar.smrdici.ui.screens.profile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.petar.smrdici.utils.rememberWindowInfo
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(),
    homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory(LocalContext.current)),
    themeViewModel: ThemeViewModel = viewModel(factory = ThemeViewModelFactory(LocalContext.current))
) {
    val authState by authViewModel.authState.collectAsState()
    val themeMode by themeViewModel.themeMode.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val windowInfo = rememberWindowInfo()

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    fun showSnackbar(message: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        authViewModel.initGoogleSignIn(context)
    }

    val user = if (authState is AuthState.Authenticated) {
        (authState as AuthState.Authenticated).user
    } else null

    val syncStatus by homeViewModel.syncStatus.collectAsState()

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

    LaunchedEffect(authState) {
        Log.d("ProfileScreen", "LaunchedEffect(authState): тренутно стање = $authState")
        if (authState is AuthState.NotAuthenticated) {
            Log.d("ProfileScreen", "Детектована промена на NotAuthenticated, навигирам на Login")
            navController.navigate(Screen.Login.route) {
                popUpTo(Screen.Home.route) { inclusive = true }
            }
        }
    }

    val screenPadding = if (windowInfo.isSmallWidth) 12.dp else 16.dp
    val spacingMedium = if (windowInfo.isSmallHeight) 12.dp else 24.dp

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppHeader(
                title = "Профил",
                user = user,
                navController = navController,
                showProfileIcon = false
            )
        }
    ) { paddingValues ->
        if (user != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
                    .padding(screenPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(if (windowInfo.isSmallHeight) 8.dp else 16.dp))

                // Име корисника
                Text(
                    text = user.displayName ?: "Корисник",
                    style = if (windowInfo.isSmallWidth) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                // Имејл
                Text(
                    text = user.email ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(spacingMedium))

                // Подешавања
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(if (windowInfo.isSmallWidth) 12.dp else 16.dp)
                    ) {
                        Text(
                            text = "Подешавања",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Дугме за синхронизацију
                        SettingsItem(
                            icon = Icons.Default.Refresh,
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

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp)
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

                Spacer(modifier = Modifier.height(spacingMedium))

                // Дугме за одјаву
                Button(
                    onClick = {
                        Log.d("ProfileScreen", "Клик на дугме за одјаву")
                        try {
                            authViewModel.signOut()
                            navController.navigate(Screen.Login.route) {
                                popUpTo(Screen.Home.route) { inclusive = true }
                            }
                        } catch (e: Exception) {
                            Log.e("ProfileScreen", "Грешка при одјави: ${e.message}", e)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = "Одјави се",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Нисте пријављени",
                    style = MaterialTheme.typography.headlineMedium
                )
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
            .height(56.dp)
            .clickable(onClick = onClick, enabled = !isLoading)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        if (trailingText != null) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.width(8.dp))
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun recreateActivity(context: Context) {
    (context as? Activity)?.let { activity ->
        val intent = Intent(activity, com.petar.smrdici.ui.MainActivity::class.java)
        activity.finish()
        activity.startActivity(intent)
    }
}