package app.shomar.data.model

import app.shomar.data.calendar.CalendarMonth

/** One currency's income and expenses in a month. Expenses are zero or negative. */
data class MonthTotals(val currency: String, val incomeMinor: Long, val expensesMinor: Long)

/**
 * The dashboard numbers for one currency (currencies are never added together).
 *
 * - `netMinor`: income plus expenses of the month.
 * - `balanceMinor`: what is in the accounts right now (credit cards not included).
 * - `debtMinor`: what is owed on credit cards right now, as a positive number.
 */
data class CurrencySummary(
    val currency: String,
    val incomeMinor: Long,
    val expensesMinor: Long,
    val netMinor: Long,
    val balanceMinor: Long,
    val debtMinor: Long,
)

data class DashboardState(val month: CalendarMonth, val summaries: List<CurrencySummary>)

object Dashboard {
    /** One summary per currency that has an account or a transaction in the month, sorted by code. */
    fun build(totals: List<MonthTotals>, accounts: List<Account>): List<CurrencySummary> {
        val currencies = (totals.map { it.currency } + accounts.map { it.currency }).distinct().sorted()
        return currencies.map { currency ->
            val monthly = totals.firstOrNull { it.currency == currency }
            val income = monthly?.incomeMinor ?: 0L
            val expenses = monthly?.expensesMinor ?: 0L
            val ofCurrency = accounts.filter { it.currency == currency }
            CurrencySummary(
                currency = currency,
                incomeMinor = income,
                expensesMinor = expenses,
                netMinor = income + expenses,
                balanceMinor = ofCurrency
                    .filter { it.type != AccountType.CREDIT_CARD }
                    .sumOf { it.balanceMinor },
                // A credit card that is in credit (positive balance) owes nothing.
                debtMinor = ofCurrency
                    .filter { it.type == AccountType.CREDIT_CARD }
                    .sumOf { maxOf(0L, -it.balanceMinor) },
            )
        }
    }
}