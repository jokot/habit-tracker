package com.jktdeveloper.habitto.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habittracker.domain.model.Identity

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IdentityStrip(
    identities: List<Identity>,
    onChipClick: (Identity) -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (identities.isEmpty()) return
    val visible = identities.take(3)
    val extra = identities.size - visible.size
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
        visible.forEach { identity -> IdentityChip(identity, onClick = { onChipClick(identity) }) }
        if (extra > 0) IdentityMorePill(extra, onClick = onMoreClick)
    }
}
