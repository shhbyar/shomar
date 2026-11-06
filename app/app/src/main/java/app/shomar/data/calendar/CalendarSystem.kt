package app.shomar.data.calendar

import java.time.LocalDate

/** An inclusive range of ISO dates. */
data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    operator fun contains(date: LocalDate): Boolean = date >= start && date <= endInclusive
}

/** One month of a calendar system, e.g. Aban 1405 = 2026-10-23 .. 2026-11-21. */
data class CalendarMonth(val year: Int, val month: Int, val name: String, val range: DateRange) {
    val title: String get() = "$name $year"
}

/**
 * A way of showing and grouping dates. Stored data is always Gregorian ISO dates; a calendar
 * system only decides which months exist and how dates are written and read.
 */
interface CalendarSystem {
    val id: String
    val displayName: String

    /** The month with this number (1-12) of this year. */
    fun month(year: Int, month: Int): CalendarMonth

    /** The month that contains `date`. */
    fun monthOf(date: LocalDate): CalendarMonth

    /** For text fields: "2026-10-24" or "1405/08/02". */
    fun formatNumeric(date: LocalDate): String

    /** For lists: "24 Oct 2026" or "2 Aban 1405". */
    fun formatLong(date: LocalDate): String

    /** Reads what formatNumeric writes (Persian digits and - / . separators also work); null if invalid. */
    fun parse(text: String): LocalDate?

    /** The month `months` after `month` (before it, if negative). */
    fun shift(month: CalendarMonth, months: Int): CalendarMonth {
        val index = month.year * 12 + (month.month - 1) + months
        return month(Math.floorDiv(index, 12), Math.floorMod(index, 12) + 1)
    }
}

object CalendarSystems {
    val all: List<CalendarSystem> = listOf(GregorianCalendarSystem, JalaliCalendarSystem)

    /** Unknown or missing ids give the Gregorian calendar. */
    fun byId(id: String?): CalendarSystem = all.firstOrNull { it.id == id } ?: GregorianCalendarSystem
}

/** Splits a typed date such as "1405/08/02" or "۱۴۰۵-۸-۲" into year, month and day. */
internal object DateParts {
    fun parse(text: String): Triple<Int, Int, Int>? {
        val numbers = mutableListOf<String>()
        val current = StringBuilder()
        for (ch in text.trim()) {
            val digit = Character.digit(ch, 10) // digits of any script
            when {
                digit >= 0 -> current.append(digit)
                ch == '-' || ch == '/' || ch == '.' || ch == '\u066B' -> {
                    if (current.isEmpty()) return null
                    numbers += current.toString()
                    current.clear()
                }
                else -> return null
            }
        }
        if (current.isNotEmpty()) numbers += current.toString()
        if (numbers.size != 3 || numbers[0].length != 4 || numbers[1].length > 2 || numbers[2].length > 2) return null
        return Triple(numbers[0].toInt(), numbers[1].toInt(), numbers[2].toInt())
    }
}