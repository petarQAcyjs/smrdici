package com.petar.smrdici.ui.screens.calendar

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimeInput
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    showDialog: Boolean,
    initialHour: Int,
    initialMinute: Int,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    if (!showDialog) return

    val state = remember { TimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    ) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onTimeSelected(state.hour, state.minute)
                }
            ) {
                Text("Потврди")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Откажи")
            }
        },
        text = {
            TimePicker(state = state)
        }
    )
} 