package app.shomar.data.model

enum class AccountType(val wire: String, val label: String) {
    CASH("cash", "Cash"),
    CHECKING("checking", "Checking"),
    SAVINGS("savings", "Savings"),
    CREDIT_CARD("credit_card", "Credit card");

    companion object {
        /** `wire` is the value the server and the local database use. */
        fun fromWire(value: String): AccountType = entries.first { it.wire == value }
    }
}

/** `balanceMinor` is the current balance: the opening balance plus the account's transactions. */
data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val currency: String,
    val openingBalanceMinor: Long,
    val balanceMinor: Long,
)