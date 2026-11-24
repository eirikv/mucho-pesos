package vige.land.muchopesos.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.io.File
import vige.land.muchopesos.data.auth.GoogleAuthManager
import vige.land.muchopesos.data.local.LocalLedgerCache
import vige.land.muchopesos.data.local.PreferencesStore
import vige.land.muchopesos.data.remote.GoogleApiFactory
import vige.land.muchopesos.data.repository.LedgerRepository
import vige.land.muchopesos.data.sheet.DemoSheetGateway
import vige.land.muchopesos.data.sheet.GoogleSheetsGateway
import vige.land.muchopesos.ui.screens.dashboard.DashboardViewModel
import vige.land.muchopesos.ui.screens.expense.ExpenseEditorViewModel
import vige.land.muchopesos.ui.screens.onboarding.OnboardingViewModel
import vige.land.muchopesos.ui.screens.settings.SettingsViewModel
import vige.land.muchopesos.ui.screens.statistics.StatisticsViewModel

/**
 * Lightweight manual dependency injection container for Mucho Pesos.
 */
class AppContainer(appContext: Context) {

    private val context = appContext.applicationContext

    val googleAuthManager: GoogleAuthManager by lazy {
        GoogleAuthManager(context)
    }

    private val preferencesStore: PreferencesStore by lazy {
        PreferencesStore(context)
    }

    private val localLedgerCache: LocalLedgerCache by lazy {
        LocalLedgerCache(File(context.filesDir, "ledger_cache.json"))
    }

    val demoSheetGateway: DemoSheetGateway by lazy {
        DemoSheetGateway(File(context.filesDir, "demo_sheet.json"))
    }

    private val googleSheetsGateway: GoogleSheetsGateway by lazy {
        val okHttpClient = GoogleApiFactory.createOkHttpClient(
            accessTokenProvider = { googleAuthManager.getFreshAccessToken() },
        )
        val sheetsApi = GoogleApiFactory.createSheetsApi(okHttpClient)
        val driveApi = GoogleApiFactory.createDriveApi(okHttpClient)
        GoogleSheetsGateway(
            sheetsApi = sheetsApi,
            driveApi = driveApi,
        )
    }

    val ledgerRepository: LedgerRepository by lazy {
        LedgerRepository(
            preferences = preferencesStore,
            localCache = localLedgerCache,
            googleGateway = googleSheetsGateway,
            demoGateway = demoSheetGateway,
            onGoogleSignOut = { googleAuthManager.signOut() },
        )
    }

    fun dashboardViewModelFactory(): ViewModelProvider.Factory = singleFactory {
        DashboardViewModel(ledgerRepository)
    }

    fun onboardingViewModelFactory(): ViewModelProvider.Factory = singleFactory {
        OnboardingViewModel(ledgerRepository)
    }

    fun expenseEditorViewModelFactory(editTxId: String?): ViewModelProvider.Factory = singleFactory {
        ExpenseEditorViewModel(
            repository = ledgerRepository,
            editTxId = editTxId,
        )
    }

    fun settingsViewModelFactory(): ViewModelProvider.Factory = singleFactory {
        SettingsViewModel(ledgerRepository)
    }

    fun statisticsViewModelFactory(): ViewModelProvider.Factory = singleFactory {
        StatisticsViewModel(ledgerRepository)
    }

    private inline fun <reified T : ViewModel> singleFactory(
        crossinline creator: () -> T,
    ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM {
            if (modelClass.isAssignableFrom(T::class.java)) {
                return creator() as VM
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
