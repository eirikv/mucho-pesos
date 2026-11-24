package vige.land.muchopesos.ui.screens.statistics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import vige.land.muchopesos.domain.logic.MoneyFormatter
import vige.land.muchopesos.domain.model.CategoryGroup
import vige.land.muchopesos.domain.model.CategoryGroupMonthlyTotal
import vige.land.muchopesos.domain.model.CategoryMonthlyTotal
import vige.land.muchopesos.domain.model.CurrencySpec
import vige.land.muchopesos.domain.model.LedgerConfig
import vige.land.muchopesos.domain.model.MonthOverMonthDelta
import vige.land.muchopesos.domain.model.MonthlySummary
import vige.land.muchopesos.domain.model.MonthlyTrendPoint
import vige.land.muchopesos.domain.model.PartnerRole
import vige.land.muchopesos.ui.components.CategoryBadge
import vige.land.muchopesos.ui.components.categoryColorsFor
import vige.land.muchopesos.ui.components.formatCompactMoney
import vige.land.muchopesos.ui.components.formatShortMonth
import vige.land.muchopesos.ui.components.formatYearMonth
import vige.land.muchopesos.ui.components.groupIconFor
import vige.land.muchopesos.ui.theme.MuchoPesosTheme
import vige.land.muchopesos.ui.theme.tabular

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel,
    onOpenSettings: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh(force = false)
    }

    val config = state.config ?: return
    val currency = config.currencySpec
    val viewerRole = state.currentUserRole
    val otherRole = viewerRole.other()
    val viewerName = config.displayNameFor(viewerRole)
    val otherName = config.displayNameFor(otherRole)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Statistics",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.refresh(force = true)
                        },
                        enabled = !state.isSyncing,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Sync ledger",
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings",
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = bottomBar,
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.isSyncing,
            onRefresh = { viewModel.refresh(force = true) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.isSyncing) {
                    item(key = "stats_sync_progress") {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(100.dp)),
                        )
                    }
                }

                if (state.errorMessage != null) {
                    item(key = "stats_error") {
                        StatsErrorBanner(
                            message = state.errorMessage.orEmpty(),
                            onRetry = { viewModel.refresh(force = true) },
                            onDismiss = viewModel::clearError,
                        )
                    }
                }

                // Section 1: Month Selector Bar
                item(key = "month_selector") {
                    MonthSelectorBar(
                        selectedMonth = state.selectedMonth,
                        canGoNext = state.canGoNextMonth,
                        showLatestButton = !state.isViewingLatestMonth,
                        onPreviousMonth = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.previousMonth()
                        },
                        onNextMonth = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.nextMonth()
                        },
                        onJumpToLatest = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.jumpToLatestMonth()
                        },
                    )
                }

                // Section 2: Monthly Overview & Partner Shares Hero Card
                item(key = "monthly_overview_hero") {
                    MonthlyOverviewHeroCard(
                        summary = state.selectedSummary,
                        monthOverMonthDelta = state.monthOverMonthDelta,
                        config = config,
                        viewerRole = viewerRole,
                    )
                }

                // Section 3: 6-Month Spending Trend Card
                item(key = "six_month_trend") {
                    SixMonthTrendCard(
                        trendPoints = state.trendPoints,
                        selectedMonth = state.selectedMonth,
                        selectedSummary = state.selectedSummary,
                        currency = currency,
                        onSelectMonth = { ym ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.selectMonth(ym)
                        },
                    )
                }

                // Section 4: Category Breakdown Header & Toggle
                item(key = "category_breakdown_header") {
                    CategoryBreakdownHeader(
                        mode = state.breakdownMode,
                        onModeChange = viewModel::selectBreakdownMode,
                    )
                }

                if (state.selectedSummary.expenseCount == 0) {
                    item(key = "empty_month_card") {
                        EmptyMonthStatsCard(selectedMonth = state.selectedMonth)
                    }
                } else if (state.breakdownMode == CategoryBreakdownMode.CATEGORIES) {
                    items(
                        items = state.selectedSummary.byCategory,
                        key = { "cat_${it.category.group.name}_${it.category.id}" },
                    ) { item ->
                        CategoryTotalRowCard(
                            item = item,
                            currency = currency,
                            viewerRole = viewerRole,
                            viewerName = viewerName,
                            otherName = otherName,
                        )
                    }
                } else {
                    items(
                        items = state.selectedSummary.byCategoryGroup,
                        key = { "grp_${it.group.name}" },
                    ) { item ->
                        CategoryGroupTotalRowCard(
                            item = item,
                            currency = currency,
                            viewerRole = viewerRole,
                            viewerName = viewerName,
                            otherName = otherName,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) {
                Text("Retry", color = MaterialTheme.colorScheme.onErrorContainer)
            }
            TextButton(onClick = onDismiss) {
                Text("Hide", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun MonthSelectorBar(
    selectedMonth: YearMonth,
    canGoNext: Boolean,
    showLatestButton: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onJumpToLatest: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onPreviousMonth) {
                Icon(
                    imageVector = Icons.Rounded.ChevronLeft,
                    contentDescription = "Previous month",
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = formatYearMonth(selectedMonth),
                    style = MaterialTheme.typography.titleMedium.tabular,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (showLatestButton) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .clickable(onClick = onJumpToLatest),
                    ) {
                        Text(
                            text = "Latest",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            IconButton(
                onClick = onNextMonth,
                enabled = canGoNext,
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "Next month",
                )
            }
        }
    }
}

@Composable
private fun MonthlyOverviewHeroCard(
    summary: MonthlySummary,
    monthOverMonthDelta: MonthOverMonthDelta?,
    config: LedgerConfig,
    viewerRole: PartnerRole,
) {
    val currency = config.currencySpec
    val financeColors = MuchoPesosTheme.financeColors
    val otherRole = viewerRole.other()
    val viewerName = config.displayNameFor(viewerRole)
    val otherName = config.displayNameFor(otherRole)

    val partnerTotals = summary.partnerTotals
    val viewerShare = partnerTotals.shareFor(viewerRole)
    val otherShare = partnerTotals.shareFor(otherRole)
    val viewerPaid = partnerTotals.paidFor(viewerRole)
    val otherPaid = partnerTotals.paidFor(otherRole)
    val viewerPercent = partnerTotals.sharePercentFor(viewerRole)
    val otherPercent = partnerTotals.sharePercentFor(otherRole)

    val expenseCountLabel = when (summary.expenseCount) {
        0 -> "No expenses"
        1 -> "1 expense"
        else -> "${summary.expenseCount} expenses"
    }

    val viewerBarColor = MaterialTheme.colorScheme.primary
    val otherBarColor = MaterialTheme.colorScheme.tertiary

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header row: Month + count & MoM delta pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Monthly Spending",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${formatYearMonth(summary.yearMonth)} • $expenseCountLabel",
                        style = MaterialTheme.typography.bodySmall.tabular,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (monthOverMonthDelta != null && monthOverMonthDelta.percentDelta != null) {
                    val pct = monthOverMonthDelta.percentDelta
                    val cmp = pct.compareTo(BigDecimal.ZERO)
                    val prevShort = formatShortMonth(
                        yearMonth = monthOverMonthDelta.previousMonth,
                        referenceYear = summary.yearMonth.year,
                    )
                    val (pillBg, pillFg, pillText) = when {
                        cmp < 0 -> Triple(
                            financeColors.positiveContainer,
                            financeColors.onPositiveContainer,
                            "↓ ${pct.abs().toPlainString()}% vs $prevShort",
                        )
                        cmp > 0 -> Triple(
                            financeColors.negativeContainer,
                            financeColors.onNegativeContainer,
                            "↑ ${pct.toPlainString()}% vs $prevShort",
                        )
                        else -> Triple(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            "0.0% vs $prevShort",
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = pillBg,
                    ) {
                        Text(
                            text = pillText,
                            style = MaterialTheme.typography.labelMedium.tabular.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = pillFg,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            // Overall monthly total
            Text(
                text = MoneyFormatter.format(summary.totalExpenses, currency),
                style = MaterialTheme.typography.displayMedium.tabular,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Dual-segment proportional horizontal bar
            PartnerShareSplitBar(
                viewerPercent = viewerPercent,
                otherPercent = otherPercent,
                hasExpenses = summary.totalExpenses.compareTo(BigDecimal.ZERO) > 0,
                viewerColor = viewerBarColor,
                otherColor = otherBarColor,
            )

            // Two equal-width, equal-height side-by-side partner share cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PartnerMonthlyShareTile(
                    partnerName = viewerName,
                    isViewer = true,
                    indicatorColor = viewerBarColor,
                    shareAmount = MoneyFormatter.format(viewerShare, currency),
                    sharePercent = "${viewerPercent.toPlainString()}% share",
                    paidAmount = "Paid ${MoneyFormatter.format(viewerPaid, currency)}",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                PartnerMonthlyShareTile(
                    partnerName = otherName,
                    isViewer = false,
                    indicatorColor = otherBarColor,
                    shareAmount = MoneyFormatter.format(otherShare, currency),
                    sharePercent = "${otherPercent.toPlainString()}% share",
                    paidAmount = "Paid ${MoneyFormatter.format(otherPaid, currency)}",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }

            // Subtle settlement exclusion footnote
            if (summary.settlementCount > 0) {
                val noun = if (summary.settlementCount == 1) "settlement" else "settlements"
                Text(
                    text = "Excludes ${summary.settlementCount} $noun (${MoneyFormatter.format(summary.settlementTotal, currency)})",
                    style = MaterialTheme.typography.bodySmall.tabular,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PartnerShareSplitBar(
    viewerPercent: BigDecimal,
    otherPercent: BigDecimal,
    hasExpenses: Boolean,
    viewerColor: Color,
    otherColor: Color,
) {
    val viewerWeight = viewerPercent.toFloat().coerceAtLeast(0f)
    val otherWeight = otherPercent.toFloat().coerceAtLeast(0f)

    if (!hasExpenses || (viewerWeight <= 0f && otherWeight <= 0f)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (viewerWeight > 0f) {
            Box(
                modifier = Modifier
                    .weight(viewerWeight)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(100.dp))
                    .background(viewerColor),
            )
        }
        if (otherWeight > 0f) {
            Box(
                modifier = Modifier
                    .weight(otherWeight)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(100.dp))
                    .background(otherColor),
            )
        }
    }
}

@Composable
private fun PartnerMonthlyShareTile(
    partnerName: String,
    isViewer: Boolean,
    indicatorColor: Color,
    shareAmount: String,
    sharePercent: String,
    paidAmount: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(indicatorColor),
                )
                Text(
                    text = partnerName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isViewer) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "You",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = shareAmount,
                style = MaterialTheme.typography.titleLarge.tabular,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = sharePercent,
                style = MaterialTheme.typography.labelMedium.tabular,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = paidAmount,
                style = MaterialTheme.typography.bodySmall.tabular,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SixMonthTrendCard(
    trendPoints: List<MonthlyTrendPoint>,
    selectedMonth: YearMonth,
    selectedSummary: MonthlySummary,
    currency: CurrencySpec,
    onSelectMonth: (YearMonth) -> Unit,
) {
    val maxAmount = trendPoints.maxOfOrNull { it.totalExpenses } ?: BigDecimal.ZERO
    val referenceYear = trendPoints.lastOrNull()?.yearMonth?.year ?: selectedMonth.year

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "6-Month Trend",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${formatShortMonth(selectedMonth, referenceYear)} • ${MoneyFormatter.format(selectedSummary.totalExpenses, currency)}",
                    style = MaterialTheme.typography.labelMedium.tabular,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                trendPoints.forEach { point ->
                    val isSelected = point.yearMonth == selectedMonth
                    val hasSpending = point.totalExpenses.compareTo(BigDecimal.ZERO) > 0
                    val targetFraction = if (hasSpending && maxAmount.compareTo(BigDecimal.ZERO) > 0) {
                        point.totalExpenses
                            .divide(maxAmount, 4, RoundingMode.HALF_UP)
                            .toFloat()
                            .coerceIn(0.08f, 1f)
                    } else {
                        0f
                    }
                    val animatedFraction by animateFloatAsState(
                        targetValue = targetFraction,
                        animationSpec = tween(durationMillis = 320),
                        label = "trend_bar_${point.yearMonth}",
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectMonth(point.yearMonth) }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = if (hasSpending) {
                                formatCompactMoney(point.totalExpenses, currency)
                            } else if (isSelected) {
                                formatCompactMoney(BigDecimal.ZERO, currency)
                            } else {
                                ""
                            },
                            style = MaterialTheme.typography.labelSmall.tabular.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.height(16.dp),
                        )

                        Box(
                            modifier = Modifier
                                .height(108.dp)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            val barColor = when {
                                isSelected && hasSpending -> MaterialTheme.colorScheme.primary
                                isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                hasSpending -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                            val barHeight = if (hasSpending) {
                                (108f * animatedFraction).dp.coerceAtLeast(8.dp)
                            } else {
                                4.dp
                            }

                            Box(
                                modifier = Modifier
                                    .width(28.dp)
                                    .height(barHeight)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 10.dp,
                                            topEnd = 10.dp,
                                            bottomStart = 4.dp,
                                            bottomEnd = 4.dp,
                                        ),
                                    )
                                    .background(barColor),
                            )
                        }

                        Text(
                            text = formatShortMonth(point.yearMonth, referenceYear),
                            style = MaterialTheme.typography.labelSmall.tabular.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryBreakdownHeader(
    mode: CategoryBreakdownMode,
    onModeChange: (CategoryBreakdownMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "By Category",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth(),
        ) {
            SegmentedButton(
                selected = mode == CategoryBreakdownMode.CATEGORIES,
                onClick = { onModeChange(CategoryBreakdownMode.CATEGORIES) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) {
                Text("Categories")
            }
            SegmentedButton(
                selected = mode == CategoryBreakdownMode.GROUPS,
                onClick = { onModeChange(CategoryBreakdownMode.GROUPS) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) {
                Text("Groups")
            }
        }
    }
}

@Composable
private fun CategoryTotalRowCard(
    item: CategoryMonthlyTotal,
    currency: CurrencySpec,
    viewerRole: PartnerRole,
    viewerName: String,
    otherName: String,
) {
    val (_, groupContentColor) = categoryColorsFor(item.category.group)
    val otherRole = viewerRole.other()

    BreakdownRowCard(
        badge = {
            CategoryBadge(
                category = item.category,
                size = 44.dp,
                iconSize = 22.dp,
            )
        },
        title = item.category.name,
        subtitle = "${item.category.group.displayName} • ${formatExpenseCount(item.expenseCount)}",
        totalAmountText = MoneyFormatter.format(item.totalAmount, currency),
        percentText = "${item.percentOfMonthTotal.toPlainString()}%",
        progressFraction = (item.percentOfMonthTotal.toFloat() / 100f).coerceIn(0f, 1f),
        progressColor = groupContentColor,
        partnerFooter = "$viewerName: ${MoneyFormatter.format(item.shareFor(viewerRole), currency)}  •  $otherName: ${MoneyFormatter.format(item.shareFor(otherRole), currency)}",
    )
}

@Composable
private fun CategoryGroupTotalRowCard(
    item: CategoryGroupMonthlyTotal,
    currency: CurrencySpec,
    viewerRole: PartnerRole,
    viewerName: String,
    otherName: String,
) {
    val (groupContainerColor, groupContentColor) = categoryColorsFor(item.group)
    val otherRole = viewerRole.other()

    BreakdownRowCard(
        badge = {
            GroupBadge(
                group = item.group,
                containerColor = groupContainerColor,
                contentColor = groupContentColor,
            )
        },
        title = item.group.displayName,
        subtitle = formatExpenseCount(item.expenseCount),
        totalAmountText = MoneyFormatter.format(item.totalAmount, currency),
        percentText = "${item.percentOfMonthTotal.toPlainString()}%",
        progressFraction = (item.percentOfMonthTotal.toFloat() / 100f).coerceIn(0f, 1f),
        progressColor = groupContentColor,
        partnerFooter = "$viewerName: ${MoneyFormatter.format(item.shareFor(viewerRole), currency)}  •  $otherName: ${MoneyFormatter.format(item.shareFor(otherRole), currency)}",
    )
}

@Composable
private fun GroupBadge(
    group: CategoryGroup,
    containerColor: Color,
    contentColor: Color,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = groupIconFor(group),
            contentDescription = group.displayName,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun BreakdownRowCard(
    badge: @Composable () -> Unit,
    title: String,
    subtitle: String,
    totalAmountText: String,
    percentText: String,
    progressFraction: Float,
    progressColor: Color,
    partnerFooter: String,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                badge()

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = totalAmountText,
                        style = MaterialTheme.typography.titleMedium.tabular.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = percentText,
                        style = MaterialTheme.typography.labelMedium.tabular,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Thin rounded horizontal proportion bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                if (progressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressFraction.coerceIn(0.03f, 1f))
                            .clip(RoundedCornerShape(100.dp))
                            .background(progressColor),
                    )
                }
            }

            // Compact partner share footer
            Text(
                text = partnerFooter,
                style = MaterialTheme.typography.bodySmall.tabular,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyMonthStatsCard(
    selectedMonth: YearMonth,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.BarChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp),
            )
            Text(
                text = "No expenses in ${formatYearMonth(selectedMonth)}",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Tap another month in the trend chart or add an expense on the Ledger tab to see spending totals and category breakdowns.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun formatExpenseCount(count: Int): String =
    if (count == 1) "1 expense" else "$count expenses"
