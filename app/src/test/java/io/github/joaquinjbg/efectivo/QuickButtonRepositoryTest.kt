package io.github.joaquinjbg.efectivo

import io.github.joaquinjbg.efectivo.data.Category
import io.github.joaquinjbg.efectivo.data.ExpenseRepository
import io.github.joaquinjbg.efectivo.data.QuickButton
import io.github.joaquinjbg.efectivo.data.Seed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QuickButtonRepositoryTest {

    private lateinit var store: FakeStore
    private lateinit var repository: ExpenseRepository

    @Before
    fun setUp() {
        store = FakeStore()
        store.categories.value = listOf(
            Category(id = 1, name = "Café", emoji = "☕", sortOrder = 0),
            Category(id = 2, name = "Varios", emoji = "🧾", sortOrder = 1),
        )
        store.nextId = 100
        repository = ExpenseRepository(
            expenseDao = FakeExpenseDao(store),
            cashDao = FakeCashDao(store),
            categoryDao = FakeCategoryDao(store),
            quickButtonDao = FakeQuickButtonDao(store),
        )
    }

    private suspend fun seedButtons() {
        listOf("A", "B", "C").forEachIndexed { index, label ->
            repository.addQuickButton(
                QuickButton(
                    label = label,
                    emoji = "🪙",
                    categoryId = 2,
                    fixedAmountCents = 100L * (index + 1),
                    sortOrder = index,
                )
            )
        }
    }

    @Test
    fun `reorder rewrites sortOrder from zero`() = runTest {
        seedButtons()
        val original = repository.observeQuickButtons().first()

        val moved = original.toMutableList().apply { add(0, removeAt(2)) }
        repository.reorderQuickButtons(moved)

        val result = repository.observeQuickButtons().first()
        assertEquals(listOf("C", "A", "B"), result.map { it.label })
        assertEquals(listOf(0, 1, 2), result.map { it.sortOrder })
    }

    @Test
    fun `only enabled buttons reach the widget`() = runTest {
        seedButtons()
        val buttons = repository.observeQuickButtons().first()
        repository.updateQuickButton(buttons[1].copy(enabled = false))

        val enabled = repository.enabledQuickButtons()

        assertEquals(listOf("A", "C"), enabled.map { it.label })
    }

    @Test
    fun `deleting a button removes it from the widget list`() = runTest {
        seedButtons()
        val buttons = repository.observeQuickButtons().first()

        repository.deleteQuickButton(buttons.first().id)

        assertEquals(listOf("B", "C"), repository.enabledQuickButtons().map { it.label })
        assertNull(repository.quickButtonById(buttons.first().id))
    }

    @Test
    fun `seed creates the categories and buttons described in the spec`() = runTest {
        val empty = FakeStore()
        val categoryDao = FakeCategoryDao(empty)
        val quickButtonDao = FakeQuickButtonDao(empty)

        Seed.applyIfEmpty(categoryDao, quickButtonDao)

        assertEquals(
            listOf("Café", "Desayuno", "Gasoil", "Comida", "Ocio", "Transporte", "Varios"),
            categoryDao.getAll().map { it.name },
        )
        val buttons = quickButtonDao.enabled()
        assertEquals(10, buttons.size)
        assertEquals(200L, buttons.first { it.label == "Café" }.fixedAmountCents)
        assertEquals(500L, buttons.first { it.label == "Desayuno" }.fixedAmountCents)
        assertEquals(5000L, buttons.first { it.label == "Gasoil" }.fixedAmountCents)
        assertNull(buttons.first { it.label == "Ocio" }.fixedAmountCents)
        assertEquals(
            listOf(100L, 200L, 500L, 1000L, 2000L, 5000L),
            buttons.filter { it.emoji == "🪙" }.map { it.fixedAmountCents },
        )
        val varios = categoryDao.getAll().first { it.name == "Varios" }
        assertTrue(buttons.filter { it.emoji == "🪙" }.all { it.categoryId == varios.id })
    }

    @Test
    fun `seed is idempotent`() = runTest {
        val empty = FakeStore()
        val categoryDao = FakeCategoryDao(empty)
        val quickButtonDao = FakeQuickButtonDao(empty)

        Seed.applyIfEmpty(categoryDao, quickButtonDao)
        Seed.applyIfEmpty(categoryDao, quickButtonDao)

        assertEquals(7, categoryDao.count())
        assertEquals(10, quickButtonDao.count())
    }
}
