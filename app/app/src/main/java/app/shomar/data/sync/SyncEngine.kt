package app.shomar.data.sync

import app.shomar.data.local.AccountDao
import app.shomar.data.local.AccountEntity
import app.shomar.data.local.CategoryDao
import app.shomar.data.local.CategoryEntity
import app.shomar.data.local.SyncMetaDao
import app.shomar.data.local.SyncMetaEntity
import app.shomar.data.local.SyncState
import app.shomar.data.local.TransactionDao
import app.shomar.data.local.TransactionEntity
import app.shomar.data.remote.AccountDto
import app.shomar.data.remote.AccountPut
import app.shomar.data.remote.ApiClient
import app.shomar.data.remote.ApiException
import app.shomar.data.remote.CategoryDto
import app.shomar.data.remote.CategoryPut
import app.shomar.data.remote.TransactionDto
import app.shomar.data.remote.TransactionPut
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TRANSACTION_PAGE_SIZE = 500

/** What a sync did. Problems are per-item failures; the rest of the sync carried on. */
class SyncReport {
    var pushed = 0
    val pulled = linkedMapOf("accounts" to 0, "categories" to 0, "transactions" to 0)
    val problems = mutableListOf<String>()

    fun summary(): String = buildString {
        append("Pushed $pushed. Pulled ")
        append(pulled.entries.joinToString(", ") { "${it.value} ${it.key}" })
        append(".")
        if (problems.isNotEmpty()) {
            append("\n\nProblems:")
            problems.forEach { append("\n- $it") }
        }
    }
}

/**
 * Two-way sync between the local database and the server.
 *
 * 1. Push: send local creates/edits with PUT and deletions with DELETE.
 * 2. Pull: fetch everything the server changed since the last pull.
 *
 * Pushing first means a local edit isn't overwritten by an older server copy.
 * Throws IOException when the server can't be reached, and ApiException(401)
 * when the login is no longer valid; everything else ends up in the report.
 */
