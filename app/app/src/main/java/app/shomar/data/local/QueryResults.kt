package app.shomar.data.local

import androidx.room.Embedded

/** An account row plus its current balance (opening balance + its transactions). */
data class AccountWithBalance(
    @Embedded val account: AccountEntity,
    val balanceMinor: Long,
)

/** One currency's income and expenses within a date range. Expenses are zero or negative. */
data class MonthTotalsRow(
    val currency: String,
    val incomeMinor: Long,
    val expensesMinor: Long,
)