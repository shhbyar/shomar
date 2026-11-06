package app.shomar.ui.transactions

import app.shomar.data.calendar.CalendarSystem
import app.shomar.data.model.Account
import app.shomar.data.model.Category
import app.shomar.data.model.Money
import app.shomar.data.model.Transaction
import app.shomar.ui.common.CategoryOptions
import java.util.Locale

/** A transaction ready to draw: all text is already formatted. */
data class TransactionRow(
    val id: String,
    val title: String,
    val subtitle: String,
    val amountText: String,
    val isIncome: Boolean,
)

object TransactionRows {
    /**
     * Prepares the list rows. Run this when the data changes (not while drawing).
     * Transactions of accounts that no longer exist are left out. The input order is kept.
     */
    fun build(
        transactions: List<Transaction>,
        accounts: List<Account>,
        categories: List<Category>,
        calendar: CalendarSystem,
        locale: Locale = Locale.getDefault(),
    ): List<TransactionRow> {
        val accountsById = accounts.associateBy { it.id }
        val labels = CategoryOptions.labelsById(categories)
        return transactions.mapNotNull { transaction ->
            val account = accountsById[transaction.accountId] ?: return@mapNotNull null
            val categoryLabel = transaction.categoryId?.let { labels[it] }
            TransactionRow(
                id = transaction.id,
                title = transaction.merchant ?: categoryLabel ?: "Transaction",
                subtitle = calendar.formatLong(transaction.occurredOn) +
                        " · " + (categoryLabel ?: "Uncategorized") +
                        " · " + account.name,
                amountText = Money.format(transaction.amountMinor, account.currency, locale),
                isIncome = transaction.amountMinor > 0,
            )
        }
    }
}