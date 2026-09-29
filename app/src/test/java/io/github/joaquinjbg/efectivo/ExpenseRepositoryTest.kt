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
    fun `undo removes only the latest expense`() = runTest {
        val today = LocalDate.of(2026, 9, 29)
        repository.addExpense(200, cafe, null, ExpenseSource.WIDGET, at(today, 9))
        repository.addExpense(500, ocio, null, ExpenseSource.WIDGET, at(today, 10))
        val newest = repository.addExpense(5000, gasoil, null, ExpenseSource.APP, at(today, 11))

        val undone = repository.undoLastExpense()

        assertEquals(newest, undone?.id)
        assertEquals(5000L, undone?.amountCents)
        assertEquals("Gasoil", undone?.categoryName)
        val remaining = repository.observeAllExpenses().first()
        assertEquals(2, remaining.size)
        assertEquals(listOf(500L, 200L), remaining.map { it.amountCents })
    }

    @Test
    fun `undo on an empty ledger returns null and changes nothing`() = runTest {
        repository.addCash(5000)

        assertNull(repository.undoLastExpense())
        assertEquals(5000L, repository.balance())
    }

    @Test
    fun `undo does not touch cash movements`() = runTest {
        repository.addExpense(300, cafe, null, ExpenseSource.WIDGET)
        repository.addCash(10000)

        val undone = repository.undoLastExpense()

        assertEquals(300L, undone?.amountCents)
        assertEquals(10000L, repository.balance())
    }

    @Test
    fun `undo stages the header message before deleting and notifying`() = runTest {
        val events = mutableListOf<String>()
        val repo = ExpenseRepository(
            expenseDao = FakeExpenseDao(store),
            cashDao = FakeCashDao(store),
            categoryDao = FakeCategoryDao(store),
            quickButtonDao = FakeQuickButtonDao(store),
            zone = zone,
            onChanged = { events += "refresh" },
        )
        repo.addExpense(200, cafe, null, ExpenseSource.WIDGET)
        events.clear()

        repo.undoLastExpense { latest -> events += "message:" + latest.categoryName }

        // The message must be staged first, so the single refresh paints it together
        // with the new balance.
        assertEquals(listOf("message:Café", "refresh"), events)
        assertEquals(0, repo.observeAllExpenses().first().size)
    }

    @Test
    fun `undo skips the hook when there is nothing to delete`() = runTest {
        var hookCalls = 0

        val undone = repository.undoLastExpense { hookCalls++ }

        assertNull(undone)
        assertEquals(0, hookCalls)
    }

    @Test
    fun `monthly breakdown sums per category and shares add up`() = runTest {
        val september = YearMonth.of(2026, 9)
        repository.addExpense(200, cafe, null, ExpenseSource.WIDGET, at(LocalDate.of(2026, 9, 1)))
        repository.addExpense(300, cafe, null, ExpenseSource.WIDGET, at(LocalDate.of(2026, 9, 15)))
        repository.addExpense(1500, ocio, null, ExpenseSource.APP, at(LocalDate.of(2026, 9, 20)))
        // Outside the month, must be ignored.
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

        repo.addExpense(100, cafe, null, ExpenseSource.WIDGET)
        repo.addCash(1000)
        repo.undoLastExpense()

        assertEquals(3, notifications)
    }
}
