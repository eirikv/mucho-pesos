package vige.land.muchopesos.ui.screens.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.math.BigDecimal
import java.time.YearMonth
import vige.land.muchopesos.data.repository.LedgerUiSnapshot
import vige.land.muchopesos.domain.logic.BalanceCalculator
import vige.land.muchopesos.domain.logic.MoneyFormatter
import vige.land.muchopesos.domain.model.ActiveTransaction
import vige.land.muchopesos.domain.model.CategoryGroup
import vige.land.muchopesos.domain.model.LedgerConfig
import vige.land.muchopesos.domain.model.PartnerRole
import vige.land.muchopesos.ui.components.CategoryBadge
import vige.land.muchopesos.ui.components.formatMonthYearHeader
import vige.land.muchopesos.ui.components.formatShortDate
import vige.land.muchopesos.ui.components.formatSyncTimestamp
import vige.land.muchopesos.ui.screens.detail.TransactionDetailSheet
import vige.land.muchopesos.ui.screens.settle.SettleUpSheet
import vige.land.muchopesos.ui.theme.MuchoPesosTheme
import vige.land.muchopesos.ui.theme.tabular

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onAddExpense: () -> Unit,
    onEditExpense: (txId: String) -> Unit,
    onOpenSettings: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    val snapshot by viewModel.ledgerState.collectAsStateWithLifecycle()
    val controls by viewModel.controls.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()

    val isFabExpanded by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 80
        }
    }

    // Throttled automatic refresh on resume
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh(force = false)
    }

    LaunchedEffect(controls.snackbarMessage) {
        val message = controls.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeSnackbar()
    }

    val config = snapshot.config ?: return
    val filteredTransactions = remember(snapshot.activeTransactions, controls.selectedCategoryGroup) {
        val filter = controls.selectedCategoryGroup
        if (filter == null) {
            snapshot.activeTransactions
        } else {
            snapshot.activeTransactions.filter { it.category.group == filter }
        }
    }

    val groupedByMonth = remember(filteredTransactions) {
        filteredTransactions.groupBy { YearMonth.from(it.date) }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = config.ledgerName,
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
                        enabled = !snapshot.isSyncing,
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onAddExpense()
                },
                expanded = isFabExpanded,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                    )
                },
                text = { Text("Add Expense") },
            )
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = snapshot.isSyncing,
            onRefresh = { viewModel.refresh(force = true) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 104.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                // 1. Net Balance Hero Card
                item(key = "hero_balance") {
                    NetBalanceHeroCard(
                        snapshot = snapshot,
                        config = config,
                        onSettleUpClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.openSettleUpSheet()
                        },
                    )
                }

                // 2. Sync Status Strip + optional error banner
                item(key = "sync_strip") {
                    SyncStatusStrip(
                        snapshot = snapshot,
                        onRetry = { viewModel.refresh(force = true) },
                        onDismissError = viewModel::clearError,
                    )
                }

                // 3. Category Group Filter Chips
                item(key = "category_filters") {
                    CategoryFilterRow(
                        selectedGroup = controls.selectedCategoryGroup,
                        onSelectGroup = viewModel::selectCategoryGroup,
                    )
                }

                // 4. Feed or Empty State
                if (filteredTransactions.isEmpty()) {
                    item(key = "empty_feed") {
                        EmptyFeedCard(
                            isFiltered = controls.selectedCategoryGroup != null,
                            filterName = controls.selectedCategoryGroup?.displayName,
                            onClearFilter = { viewModel.selectCategoryGroup(null) },
                            onAddExpense = onAddExpense,
                        )
                    }
                } else {
                    groupedByMonth.forEach { (yearMonth, monthTransactions) ->
                        item(key = "header_${yearMonth}") {
                            Text(
                                text = formatMonthYearHeader(monthTransactions.first().date),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    start = 4.dp,
                                    top = 8.dp,
                                    bottom = 2.dp,
                                ),
                            )
                        }

                        items(
                            items = monthTransactions,
                            key = { it.txId },
                        ) { tx ->
                            TransactionRowCard(
                                transaction = tx,
                                config = config,
                                viewerRole = snapshot.currentUserRole,
                                onClick = { viewModel.openTransactionDetail(tx) },
                            )
                        }
                    }
                }
            }
        }
    }

    // Transaction Detail ModalBottomSheet
    val detailTx = controls.selectedTransaction
    if (detailTx != null) {
        TransactionDetailSheet(
            transaction = detailTx,
            config = config,
            viewerRole = snapshot.currentUserRole,
            isMutating = controls.isMutating,
            onDismiss = viewModel::dismissTransactionDetail,
            onEdit = { tx ->
                viewModel.dismissTransactionDetail()
                onEditExpense(tx.txId)
            },
            onDelete = viewModel::deleteTransaction,
        )
    }

    // Settle Up ModalBottomSheet
    if (controls.showSettleUpSheet) {
        SettleUpSheet(
            config = config,
            balance = snapshot.balanceSummary,
            viewerRole = snapshot.currentUserRole,
            isSubmitting = controls.isMutating,
            onDismiss = viewModel::dismissSettleUpSheet,
            onConfirmSettlement = viewModel::recordSettlement,
        )
    }
}

