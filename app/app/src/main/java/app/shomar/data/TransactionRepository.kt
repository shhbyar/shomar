package app.shomar.data

import app.shomar.data.local.SyncState
import app.shomar.data.local.TransactionDao
import app.shomar.data.local.TransactionEntity
import app.shomar.data.model.Transaction
import app.shomar.data.sync.SyncTrigger
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import app.shomar.data.calendar.DateRange
import app.shomar.data.model.MonthTotals

/** Offline-first, like AccountRepository: write locally, then ask for a sync. */
class TransactionRepository(
    private val dao: TransactionDao,
    private val syncTrigger: SyncTrigger,
) {

    /** Newest first. */
    fun observeAll(): Flow<List<Transaction>> =
        dao.observeActive().map { rows -> rows.map { it.toModel() } }

    /** Income and expenses per currency for the dates in `range`. */
    fun observeMonthTotals(range: DateRange): Flow<List<MonthTotals>> =
        dao.observeMonthTotals(range.start.toString(), range.endInclusive.toString())
            .map { rows -> rows.map { MonthTotals(it.currency, it.incomeMinor, it.expensesMinor) } }

    /** `amountMinor` is signed (negative = money out) and must not be zero. */
    suspend fun create(
        accountId: String,
        categoryId: String?,
        amountMinor: Long,
        occurredOn: LocalDate,
        merchant: String?,
        note: String?,
    ): Transaction {
        require(amountMinor != 0L) { "A transaction's amount cannot be zero" }
        val entity = TransactionEntity(
            id = UUID.randomUUID().toString(),
            accountId = accountId,
            categoryId = categoryId,
            amountMinor = amountMinor,
            occurredOn = occurredOn.toString(), // ISO, e.g. 2026-10-22
            merchant = merchant.blankToNull(),
            note = note.blankToNull(),
            syncState = SyncState.PENDING_UPSERT,
            localVersion = 1,
        )
        dao.upsert(entity)
        syncTrigger.requestSync()
        return entity.toModel()
    }

    suspend fun delete(id: String) {
        dao.markDeleted(id)
        syncTrigger.requestSync()
    }
}

private fun String?.blankToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun TransactionEntity.toModel() = Transaction(
    id = id,
    accountId = accountId,
    categoryId = categoryId,
    amountMinor = amountMinor,
    occurredOn = LocalDate.parse(occurredOn),
    merchant = merchant,
    note = note,
)