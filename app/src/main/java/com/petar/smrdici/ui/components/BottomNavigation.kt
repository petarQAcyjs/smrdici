package com.petar.smrdici.ui.components

import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
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
        BottomNavItem.Lists,
        BottomNavItem.Profile
    )
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = if (currentRoute == item.screen.route) {
                            item.selectedIcon
                        } else {
                            item.unselectedIcon
                        },
                        contentDescription = item.titleResId?.let { stringResource(it) }
                    )
                },
                label = {
                    item.titleResId?.let {
                        Text(text = stringResource(it))
                    }
                },
                selected = currentRoute == item.screen.route,
                onClick = {
                    if (currentRoute != item.screen.route) {
                        navController.navigate(item.screen.route) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
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
    
    object Profile : BottomNavItem(
        screen = Screen.Profile,
        titleResId = R.string.profile,
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person
    )
} 