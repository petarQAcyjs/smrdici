package com.petar.smrdici.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseUser
import com.petar.smrdici.ui.navigation.Screen
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * Компонента за заглавље апликације.
 * 
 * @param title Наслов који ће бити приказан.
 * @param user Пријављени корисник (опционо).
 * @param navController Навигациони контролер.
 * @param showBackButton Да ли приказати дугме за назад.
 * @param onBackClick Акција која се извршава приликом клика на дугме за назад.
 * @param onMenuClick Акција која се извршава приликом клика на мени, ако је мени приказан.
 * @param showMenu Да ли приказати дугме за мени.
 * @param showProfileIcon Да ли приказати дугме за профил.
 * @param showLogoutButton Да ли приказати дугме за одјављивање.
 * @param onLogoutClick Акција која се извршава приликом клика на дугме за одјављивање.
 * @param onTitleLongPress Акција која се извршава приликом дугог притиска на наслов.
 * @param actions Custom actions to be displayed in the top app bar
 */
@Composable
fun AppHeader(
    title: String,
    user: FirebaseUser? = null,
    navController: NavController? = null,
    showBackButton: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    onMenuClick: () -> Unit = {},
    showMenu: Boolean = false,
    showProfileIcon: Boolean = true,
    showLogoutButton: Boolean = false,
    onLogoutClick: () -> Unit = {},
    onTitleLongPress: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    var isPressed by remember { mutableStateOf(false) }
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        label = "title scale animation"
    )

    // Custom slim header using Surface instead of TopAppBar for better height control
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp), // Increased from 40dp to 48dp to accommodate title better
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            // Navigation icon section (left side)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(80.dp), // Reduced from 100dp to give more space to title
                contentAlignment = Alignment.CenterStart
            ) {
                if (showBackButton) {
                    IconButton(
                        onClick = { 
                            if (onBackClick != null) {
                                onBackClick()
                            } else if (navController != null) {
                                navController.navigateUp() 
                            }
                        },
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else if (showMenu) {
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Мени",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            
            // Title section (centered)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.7f), // Increased from 0.6f to 0.7f for more title space
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 1, // Ensure single line
                    modifier = Modifier
                        .scale(scale)
                        .then(
                            if (onTitleLongPress != null) {
                                Modifier.pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = {
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            isPressed = true
                                            scope.launch {
                                                delay(100)
                                                isPressed = false
                                                onTitleLongPress()
                                            }
                                        }
                                    )
                                }
                            } else {
                                Modifier
                            }
                        ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // Actions section (right side)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(80.dp), // Reduced from 100dp to match left side
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Custom actions
                    actions()
                    
                    // Profile icon
                    if (showProfileIcon && navController != null) {
                        IconButton(
                            onClick = { 
                                if (user != null) {
                                    navController.navigate(Screen.Profile.route)
                                }
                            },
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Профил",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    
                    // Logout button
                    if (showLogoutButton) {
                        IconButton(
                            onClick = onLogoutClick,
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Одјави се",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
} 