package vige.land.muchopesos.ui.screens.settle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.time.LocalDate
import vige.land.muchopesos.domain.logic.MoneyFormatter
import vige.land.muchopesos.domain.model.LedgerConfig
import vige.land.muchopesos.domain.model.NetBalanceSummary
import vige.land.muchopesos.domain.model.PartnerRole
import vige.land.muchopesos.ui.components.LedgerDatePickerDialog
import vige.land.muchopesos.ui.components.formatFullDate
import vige.land.muchopesos.ui.theme.MuchoPesosTheme
import vige.land.muchopesos.ui.theme.tabular

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleUpSheet(
    config: LedgerConfig,
    balance: NetBalanceSummary,
    viewerRole: PartnerRole,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirmSettlement: (
        paidBy: PartnerRole,
        amount: BigDecimal,
        date: LocalDate,
        notes: String,
    ) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current
    val currency = config.currencySpec
    val financeColors = MuchoPesosTheme.financeColors

    val initialPayer = balance.debtorRole ?: viewerRole
    val initialAmountText = if (!balance.isSettledUp && balance.amount.compareTo(BigDecimal.ZERO) > 0) {
        MoneyFormatter.toSheetPlainString(balance.amount, currency.fractionDigits)
    } else {
        ""
    }

    var paidBy by remember { mutableStateOf(initialPayer) }
    var amountInput by remember { mutableStateOf(initialAmountText) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var notes by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    val parsedAmount = remember(amountInput, currency.fractionDigits) {
        MoneyFormatter.parseUserAmount(amountInput, currency.fractionDigits)
    }
    val isAmountValid = parsedAmount != null && parsedAmount.compareTo(BigDecimal.ZERO) > 0
    val recipient = paidBy.other()

    val payerLabel = if (paidBy == viewerRole) {
        "${config.displayNameFor(paidBy)} (You)"
    } else {
        config.displayNameFor(paidBy)
    }
    val recipientLabel = if (recipient == viewerRole) {
        "${config.displayNameFor(recipient)} (You)"
    } else {
        config.displayNameFor(recipient)
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Settle up",
                style = MaterialTheme.typography.headlineSmall,
            )

            // Direction card with flip toggle
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = financeColors.settledContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = payerLabel,
                            style = MaterialTheme.typography.titleSmall,
                            color = financeColors.onSettledContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "pays",
                            tint = financeColors.onSettledContainer,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = recipientLabel,
                            style = MaterialTheme.typography.titleSmall,
                            color = financeColors.onSettledContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    IconButton(
                        onClick = { paidBy = paidBy.other() },
                        enabled = !isSubmitting,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SwapHoriz,
                            contentDescription = "Flip payment direction",
                            tint = financeColors.onSettledContainer,
                        )
                    }
                }
            }

            // Amount field
            OutlinedTextField(
                value = amountInput,
                onValueChange = { amountInput = it },
                label = { Text("Settlement amount (${currency.code})") },
                prefix = {
                    Text(
                        text = "${currency.symbol} ",
                        style = MaterialTheme.typography.titleLarge.tabular,
                    )
                },
                textStyle = MaterialTheme.typography.headlineSmall.tabular,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (currency.fractionDigits > 0) {
                        KeyboardType.Decimal
                    } else {
                        KeyboardType.Number
                    },
                ),
                isError = amountInput.isNotBlank() && !isAmountValid,
                supportingText = {
                    if (amountInput.isNotBlank() && !isAmountValid) {
                        Text("Enter a valid positive amount in ${currency.code}")
                    }
                },
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )

            // Quick fill chip if full balance differs
            if (!balance.isSettledUp && balance.amount.compareTo(BigDecimal.ZERO) > 0) {
                val fullPlain = MoneyFormatter.toSheetPlainString(balance.amount, currency.fractionDigits)
                if (amountInput.trim() != fullPlain) {
                    AssistChip(
                        onClick = {
                            amountInput = fullPlain
                            balance.debtorRole?.let { paidBy = it }
                        },
                        label = {
                            Text(
                                text = "Use full balance (${MoneyFormatter.format(balance.amount, currency)})",
                                style = MaterialTheme.typography.labelLarge.tabular,
                            )
                        },
                    )
                }
            }

            // Date selector
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isSubmitting) { showDatePicker = true },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Date",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatFullDate(selectedDate),
                            style = MaterialTheme.typography.bodyLarge.tabular,
                        )
                    }
                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Note (optional)") },
                placeholder = { Text("e.g., Bank transfer, Vipps, Venmo") },
                singleLine = false,
                maxLines = 3,
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = {
                    val amount = parsedAmount ?: return@Button
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onConfirmSettlement(paidBy, amount, selectedDate, notes.trim())
                },
                enabled = isAmountValid && !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Recording settlement…")
                } else {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (parsedAmount != null && isAmountValid) {
                            "Record ${MoneyFormatter.format(parsedAmount, currency)}"
                        } else {
                            "Confirm settlement"
                        },
                        style = MaterialTheme.typography.labelLarge.tabular,
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        LedgerDatePickerDialog(
            initialDate = selectedDate,
            onDateSelected = {
                selectedDate = it
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}
