package vige.land.muchopesos.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import vige.land.muchopesos.domain.logic.BalanceCalculator
import vige.land.muchopesos.domain.logic.MoneyFormatter
import vige.land.muchopesos.domain.model.ActiveTransaction
import vige.land.muchopesos.domain.model.LedgerConfig
import vige.land.muchopesos.domain.model.PartnerRole
import vige.land.muchopesos.ui.components.CategoryBadge
import vige.land.muchopesos.ui.components.formatFullDate
import vige.land.muchopesos.ui.components.formatSplitTypeLabel
import vige.land.muchopesos.ui.theme.MuchoPesosTheme
import vige.land.muchopesos.ui.theme.tabular

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailSheet(
    transaction: ActiveTransaction,
    config: LedgerConfig,
    viewerRole: PartnerRole,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onEdit: (ActiveTransaction) -> Unit,
    onDelete: (ActiveTransaction) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val currency = config.currencySpec
    val impact = remember(transaction, viewerRole, currency.fractionDigits) {
        BalanceCalculator.computeImpactForViewer(
            tx = transaction,
            viewerRole = viewerRole,
            scale = currency.fractionDigits,
        )
    }
    val financeColors = MuchoPesosTheme.financeColors

    val partnerALabel = config.displayNameFor(PartnerRole.PARTNER_A).let {
        if (viewerRole == PartnerRole.PARTNER_A) "$it (You)" else it
    }
    val partnerBLabel = config.displayNameFor(PartnerRole.PARTNER_B).let {
        if (viewerRole == PartnerRole.PARTNER_B) "$it (You)" else it
    }
    val payerName = if (transaction.paidBy == viewerRole) {
        "${config.displayNameFor(transaction.paidBy)} (You)"
    } else {
        config.displayNameFor(transaction.paidBy)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CategoryBadge(
                    category = transaction.category,
                    size = 52.dp,
                    iconSize = 26.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transaction.description,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (transaction.isSettlement) {
                            "Settlement"
                        } else {
                            "${transaction.category.group.displayName} • ${transaction.category.name}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Total & Impact Card
            val cmp = impact.netForViewer.compareTo(BigDecimal.ZERO)
            val (badgeBg, badgeFg, impactText) = when {
                transaction.isSettlement -> Triple(
                    financeColors.settledContainer,
                    financeColors.onSettledContainer,
                    if (transaction.paidBy == viewerRole) {
                        "You paid ${config.displayNameFor(viewerRole.other())}"
                    } else {
                        "${config.displayNameFor(transaction.paidBy)} paid you"
                    },
                )
                cmp > 0 -> Triple(
                    financeColors.positiveContainer,
                    financeColors.onPositiveContainer,
                    "You lent ${MoneyFormatter.format(impact.netForViewer, currency)}",
                )
                cmp < 0 -> Triple(
                    financeColors.negativeContainer,
                    financeColors.onNegativeContainer,
                    "You borrowed ${MoneyFormatter.format(impact.netForViewer.abs(), currency)}",
                )
                else -> Triple(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    "No net balance change",
                )
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Total amount",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = MoneyFormatter.format(transaction.amount, currency),
                            style = MaterialTheme.typography.headlineMedium.tabular,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = badgeBg,
                    ) {
                        Text(
                            text = impactText,
                            style = MaterialTheme.typography.labelLarge.tabular,
                            color = badgeFg,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            // Breakdown details
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DetailRow(label = "Date", value = formatFullDate(transaction.date))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DetailRow(label = "Paid by", value = payerName)
                    if (!transaction.isSettlement) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        DetailRow(
                            label = "Split type",
                            value = formatSplitTypeLabel(transaction.splitType, transaction.splitMeta),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        DetailRow(
                            label = "$partnerALabel share",
                            value = MoneyFormatter.format(transaction.partnerAShare, currency),
                            emphasizeValue = true,
                        )
                        DetailRow(
                            label = "$partnerBLabel share",
                            value = MoneyFormatter.format(transaction.partnerBShare, currency),
                            emphasizeValue = true,
                        )
                    }
                    if (transaction.notes.isNotBlank()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Notes",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = transaction.notes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DetailRow(
                        label = "Record ID",
                        value = transaction.txId.take(12),
                    )
                }
            }

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!transaction.isSettlement) {
                    FilledTonalButton(
                        onClick = { onEdit(transaction) },
                        enabled = !isMutating,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Edit")
                    }
                }
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    enabled = !isMutating,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete")
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(if (transaction.isSettlement) "Delete settlement?" else "Delete expense?")
            },
            text = {
                Text(
                    "This appends a VOID entry to your Google Sheet so your history stays auditable, " +
                        "and removes \"${transaction.description}\" from your active balance and feed.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showDeleteConfirm = false
                        onDelete(transaction)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    emphasizeValue: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.tabular.copy(
                fontWeight = if (emphasizeValue) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
