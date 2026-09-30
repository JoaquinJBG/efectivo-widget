package io.github.joaquinjbg.efectivo

import io.github.joaquinjbg.efectivo.data.CashMovement
import io.github.joaquinjbg.efectivo.data.CashMovementDao
import io.github.joaquinjbg.efectivo.data.Category
import io.github.joaquinjbg.efectivo.data.CategoryDao
import io.github.joaquinjbg.efectivo.data.CategoryTotal
import io.github.joaquinjbg.efectivo.data.Expense
import io.github.joaquinjbg.efectivo.data.ExpenseDao
import io.github.joaquinjbg.efectivo.data.ExpenseWithCategory
import io.github.joaquinjbg.efectivo.data.QuickButton
import io.github.joaquinjbg.efectivo.data.QuickButtonDao
import io.github.joaquinjbg.efectivo.data.WidgetTotals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** In-memory stand-ins for the Room DAOs so the repository can be tested on the JVM. */
class FakeStore {
    val categories = MutableStateFlow<List<Category>>(emptyList())
    val expenses = MutableStateFlow<List<Expense>>(emptyList())
    val cash = MutableStateFlow<List<CashMovement>>(emptyList())
    val quickButtons = MutableStateFlow<List<QuickButton>>(emptyList())
    var nextId = 1L

    fun categoryOf(id: Long): Category =
        categories.value.first { it.id == id }

    fun join(expense: Expense): ExpenseWithCategory {
        val category = categoryOf(expense.categoryId)
        return ExpenseWithCategory(
            id = expense.id,
            amountCents = expense.amountCents,
            categoryId = expense.categoryId,
            timestamp = expense.timestamp,
            note = expense.note,
            source = expense.source,
            categoryName = category.name,
            categoryEmoji = category.emoji,
        )
    }

    fun sorted(list: List<Expense>): List<Expense> =
        list.sortedWith(compareByDescending<Expense> { it.timestamp }.thenByDescending { it.id })
}

class FakeCategoryDao(private val store: FakeStore) : CategoryDao {
    override fun observeAll(): Flow<List<Category>> =
        store.categories.map { list -> list.sortedWith(compareBy({ it.sortOrder }, { it.name })) }

    override suspend fun getAll(): List<Category> =
        store.categories.value.sortedWith(compareBy({ it.sortOrder }, { it.name }))

    override suspend fun count(): Int = store.categories.value.size

    override suspend fun insertAll(categories: List<Category>): List<Long> {
        val inserted = categories.map { it.copy(id = store.nextId++) }
        store.categories.value = store.categories.value + inserted
        return inserted.map { it.id }
    }
}

class FakeExpenseDao(private val store: FakeStore) : ExpenseDao {
    override suspend fun insert(expense: Expense): Long {
        val row = expense.copy(id = store.nextId++)
        store.expenses.value = store.expenses.value + row
        return row.id
    }

    override suspend fun update(expense: Expense) {
        store.expenses.value = store.expenses.value.map { if (it.id == expense.id) expense else it }
    }

    override suspend fun delete(expense: Expense) = deleteById(expense.id)

    override suspend fun deleteById(id: Long) {
        store.expenses.value = store.expenses.value.filterNot { it.id == id }
    }

    override suspend fun getById(id: Long): Expense? = store.expenses.value.find { it.id == id }

    override fun observeTotal(): Flow<Long> = store.expenses.map { list -> list.sumOf { it.amountCents } }

    override suspend fun total(): Long = store.expenses.value.sumOf { it.amountCents }

    override fun observeTotalBetween(from: Long, until: Long): Flow<Long> =
        store.expenses.map { list -> list.filter { it.timestamp in from until until }.sumOf { it.amountCents } }

    override suspend fun totalBetween(from: Long, until: Long): Long =
        store.expenses.value.filter { it.timestamp in from until until }.sumOf { it.amountCents }

    override fun observeAllWithCategory(): Flow<List<ExpenseWithCategory>> =
        store.expenses.map { list -> store.sorted(list).map(store::join) }

    override fun observeBetweenWithCategory(from: Long, until: Long): Flow<List<ExpenseWithCategory>> =
        store.expenses.map { list ->
            store.sorted(list.filter { it.timestamp in from until until }).map(store::join)
        }

    override suspend fun betweenWithCategory(from: Long, until: Long): List<ExpenseWithCategory> =
        store.sorted(store.expenses.value.filter { it.timestamp in from until until }).map(store::join)

