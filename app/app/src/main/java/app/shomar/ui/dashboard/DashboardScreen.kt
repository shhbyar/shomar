package app.shomar.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.shomar.data.AppState
import app.shomar.data.model.Money

/** One currency's card with its text already formatted. */
private data class CardText(
    val currency: String,
    val income: String,
    val expenses: String,
    val net: String,
    val balance: String,
    val debts: String,
)

@Composable
fun DashboardScreen(state: AppState, modifier: Modifier = Modifier) {
    val dashboard by state.dashboard.collectAsState()
    val offset by state.monthOffset.collectAsState()

    // Formatting happens only when the numbers change, not on every frame.
    val cards = remember(dashboard) {
        dashboard.summaries.map { summary ->
            val code = summary.currency
            CardText(
                currency = code,
                income = Money.format(summary.incomeMinor, code),
                expenses = Money.format(summary.expensesMinor, code),
                net = Money.format(summary.netMinor, code),
                balance = Money.format(summary.balanceMinor, code),
                debts = Money.format(summary.debtMinor, code),
            )
        }
    }

    LazyColumn(modifier = modifier.padding(horizontal = 16.dp)) {
        item {
            Column {
                Text("Dashboard", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = { state.monthOffset.value = offset - 1 }) { Text("‹") }
                    Text(
                        dashboard.month.title,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = { state.monthOffset.value = offset + 1 }) { Text("›") }
                }
                if (offset != 0) {
                    TextButton(onClick = { state.monthOffset.value = 0 }) { Text("Back to this month") }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (cards.isEmpty()) {
            item { Text("Nothing to show yet. Add an account and some transactions.") }
        }
        items(cards, key = { it.currency }) { card ->
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(card.currency, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    AmountLine("Income", card.income)
                    AmountLine("Expenses", card.expenses)
                    AmountLine("Net this month", card.net)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    AmountLine("Balance now", card.balance)
                    AmountLine("Debts", card.debts)
                }
            }
        }

        item {
            Text(
                "Due this month: coming with loans and installments.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AmountLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value)
    }
}