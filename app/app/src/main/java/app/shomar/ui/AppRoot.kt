package app.shomar.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.shomar.AppContainer
import app.shomar.ui.accounts.AccountsScreen
import app.shomar.ui.categories.CategoriesScreen
import app.shomar.ui.common.ChoiceButton
import app.shomar.ui.dashboard.DashboardScreen
import app.shomar.ui.server.ServerScreen
import app.shomar.ui.settings.SettingsScreen
import app.shomar.ui.transactions.TransactionsScreen

private val TABS = listOf("Dashboard", "Transactions", "Accounts", "Categories", "Settings", "Server")

// Temporary tab bar. Real navigation comes later.
@Composable
fun AppRoot(container: AppContainer) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                TABS.forEachIndexed { index, title ->
                    ChoiceButton(title, selected = tab == index) { tab = index }
                    Spacer(Modifier.width(8.dp))
                }
            }
            when (tab) {
                0 -> DashboardScreen(container.state, Modifier.weight(1f))
                1 -> TransactionsScreen(container.state, container.transactions, Modifier.weight(1f))
                2 -> AccountsScreen(container.state, container.accounts, Modifier.weight(1f))
                3 -> CategoriesScreen(container.state, container.categories, Modifier.weight(1f))
                4 -> SettingsScreen(container.settings, Modifier.weight(1f))
                else -> ServerScreen(
                    api = container.api,
                    tokens = container.tokens,
                    sync = container.sync,
                    serverUrl = container.serverUrl,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}