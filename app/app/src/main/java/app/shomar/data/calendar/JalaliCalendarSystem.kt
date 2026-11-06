package app.shomar.data.calendar

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * The Jalali (Persian, Solar Hijri) calendar.
 *
 * Leap years follow the 33-year rule, which matches the official calendar for the
 * 1200s to 1600s (it is checked against a reference library in the tests). Dates are
 * counted from a known anchor: 1 Farvardin 1400 = 2021-03-21.
 */
object JalaliCalendarSystem : CalendarSystem {
    override val id = "jalali"
    override val displayName = "Jalali (Persian)"

    private val ANCHOR_DATE: LocalDate = LocalDate.of(2021, 3, 21)
    private const val ANCHOR_YEAR = 1400

    private val MONTH_NAMES = listOf(
        "Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar",
        "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand",
    )

    fun isLeapYear(year: Int): Boolean = Math.floorMod(25L * year + 11L, 33L) < 8L

    fun monthLength(year: Int, month: Int): Int = when {
        month <= 6 -> 31
        month <= 11 -> 30
        else -> if (isLeapYear(year)) 30 else 29
    }

    private fun yearLength(year: Int): Long = if (isLeapYear(year)) 366L else 365L

    /** The Gregorian date of 1 Farvardin of `year`. */
    private fun firstDayOfYear(year: Int): LocalDate {
        var date = ANCHOR_DATE
        if (year >= ANCHOR_YEAR) {
            for (y in ANCHOR_YEAR until year) date = date.plusDays(yearLength(y))
        } else {
            for (y in year until ANCHOR_YEAR) date = date.minusDays(yearLength(y))
        }
        return date
    }

    /** The Gregorian date of a Jalali date. The caller makes sure the month and day exist. */
    fun toGregorian(year: Int, month: Int, day: Int): LocalDate {
        var days = (day - 1).toLong()
        for (m in 1 until month) days += monthLength(year, m)
        return firstDayOfYear(year).plusDays(days)
    }

    /** Year, month and day of a Gregorian date in the Jalali calendar. */
    fun fromGregorian(date: LocalDate): Triple<Int, Int, Int> {
        var year = date.year - 621
        while (firstDayOfYear(year) > date) year--
        while (firstDayOfYear(year + 1) <= date) year++
        var remaining = ChronoUnit.DAYS.between(firstDayOfYear(year), date).toInt()
        var month = 1
        while (remaining >= monthLength(year, month)) {
            remaining -= monthLength(year, month)
            month++
        }
        return Triple(year, month, remaining + 1)
    }

    override fun month(year: Int, month: Int): CalendarMonth {
        val first = toGregorian(year, month, 1)
        val last = first.plusDays(monthLength(year, month) - 1L)
        return CalendarMonth(year, month, MONTH_NAMES[month - 1], DateRange(first, last))
    }

    override fun monthOf(date: LocalDate): CalendarMonth {
        val (year, month, _) = fromGregorian(date)
        return month(year, month)
    }

    override fun formatNumeric(date: LocalDate): String {
        val (year, month, day) = fromGregorian(date)
        return "%04d/%02d/%02d".format(Locale.ROOT, year, month, day)
    }

    override fun formatLong(date: LocalDate): String {
        val (year, month, day) = fromGregorian(date)
        return "$day ${MONTH_NAMES[month - 1]} $year"
    }

    override fun parse(text: String): LocalDate? {
        val (year, month, day) = DateParts.parse(text) ?: return null
        if (year !in 1..3000 || month !in 1..12 || day < 1 || day > monthLength(year, month)) return null
        return toGregorian(year, month, day)
    }
}