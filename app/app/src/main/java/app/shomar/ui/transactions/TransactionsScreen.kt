package app.shomar.ui.transactions

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
import app.shomar.data.AppState
import app.shomar.data.TransactionRepository
import app.shomar.data.model.CategoryKind
import app.shomar.ui.common.CategoryOption
import app.shomar.ui.common.CategoryOptions
import app.shomar.ui.common.ChoiceButton
import app.shomar.ui.common.Picker
import java.time.LocalDate
import kotlinx.coroutines.launch

private val NO_CATEGORY = CategoryOption(id = "", label = "No category")

@Composable
fun TransactionsScreen(
    state: AppState,
    transactions: TransactionRepository,
    modifier: Modifier = Modifier,
) {
    val rows by state.transactionRows.collectAsState()
    val allAccounts by state.accounts.collectAsState()
    val allCategories by state.categories.collectAsState()
    val calendar by state.calendar.collectAsState()
    val scope = rememberCoroutineScope()

    var isExpense by rememberSaveable { mutableStateOf(true) }
    var accountId by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    // The date is typed in the chosen calendar; it starts again from today when the calendar changes.
    var dateText by rememberSaveable(calendar.id) { mutableStateOf(calendar.formatNumeric(LocalDate.now())) }
    var merchant by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }

    val account = allAccounts.firstOrNull { it.id == accountId } ?: allAccounts.firstOrNull()
    val kind = if (isExpense) CategoryKind.EXPENSE else CategoryKind.INCOME
    val categoryOptions = remember(allCategories, kind) {
        listOf(NO_CATEGORY) + CategoryOptions.forKind(allCategories, kind)
    }
    val selectedCategory = categoryOptions.firstOrNull { it.id == categoryId } ?: NO_CATEGORY

    val amount: Long? = account?.let { TransactionForm.signedAmount(amountText, it.currency, isExpense) }
    val date: LocalDate? = calendar.parse(dateText)
    val canAdd = account != null && amount != null && date != null

    LazyColumn(modifier = modifier.padding(horizontal = 16.dp)) {
        item {
            Column {
                Text("Transactions", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))

                if (account == null) {
                    Text("Add an account first (Accounts tab), then you can record transactions.")
                } else {
                    Row {
                        ChoiceButton("Expense", selected = isExpense) { isExpense = true }
                        Spacer(Modifier.width(8.dp))
                        ChoiceButton("Income", selected = !isExpense) { isExpense = false }
                    }
                    Picker(
                        label = "Account",
                        options = allAccounts,
                        selected = account,
                        optionLabel = { it.name },
                        onSelect = { accountId = it.id },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Picker(
                        label = "Category",
                        options = categoryOptions,
                        selected = selectedCategory,
                        optionLabel = { it.label },
                        onSelect = { categoryId = it.id },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount in ${account.currency}") },
                        singleLine = true,
                        isError = amountText.isNotBlank() && amount == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it },
                        label = { Text("Date, ${calendar.displayName} (like ${calendar.formatNumeric(LocalDate.now())})") },
                        singleLine = true,
                        isError = date == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = merchant,
                        onValueChange = { merchant = it },
                        label = { Text("Merchant (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Note (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        enabled = canAdd,
                        onClick = {
                            val inAccount = account
                            val signedAmount = amount
                            val day = date
                            if (signedAmount != null && day != null) {
                                val chosenCategory = selectedCategory.id.takeIf { it.isNotEmpty() }
                                val merchantText = merchant
                                val noteText = note
                                amountText = ""
                                merchant = ""
                                note = ""
                                scope.launch {
                                    transactions.create(
                                        accountId = inAccount.id,
                                        categoryId = chosenCategory,
                                        amountMinor = signedAmount,
                                        occurredOn = day,
                                        merchant = merchantText,
                                        note = noteText,
                                    )
                                }
                            }
                        },
                    ) {
                        Text("Add transaction")
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        if (account != null && rows.isEmpty()) {
            item { Text("No transactions yet.") }
        }
        items(rows, key = { it.id }) { row ->
            ListItem(
                headlineContent = { Text(row.title) },
                supportingContent = { Text(row.subtitle) },
                trailingContent = {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            row.amountText,
                            color = if (row.isIncome) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        TextButton(onClick = { scope.launch { transactions.delete(row.id) } }) {
                            Text("Delete")
                        }
                    }
                },
            )
            HorizontalDivider()
        }
    }
}