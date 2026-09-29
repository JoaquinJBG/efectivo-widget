package io.github.joaquinjbg.efectivo.util

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Spanish date labels used by History and the monthly summary. */
object Dates {

    val SPANISH: Locale = Locale.forLanguageTag("es-ES")

    private val dayFormat = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", SPANISH)
    private val dayWithYearFormat = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", SPANISH)
    private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", SPANISH)

    fun dayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> "Hoy"
        today.minusDays(1) -> "Ayer"
        else -> {
            val formatter = if (date.year == today.year) dayFormat else dayWithYearFormat
            date.format(formatter).replaceFirstChar { it.uppercase(SPANISH) }
        }
    }

    fun monthLabel(month: YearMonth): String =
        month.atDay(1).format(monthFormat).replaceFirstChar { it.uppercase(SPANISH) }
}
