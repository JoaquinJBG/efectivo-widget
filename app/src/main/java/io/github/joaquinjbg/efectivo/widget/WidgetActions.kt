package io.github.joaquinjbg.efectivo.widget

import android.content.Context
import android.os.SystemClock
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import io.github.joaquinjbg.efectivo.Graph
import io.github.joaquinjbg.efectivo.data.ExpenseSource
import io.github.joaquinjbg.efectivo.util.Haptics
import io.github.joaquinjbg.efectivo.util.Money
import kotlinx.coroutines.delay

/** Text shown briefly in the widget header after an action. */
val WidgetMessageKey = stringPreferencesKey("widget_message")

object WidgetParams {
    val buttonId = ActionParameters.Key<Long>("buttonId")
}

private const val MESSAGE_VISIBLE_MILLIS = 2_500L

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

        // Stage the header message before the write, so the refresh the repository
        // triggers paints balance, today's total and the message in one pass.
        setMessage(context, glanceId, button.label + " " + Money.formatShort(amount))
        repository.addExpense(
            amountCents = amount,
            categoryId = button.categoryId,
            note = null,
            source = ExpenseSource.WIDGET,
        )
        clearMessageLater(context, glanceId)
    }
}

/** Deletes the most recent expense and reports what was removed. */
class UndoAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        // Undo is deliberately not rate-limited: tapping it twice removes two expenses.
        Haptics.tick(context)

        val undone = Graph.repository(context).undoLastExpense { latest ->
            setMessage(
                context,
                glanceId,
                "Deshecho: " + latest.categoryName + " " + Money.formatShort(latest.amountCents),
            )
        }
        if (undone == null) {
            setMessage(context, glanceId, "Nada que deshacer")
            EfectivoWidget.update(context, glanceId)
        }
        clearMessageLater(context, glanceId)
    }
}

private suspend fun setMessage(context: Context, glanceId: GlanceId, message: String) {
    updateAppWidgetState(context, glanceId) { prefs -> prefs[WidgetMessageKey] = message }
}

/**
 * Wipes the header message after a short pause. Runs at the tail of the callback,
 * well inside the 10 s an app-widget broadcast gets, and after the visible refresh.
 */
private suspend fun clearMessageLater(context: Context, glanceId: GlanceId) {
    delay(MESSAGE_VISIBLE_MILLIS)
    updateAppWidgetState(context, glanceId) { prefs -> prefs.remove(WidgetMessageKey) }
    EfectivoWidget.update(context, glanceId)
}
