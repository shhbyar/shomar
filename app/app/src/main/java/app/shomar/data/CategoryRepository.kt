package app.shomar.data

import app.shomar.data.local.CategoryDao
import app.shomar.data.local.CategoryEntity
import app.shomar.data.local.SyncState
import app.shomar.data.model.Category
import app.shomar.data.model.CategoryKind
import app.shomar.data.sync.SyncTrigger
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Offline-first, like AccountRepository: write locally, then ask for a sync. */
class CategoryRepository(
    private val dao: CategoryDao,
    private val syncTrigger: SyncTrigger,
) {

    fun observeAll(): Flow<List<Category>> =
        dao.observeActive().map { rows -> rows.map { it.toModel() } }

    /** `parentId` makes it a subcategory; the parent must be a top-level category of the same kind. */
    suspend fun create(name: String, kind: CategoryKind, parentId: String? = null): Category {
        val entity = CategoryEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            kind = kind.wire,
            parentId = parentId,
            syncState = SyncState.PENDING_UPSERT,
            localVersion = 1,
        )
        dao.upsert(entity)
        syncTrigger.requestSync()
        return entity.toModel()
    }

    /**
     * Hides the category and its subcategories at once, as the server will do. Transactions
     * in them show as uncategorized right away and are updated for good by the next sync.
     */
    suspend fun delete(id: String) {
        dao.childIds(id).forEach { dao.markDeleted(it) }
        dao.markDeleted(id)
        syncTrigger.requestSync()
    }
}

private fun CategoryEntity.toModel() = Category(
    id = id,
    name = name,
    kind = CategoryKind.fromWire(kind),
    parentId = parentId,
)