package app.shomar.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---- What the server sends (unknown fields such as created_at are ignored) ----

@Serializable
data class AccountDto(
    val id: String,
    val name: String,
    val type: String,
    val currency: String,
    @SerialName("opening_balance_minor") val openingBalanceMinor: Long,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    val kind: String,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class TransactionDto(
    val id: String,
    @SerialName("account_id") val accountId: String,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("amount_minor") val amountMinor: Long,
    @SerialName("occurred_on") val occurredOn: String,
    val merchant: String? = null,
    val note: String? = null,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

// ---- What the phone sends with PUT (the id travels in the URL) ----

@Serializable
data class AccountPut(
    val name: String,
    val type: String,
    val currency: String,
    @SerialName("opening_balance_minor") val openingBalanceMinor: Long,
)

@Serializable
data class CategoryPut(
    val name: String,
    val kind: String,
    @SerialName("parent_id") val parentId: String?,
)

@Serializable
data class TransactionPut(
    @SerialName("account_id") val accountId: String,
    @SerialName("category_id") val categoryId: String?,
    @SerialName("amount_minor") val amountMinor: Long,
    @SerialName("occurred_on") val occurredOn: String,
    val merchant: String?,
    val note: String?,
)
