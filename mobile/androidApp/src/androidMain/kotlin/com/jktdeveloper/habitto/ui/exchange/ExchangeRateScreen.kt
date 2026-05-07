package com.jktdeveloper.habitto.ui.exchange

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habittracker.domain.model.RateTier
import com.habittracker.domain.usecase.ExchangeRateCalculator
import com.habittracker.domain.usecase.PointCalculator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeRateScreen(
    viewModel: ExchangeRateViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Exchange rate",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                windowInsets = WindowInsets(0.dp),
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            item { Hero(state) }
            item { TierLadder(state.currentTier) }
            if (state.comparison.isNotEmpty()) {
                item { ComparisonHeader() }
                items(state.comparison) { row -> ComparisonRowView(row) }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun Hero(state: ExchangeRateState) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(
            "TODAY",
            fontSize = 11.sp,
            letterSpacing = 0.4.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = formatRate(state.currentRate) + "×",
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            lineHeight = 56.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = heroSubtitle(state),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun heroSubtitle(state: ExchangeRateState): String {
    val daysToNext = state.daysToNext
    val nextLevel = state.currentTier.level + 1
    return if (daysToNext == null) {
        "Top tier reached."
    } else {
        val nextRate = ExchangeRateCalculator.tiers
            .firstOrNull { it.level == nextLevel }
            ?.rate
        val nextRateLabel = nextRate?.let { formatRate(it) + "×" } ?: ""
        "You're at Tier ${state.currentTier.level} of 5. $daysToNext days to $nextRateLabel."
    }
}

@Composable
private fun TierLadder(currentTier: RateTier) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        ExchangeRateCalculator.tiers.reversed().forEach { tier ->
            TierRow(
                tier = tier,
                isCurrent = tier.level == currentTier.level,
                isPassed = tier.level < currentTier.level,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TierRow(tier: RateTier, isCurrent: Boolean, isPassed: Boolean) {
    val borderColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val labelColor = when {
        isCurrent || isPassed -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val rangeText = if (tier.maxStreak == null) "${tier.minStreak}+ days"
    else "${tier.minStreak}–${tier.maxStreak} days"

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.20f)
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (isCurrent) 2.dp else 1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (isPassed || isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isPassed) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Text(
                        tier.level.toString(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Tier ${tier.level} · ${formatRate(tier.rate)}×",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = labelColor,
                )
                Text(
                    rangeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ComparisonHeader() {
    Text(
        "What it costs now",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
    )
}

@Composable
private fun ComparisonRowView(row: ComparisonRow) {
    val basePerTap = PointCalculator.pointsSpent(1.0, row.baseCostPerUnit)
    val rate = if (row.baseCostPerUnit > 0.0) row.currentCostPerUnit / row.baseCostPerUnit else 1.0
    val currentPerTap = PointCalculator.pointsSpentWithRate(1.0, row.baseCostPerUnit, rate)
    val isFree = row.baseCostPerUnit == 0.0
    val showArrow = !isFree && basePerTap != currentPerTap

    Surface(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "per ${row.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isFree) {
                Text(
                    "FREE",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (showArrow) {
                        Text(
                            basePerTap.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = TextDecoration.LineThrough,
                        )
                        Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Text(
                        currentPerTap.toString(),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (currentPerTap > basePerTap)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "pt / ${row.unit}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun formatRate(value: Double): String {
    val rounded = (value * 10).toInt()
    val whole = rounded / 10
    val frac = rounded % 10
    return "$whole.$frac"
}

private fun formatNumber(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        val rounded = (value * 10).toInt() / 10.0
        rounded.toString()
    }
}
