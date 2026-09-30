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
 * Repintar el widget no es instantáneo, así que un segundo toque impaciente llegaba antes
 * de que el primero enseñara nada y apuntaba el gasto dos veces sin avisar. Los toques
 * repetidos sobre el mismo control dentro de esta ventana se descartan.
 */
private const val DOUBLE_TAP_GUARD_MILLIS = 1_500L

private object TapGuard {
    @Volatile
    private var lastTarget: String = ""

    @Volatile
    private var lastTapAt: Long = 0L

    /** Devuelve false si [target] ya se pulsó dentro de la ventana de guarda. */
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

/** Apunta el gasto de un botón de importe fijo, sin pedir confirmación. */
class QuickAddAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val buttonId = parameters[WidgetParams.buttonId] ?: return
        if (!TapGuard.accept("add:$buttonId")) return

        // Vibrar primero: es la única respuesta que no espera ni a la base de datos ni al repintado.
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

/** Borra un gasto desde su propia fila en la lista del día. */
class DeleteExpenseAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val expenseId = parameters[WidgetParams.expenseId] ?: return
        // Con la misma guarda que los botones: la fila se recoloca en cuanto la lista se
        // repinta, y un segundo toque caería sobre la que haya ocupado su sitio.
        if (!TapGuard.accept("delete:$expenseId")) return

        Haptics.tick(context)
        Graph.repository(context).deleteExpense(expenseId)
    }
}
