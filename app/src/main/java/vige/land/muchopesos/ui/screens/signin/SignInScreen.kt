package vige.land.muchopesos.ui.screens.signin

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import vige.land.muchopesos.data.auth.AuthScopeResult
import vige.land.muchopesos.data.auth.GoogleAuthManager
import vige.land.muchopesos.data.auth.GoogleSignInResult
import vige.land.muchopesos.data.repository.LedgerRepository
import vige.land.muchopesos.domain.model.UserSession
import vige.land.muchopesos.ui.theme.MuchoPesosTheme

@Composable
fun SignInScreen(
    googleAuthManager: GoogleAuthManager,
    ledgerRepository: LedgerRepository,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val isConfigured = remember { googleAuthManager.isGoogleSignInConfigured() }
    var isSigningInGoogle by remember { mutableStateOf(false) }
    var isStartingDemo by remember { mutableStateOf(false) }
    var pendingSession by remember { mutableStateOf<UserSession?>(null) }

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { activityResult ->
        val session = pendingSession
        pendingSession = null
        if (session == null) {
            isSigningInGoogle = false
            return@rememberLauncherForActivityResult
        }

        val token = googleAuthManager.extractTokenFromActivityResult(activityResult.data)
        if (token != null) {
            scope.launch {
                ledgerRepository.onGoogleSignedIn(session)
                isSigningInGoogle = false
            }
        } else {
            isSigningInGoogle = false
            scope.launch {
                snackbarHostState.showSnackbar(
                    "Google Sheets & Drive permission is required to sync your shared ledger.",
                )
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top editorial brand section
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(34.dp),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Mucho Pesos",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Split shared household expenses with your partner — backed entirely by a Google Sheet you both own.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Feature highlights card
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        FeatureRow(
                            icon = Icons.Rounded.TableChart,
                            title = "Your Google Sheet, zero servers",
                            subtitle = "Append-only ledger with full audit trail — no subscription or third-party database.",
                        )
                        FeatureRow(
                            icon = Icons.Rounded.Calculate,
                            title = "Exact splits, down to the cent",
                            subtitle = "Equal 50/50, exact amounts, percentages, or 100% splits with exact decimal math.",
                        )
                        FeatureRow(
                            icon = Icons.Rounded.CloudDone,
                            title = "One clear net balance",
                            subtitle = "Always know who owes whom and settle up in one tap.",
                        )
                    }
                }

                if (!isConfigured) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MuchoPesosTheme.financeColors.demoContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MuchoPesosTheme.financeColors.onDemoContainer,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Google OAuth not configured yet",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MuchoPesosTheme.financeColors.onDemoContainer,
                                )
                                Text(
                                    text = "Add GOOGLE_WEB_CLIENT_ID to local.properties (see GOOGLE_CLOUD_SETUP.md) to enable live Google Sheets sync, or jump straight into Demo Mode below.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MuchoPesosTheme.financeColors.onDemoContainer,
                                )
                            }
                        }
                    }
                }
            }

            // Bottom action buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val activity = context.findActivity()
                        if (activity == null) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Unable to launch Google Sign-In from this context.")
                            }
                            return@Button
                        }

                        scope.launch {
                            isSigningInGoogle = true
                            when (val signIn = googleAuthManager.signInWithGoogle(activity)) {
                                is GoogleSignInResult.Cancelled -> {
                                    isSigningInGoogle = false
                                }
                                is GoogleSignInResult.Failed -> {
                                    isSigningInGoogle = false
                                    snackbarHostState.showSnackbar(signIn.message)
                                }
                                is GoogleSignInResult.Success -> {
                                    when (val auth = googleAuthManager.requestApiAuthorization()) {
                                        is AuthScopeResult.Authorized -> {
                                            ledgerRepository.onGoogleSignedIn(signIn.session)
                                            isSigningInGoogle = false
                                        }
                                        is AuthScopeResult.NeedsConsent -> {
                                            pendingSession = signIn.session
                                            val request = IntentSenderRequest.Builder(
                                                auth.pendingIntent.intentSender,
                                            ).build()
                                            consentLauncher.launch(request)
                                        }
                                        is AuthScopeResult.Failed -> {
                                            isSigningInGoogle = false
                                            snackbarHostState.showSnackbar(auth.message)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isSigningInGoogle && !isStartingDemo,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    if (isSigningInGoogle) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Signing in…")
                    } else {
                        Text(
                            text = "Sign in with Google",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }

                FilledTonalButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch {
                            isStartingDemo = true
                            val result = ledgerRepository.signInDemo()
                            isStartingDemo = false
                            result.onFailure { err ->
                                snackbarHostState.showSnackbar(
                                    err.message ?: "Failed to start Demo Mode.",
                                )
                            }
                        }
                    },
                    enabled = !isSigningInGoogle && !isStartingDemo,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                ) {
                    if (isStartingDemo) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Loading Demo Ledger…")
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Try Demo Mode",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
