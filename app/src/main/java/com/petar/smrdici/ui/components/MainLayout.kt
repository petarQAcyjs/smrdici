package com.petar.smrdici.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController

@Composable
fun MainLayout(
    navController: NavController? = null,
    content: @Composable () -> Unit
) {
    Scaffold(
        bottomBar = {
            navController?.let {
                BottomNavBar(navController = it)
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
        ) {
            content()
        }
    }
}
