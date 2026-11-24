package vige.land.muchopesos.ui.screens.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import vige.land.muchopesos.ui.screens.dashboard.DashboardScreen
import vige.land.muchopesos.ui.screens.dashboard.DashboardViewModel
import vige.land.muchopesos.ui.screens.statistics.StatisticsScreen
import vige.land.muchopesos.ui.screens.statistics.StatisticsViewModel

enum class HomeTab {
    LEDGER,
    STATISTICS,
}

@Composable
fun HomeShellScreen(
    dashboardViewModel: DashboardViewModel,
    statisticsViewModel: StatisticsViewModel,
    onAddExpense: () -> Unit,
    onEditExpense: (txId: String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var activeTab by rememberSaveable { mutableStateOf(HomeTab.LEDGER) }

    val sharedBottomBar: @Composable () -> Unit = {
        NavigationBar {
            NavigationBarItem(
                selected = activeTab == HomeTab.LEDGER,
                onClick = { activeTab = HomeTab.LEDGER },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.ReceiptLong,
                        contentDescription = "Ledger",
                    )
                },
                label = { Text("Ledger") },
            )
            NavigationBarItem(
                selected = activeTab == HomeTab.STATISTICS,
                onClick = { activeTab = HomeTab.STATISTICS },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.BarChart,
                        contentDescription = "Statistics",
                    )
                },
                label = { Text("Statistics") },
            )
        }
    }

    when (activeTab) {
        HomeTab.LEDGER -> DashboardScreen(
            viewModel = dashboardViewModel,
            onAddExpense = onAddExpense,
            onEditExpense = onEditExpense,
            onOpenSettings = onOpenSettings,
            bottomBar = sharedBottomBar,
        )

        HomeTab.STATISTICS -> StatisticsScreen(
            viewModel = statisticsViewModel,
            onOpenSettings = onOpenSettings,
            bottomBar = sharedBottomBar,
        )
    }
}
