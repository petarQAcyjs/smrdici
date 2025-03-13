package com.petar.smrdici.ui.components

import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.petar.smrdici.R
import com.petar.smrdici.ui.navigation.Screen

@Composable
fun BottomNavigation(navController: NavController) {
    Log.d("BottomNavigation", "BottomNavigation composable called")
    
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Budget,
        BottomNavItem.Calendar,
        BottomNavItem.Lists
    )
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    NavigationBar {
        items.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
            
            NavigationBarItem(
                icon = { 
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.titleResId?.let { stringResource(it) }
                    )
                },
                label = { 
                    item.titleResId?.let { 
                        Text(stringResource(it)) 
                    } ?: Text(item.screen.route.replaceFirstChar { it.uppercase() })
                },
                selected = selected,
                onClick = {
                    navController.navigate(item.screen.route) {
                        // Избегавање вишеструких копија исте дестинације на стеку
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // Избегавање истих дестинација
                        launchSingleTop = true
                        // Чување и враћање стања при навигацији
                        restoreState = true
                    }
                }
            )
        }
    }
}

sealed class BottomNavItem(
    val screen: Screen,
    val titleResId: Int? = null,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector
) {
    object Home : BottomNavItem(
        screen = Screen.Home,
        titleResId = R.string.home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )
    
    object Budget : BottomNavItem(
        screen = Screen.Budget,
        titleResId = R.string.budget,
        selectedIcon = Icons.Filled.AccountBox,
        unselectedIcon = Icons.Outlined.AccountBox
    )
    
    object Calendar : BottomNavItem(
        screen = Screen.Calendar,
        titleResId = R.string.calendar,
        selectedIcon = Icons.Filled.DateRange,
        unselectedIcon = Icons.Outlined.DateRange
    )
    
    object Lists : BottomNavItem(
        screen = Screen.Lists,
        titleResId = R.string.lists,
        selectedIcon = Icons.Filled.List,
        unselectedIcon = Icons.Outlined.List
    )
} 