package app.shomar.data.calendar

import java.time.DateTimeException
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

object GregorianCalendarSystem : CalendarSystem {
    override val id = "gregorian"
    override val displayName = "Gregorian"

    override fun month(year: Int, month: Int): CalendarMonth {
        val first = LocalDate.of(year, month, 1)
        val last = first.withDayOfMonth(first.lengthOfMonth())
        val name = Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        return CalendarMonth(year, month, name, DateRange(first, last))
    }

    override fun monthOf(date: LocalDate): CalendarMonth = month(date.year, date.monthValue)

    override fun formatNumeric(date: LocalDate): String = date.toString() // ISO: 2026-10-24

    override fun formatLong(date: LocalDate): String {
        val monthName = date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        return "${date.dayOfMonth} $monthName ${date.year}"
    }

    override fun parse(text: String): LocalDate? {
        val (year, month, day) = DateParts.parse(text) ?: return null
        return try {
            LocalDate.of(year, month, day)
        } catch (e: DateTimeException) {
            null
        }
    }
}