package io.github.joaquinjbg.efectivo.widget

import android.content.Context
import android.os.SystemClock
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import io.github.joaquinjbg.efectivo.Graph
import io.github.joaquinjbg.efectivo.data.ExpenseSource
import io.github.joaquinjbg.efectivo.util.Haptics

object WidgetParams {
    val buttonId = ActionParameters.Key<Long>("buttonId")
    val expenseId = ActionParameters.Key<Long>("expenseId")
}

/**
 * A widget redraw is not instant, so an impatient second tap used to land before the
 * first one showed anything and silently booked the expense twice. Repeat taps on the
 * same control inside this window are dropped.
 */
private const val DOUBLE_TAP_GUARD_MILLIS = 1_500L

private object TapGuard {
    @Volatile
    private var lastTarget: String = ""

    @Volatile
    private var lastTapAt: Long = 0L

    /** Returns false when [target] was already tapped within the guard window. */
    @Synchronized
    fun accept(target: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (target == lastTarget && now - lastTapAt < DOUBLE_TAP_GUARD_MILLIS) return false
        lastTarget = target
        lastTapAt = now
        return true
    }
}

/*
 * Ninguna de estas acciones enseña un aviso en la cabecera. El que había tapaba las
 * tarjetas de Efectivo y Hoy, y para borrarse después obligaba a esperar 2,5 s dentro
 * del callback: eso mantenía el proceso despierto y costaba un repintado de más.
 * Ahora cada toque es escribir y repintar una vez.
 */

/** Writes the expense for a fixed-amount quick button, with no confirmation step. */
class QuickAddAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val buttonId = parameters[WidgetParams.buttonId] ?: return
        if (!TapGuard.accept("add:$buttonId")) return

        // Buzz first: the only feedback that does not wait for the database or a redraw.
        Haptics.tick(context)

        val repository = Graph.repository(context)
        val button = repository.quickButtonById(buttonId) ?: return
        val amount = button.fixedAmountCents ?: return

        repository.addExpense(
            amountCents = amount,
            categoryId = button.categoryId,
            note = null,
            source = ExpenseSource.WIDGET,
        )
    }
}

/** Deletes one expense straight from its row in the day list. */
class DeleteExpenseAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val expenseId = parameters[WidgetParams.expenseId] ?: return
        // Guarded like the quick buttons: the row moves as soon as the list redraws,
        // and a second tap would land on whatever took its place.
        if (!TapGuard.accept("delete:$expenseId")) return

        Haptics.tick(context)
        Graph.repository(context).deleteExpense(expenseId)
    }
}
