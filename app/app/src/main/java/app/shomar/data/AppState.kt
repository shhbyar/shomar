package app.shomar.data

import app.shomar.data.calendar.CalendarSystem
import app.shomar.data.model.Account
import app.shomar.data.model.Category
import app.shomar.data.model.Dashboard
import app.shomar.data.model.DashboardState
import app.shomar.data.settings.AppSettings
import app.shomar.ui.transactions.TransactionRow
import app.shomar.ui.transactions.TransactionRows
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/**
 * What the screens show, kept in memory for as long as the app runs. Screens read the
 * latest value straight away instead of querying the database each time they appear,
 * and everything that needs formatting is prepared here, off the main thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppState(
    scope: CoroutineScope,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    transactionRepository: TransactionRepository,
    settings: AppSettings,
) {
    val calendar: StateFlow<CalendarSystem> = settings.calendar

    val accounts: StateFlow<List<Account>> =
        accountRepository.observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())

    val categories: StateFlow<List<Category>> =
        categoryRepository.observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Newest first, with text already formatted for the chosen calendar. */
    val transactionRows: StateFlow<List<TransactionRow>> =
        combine(transactionRepository.observeAll(), accounts, categories, calendar) { transactions, accountList, categoryList, system ->
            TransactionRows.build(transactions, accountList, categoryList, system)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Which month the dashboard shows: 0 = the current month, -1 = the one before, and so on. */
    val monthOffset = MutableStateFlow(0)

    val dashboard: StateFlow<DashboardState> =
        combine(calendar, monthOffset) { system, offset ->
            system.shift(system.monthOf(LocalDate.now()), offset)
        }
            .flatMapLatest { month ->
                transactionRepository.observeMonthTotals(month.range)
                    .combine(accounts) { totals, accountList ->
                        DashboardState(month, Dashboard.build(totals, accountList))
                    }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                DashboardState(calendar.value.monthOf(LocalDate.now()), emptyList()),
            )
}