    override suspend fun latestWithCategory(): ExpenseWithCategory? =
        store.sorted(store.expenses.value).firstOrNull()?.let(store::join)

    private fun totals(
        dayFrom: Long, dayUntil: Long,
        weekFrom: Long, weekUntil: Long,
        monthFrom: Long, monthUntil: Long,
    ): WidgetTotals {
        fun sum(from: Long, until: Long) =
            store.expenses.value.filter { it.timestamp in from until until }.sumOf { it.amountCents }
        return WidgetTotals(
            balanceCents = store.cash.value.sumOf { it.amountCents } -
                store.expenses.value.sumOf { it.amountCents },
            todayCents = sum(dayFrom, dayUntil),
            weekCents = sum(weekFrom, weekUntil),
            monthCents = sum(monthFrom, monthUntil),
        )
    }

    override fun observeWidgetTotals(
        dayFrom: Long, dayUntil: Long,
        weekFrom: Long, weekUntil: Long,
        monthFrom: Long, monthUntil: Long,
    ): Flow<WidgetTotals> = combine(store.expenses, store.cash) { _, _ ->
        totals(dayFrom, dayUntil, weekFrom, weekUntil, monthFrom, monthUntil)
    }

    override suspend fun widgetTotals(
        dayFrom: Long, dayUntil: Long,
        weekFrom: Long, weekUntil: Long,
        monthFrom: Long, monthUntil: Long,
    ): WidgetTotals = totals(dayFrom, dayUntil, weekFrom, weekUntil, monthFrom, monthUntil)

    override fun observeTotalsByCategory(from: Long, until: Long): Flow<List<CategoryTotal>> =
        store.expenses.map { list ->
            list.filter { it.timestamp in from until until }
                .groupBy { it.categoryId }
                .map { (categoryId, rows) ->
                    val category = store.categoryOf(categoryId)
                    CategoryTotal(
                        categoryId = categoryId,
                        categoryName = category.name,
                        categoryEmoji = category.emoji,
                        totalCents = rows.sumOf { it.amountCents },
                    )
                }
                .sortedByDescending { it.totalCents }
        }
}

class FakeCashDao(private val store: FakeStore) : CashMovementDao {
    override suspend fun insert(movement: CashMovement): Long {
        val row = movement.copy(id = store.nextId++)
        store.cash.value = store.cash.value + row
        return row.id
    }

    override suspend fun deleteById(id: Long) {
        store.cash.value = store.cash.value.filterNot { it.id == id }
    }

    override fun observeTotal(): Flow<Long> = store.cash.map { list -> list.sumOf { it.amountCents } }

    override suspend fun total(): Long = store.cash.value.sumOf { it.amountCents }

    override fun observeAll(): Flow<List<CashMovement>> =
        store.cash.map { list -> list.sortedByDescending { it.timestamp } }
}

class FakeQuickButtonDao(private val store: FakeStore) : QuickButtonDao {
    private fun sorted(list: List<QuickButton>) =
        list.sortedWith(compareBy({ it.sortOrder }, { it.id }))

    override fun observeAll(): Flow<List<QuickButton>> = store.quickButtons.map(::sorted)

    override fun observeEnabled(): Flow<List<QuickButton>> =
        store.quickButtons.map { list -> sorted(list.filter { it.enabled }) }

    override suspend fun enabled(): List<QuickButton> =
        sorted(store.quickButtons.value.filter { it.enabled })

    override suspend fun getById(id: Long): QuickButton? =
        store.quickButtons.value.find { it.id == id }

    override suspend fun count(): Int = store.quickButtons.value.size

    override suspend fun insert(button: QuickButton): Long {
        val row = button.copy(id = store.nextId++)
        store.quickButtons.value = store.quickButtons.value + row
        return row.id
    }

    override suspend fun insertAll(buttons: List<QuickButton>): List<Long> =
        buttons.map { insert(it) }

    override suspend fun update(button: QuickButton) {
        store.quickButtons.value = store.quickButtons.value.map { if (it.id == button.id) button else it }
    }

    override suspend fun updateAll(buttons: List<QuickButton>) {
        buttons.forEach { update(it) }
    }

    override suspend fun deleteById(id: Long) {
        store.quickButtons.value = store.quickButtons.value.filterNot { it.id == id }
    }
}
