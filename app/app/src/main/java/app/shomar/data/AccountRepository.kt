package app.shomar.data

import app.shomar.data.local.AccountDao
import app.shomar.data.local.AccountEntity
import app.shomar.data.local.SyncState
import app.shomar.data.model.Account
import app.shomar.data.model.AccountType
import app.shomar.data.sync.SyncTrigger
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Offline-first: every change is written to the local database first and marked
 * as pending. The sync worker (a later step) pushes it to the server.
 */
class AccountRepository(
    private val dao: AccountDao,
    private val syncTrigger: SyncTrigger,
) {

    /** Accounts by name, each with its current balance. */
    fun observeAll(): Flow<List<Account>> =
        dao.observeWithBalance().map { rows -> rows.map { it.account.toModel(it.balanceMinor) } }

    suspend fun create(
        name: String,
        type: AccountType,
        currency: String,
        openingBalanceMinor: Long = 0,
    ): Account {
        val entity = AccountEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            type = type.wire,
            currency = currency,
            openingBalanceMinor = openingBalanceMinor,
            syncState = SyncState.PENDING_UPSERT,
            localVersion = 1,
        )
        dao.upsert(entity)
        syncTrigger.requestSync()
        return entity.toModel(balanceMinor = openingBalanceMinor)
    }

    /** Hidden immediately; removed for good once the server confirms. */
    suspend fun delete(id: String) {
        dao.markDeleted(id)
        syncTrigger.requestSync()
    }
}

private fun AccountEntity.toModel(balanceMinor: Long) = Account(
    id = id,
    name = name,
    type = AccountType.fromWire(type),
    currency = currency,
    openingBalanceMinor = openingBalanceMinor,
    balanceMinor = balanceMinor,
)