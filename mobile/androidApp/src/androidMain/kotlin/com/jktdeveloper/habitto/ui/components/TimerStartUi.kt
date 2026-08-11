package com.jktdeveloper.habitto.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** The durations a want timer can be started for. */
private val DURATIONS_MIN = listOf(5, 10, 15, 20, 30, 60)

/**
 * "How long?" picker for a want timer. Shared so Home and want detail offer the same
 * durations from the same sheet — Home starts timers inline, without the detour.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DurationSheet(
    onPick: (durationSec: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("How long?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            // Wraps: six chips do not fit one row on a small screen.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DURATIONS_MIN.forEach { mins ->
                    AssistChip(
                        onClick = { onPick(mins * 60) },
                        label = { Text("$mins min") },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Confirmation for starting a timer while another want is still counting down. */
@Composable
fun ReplaceTimerDialog(
    otherWantName: String,
    elapsedMin: Int,
    minutesLeft: Int,
    onReplace: () -> Unit,
    onKeep: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text("Replace running timer?") },
        text = {
            val tail = if (elapsedMin >= 1) {
                "Starting a new one will log $elapsedMin min and end it."
            } else {
                "Starting a new one will discard it."
            }
            Text("You have a $minutesLeft min timer for $otherWantName. $tail")
        },
        confirmButton = { Button(onClick = onReplace) { Text("Replace") } },
        dismissButton = { TextButton(onClick = onKeep) { Text("Keep") } },
    )
}
