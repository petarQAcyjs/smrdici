package com.petar.smrdici.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
 * @param onMenuClick Акција која се извршава приликом клика на мени, ако је мени приказан.
 * @param showMenu Да ли приказати дугме за мени.
 * @param showProfileIcon Да ли приказати дугме за профил.
 * @param onTitleLongPress Акција која се извршава приликом дугог притиска на наслов.
 * @param actions Custom actions to be displayed in the top app bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppHeader(
    title: String,
    user: FirebaseUser?,
    navController: NavController,
    showBackButton: Boolean = false,
    onMenuClick: () -> Unit = {},
    showMenu: Boolean = false,
    showProfileIcon: Boolean = true,
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

    androidx.compose.material3.CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
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
        },
        navigationIcon = {
            if (showBackButton) {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else if (showMenu) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Мени",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp))
            }
        },
        actions = {
            // Custom actions
            actions()
            
            // Profile icon
            if (showProfileIcon) {
                IconButton(
                    onClick = { 
                        if (user != null) {
                            navController.navigate(Screen.Profile.route)
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Профил",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
} 