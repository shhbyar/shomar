package app.shomar.ui.accounts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.shomar.data.AccountRepository
import app.shomar.data.AppState
import app.shomar.data.model.Account
import app.shomar.data.model.AccountType
import app.shomar.data.model.Money
import app.shomar.ui.common.Picker
import kotlinx.coroutines.launch

// Temporary screen. The real screens come later.

// Placeholder until the user's default currency comes from the server.
private const val TEMP_CURRENCY = "EUR"

/** An account ready to draw. */
private data class AccountRow(val id: String, val name: String, val typeLabel: String, val balanceText: String)

private fun Account.toRow(): AccountRow {
    val balanceText = if (type == AccountType.CREDIT_CARD && balanceMinor < 0) {
        "Owed " + Money.format(-balanceMinor, currency)
    } else {
        Money.format(balanceMinor, currency)
    }
    return AccountRow(id, name, type.label, balanceText)
}

@Composable
fun AccountsScreen(state: AppState, repository: AccountRepository, modifier: Modifier = Modifier) {
    val accounts by state.accounts.collectAsState()
    val rows = remember(accounts) { accounts.map { it.toRow() } }
    val scope = rememberCoroutineScope()

    var name by rememberSaveable { mutableStateOf("") }
    var typeWire by rememberSaveable { mutableStateOf(AccountType.CHECKING.wire) }
    var currency by rememberSaveable { mutableStateOf(TEMP_CURRENCY) }
    var balance by rememberSaveable { mutableStateOf("") }

    val type = AccountType.fromWire(typeWire)
    val currencyCode = currency.trim().uppercase()
    // An empty balance means 0; null means "what was typed is not a valid amount".
    val balanceMinor: Long? = if (balance.isBlank()) 0L else Money.parse(balance, currencyCode)
    val canAdd = name.isNotBlank() && currencyCode.length == 3 && balanceMinor != null

    LazyColumn(modifier = modifier.padding(horizontal = 16.dp)) {
        item {
            Column {
                Text("Accounts", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("New account name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Picker(
                    label = "Type",
                    options = AccountType.entries.toList(),
                    selected = type,
                    optionLabel = { it.label },
                    onSelect = { typeWire = it.wire },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it },
                        label = { Text("Currency") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = balance,
                        onValueChange = { balance = it },
                        label = { Text("Opening balance") },
                        singleLine = true,
                        isError = balanceMinor == null,
                        modifier = Modifier.weight(2f),
                    )
                }
                if (balanceMinor == null) {
                    Text(
                        "That isn't a valid amount for $currencyCode",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = canAdd,
                    onClick = {
                        val amount = balanceMinor
                        if (amount != null) {
                            val newName = name.trim()
                            name = ""
                            balance = ""
                            scope.launch { repository.create(newName, type, currencyCode, amount) }
                        }
                    },
                ) {
                    Text("Add account")
                }
                Spacer(Modifier.height(8.dp))
                if (rows.isEmpty()) Text("No accounts yet. Add one above.")
            }
        }

        items(rows, key = { it.id }) { row ->
            ListItem(
                headlineContent = { Text(row.name) },
                supportingContent = { Text(row.typeLabel) },
                trailingContent = {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(row.balanceText)
                        TextButton(onClick = { scope.launch { repository.delete(row.id) } }) {
                            Text("Delete")
                        }
                    }
                },
            )
            HorizontalDivider()
        }
    }
}