package com.jktdeveloper.habitto.ui.onboarding.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habittracker.domain.model.Identity
import com.habittracker.domain.model.TemplateWithIdentities
import com.jktdeveloper.habitto.ui.components.HabitGlyph

@Composable
fun HabitsStep(
    templates: List<TemplateWithIdentities>,
    selectedIdentityIds: Set<String>,
    selectedTemplateIds: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Group each template by its PRIMARY identity = first identity in pick order
    // (selectedIdentityIds) that recommends the template. Fall back to first
    // recommender if no overlap (shouldn't happen — VM filters to picked identities).
    val orderedIds = selectedIdentityIds.toList()
    val grouped: Map<Identity, List<TemplateWithIdentities>> = templates
        .mapNotNull { item ->
            val primary = orderedIds.firstNotNullOfOrNull { iid ->
                item.recommendedBy.firstOrNull { it.id == iid }
            } ?: item.recommendedBy.firstOrNull()
            primary?.let { it to item }
        }
        .groupBy({ it.first }, { it.second })

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        orderedIds.forEach { iid ->
            val primaryIdentity = grouped.keys.firstOrNull { it.id == iid } ?: return@forEach
            val sectionItems = grouped[primaryIdentity] ?: return@forEach
            if (sectionItems.isEmpty()) return@forEach
            item(key = "header-$iid") {
                Text(
                    primaryIdentity.name.uppercase(),
                    fontSize = 11.sp,
                    letterSpacing = 0.6.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
            items(sectionItems, key = { it.template.id }) { item ->
                HabitRow(
                    item = item,
                    primaryIdentity = primaryIdentity,
                    selected = item.template.id in selectedTemplateIds,
                    onClick = { onToggle(item.template.id) },
                )
            }
        }
    }
}

@Composable
private fun HabitRow(
    item: TemplateWithIdentities,
    primaryIdentity: Identity,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surface
    val border = BorderStroke(
        width = 1.dp,
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant,
    )
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = border,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HabitGlyph(
                iconName = item.template.iconName,
                hue = primaryIdentity.hue.toFloat(),
                size = 40.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.template.name,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Target ${item.template.defaultDailyTarget} ${item.template.unit} · ${item.template.defaultThreshold.toInt()} per pt",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val otherNames = item.recommendedBy
                    .filter { it.id != primaryIdentity.id }
                    .map { it.name }
                if (otherNames.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    AlsoForPill(otherNames = otherNames)
                }
            }
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AlsoForPill(otherNames: List<String>) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            "Also for ${otherNames.joinToString(", ")}",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}
