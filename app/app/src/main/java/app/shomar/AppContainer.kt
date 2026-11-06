package app.shomar

import android.content.Context
import androidx.room.Room
import app.shomar.data.AccountRepository
import app.shomar.data.AppState
import app.shomar.data.CategoryRepository
import app.shomar.data.TransactionRepository
import app.shomar.data.local.ShomarDatabase
import app.shomar.data.remote.ApiClient
import app.shomar.data.remote.TokenStore
import app.shomar.data.settings.AppSettings
import app.shomar.data.sync.SyncEngine
import app.shomar.data.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

/** Composition root: builds the long-lived objects once and hands them out. */
class AppContainer(context: Context) {

    val serverUrl = "http://192.168.1.11:8000" // keep your own address here

    /** Lives as long as the app; the in-memory state keeps running in it. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database: ShomarDatabase =
        Room.databaseBuilder(context.applicationContext, ShomarDatabase::class.java, "shomar.db")
            .fallbackToDestructiveMigration(dropAllTables = true) // development only
            .build()

    val syncScheduler: SyncScheduler = SyncScheduler(context)

    val accounts: AccountRepository = AccountRepository(database.accountDao(), syncScheduler)
    val categories: CategoryRepository = CategoryRepository(database.categoryDao(), syncScheduler)
    val transactions: TransactionRepository = TransactionRepository(database.transactionDao(), syncScheduler)

    val tokens: TokenStore = TokenStore(context)
    val api: ApiClient = ApiClient(serverUrl, OkHttpClient(), tokens)
    val sync: SyncEngine = SyncEngine(
        api,
        database.accountDao(),
        database.categoryDao(),
        database.transactionDao(),
        database.syncMetaDao(),
    )

    val settings: AppSettings = AppSettings(context)
    val state: AppState = AppState(appScope, accounts, categories, transactions, settings)
}