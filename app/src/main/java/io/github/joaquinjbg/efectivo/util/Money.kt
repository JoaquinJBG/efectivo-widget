package io.github.joaquinjbg.efectivo.util

import kotlin.math.absoluteValue

/** Spanish money formatting: 1234567 cents -> "12.345,67 €". */
object Money {

    /**
     * [spaced] = false pega el símbolo al número ("2€" en vez de "2 €"). Lo usan los
     * botones de moneda del widget, donde el espacio se come sitio y no aporta.
     */
    fun format(cents: Long, spaced: Boolean = true): String =
        formatNumber(cents) + (if (spaced) " " else "") + "€"

    fun formatNumber(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val abs = cents.absoluteValue
        val units = abs / 100
        val decimals = abs % 100
        return "$sign${groupThousands(units)},${decimals.toString().padStart(2, '0')}"
    }

    /** Short form used on widget buttons: "2 €" when whole, "2,50 €" otherwise. */
    fun formatShort(cents: Long, spaced: Boolean = true): String {
        val abs = cents.absoluteValue
        val gap = if (spaced) " " else ""
        return if (abs % 100 == 0L) {
            val sign = if (cents < 0) "-" else ""
            "$sign${groupThousands(abs / 100)}$gap€"
        } else {
            format(cents, spaced)
        }
    }

    private fun groupThousands(value: Long): String {
        val digits = value.toString()
        val sb = StringBuilder()
        for ((i, c) in digits.withIndex()) {
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append('.')
            sb.append(c)
        }
        return sb.toString()
    }

    /**
     * Parses what the custom keypad produced ("12", "12,5", "12,50", "" -> null).
     * Only digits and a single comma are expected.
     */
    fun parse(text: String): Long? {
        if (text.isBlank()) return null
        val cleaned = text.replace(".", "").replace(',', '.')
        val value = cleaned.toDoubleOrNull() ?: return null
        if (value < 0) return null
        return Math.round(value * 100.0)
    }
}
