package io.github.joaquinjbg.efectivo

import android.content.Context
import androidx.glance.appwidget.updateAll
import io.github.joaquinjbg.efectivo.data.EfectivoDatabase
import io.github.joaquinjbg.efectivo.data.ExpenseRepository
import io.github.joaquinjbg.efectivo.data.Seed
import io.github.joaquinjbg.efectivo.widget.EfectivoWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Montaje a mano: una base de datos y un repositorio para todo el proceso. */
object Graph {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var repo: ExpenseRepository? = null

    fun repository(context: Context): ExpenseRepository =
        repo ?: synchronized(this) {
            repo ?: build(context.applicationContext).also { repo = it }
        }

    private fun build(appContext: Context): ExpenseRepository {
        val db = EfectivoDatabase.get(appContext)
        return ExpenseRepository(
            expenseDao = db.expenseDao(),
            cashDao = db.cashMovementDao(),
            categoryDao = db.categoryDao(),
            quickButtonDao = db.quickButtonDao(),
            // Toda escritura, venga de la app o del widget, refresca el widget.
            onChanged = { EfectivoWidget.updateAll(appContext) },
        )
    }

    fun seed(context: Context) {
        val db = EfectivoDatabase.get(context.applicationContext)
        appScope.launch { Seed.applyIfEmpty(db.categoryDao(), db.quickButtonDao()) }
    }
}
