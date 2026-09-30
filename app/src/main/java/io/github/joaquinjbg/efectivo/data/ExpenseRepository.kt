package io.github.joaquinjbg.efectivo.data

import io.github.joaquinjbg.efectivo.util.TimeRanges
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Single access point to the data. The balance is always derived, never stored.
 *
 * [onChanged] is invoked after every write so the caller can refresh the widget.
 */
class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val cashDao: CashMovementDao,
    private val categoryDao: CategoryDao,
    private val quickButtonDao: QuickButtonDao,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = System::currentTimeMillis,
    private val onChanged: suspend () -> Unit = {},
) {

    // ---- reads ----

    fun observeBalance(): Flow<Long> =
        combine(cashDao.observeTotal(), expenseDao.observeTotal()) { cash, spent -> cash - spent }

    suspend fun balance(): Long = cashDao.total() - expenseDao.total()

    fun observeCategories(): Flow<List<Category>> = categoryDao.observeAll()

    fun observeQuickButtons(): Flow<List<QuickButton>> = quickButtonDao.observeAll()

    fun observeEnabledQuickButtons(): Flow<List<QuickButton>> = quickButtonDao.observeEnabled()

    fun observeExpensesOn(date: LocalDate): Flow<List<ExpenseWithCategory>> {
        val (from, until) = TimeRanges.day(date, zone)
        return expenseDao.observeBetweenWithCategory(from, until)
    }

    fun observeTotalOn(date: LocalDate): Flow<Long> {
        val (from, until) = TimeRanges.day(date, zone)
        return expenseDao.observeTotalBetween(from, until)
    }

    suspend fun totalOn(date: LocalDate): Long {
        val (from, until) = TimeRanges.day(date, zone)
        return expenseDao.totalBetween(from, until)
    }

    /** Balance, day, week and month in a single query — used by the widget. */
    fun observeWidgetTotals(date: LocalDate, month: YearMonth): Flow<WidgetTotals> {
        val (dayFrom, dayUntil) = TimeRanges.day(date, zone)
        val (weekFrom, weekUntil) = TimeRanges.week(date, zone)
        val (monthFrom, monthUntil) = TimeRanges.month(month, zone)
        return expenseDao.observeWidgetTotals(
            dayFrom, dayUntil, weekFrom, weekUntil, monthFrom, monthUntil,
        )
    }

    suspend fun widgetTotals(date: LocalDate, month: YearMonth): WidgetTotals {
        val (dayFrom, dayUntil) = TimeRanges.day(date, zone)
        val (weekFrom, weekUntil) = TimeRanges.week(date, zone)
        val (monthFrom, monthUntil) = TimeRanges.month(month, zone)
        return expenseDao.widgetTotals(
            dayFrom, dayUntil, weekFrom, weekUntil, monthFrom, monthUntil,
        )
    }

    fun observeTotalOfWeek(date: LocalDate): Flow<Long> {
        val (from, until) = TimeRanges.week(date, zone)
        return expenseDao.observeTotalBetween(from, until)
    }

    suspend fun totalOfWeek(date: LocalDate): Long {
        val (from, until) = TimeRanges.week(date, zone)
        return expenseDao.totalBetween(from, until)
    }

    fun observeTotalOfMonth(month: YearMonth): Flow<Long> {
        val (from, until) = TimeRanges.month(month, zone)
        return expenseDao.observeTotalBetween(from, until)
    }

    suspend fun totalOfMonth(month: YearMonth): Long {
        val (from, until) = TimeRanges.month(month, zone)
        return expenseDao.totalBetween(from, until)
    }

    suspend fun expensesOn(date: LocalDate): List<ExpenseWithCategory> {
        val (from, until) = TimeRanges.day(date, zone)
        return expenseDao.betweenWithCategory(from, until)
    }

    fun observeAllExpenses(): Flow<List<ExpenseWithCategory>> = expenseDao.observeAllWithCategory()

    /** History grouped by day, newest day first, each with its own total. */
    fun observeExpensesByDay(): Flow<List<DaySection>> =
        expenseDao.observeAllWithCategory().map { expenses ->
            expenses
                .groupBy { TimeRanges.toLocalDate(it.timestamp, zone) }
                .entries
                .sortedByDescending { it.key }
                .map { (date, items) ->
                    DaySection(
                        date = date,
                        totalCents = items.sumOf { it.amountCents },
                        expenses = items,
                    )
                }
        }

    fun observeMonthlySummary(month: YearMonth): Flow<MonthlySummary> {
        val (from, until) = TimeRanges.month(month, zone)
        return combine(
            expenseDao.observeTotalBetween(from, until),
            expenseDao.observeTotalsByCategory(from, until),
        ) { total, byCategory ->
            MonthlySummary(
                month = month,
                totalCents = total,
                breakdown = byCategory.map { row ->
                    CategoryShare(
                        categoryId = row.categoryId,
                        name = row.categoryName,
                        emoji = row.categoryEmoji,
                        totalCents = row.totalCents,
                        share = if (total == 0L) 0f else row.totalCents.toFloat() / total.toFloat(),
                    )
                },
            )
        }
    }

    suspend fun expenseById(id: Long): Expense? = expenseDao.getById(id)

    suspend fun quickButtonById(id: Long): QuickButton? = quickButtonDao.getById(id)

    suspend fun categories(): List<Category> = categoryDao.getAll()

    // ---- writes ----

    suspend fun addExpense(
        amountCents: Long,
        categoryId: Long,
        note: String? = null,
        source: ExpenseSource,
        timestamp: Long = now(),
    ): Long {
        val id = expenseDao.insert(
            Expense(
                amountCents = amountCents,
                categoryId = categoryId,
                timestamp = timestamp,
                note = note?.takeIf { it.isNotBlank() },
                source = source,
            )
        )
        onChanged()
        return id
    }

    suspend fun updateExpense(expense: Expense) {
        expenseDao.update(expense)
        onChanged()
    }

    suspend fun deleteExpense(id: Long) {
        expenseDao.deleteById(id)
        onChanged()
    }

    suspend fun addCash(amountCents: Long, note: String? = null, timestamp: Long = now()): Long {
        val id = cashDao.insert(
            CashMovement(
                amountCents = amountCents,
                timestamp = timestamp,
                note = note?.takeIf { it.isNotBlank() },
            )
        )
        onChanged()
        return id
    }

    // ---- quick buttons ----

    /**
     * Un botón normal ES su categoría: al crearlo se crea, al renombrarlo se renombra y
     * al borrarlo se archiva. Los botones de moneda quedan fuera de esa regla — si cada
     * uno tuviera la suya, el resumen del mes se llenaría de "1 €", "2 €", "5 €" en vez
     * de agruparlos — y siguen apuntando a una categoría que se elige.
     */
    suspend fun addQuickButton(button: QuickButton): Long {
        val categoryId = if (button.compact) {
            button.categoryId
        } else {
            categoryDao.insert(
                Category(
                    name = button.label,
                    emoji = button.emoji,
                    sortOrder = categoryDao.nextSortOrder(),
                )
            )
        }
        val id = quickButtonDao.insert(button.copy(categoryId = categoryId))
        onChanged()
        return id
    }

    suspend fun updateQuickButton(button: QuickButton) {
        if (!button.compact) {
            val category = categoryDao.getById(button.categoryId)
            // Si la categoría no existe, o la comparte con otro botón, hay que darle una
            // propia: renombrar una compartida arrastraría los gastos de ese otro botón.
            val shared = quickButtonDao.countSharingCategory(button.categoryId, button.id) > 0
            if (category == null || shared) {
                val categoryId = categoryDao.insert(
                    Category(
                        name = button.label,
                        emoji = button.emoji,
                        sortOrder = categoryDao.nextSortOrder(),
                    )
                )
                quickButtonDao.update(button.copy(categoryId = categoryId))
                onChanged()
                return
            }
            categoryDao.update(category.copy(name = button.label, emoji = button.emoji))
        }
        quickButtonDao.update(button)
        onChanged()
    }

    suspend fun deleteQuickButton(id: Long) {
        val button = quickButtonDao.getById(id)
        val shared = button != null &&
            quickButtonDao.countSharingCategory(button.categoryId, id) > 0
        quickButtonDao.deleteById(id)
        // Archivar, no borrar: los gastos que ya tenía deben seguir viéndose con su nombre.
        // Si otro botón usa la misma categoría, se queda como está.
        if (button != null && !button.compact && !shared) categoryDao.archive(button.categoryId)
        onChanged()
    }

    /** Persists [buttons] in the given order as sortOrder 0..n-1. */
    suspend fun reorderQuickButtons(buttons: List<QuickButton>) {
        quickButtonDao.updateAll(buttons.mapIndexed { index, b -> b.copy(sortOrder = index) })
        onChanged()
    }

    suspend fun enabledQuickButtons(): List<QuickButton> = quickButtonDao.enabled()
}

data class DaySection(
    val date: LocalDate,
    val totalCents: Long,
    val expenses: List<ExpenseWithCategory>,
)

data class CategoryShare(
    val categoryId: Long,
    val name: String,
    val emoji: String,
    val totalCents: Long,
    val share: Float,
)

data class MonthlySummary(
    val month: YearMonth,
    val totalCents: Long,
    val breakdown: List<CategoryShare>,
)