class SyncEngine(
    private val api: ApiClient,
    private val accounts: AccountDao,
    private val categories: CategoryDao,
    private val transactions: TransactionDao,
    private val meta: SyncMetaDao,
) {
    private val lock = Mutex() // never two syncs at once

    suspend fun sync(): SyncReport = lock.withLock {
        val report = SyncReport()
        push(report)
        pull(report)
        report
    }

    // ------------------------------------------------------------------ push

    private suspend fun push(report: SyncReport) {
        val pendingAccounts = accounts.getPending()
        val pendingCategories = categories.getPending()
        val pendingTransactions = transactions.getPending()

        // 1. Creates and edits, in dependency order: accounts, categories (parents first), transactions.
        pushUpserts(
            label = "account",
            rows = pendingAccounts.filter { it.syncState == SyncState.PENDING_UPSERT },
            report = report,
            idOf = { it.id },
            send = { api.putAccount(it.id, AccountPut(it.name, it.type, it.currency, it.openingBalanceMinor)) },
            onSent = { accounts.markSynced(it.id, it.localVersion) },
            onRejected = { accounts.delete(it.id) },
        )
        pushUpserts(
            label = "category",
            rows = pendingCategories
                .filter { it.syncState == SyncState.PENDING_UPSERT }
                .sortedBy { it.parentId != null }, // top-level ones first
            report = report,
            idOf = { it.id },
            send = { api.putCategory(it.id, CategoryPut(it.name, it.kind, it.parentId)) },
            onSent = { categories.markSynced(it.id, it.localVersion) },
            onRejected = { categories.delete(it.id) },
        )
        pushUpserts(
            label = "transaction",
            rows = pendingTransactions.filter { it.syncState == SyncState.PENDING_UPSERT },
            report = report,
            idOf = { it.id },
            send = {
                api.putTransaction(
                    it.id,
                    TransactionPut(it.accountId, it.categoryId, it.amountMinor, it.occurredOn, it.merchant, it.note),
                )
            },
            onSent = { transactions.markSynced(it.id, it.localVersion) },
            onRejected = { transactions.delete(it.id) },
        )

        // 2. Deletions, the other way round: transactions, categories, accounts.
        pushDeletes(
            label = "transaction",
            ids = pendingTransactions.filter { it.syncState == SyncState.PENDING_DELETE }.map { it.id },
            path = "/transactions",
            report = report,
            onDeleted = { transactions.deletePendingDelete(it) },
        )
        pushDeletes(
            label = "category",
            ids = pendingCategories.filter { it.syncState == SyncState.PENDING_DELETE }.map { it.id },
            path = "/categories",
            report = report,
            onDeleted = { categories.deletePendingDelete(it) },
        )
        pushDeletes(
            label = "account",
            ids = pendingAccounts.filter { it.syncState == SyncState.PENDING_DELETE }.map { it.id },
            path = "/accounts",
            report = report,
            onDeleted = { accounts.deletePendingDelete(it) },
        )
    }

    private suspend fun <E> pushUpserts(
        label: String,
        rows: List<E>,
        report: SyncReport,
        idOf: (E) -> String,
        send: suspend (E) -> Unit,
        onSent: suspend (E) -> Unit,
        onRejected: suspend (E) -> Unit,
    ) {
        for (row in rows) {
            try {
                send(row)
                onSent(row)
                report.pushed++
            } catch (e: ApiException) {
                when (e.code) {
                    401 -> throw e
                    // The server refuses this id (e.g. the item was deleted there): the server wins.
                    409 -> {
                        onRejected(row)
                        report.problems += "$label ${idOf(row)}: ${e.message} (dropped locally)"
                    }
                    // Anything else (e.g. 422): keep the row and report it; it is retried next time.
                    else -> report.problems += "$label ${idOf(row)}: ${e.message}"
                }
            }
        }
    }

    private suspend fun pushDeletes(
        label: String,
        ids: List<String>,
        path: String,
        report: SyncReport,
        onDeleted: suspend (String) -> Unit,
    ) {
        for (id in ids) {
            try {
                try {
                    api.delete("$path/$id")
                } catch (e: ApiException) {
                    if (e.code != 404) throw e // 404: the server never had it, so it's already gone
                }
                onDeleted(id)
                report.pushed++
            } catch (e: ApiException) {
                if (e.code == 401) throw e
                report.problems += "$label $id: ${e.message}"
            }
        }
    }

    // ------------------------------------------------------------------ pull

    private suspend fun pull(report: SyncReport) {
        // Order doesn't matter for correctness (there are no foreign keys locally).
        pullResource(
            name = "accounts",
            report = report,
            pageSize = null, // the server returns all accounts in one go
            fetch = { since, _ -> api.listAccounts(since) },
            updatedAtOf = { it.updatedAt },
            applyItem = { applyAccount(it) },
        )
        pullResource(
            name = "categories",
            report = report,
            pageSize = null,
            fetch = { since, _ -> api.listCategories(since) },
            updatedAtOf = { it.updatedAt },
            applyItem = { applyCategory(it) },
        )
        pullResource(
            name = "transactions",
            report = report,
            pageSize = TRANSACTION_PAGE_SIZE,
            fetch = { since, offset -> api.listTransactions(since, TRANSACTION_PAGE_SIZE, offset) },
            updatedAtOf = { it.updatedAt },
            applyItem = { applyTransaction(it) },
        )
    }

    /**
     * Fetches everything changed since this resource's cursor, page by page, applies it,
     * and only then moves the cursor forward (so a failure halfway just repeats the work).
     * The cursor is the newest `updated_at` the server itself reported, so the phone's
     * clock plays no part.
     */
    private suspend fun <D> pullResource(
        name: String,
        report: SyncReport,
        pageSize: Int?,
        fetch: suspend (since: String, offset: Int) -> List<D>,
        updatedAtOf: (D) -> String,
        applyItem: suspend (D) -> Boolean,
    ) {
        val key = "pull.$name"
        val since = meta.get(key)?.let { Instant.parse(it) } ?: Instant.EPOCH
        var newest = since
        var offset = 0
        while (true) {
            val page = fetch(since.toString(), offset)
            for (item in page) {
                if (applyItem(item)) report.pulled[name] = report.pulled.getValue(name) + 1
                val updated = OffsetDateTime.parse(updatedAtOf(item)).toInstant()
                if (updated > newest) newest = updated
            }
            if (pageSize == null || page.size < pageSize) break
            offset += page.size
        }
        meta.put(SyncMetaEntity(key, newest.toString()))
    }

    // Local changes that haven't been pushed yet win over the server's copy.

    private suspend fun applyAccount(dto: AccountDto): Boolean {
        val local = accounts.get(dto.id)
        if (local != null && local.syncState != SyncState.SYNCED) return false
        if (dto.deletedAt != null) {
            if (local != null) accounts.delete(dto.id)
            return local != null
        }
        accounts.upsert(
            AccountEntity(
                id = dto.id,
                name = dto.name,
                type = dto.type,
                currency = dto.currency,
                openingBalanceMinor = dto.openingBalanceMinor,
                syncState = SyncState.SYNCED,
                localVersion = local?.localVersion ?: 0,
            ),
        )
        return true
    }

    private suspend fun applyCategory(dto: CategoryDto): Boolean {
        val local = categories.get(dto.id)
        if (local != null && local.syncState != SyncState.SYNCED) return false
        if (dto.deletedAt != null) {
            if (local != null) categories.delete(dto.id)
            return local != null
        }
        categories.upsert(
            CategoryEntity(
                id = dto.id,
                name = dto.name,
                kind = dto.kind,
                parentId = dto.parentId,
                syncState = SyncState.SYNCED,
                localVersion = local?.localVersion ?: 0,
            ),
        )
        return true
    }

    private suspend fun applyTransaction(dto: TransactionDto): Boolean {
        val local = transactions.get(dto.id)
        if (local != null && local.syncState != SyncState.SYNCED) return false
        if (dto.deletedAt != null) {
            if (local != null) transactions.delete(dto.id)
            return local != null
        }
        transactions.upsert(
            TransactionEntity(
                id = dto.id,
                accountId = dto.accountId,
                categoryId = dto.categoryId,
                amountMinor = dto.amountMinor,
                occurredOn = dto.occurredOn,
                merchant = dto.merchant,
                note = dto.note,
                syncState = SyncState.SYNCED,
                localVersion = local?.localVersion ?: 0,
            ),
        )
        return true
    }
}
