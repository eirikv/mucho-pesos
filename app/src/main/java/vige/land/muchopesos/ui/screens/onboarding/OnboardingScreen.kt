package vige.land.muchopesos.ui.screens.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vige.land.muchopesos.domain.model.CurrencyCatalog
import vige.land.muchopesos.domain.model.CurrencySpec
import vige.land.muchopesos.ui.theme.tabular

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session by viewModel.userSession.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    var showCurrencyPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect a Ledger") },
                actions = {
                    IconButton(onClick = { viewModel.signOut() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = "Sign out",
                        )
                    }
                },
            )
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
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (session != null) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = "Signed in as ${session?.displayName.orEmpty()}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = session?.email.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth(),
            ) {
                SegmentedButton(
                    selected = state.mode == OnboardingMode.CREATE,
                    onClick = { viewModel.onModeChange(OnboardingMode.CREATE) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {
                        SegmentedButtonDefaults.Icon(active = state.mode == OnboardingMode.CREATE) {
                            Icon(
                                imageVector = Icons.Rounded.AddCircleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                ) {
                    Text("Create New")
                }

                SegmentedButton(
                    selected = state.mode == OnboardingMode.LINK,
                    onClick = { viewModel.onModeChange(OnboardingMode.LINK) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {
                        SegmentedButtonDefaults.Icon(active = state.mode == OnboardingMode.LINK) {
                            Icon(
                                imageVector = Icons.Rounded.Link,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                ) {
                    Text("Link Existing")
                }
            }

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
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            if (state.mode == OnboardingMode.CREATE) {
                CreateLedgerSection(
                    state = state,
                    onLedgerNameChange = viewModel::onLedgerNameChange,
                    onOpenCurrencyPicker = { showCurrencyPicker = true },
                    onPartnerANameChange = viewModel::onPartnerANameChange,
                    onPartnerBEmailChange = viewModel::onPartnerBEmailChange,
                    onPartnerBNameChange = viewModel::onPartnerBNameChange,
                    onCreateClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.createLedger()
                    },
                )
            } else {
                LinkLedgerSection(
                    state = state,
                    onLinkInputChange = viewModel::onLinkInputChange,
                    onOpenDrivePicker = viewModel::openDrivePicker,
                    onLinkClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.linkLedger()
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showCurrencyPicker) {
        CurrencyPickerSheet(
            selected = state.selectedCurrency,
            onSelected = {
                viewModel.onCurrencySelected(it)
                showCurrencyPicker = false
            },
            onDismiss = { showCurrencyPicker = false },
        )
    }

    if (state.showDrivePicker) {
        DriveFilePickerDialog(
            searchQuery = state.driveSearchQuery,
            files = state.driveFiles,
            isLoading = state.isLoadingDriveFiles,
            errorMessage = state.driveErrorMessage,
            onSearchQueryChange = viewModel::onDriveSearchQueryChange,
            onSearchSubmit = { viewModel.loadDriveSpreadsheets() },
            onSelectFile = { item ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.linkLedger(overrideUrlOrId = item.id)
            },
            onDismiss = viewModel::dismissDrivePicker,
        )
    }
}

@Composable
private fun CreateLedgerSection(
    state: OnboardingUiState,
    onLedgerNameChange: (String) -> Unit,
    onOpenCurrencyPicker: () -> Unit,
    onPartnerANameChange: (String) -> Unit,
    onPartnerBEmailChange: (String) -> Unit,
    onPartnerBNameChange: (String) -> Unit,
    onCreateClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Create a new Google Sheet formatted with Config and Ledger tabs and automatically share editor access with your partner.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.ledgerName,
            onValueChange = onLedgerNameChange,
            label = { Text("Ledger name") },
            placeholder = { Text("e.g., Our Household") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )

        // Home currency selector card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(enabled = !state.isSubmitting) { onOpenCurrencyPicker() },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Home currency (locked for this ledger)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${state.selectedCurrency.code} (${state.selectedCurrency.symbol}) — ${state.selectedCurrency.name}",
                        style = MaterialTheme.typography.bodyLarge.tabular,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Choose currency",
                )
            }
        }

        OutlinedTextField(
            value = state.partnerAName,
            onValueChange = onPartnerANameChange,
            label = { Text("Your display name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.partnerBEmail,
            onValueChange = onPartnerBEmailChange,
            label = { Text("Partner's Gmail address") },
            placeholder = { Text("partner@gmail.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.partnerBName,
            onValueChange = onPartnerBNameChange,
            label = { Text("Partner's display name") },
            placeholder = { Text("e.g., Sam") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(6.dp))

        Button(
            onClick = onCreateClick,
            enabled = state.canCreate,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Creating & sharing Sheet…")
            } else {
                Text("Create & Share Ledger")
            }
        }
    }
}

@Composable
private fun LinkLedgerSection(
    state: OnboardingUiState,
    onLinkInputChange: (String) -> Unit,
    onOpenDrivePicker: () -> Unit,
    onLinkClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Already invited by your partner? Pick their shared spreadsheet from Google Drive or paste the Google Sheets link below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FilledTonalButton(
            onClick = onOpenDrivePicker,
            enabled = !state.isSubmitting,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Pick from Google Drive")
        }

        OutlinedTextField(
            value = state.linkInput,
            onValueChange = onLinkInputChange,
            label = { Text("Google Sheets URL or Spreadsheet ID") },
            placeholder = { Text("https://docs.google.com/spreadsheets/d/…") },
            minLines = 2,
            maxLines = 3,
            enabled = !state.isSubmitting,
            isError = state.linkInput.isNotBlank() && state.extractedSpreadsheetId == null,
            supportingText = {
                val detectedId = state.extractedSpreadsheetId
                when {
                    detectedId != null -> {
                        val preview = if (detectedId.length > 24) "${detectedId.take(24)}…" else detectedId
                        Text("Detected Spreadsheet ID: $preview")
                    }
                    state.linkInput.isNotBlank() -> {
                        Text("Paste a full Google Sheets URL or valid spreadsheet ID.")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(6.dp))

        Button(
            onClick = onLinkClick,
            enabled = state.canLink,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Linking spreadsheet…")
            } else {
                Text("Link Ledger")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyPickerSheet(
    selected: CurrencySpec,
    onSelected: (CurrencySpec) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Text(
                text = "Select home currency",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            ) {
                items(
                    items = CurrencyCatalog.all,
                    key = { it.code },
                ) { spec ->
                    val isSelected = spec.code == selected.code
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSelected(spec) },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    text = "${spec.code} • ${spec.name}",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = "Symbol: ${spec.symbol}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
