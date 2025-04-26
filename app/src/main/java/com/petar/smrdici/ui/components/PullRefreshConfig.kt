package com.petar.smrdici.ui.components

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Стандардизована компонента за приказ PullRefreshIndicator са конзистентним изгледом.
 * Користи примарне боје теме за позадину и садржај, увеk користи scale параметар.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun StandardPullRefreshIndicator(
    refreshing: Boolean,
    state: PullRefreshState,
    modifier: Modifier = Modifier
) {
    // Koristimo jače kontrastne boje za bolju vidljivost
    PullRefreshIndicator(
        refreshing = refreshing,
        state = state,
        modifier = modifier,
        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.primary,
        scale = true
    )
} 