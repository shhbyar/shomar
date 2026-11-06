package app.shomar.ui.transactions

import app.shomar.data.model.Money
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.math.abs

object TransactionForm {
    /**
     * The signed amount in minor units: expenses are negative, income positive. A minus sign
     * typed by the user doesn't flip it (the Expense/Income choice decides). Null when the
     * text isn't a valid amount for the currency, or is zero.
     */
    fun signedAmount(text: String, currencyCode: String, isExpense: Boolean): Long? {
        val parsed = Money.parse(text, currencyCode) ?: return null
        val magnitude = abs(parsed)
        if (magnitude == 0L) return null
        return if (isExpense) -magnitude else magnitude
    }

    /** An ISO date such as 2026-10-22, or null. */
    fun parseDate(text: String): LocalDate? =
        try {
            LocalDate.parse(text.trim())
        } catch (e: DateTimeParseException) {
            null
        }
}