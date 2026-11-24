package vige.land.muchopesos.ui.screens.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.math.BigDecimal
import vige.land.muchopesos.domain.logic.MoneyFormatter
import vige.land.muchopesos.domain.model.PartnerRole
import vige.land.muchopesos.domain.model.SplitType
import vige.land.muchopesos.ui.components.CategoryBadge
import vige.land.muchopesos.ui.components.LedgerDatePickerDialog
import vige.land.muchopesos.ui.components.formatFullDate
import vige.land.muchopesos.ui.theme.MuchoPesosTheme
import vige.land.muchopesos.ui.theme.tabular

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditorScreen(
    viewModel: ExpenseEditorViewModel,
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    var showCategorySheet by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val config = state.config
    val currency = state.currency
    val partnerALabel = config?.displayNameFor(PartnerRole.PARTNER_A).orEmpty().ifBlank { "Partner A" }
    val partnerBLabel = config?.displayNameFor(PartnerRole.PARTNER_B).orEmpty().ifBlank { "Partner B" }

    val partnerAWithYou = if (state.viewerRole == PartnerRole.PARTNER_A) {
        "$partnerALabel (You)"
    } else {
        partnerALabel
    }
    val partnerBWithYou = if (state.viewerRole == PartnerRole.PARTNER_B) {
        "$partnerBLabel (You)"
    } else {
        partnerBLabel
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (state.isEditMode) "Edit Expense" else "Add Expense")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (state.isEditMode) {
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            enabled = !state.isSaving,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete expense",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                BoxSaveBar(
                    isEditMode = state.isEditMode,
                    isSaving = state.isSaving,
                    canSave = state.canSave,
                    onSave = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.save(onSuccess = onNavigateBack)
                    },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = state.errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            // Amount field locked to home currency (matched height, shape, and scale with Description)
            val showAmountError = state.amountInput.isNotBlank() && state.parsedAmount == null
            OutlinedTextField(
                value = state.amountInput,
                onValueChange = viewModel::onAmountChange,
                label = { Text("Amount (${currency.code})") },
                prefix = {
                    Text(
                        text = "${currency.symbol} ",
                        style = MaterialTheme.typography.bodyLarge.tabular.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge.tabular.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (currency.fractionDigits > 0) {
                        KeyboardType.Decimal
                    } else {
                        KeyboardType.Number
                    },
                ),
                isError = showAmountError,
                supportingText = if (showAmountError) {
                    {
                        Text("Enter a positive amount with up to ${currency.fractionDigits} decimals")
                    }
                } else {
                    null
                },
                enabled = !state.isSaving,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            // Description
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Description") },
                placeholder = { Text("e.g., Weekly Groceries, Gas Station") },
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                enabled = !state.isSaving,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            // Category & Date selector cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(enabled = !state.isSaving) { showCategorySheet = true },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CategoryBadge(
                            category = state.category,
                            size = 36.dp,
                            iconSize = 19.dp,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.category.group.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = state.category.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowRight,
                            contentDescription = "Choose category",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(enabled = !state.isSaving) { showDatePicker = true },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Date",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = formatFullDate(state.date),
                                style = MaterialTheme.typography.titleSmall.tabular,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // Paid By segmented selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Paid by",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SegmentedButton(
                        selected = state.paidBy == PartnerRole.PARTNER_A,
                        onClick = { viewModel.onPaidByChange(PartnerRole.PARTNER_A) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        enabled = !state.isSaving,
                    ) {
                        Text(
                            text = partnerAWithYou,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    SegmentedButton(
                        selected = state.paidBy == PartnerRole.PARTNER_B,
                        onClick = { viewModel.onPaidByChange(PartnerRole.PARTNER_B) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        enabled = !state.isSaving,
                    ) {
                        Text(
                            text = partnerBWithYou,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Split Type segmented selector
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Split",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val splitModes = listOf(
                    SplitType.EQUAL to "Equal",
                    SplitType.EXACT to "Exact",
                    SplitType.PERCENTAGE to "%",
                    SplitType.FULL to "Full",
                )

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    splitModes.forEachIndexed { index, (type, label) ->
                        SegmentedButton(
                            selected = state.splitType == type,
                            onClick = { viewModel.onSplitTypeChange(type) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = splitModes.size,
                            ),
                            enabled = !state.isSaving,
                        ) {
                            Text(label)
                        }
                    }
                }

                SplitDetailsCard(
                    state = state,
                    partnerALabel = partnerAWithYou,
                    partnerBLabel = partnerBWithYou,
                    onExactPartnerAChange = viewModel::onExactPartnerAChange,
                    onExactPartnerBChange = viewModel::onExactPartnerBChange,
                    onFillRemainingFor = viewModel::fillRemainingFor,
                    onPercentPartnerAChange = viewModel::onPercentPartnerAChange,
                    onPercentPartnerBChange = viewModel::onPercentPartnerBChange,
                    onFullOwedByChange = viewModel::onFullOwedByChange,
                )
            }

            // Notes
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Notes (optional)") },
                placeholder = { Text("Add receipt details or context") },
                minLines = 2,
                maxLines = 4,
                enabled = !state.isSaving,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showCategorySheet) {
        CategoryPickerSheet(
            selectedCategory = state.category,
            onCategorySelected = {
                viewModel.onCategorySelected(it)
                showCategorySheet = false
            },
            onDismiss = { showCategorySheet = false },
        )
    }

    if (showDatePicker) {
        LedgerDatePickerDialog(
            initialDate = state.date,
            onDateSelected = {
                viewModel.onDateSelected(it)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete expense?") },
            text = {
                Text(
                    "This appends a VOID record to your Google Sheet (preserving audit history) " +
                        "and removes this expense from your active feed and net balance.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showDeleteDialog = false
                        viewModel.delete(onSuccess = onNavigateBack)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitDetailsCard(
    state: ExpenseEditorUiState,
    partnerALabel: String,
    partnerBLabel: String,
    onExactPartnerAChange: (String) -> Unit,
    onExactPartnerBChange: (String) -> Unit,
    onFillRemainingFor: (PartnerRole) -> Unit,
    onPercentPartnerAChange: (String) -> Unit,
    onPercentPartnerBChange: (String) -> Unit,
    onFullOwedByChange: (PartnerRole) -> Unit,
) {
    val currency = state.currency
    val financeColors = MuchoPesosTheme.financeColors
    val liveSplit = state.liveSplitCalculation?.getOrNull()

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state.splitType) {
                SplitType.EQUAL, SplitType.SETTLEMENT -> {
                    Text(
                        text = "Split 50/50 between partners",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (liveSplit != null) {
                        SharePreviewRow(
                            label = partnerALabel,
                            amount = MoneyFormatter.format(liveSplit.partnerAShare, currency),
                        )
                        SharePreviewRow(
                            label = partnerBLabel,
                            amount = MoneyFormatter.format(liveSplit.partnerBShare, currency),
                        )
                    } else {
                        Text(
                            text = "Enter an amount above to preview exact shares.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                SplitType.EXACT -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = state.exactPartnerAInput,
                            onValueChange = onExactPartnerAChange,
                            label = {
                                Text(
                                    text = partnerALabel,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            prefix = { Text("${currency.symbol} ") },
                            textStyle = MaterialTheme.typography.bodyLarge.tabular,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (currency.fractionDigits > 0) {
                                    KeyboardType.Decimal
                                } else {
                                    KeyboardType.Number
                                },
                            ),
                            enabled = !state.isSaving,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = state.exactPartnerBInput,
                            onValueChange = onExactPartnerBChange,
                            label = {
                                Text(
                                    text = partnerBLabel,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            prefix = { Text("${currency.symbol} ") },
                            textStyle = MaterialTheme.typography.bodyLarge.tabular,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (currency.fractionDigits > 0) {
                                    KeyboardType.Decimal
                                } else {
                                    KeyboardType.Number
                                },
                            ),
                            enabled = !state.isSaving,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    val remaining = state.exactRemaining
                    if (remaining != null) {
                        val remCmp = remaining.compareTo(BigDecimal.ZERO)
                        val (bg, fg, text) = when {
                            remCmp == 0 -> Triple(
                                financeColors.positiveContainer,
                                financeColors.onPositiveContainer,
                                "Allocated cleanly (${MoneyFormatter.format(BigDecimal.ZERO, currency)} remaining)",
                            )
                            remCmp > 0 -> Triple(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                MaterialTheme.colorScheme.onSurface,
                                "${MoneyFormatter.format(remaining, currency)} remaining to allocate",
                            )
                            else -> Triple(
                                financeColors.negativeContainer,
                                financeColors.onNegativeContainer,
                                "Over by ${MoneyFormatter.format(remaining.abs(), currency)}",
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = bg,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.labelMedium.tabular,
                                color = fg,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }

                        if (remCmp != 0) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                AssistChip(
                                    onClick = { onFillRemainingFor(PartnerRole.PARTNER_A) },
                                    label = { Text("Fill $partnerALabel") },
                                )
                                AssistChip(
                                    onClick = { onFillRemainingFor(PartnerRole.PARTNER_B) },
                                    label = { Text("Fill $partnerBLabel") },
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Enter a total amount above to validate exact shares.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                SplitType.PERCENTAGE -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = state.percentPartnerAInput,
                            onValueChange = onPercentPartnerAChange,
                            label = {
                                Text(
                                    text = "$partnerALabel %",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            suffix = { Text("%") },
                            textStyle = MaterialTheme.typography.bodyLarge.tabular,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            enabled = !state.isSaving,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = state.percentPartnerBInput,
                            onValueChange = onPercentPartnerBChange,
                            label = {
                                Text(
                                    text = "$partnerBLabel %",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            suffix = { Text("%") },
                            textStyle = MaterialTheme.typography.bodyLarge.tabular,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            enabled = !state.isSaving,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    val pctTotal = state.percentageTotal
                    val pctValid = pctTotal != null && pctTotal.compareTo(BigDecimal("100")) == 0
                    if (!pctValid) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = financeColors.negativeContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "Percentages must add up to 100%",
                                style = MaterialTheme.typography.labelMedium,
                                color = financeColors.onNegativeContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    } else if (liveSplit != null) {
                        SharePreviewRow(
                            label = "$partnerALabel (${state.percentPartnerAInput.trim()}%)",
                            amount = MoneyFormatter.format(liveSplit.partnerAShare, currency),
                        )
                        SharePreviewRow(
                            label = "$partnerBLabel (${state.percentPartnerBInput.trim()}%)",
                            amount = MoneyFormatter.format(liveSplit.partnerBShare, currency),
                        )
                    }
                }

                SplitType.FULL -> {
                    val formattedTotal = state.parsedAmount?.let {
                        MoneyFormatter.format(it, currency)
                    } ?: MoneyFormatter.format(BigDecimal.ZERO, currency)

                    Text(
                        text = "Who is responsible for 100% of this expense?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    FullOwedOptionRow(
                        label = "$partnerBLabel owes full $formattedTotal",
                        selected = state.fullOwedBy == PartnerRole.PARTNER_B,
                        onClick = { onFullOwedByChange(PartnerRole.PARTNER_B) },
                    )
                    FullOwedOptionRow(
                        label = "$partnerALabel owes full $formattedTotal",
                        selected = state.fullOwedBy == PartnerRole.PARTNER_A,
                        onClick = { onFullOwedByChange(PartnerRole.PARTNER_A) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SharePreviewRow(
    label: String,
    amount: String,
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = amount,
            style = MaterialTheme.typography.titleSmall.tabular.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun FullOwedOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.tabular,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun BoxSaveBar(
    isEditMode: Boolean,
    isSaving: Boolean,
    canSave: Boolean,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Button(
            onClick = onSave,
            enabled = canSave,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(if (isEditMode) "Updating sheet…" else "Saving expense…")
            } else {
                Text(
                    text = if (isEditMode) "Save Changes" else "Save Expense",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
