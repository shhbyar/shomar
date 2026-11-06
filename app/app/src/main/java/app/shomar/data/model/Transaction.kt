package app.shomar.data.model

import java.time.LocalDate

/** `amountMinor` is in the account's currency, negative = money out. See Money for the decimal rules. */
data class Transaction(
    val id: String,
    val accountId: String,
    val categoryId: String?,
    val amountMinor: Long,
    val occurredOn: LocalDate,
    val merchant: String?,
    val note: String?,
)