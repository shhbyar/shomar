package app.shomar.data.model

import java.math.BigInteger
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Amounts are stored as whole numbers of a currency's smallest unit ("minor units"),
 * for example cents. How many decimal digits that means depends on the currency.
 * Every client (this app, the web front end, the analysis code) must follow the same rule.
 */
object CurrencyRules {
    // Where the app's convention differs from the ISO 4217 table: the rial is used without
    // a subunit, so 25,000,000 IRR is stored as 25000000.
    private val overrides = mapOf("IRR" to 0)

    fun fractionDigits(currencyCode: String): Int {
        overrides[currencyCode]?.let { return it }
        val digits = try {
            Currency.getInstance(currencyCode).defaultFractionDigits
        } catch (e: IllegalArgumentException) {
            -1 // not a known currency code
        }
        return if (digits < 0) 2 else digits
    }
}

object Money {
    /** The largest amount (in minor units) the server accepts. */
    const val MAX_MINOR = 1_000_000_000_000_000L // 10^15

    /** For display, e.g. "1,234.56 EUR" or "25,000,000 IRR". */
    // NumberFormat is expensive to create and not safe to share, so keep one untouched template
    // per (language, digits) and clone it for each use.
    private val numberFormats = ConcurrentHashMap<Pair<Locale, Int>, NumberFormat>()

    fun format(amountMinor: Long, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        val digits = CurrencyRules.fractionDigits(currencyCode)
        val template = numberFormats.getOrPut(locale to digits) {
            NumberFormat.getNumberInstance(locale).apply {
                minimumFractionDigits = digits
                maximumFractionDigits = digits
            }
        }
        val formatter = template.clone() as NumberFormat
        return "${formatter.format(BigDecimal.valueOf(amountMinor, digits))} $currencyCode"
    }

    /**
     * Turns what a person typed into minor units, or null if it isn't a valid amount.
     *
     * Accepts digits in any script (including Persian and Arabic-Indic), spaces and
     * apostrophes as thousands separators, and either '.' or ',' as the decimal or
     * thousands separator. Which one is meant is decided by what follows the last
     * separator: 1 or 2 digits (for a 2-decimal currency) means decimals, exactly 3
     * means thousands grouping, anything else is rejected.
     */
    fun parse(input: String, currencyCode: String): Long? {
        val digits = CurrencyRules.fractionDigits(currencyCode)

        var negative = false
        val cleaned = StringBuilder()
        for ((index, ch) in input.trim().withIndex()) {
            val digit = Character.digit(ch, 10) // reads digits of any script
            when {
                digit >= 0 -> cleaned.append('0' + digit)
                ch == '-' || ch == '\u2212' -> if (index == 0) negative = true else return null
                ch == '.' || ch == ',' -> cleaned.append(ch)
                ch == '\u066B' -> cleaned.append('.') // Arabic decimal separator
                ch.isWhitespace() || ch == '\u00A0' || ch == '\u202F' ||
                        ch == '\u066C' || ch == '\'' || ch == '\u2019' -> Unit // thousands grouping only
                else -> return null
            }
        }
        val text = cleaned.toString()
        if (text.none { it.isDigit() }) return null

        val separators = text.count { it == '.' || it == ',' }
        val integerPart: String
        val fractionPart: String
        if (separators == 0) {
            integerPart = text
            fractionPart = ""
        } else {
            val last = text.indexOfLast { it == '.' || it == ',' }
            val head = text.substring(0, last)
            val tail = text.substring(last + 1)
            if (tail.isEmpty()) return null
            val asDecimal = digits > 0 && tail.length <= digits &&
                    (tail.length != 3 || (digits == 3 && separators == 1))
            if (asDecimal) {
                if (head.contains(text[last])) return null // e.g. "1.2.3"
                integerPart = head.filter { it.isDigit() }
                fractionPart = tail
            } else if (tail.length == 3) {
                integerPart = text.filter { it.isDigit() } // every separator is thousands grouping
                fractionPart = ""
            } else {
                return null
            }
        }

        val minor = BigInteger(integerPart.ifEmpty { "0" } + fractionPart.padEnd(digits, '0'))
        if (minor > BigInteger.valueOf(MAX_MINOR)) return null
        return if (negative) -minor.toLong() else minor.toLong()
    }
}