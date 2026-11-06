package app.shomar.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE syncState != 'PENDING_DELETE' ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<AccountEntity>>

    /** Accounts with their current balance; deleted transactions don't count. */
    @Query(
        """
        SELECT a.*, a.openingBalanceMinor + COALESCE(SUM(t.amountMinor), 0) AS balanceMinor
        FROM accounts a
        LEFT JOIN transactions t ON t.accountId = a.id AND t.syncState != 'PENDING_DELETE'
        WHERE a.syncState != 'PENDING_DELETE'
        GROUP BY a.id
        ORDER BY a.name COLLATE NOCASE
        """,
    )
    fun observeWithBalance(): Flow<List<AccountWithBalance>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE syncState != 'SYNCED'")
    suspend fun getPending(): List<AccountEntity>

    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Upsert
    suspend fun upsertAll(accounts: List<AccountEntity>)

    @Query("UPDATE accounts SET syncState = 'PENDING_DELETE' WHERE id = :id")
    suspend fun markDeleted(id: String)

    /** After a successful push: only if the row wasn't edited meanwhile. */
    @Query("UPDATE accounts SET syncState = 'SYNCED' WHERE id = :id AND localVersion = :version")
    suspend fun markSynced(id: String, version: Int)

    /** After the server confirmed a deletion. */
    @Query("DELETE FROM accounts WHERE id = :id AND syncState = 'PENDING_DELETE'")
    suspend fun deletePendingDelete(id: String)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE syncState != 'PENDING_DELETE' ORDER BY kind, name COLLATE NOCASE")
    fun observeActive(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE syncState != 'SYNCED'")
    suspend fun getPending(): List<CategoryEntity>

    /** Ids of the subcategories that are not already deleted. */
    @Query("SELECT id FROM categories WHERE parentId = :parentId AND syncState != 'PENDING_DELETE'")
    suspend fun childIds(parentId: String): List<String>

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Query("UPDATE categories SET syncState = 'PENDING_DELETE' WHERE id = :id")
    suspend fun markDeleted(id: String)

    @Query("UPDATE categories SET syncState = 'SYNCED' WHERE id = :id AND localVersion = :version")
    suspend fun markSynced(id: String, version: Int)

    @Query("DELETE FROM categories WHERE id = :id AND syncState = 'PENDING_DELETE'")
    suspend fun deletePendingDelete(id: String)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE syncState != 'PENDING_DELETE' ORDER BY occurredOn DESC, id")
    fun observeActive(): Flow<List<TransactionEntity>>

    /**
     * Income and expenses per currency for ISO dates from `from` to `to`, both included.
     * Transactions of deleted accounts and deleted transactions don't count.
     */
    @Query(
        """
        SELECT a.currency AS currency,
               COALESCE(SUM(CASE WHEN t.amountMinor > 0 THEN t.amountMinor END), 0) AS incomeMinor,
               COALESCE(SUM(CASE WHEN t.amountMinor < 0 THEN t.amountMinor END), 0) AS expensesMinor
        FROM transactions t
        JOIN accounts a ON a.id = t.accountId
        WHERE t.syncState != 'PENDING_DELETE' AND a.syncState != 'PENDING_DELETE'
          AND t.occurredOn BETWEEN :from AND :to
        GROUP BY a.currency
        """,
    )
    fun observeMonthTotals(from: String, to: String): Flow<List<MonthTotalsRow>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE syncState != 'SYNCED'")
    suspend fun getPending(): List<TransactionEntity>

    @Upsert
    suspend fun upsert(transaction: TransactionEntity)

    @Upsert
    suspend fun upsertAll(transactions: List<TransactionEntity>)

    @Query("UPDATE transactions SET syncState = 'PENDING_DELETE' WHERE id = :id")
    suspend fun markDeleted(id: String)

    @Query("UPDATE transactions SET syncState = 'SYNCED' WHERE id = :id AND localVersion = :version")
    suspend fun markSynced(id: String, version: Int)

    @Query("DELETE FROM transactions WHERE id = :id AND syncState = 'PENDING_DELETE'")
    suspend fun deletePendingDelete(id: String)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface SyncMetaDao {
    @Query("SELECT value FROM sync_meta WHERE name = :name")
    suspend fun get(name: String): String?

    @Upsert
    suspend fun put(entry: SyncMetaEntity)
}
