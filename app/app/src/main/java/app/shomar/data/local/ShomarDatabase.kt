package app.shomar.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        SyncMetaEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class ShomarDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun syncMetaDao(): SyncMetaDao
}
