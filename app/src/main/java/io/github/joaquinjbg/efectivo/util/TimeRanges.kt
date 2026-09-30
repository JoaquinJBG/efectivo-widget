package io.github.joaquinjbg.efectivo.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Rangos [inicio, fin) en milisegundos, para días y meses naturales. */
object TimeRanges {

    fun day(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }

    /** Del lunes a las 00:00 de la semana de [date] hasta el lunes siguiente. */
    fun week(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val monday = date.with(DayOfWeek.MONDAY)
        val start = monday.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = monday.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }

    fun month(month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }

    fun toLocalDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
}
