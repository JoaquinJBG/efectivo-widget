package io.github.joaquinjbg.efectivo.data

/** First-launch contents: categories and the default widget buttons. */
object Seed {

    private val categories = listOf(
        "Café" to "☕",
        "Desayuno" to "🥐",
        "Gasoil" to "⛽",
        "Comida" to "🍽️",
        "Ocio" to "🎉",
        "Transporte" to "🚌",
        "Varios" to "🧾",
    )

    suspend fun applyIfEmpty(categoryDao: CategoryDao, quickButtonDao: QuickButtonDao) {
        if (categoryDao.count() == 0) {
            categoryDao.insertAll(
                categories.mapIndexed { index, pair ->
                    Category(name = pair.first, emoji = pair.second, sortOrder = index)
                }
            )
        }
        if (quickButtonDao.count() == 0) {
            val byName = categoryDao.getAll().associateBy { it.name }
            val varios = byName.getValue("Varios").id
            val buttons = mutableListOf(
                QuickButton(
                    label = "Café",
                    emoji = "☕",
                    categoryId = byName.getValue("Café").id,
                    fixedAmountCents = 200,
                    sortOrder = 0,
                ),
                QuickButton(
                    label = "Desayuno",
                    emoji = "🥐",
                    categoryId = byName.getValue("Desayuno").id,
                    fixedAmountCents = 500,
                    sortOrder = 1,
                ),
                QuickButton(
                    label = "Gasoil",
                    emoji = "⛽",
                    categoryId = byName.getValue("Gasoil").id,
                    fixedAmountCents = 5000,
                    sortOrder = 2,
                ),
                QuickButton(
                    label = "Ocio",
                    emoji = "🎉",
                    categoryId = byName.getValue("Ocio").id,
                    fixedAmountCents = null,
                    sortOrder = 3,
                ),
            )
            listOf(100L, 200L, 500L, 1000L, 2000L, 5000L).forEachIndexed { index, cents ->
                buttons += QuickButton(
                    label = (cents / 100).toString() + " €",
                    emoji = "🪙",
                    categoryId = varios,
                    fixedAmountCents = cents,
                    sortOrder = 4 + index,
                    compact = true,
                )
            }
            quickButtonDao.insertAll(buttons)
        }
    }
}
