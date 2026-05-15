package com.jktdeveloper.habitto.ui.onboarding.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.jktdeveloper.habitto.ui.components.IdentityAvatar

@Composable
fun HabitsStep(
    pickedIdentities: List<Identity>,
    templates: List<TemplateWithIdentities>,
    selectedTemplateIds: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        if (pickedIdentities.isNotEmpty()) {
            item(key = "picked-identities") {
                PickedIdentityPillRow(pickedIdentities)
                Spacer(Modifier.height(4.dp))
            }
        }
        items(templates, key = { it.template.id }) { item ->
            val primaryIdentity = pickedIdentities.firstOrNull { id ->
                item.recommendedBy.any { it.id == id.id }
            } ?: item.recommendedBy.firstOrNull()
            HabitRow(
                item = item,
                primaryIdentity = primaryIdentity,
                selected = item.template.id in selectedTemplateIds,
                onClick = { onToggle(item.template.id) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickedIdentityPillRow(identities: List<Identity>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        identities.forEach { identity ->
            val hue = identity.hue.toFloat()
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.hsl(hue = hue, saturation = 0.30f, lightness = 0.94f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, end = 10.dp, bottom = 4.dp),
                ) {
                    IdentityAvatar(
                        iconName = identity.icon,
                        hue = hue,
                        size = 20.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        identity.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.hsl(hue = hue, saturation = 0.55f, lightness = 0.18f),
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitRow(
    item: TemplateWithIdentities,
    primaryIdentity: Identity?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val primaryHue = primaryIdentity?.hue?.toFloat() ?: 142f
    val selectedColor = Color.hsl(hue = primaryHue, saturation = 0.70f, lightness = 0.50f)
    val borderColor = if (selected) selectedColor else MaterialTheme.colorScheme.outlineVariant
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(width = 2.dp, color = borderColor),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HabitCheckbox(
                selected = selected,
                selectedColor = selectedColor,
            )
            HabitGlyph(
                iconName = item.template.iconName,
                hue = primaryHue,
                size = 40.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.template.name,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "${item.template.defaultDailyTarget} × ${item.template.defaultThreshold.toInt()} ${item.template.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.recommendedBy.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    RecommendedByPills(item.recommendedBy.toList())
                }
            }
        }
    }
}

@Composable
private fun HabitCheckbox(selected: Boolean, selectedColor: Color) {
    val borderColor = if (selected) selectedColor else MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) selectedColor else Color.Transparent)
            .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecommendedByPills(identities: List<Identity>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        identities.forEach { identity ->
            val hue = identity.hue.toFloat()
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.hsl(hue = hue, saturation = 0.30f, lightness = 0.95f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 3.dp, top = 2.dp, end = 7.dp, bottom = 2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.hsl(hue = hue, saturation = 0.70f, lightness = 0.50f)),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        identity.name.substringBefore(' '),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.hsl(hue = hue, saturation = 0.55f, lightness = 0.22f),
                    )
                }
            }
        }
    }
}