@Composable
private fun NetBalanceHeroCard(
    snapshot: LedgerUiSnapshot,
    config: LedgerConfig,
    onSettleUpClick: () -> Unit,
) {
    val financeColors = MuchoPesosTheme.financeColors
    val currency = config.currencySpec
    val viewerRole = snapshot.currentUserRole
    val otherRole = viewerRole.other()
    val otherName = config.displayNameFor(otherRole)
    val viewerName = config.displayNameFor(viewerRole)

    val netForViewer = snapshot.balanceSummary.netFor(viewerRole)
    val cmp = netForViewer.compareTo(BigDecimal.ZERO)

    val (containerColor, onContainerColor, headlineLabel) = when {
        cmp > 0 -> Triple(
            financeColors.positiveContainer,
            financeColors.onPositiveContainer,
            "$otherName owes you",
        )
        cmp < 0 -> Triple(
            financeColors.negativeContainer,
            financeColors.onNegativeContainer,
            "You owe $otherName",
        )
        else -> Triple(
            financeColors.settledContainer,
            financeColors.onSettledContainer,
            "You are all settled up",
        )
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = headlineLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = onContainerColor,
                )
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = onContainerColor.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = currency.code,
                        style = MaterialTheme.typography.labelMedium.tabular,
                        color = onContainerColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            if (cmp == 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = onContainerColor,
                        modifier = Modifier.size(34.dp),
                    )
                    Text(
                        text = MoneyFormatter.format(BigDecimal.ZERO, currency),
                        style = MaterialTheme.typography.displayMedium.tabular,
                        color = onContainerColor,
                    )
                }
            } else {
                Text(
                    text = MoneyFormatter.format(netForViewer.abs(), currency),
                    style = MaterialTheme.typography.displayLarge.tabular,
                    color = onContainerColor,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$viewerName & $otherName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainerColor.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                if (cmp != 0) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onSettleUpClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = onContainerColor,
                            contentColor = containerColor,
                        ),
                        shape = RoundedCornerShape(100.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Handshake,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Settle Up",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncStatusStrip(
    snapshot: LedgerUiSnapshot,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
) {
    val financeColors = MuchoPesosTheme.financeColors

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = if (snapshot.isSyncing) Icons.Rounded.Sync else Icons.Rounded.CloudDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = if (snapshot.isSyncing) {
                        "Syncing with ledger…"
                    } else {
                        formatSyncTimestamp(snapshot.lastSyncEpochMs)
                    },
                    style = MaterialTheme.typography.labelMedium.tabular,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (snapshot.isDemoMode) {
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = financeColors.demoContainer,
                ) {
                    Text(
                        text = "Demo Mode",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = financeColors.onDemoContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        if (snapshot.isSyncing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(100.dp)),
            )
        }

        if (snapshot.errorMessage != null) {
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
                        text = snapshot.errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetry) {
                        Text(
                            text = "Retry",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    TextButton(onClick = onDismissError) {
                        Text(
                            text = "Hide",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    selectedGroup: CategoryGroup?,
    onSelectGroup: (CategoryGroup?) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "filter_all") {
            FilterChip(
                selected = selectedGroup == null,
                onClick = { onSelectGroup(null) },
                label = { Text("All") },
            )
        }
        items(
            items = CategoryGroup.expenseGroups,
            key = { it.name },
        ) { group ->
            FilterChip(
                selected = selectedGroup == group,
                onClick = {
                    onSelectGroup(if (selectedGroup == group) null else group)
                },
                label = { Text(group.displayName) },
            )
        }
    }
}

@Composable
private fun TransactionRowCard(
    transaction: ActiveTransaction,
    config: LedgerConfig,
    viewerRole: PartnerRole,
    onClick: () -> Unit,
) {
    val currency = config.currencySpec
    val financeColors = MuchoPesosTheme.financeColors
    val impact = remember(transaction, viewerRole, currency.fractionDigits) {
        BalanceCalculator.computeImpactForViewer(
            tx = transaction,
            viewerRole = viewerRole,
            scale = currency.fractionDigits,
        )
    }

    val payerDisplay = if (transaction.paidBy == viewerRole) {
        "You paid ${MoneyFormatter.format(transaction.amount, currency)}"
    } else {
        "${config.displayNameFor(transaction.paidBy)} paid ${MoneyFormatter.format(transaction.amount, currency)}"
    }

    val cmp = impact.netForViewer.compareTo(BigDecimal.ZERO)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CategoryBadge(
                category = transaction.category,
                size = 44.dp,
                iconSize = 22.dp,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = transaction.description,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatShortDate(transaction.date)} • $payerDisplay",
                    style = MaterialTheme.typography.bodySmall.tabular,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Right-aligned net impact
            if (transaction.isSettlement) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = financeColors.settledContainer,
                    ) {
                        Text(
                            text = "Settlement",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = financeColors.onSettledContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    Text(
                        text = MoneyFormatter.format(transaction.amount, currency),
                        style = MaterialTheme.typography.titleSmall.tabular,
                        color = financeColors.settledText,
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    when {
                        cmp > 0 -> {
                            Text(
                                text = "You lent",
                                style = MaterialTheme.typography.labelSmall,
                                color = financeColors.positiveText,
                            )
                            Text(
                                text = MoneyFormatter.format(impact.netForViewer, currency),
                                style = MaterialTheme.typography.titleSmall.tabular.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = financeColors.positiveText,
                            )
                        }
                        cmp < 0 -> {
                            Text(
                                text = "You borrowed",
                                style = MaterialTheme.typography.labelSmall,
                                color = financeColors.negativeText,
                            )
                            Text(
                                text = MoneyFormatter.format(impact.netForViewer.abs(), currency),
                                style = MaterialTheme.typography.titleSmall.tabular.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = financeColors.negativeText,
                            )
                        }
                        else -> {
                            Text(
                                text = "Even",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = MoneyFormatter.format(BigDecimal.ZERO, currency),
                                style = MaterialTheme.typography.titleSmall.tabular,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFeedCard(
    isFiltered: Boolean,
    filterName: String?,
    onClearFilter: () -> Unit,
    onAddExpense: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.ReceiptLong,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp),
                )
            }
            Text(
                text = if (isFiltered) {
                    "No ${filterName.orEmpty()} expenses yet"
                } else {
                    "No expenses recorded yet"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (isFiltered) {
                    "Try selecting another category group or clear the active filter."
                } else {
                    "Add your first shared expense and it will sync straight to your Google Sheet."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (isFiltered) {
                TextButton(onClick = onClearFilter) {
                    Text("Show all expenses")
                }
            } else {
                Button(onClick = onAddExpense) {
                    Text("Add first expense")
                }
            }
        }
    }
}
