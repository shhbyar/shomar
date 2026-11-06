package app.shomar.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// These mirror the server's tables. Ids are UUID strings created on the phone.
// Money is in minor units (cents); negative = money out.
//
// `localVersion` goes up with every local edit. When a push succeeds, the row is
// marked as synced only if its version is still the one that was sent, so an edit
// made during the sync is never lost.

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String, // "cash" | "checking" | "savings" | "credit_card"
    val currency: String,
    val openingBalanceMinor: Long,
    val syncState: SyncState,
    val localVersion: Int = 0,
)

@Entity(tableName = "categories", indices = [Index("parentId")])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String, // "expense" | "income"
    val parentId: String?,
    val syncState: SyncState,
    val localVersion: Int = 0,
)

@Entity(
    tableName = "transactions",
    indices = [Index("accountId"), Index("categoryId"), Index("occurredOn")],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val categoryId: String?,
    val amountMinor: Long,
    val occurredOn: String, // ISO date such as "2026-10-01"; sorts correctly as text
    val merchant: String?,
    val note: String?,
    val syncState: SyncState,
    val localVersion: Int = 0,
)

/** Small key/value store, e.g. the time of the last successful pull. */
@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val name: String,
    val value: String,
)
