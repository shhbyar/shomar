package app.shomar.data.settings

import android.content.Context
import app.shomar.data.calendar.CalendarSystem
import app.shomar.data.calendar.CalendarSystems
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User preferences, kept on this phone for now. Later they should live on the server
 * too, so the web app and the phone agree on the calendar.
 */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _calendar = MutableStateFlow(CalendarSystems.byId(prefs.getString(KEY_CALENDAR, null)))

    /** The calendar used to show dates and to decide where a month starts and ends. */
    val calendar: StateFlow<CalendarSystem> = _calendar.asStateFlow()

    fun setCalendar(system: CalendarSystem) {
        prefs.edit().putString(KEY_CALENDAR, system.id).apply()
        _calendar.value = system
    }

    private companion object {
        const val KEY_CALENDAR = "calendar"
    }
}