package com.petar.smrdici.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow

fun Modifier.onAppear(action: () -> Unit): Modifier = composed {
    var hasAppeared = remember { false }
    
    this.onGloballyPositioned { coordinates ->
        if (!hasAppeared && coordinates.positionInWindow().y >= 0) {
            hasAppeared = true
            action()
        }
    }
} 