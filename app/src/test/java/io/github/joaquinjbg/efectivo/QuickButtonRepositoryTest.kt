package io.github.joaquinjbg.efectivo

import io.github.joaquinjbg.efectivo.data.Category
import io.github.joaquinjbg.efectivo.data.ExpenseRepository
import io.github.joaquinjbg.efectivo.data.ExpenseSource
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
    fun `a normal button creates its own category`() = runTest {
        val id = repository.addQuickButton(
            QuickButton(label = "gym", emoji = "🏋️", categoryId = 0, fixedAmountCents = null, sortOrder = 0)
        )

        val button = repository.quickButtonById(id)!!
        val category = repository.categories().first { it.id == button.categoryId }
        assertEquals("gym", category.name)
        assertEquals("🏋️", category.emoji)
    }

    @Test
    fun `a coin button reuses the category it was given`() = runTest {
        val id = repository.addQuickButton(
            QuickButton(
                label = "5 €",
                emoji = "🪙",
                categoryId = 2,
                fixedAmountCents = 500,
                sortOrder = 0,
                compact = true,
            )
        )

        // Si cada moneda creara su categoría, el resumen del mes se llenaría de importes.
        assertEquals(2, repository.quickButtonById(id)!!.categoryId)
        assertEquals(2, repository.categories().size)
    }

    @Test
    fun `renaming a button renames its category`() = runTest {
        val id = repository.addQuickButton(
            QuickButton(label = "gym", emoji = "🏋️", categoryId = 0, fixedAmountCents = null, sortOrder = 0)
        )
        val button = repository.quickButtonById(id)!!

        repository.updateQuickButton(button.copy(label = "gimnasio", emoji = "💪"))

        val category = repository.categories().first { it.id == button.categoryId }
        assertEquals("gimnasio", category.name)
        assertEquals("💪", category.emoji)
    }

    @Test
    fun `deleting a button keeps its past expenses in the history`() = runTest {
        val id = repository.addQuickButton(
            QuickButton(label = "gym", emoji = "🏋️", categoryId = 0, fixedAmountCents = 3000, sortOrder = 0)
        )
        val button = repository.quickButtonById(id)!!
        repository.addExpense(3000, button.categoryId, null, ExpenseSource.WIDGET)

        repository.deleteQuickButton(id)

        // La categoría se archiva: deja de ofrecerse, pero el gasto conserva su nombre.
        assertNull(repository.categories().find { it.id == button.categoryId })
        val history = repository.observeAllExpenses().first()
        assertEquals(1, history.size)
        assertEquals("gym", history.first().categoryName)
        assertEquals(3000L, history.first().amountCents)
    }

    @Test
    fun `deleting a coin button leaves its shared category alone`() = runTest {
        val id = repository.addQuickButton(
            QuickButton(
                label = "5 €",
                emoji = "🪙",
                categoryId = 2,
                fixedAmountCents = 500,
                sortOrder = 0,
                compact = true,
            )
        )

        repository.deleteQuickButton(id)

        assertEquals(2, repository.categories().size)
    }

    @Test
    fun `renaming a button that shares a category does not rename the other one`() = runTest {
        // Situación real: un botón creado antes de esta regla apuntaba a Café, la misma
        // categoría que el botón "Café". Renombrarlo arrastraría todos los cafés.
        val cafeButton = repository.addQuickButton(
            QuickButton(label = "Café", emoji = "☕", categoryId = 1, fixedAmountCents = 200, sortOrder = 0)
        ).let { repository.quickButtonById(it)!! }
        val strayButton = QuickButton(
            id = repository.addQuickButton(
                QuickButton(label = "gym", emoji = "🏋️", categoryId = 0, fixedAmountCents = null, sortOrder = 1)
            ),
            label = "gym",
            emoji = "🏋️",
            categoryId = cafeButton.categoryId,
            fixedAmountCents = null,
            sortOrder = 1,
        )

        repository.updateQuickButton(strayButton.copy(label = "gimnasio"))

        val cafeCategory = repository.categories().first { it.id == cafeButton.categoryId }
        assertEquals("Café", cafeCategory.name)
        val moved = repository.quickButtonById(strayButton.id)!!
        assertEquals("gimnasio", repository.categories().first { it.id == moved.categoryId }.name)
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
