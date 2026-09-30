package io.github.joaquinjbg.efectivo

import io.github.joaquinjbg.efectivo.data.Category
import io.github.joaquinjbg.efectivo.data.ExpenseRepository
import io.github.joaquinjbg.efectivo.data.ExpenseSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class ExpenseRepositoryTest {

    private val zone: ZoneId = ZoneId.of("Europe/Madrid")
    private lateinit var store: FakeStore
    private lateinit var repository: ExpenseRepository

    private var cafe = 0L
    private var ocio = 0L
    private var gasoil = 0L

    private fun at(date: LocalDate, hour: Int = 12): Long =
        date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        store = FakeStore()
        store.categories.value = listOf(
            Category(id = 1, name = "Café", emoji = "☕", sortOrder = 0),
            Category(id = 2, name = "Ocio", emoji = "🎉", sortOrder = 1),
            Category(id = 3, name = "Gasoil", emoji = "⛽", sortOrder = 2),
        )
        store.nextId = 100
        cafe = 1; ocio = 2; gasoil = 3
        repository = ExpenseRepository(
            expenseDao = FakeExpenseDao(store),
            cashDao = FakeCashDao(store),
            categoryDao = FakeCategoryDao(store),
            quickButtonDao = FakeQuickButtonDao(store),
            zone = zone,
        )
    }

    @Test
    fun `balance is cash added minus expenses`() = runTest {
        repository.addCash(20000)
        repository.addExpense(250, cafe, null, ExpenseSource.WIDGET)
        repository.addExpense(5000, gasoil, null, ExpenseSource.APP)

        assertEquals(14750L, repository.balance())
        assertEquals(14750L, repository.observeBalance().first())
    }

    @Test
    fun `balance goes negative when spending exceeds cash on hand`() = runTest {
        repository.addCash(1000)
        repository.addExpense(2500, ocio, null, ExpenseSource.APP)

        assertEquals(-1500L, repository.balance())
    }

    @Test
    fun `negative cash movement corrects the balance downwards`() = runTest {
        repository.addCash(10000)
        repository.addCash(-2000, "corrección")

        assertEquals(8000L, repository.balance())
    }

    @Test
    fun `deleting one expense leaves the rest alone`() = runTest {
        val today = LocalDate.of(2026, 9, 29)
        val first = repository.addExpense(200, cafe, null, ExpenseSource.WIDGET, at(today, 9))
        val middle = repository.addExpense(500, ocio, null, ExpenseSource.WIDGET, at(today, 10))
        repository.addExpense(5000, gasoil, null, ExpenseSource.APP, at(today, 11))

        // El widget borra por fila, así que puede caer cualquiera, no solo el último.
        repository.deleteExpense(middle)

        val remaining = repository.observeAllExpenses().first()
        assertEquals(2, remaining.size)
        assertEquals(listOf(5000L, 200L), remaining.map { it.amountCents })
        assertEquals(first, remaining.last().id)
    }

    @Test
    fun `deleting an id that is not there changes nothing`() = runTest {
        repository.addCash(5000)

        repository.deleteExpense(9999)

        assertEquals(5000L, repository.balance())
    }

    @Test
    fun `deleting an expense does not touch cash movements`() = runTest {
        val expense = repository.addExpense(300, cafe, null, ExpenseSource.WIDGET)
        repository.addCash(10000)

        repository.deleteExpense(expense)

        assertEquals(10000L, repository.balance())
    }

    @Test
    fun `monthly breakdown sums per category and shares add up`() = runTest {
        val september = YearMonth.of(2026, 9)
        repository.addExpense(200, cafe, null, ExpenseSource.WIDGET, at(LocalDate.of(2026, 9, 1)))
        repository.addExpense(300, cafe, null, ExpenseSource.WIDGET, at(LocalDate.of(2026, 9, 15)))
        repository.addExpense(1500, ocio, null, ExpenseSource.APP, at(LocalDate.of(2026, 9, 20)))
        // Fuera del mes: no deben contar.
        repository.addExpense(9999, gasoil, null, ExpenseSource.APP, at(LocalDate.of(2026, 8, 31)))
        repository.addExpense(8888, gasoil, null, ExpenseSource.APP, at(LocalDate.of(2026, 10, 1)))

        val summary = repository.observeMonthlySummary(september).first()

        assertEquals(2000L, summary.totalCents)
        assertEquals(2000L, summary.breakdown.sumOf { it.totalCents })
        assertEquals(listOf("Ocio", "Café"), summary.breakdown.map { it.name })
        assertEquals(1500L, summary.breakdown[0].totalCents)
        assertEquals(500L, summary.breakdown[1].totalCents)
        assertEquals(0.75f, summary.breakdown[0].share, 0.0001f)
        assertEquals(0.25f, summary.breakdown[1].share, 0.0001f)
        assertEquals(1.0f, summary.breakdown.sumOf { it.share.toDouble() }.toFloat(), 0.0001f)
    }

    @Test
    fun `week total runs monday to sunday`() = runTest {
        val anyDay = LocalDate.of(2026, 9, 29)
        val monday = anyDay.with(java.time.DayOfWeek.MONDAY)

        repository.addExpense(500, cafe, null, ExpenseSource.APP, at(monday.minusDays(1)))
        repository.addExpense(100, cafe, null, ExpenseSource.WIDGET, at(monday))
        repository.addExpense(200, ocio, null, ExpenseSource.WIDGET, at(monday.plusDays(3)))
        repository.addExpense(300, gasoil, null, ExpenseSource.APP, at(monday.plusDays(6)))
        repository.addExpense(900, cafe, null, ExpenseSource.APP, at(monday.plusDays(7)))

        assertEquals(600L, repository.totalOfWeek(anyDay))
        assertEquals(600L, repository.observeTotalOfWeek(anyDay).first())
        // Cualquier día de esa semana da el mismo número.
        assertEquals(600L, repository.totalOfWeek(monday.plusDays(6)))
    }

    @Test
    fun `month total matches the monthly summary total`() = runTest {
        val september = YearMonth.of(2026, 9)
        repository.addExpense(200, cafe, null, ExpenseSource.WIDGET, at(LocalDate.of(2026, 9, 2)))
        repository.addExpense(1500, ocio, null, ExpenseSource.APP, at(LocalDate.of(2026, 9, 20)))
        repository.addExpense(700, gasoil, null, ExpenseSource.APP, at(LocalDate.of(2026, 10, 1)))

        assertEquals(1700L, repository.totalOfMonth(september))
        assertEquals(1700L, repository.observeTotalOfMonth(september).first())
        assertEquals(
            repository.observeMonthlySummary(september).first().totalCents,
            repository.totalOfMonth(september),
        )
    }

    @Test
    fun `widget totals agree with the individual queries`() = runTest {
        val today = LocalDate.of(2026, 9, 29)
        val month = YearMonth.from(today)
        val monday = today.with(java.time.DayOfWeek.MONDAY)

        repository.addCash(20000)
        repository.addExpense(250, cafe, null, ExpenseSource.WIDGET, at(today, 9))
        repository.addExpense(1500, ocio, null, ExpenseSource.APP, at(monday, 12))
        repository.addExpense(4000, gasoil, null, ExpenseSource.APP, at(LocalDate.of(2026, 9, 2)))
        repository.addExpense(999, cafe, null, ExpenseSource.APP, at(LocalDate.of(2026, 8, 15)))

        val totals = repository.widgetTotals(today, month)

        assertEquals(repository.balance(), totals.balanceCents)
        assertEquals(repository.totalOn(today), totals.todayCents)
        assertEquals(repository.totalOfWeek(today), totals.weekCents)
        assertEquals(repository.totalOfMonth(month), totals.monthCents)
        assertEquals(totals, repository.observeWidgetTotals(today, month).first())
    }

    @Test
    fun `expenses of a day are newest first`() = runTest {
        val day = LocalDate.of(2026, 9, 29)
        repository.addExpense(100, cafe, null, ExpenseSource.WIDGET, at(day, 8))
        repository.addExpense(200, ocio, "cine", ExpenseSource.APP, at(day, 20))
        repository.addExpense(300, gasoil, null, ExpenseSource.APP, at(day.plusDays(1), 9))

        val rows = repository.expensesOn(day)

        assertEquals(listOf(200L, 100L), rows.map { it.amountCents })
        assertEquals("cine", rows.first().note)
    }

    @Test
    fun `empty month has zero total and no breakdown`() = runTest {
        val summary = repository.observeMonthlySummary(YearMonth.of(2026, 1)).first()

        assertEquals(0L, summary.totalCents)
        assertTrue(summary.breakdown.isEmpty())
    }

    @Test
    fun `day total and history grouping respect calendar day boundaries`() = runTest {
        val day = LocalDate.of(2026, 9, 29)
        repository.addExpense(100, cafe, null, ExpenseSource.WIDGET, at(day, 0))
        repository.addExpense(200, cafe, null, ExpenseSource.WIDGET, at(day, 23))
        repository.addExpense(400, ocio, null, ExpenseSource.APP, at(day.minusDays(1), 23))

        assertEquals(300L, repository.totalOn(day))
        assertEquals(300L, repository.observeTotalOn(day).first())

        val sections = repository.observeExpensesByDay().first()
        assertEquals(listOf(day, day.minusDays(1)), sections.map { it.date })
        assertEquals(300L, sections[0].totalCents)
        assertEquals(400L, sections[1].totalCents)
    }

    @Test
    fun `onChanged fires once per write`() = runTest {
        var notifications = 0
        val repo = ExpenseRepository(
            expenseDao = FakeExpenseDao(store),
            cashDao = FakeCashDao(store),
            categoryDao = FakeCategoryDao(store),
            quickButtonDao = FakeQuickButtonDao(store),
            zone = zone,
            onChanged = { notifications++ },
        )

        val expense = repo.addExpense(100, cafe, null, ExpenseSource.WIDGET)
        repo.addCash(1000)
        repo.deleteExpense(expense)

        assertEquals(3, notifications)
    }
}
