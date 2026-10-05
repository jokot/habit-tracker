package com.jktdeveloper.habitto.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habittracker.domain.model.Identity

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IdentityStrip(
    identities: List<Identity>,
    onChipClick: (Identity) -> Unit,
    onMoreClick: () -> Unit,
    pinnedIdentityId: String? = null,
    modifier: Modifier = Modifier,
) {
    if (identities.isEmpty()) return
    val visible = identities.take(3)
    val extra = identities.size - visible.size
    // A clickable Surface reserves 48 dp of height, so a wrapped row had 8 dp of empty
    // space above and below each 32 dp chip. The chips keep their full width as targets.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        FlowRow(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "I AM",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .align(Alignment.CenterVertically),
                letterSpacing = 0.3.sp,
            )
            visible.forEach { identity ->
                IdentityChip(
                    identity = identity,
                    onClick = { onChipClick(identity) },
                    isPinned = identity.id == pinnedIdentityId,
                )
            }
            // Always shown, so the list screen opens from Today also with 3 or fewer identities.
            IdentityMorePill(extra, onClick = onMoreClick)
        }
    }
}
