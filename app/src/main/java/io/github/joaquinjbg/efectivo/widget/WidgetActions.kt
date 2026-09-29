package io.github.joaquinjbg.efectivo.widget

import android.content.Context
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

private const val MESSAGE_VISIBLE_MILLIS = 4_000L

/** Writes the expense for a fixed-amount quick button, with no confirmation step. */
class QuickAddAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val buttonId = parameters[WidgetParams.buttonId] ?: return
        val repository = Graph.repository(context)
        val button = repository.quickButtonById(buttonId) ?: return
        val amount = button.fixedAmountCents ?: return

        repository.addExpense(
            amountCents = amount,
            categoryId = button.categoryId,
            note = null,
            source = ExpenseSource.WIDGET,
        )
        Haptics.tick(context)
        flashMessage(context, glanceId, button.label + " " + Money.formatShort(amount))
    }
}

/** Deletes the most recent expense and reports what was removed. */
class UndoAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val undone = Graph.repository(context).undoLastExpense()
        Haptics.tick(context)
        val message = if (undone == null) {
            "Nada que deshacer"
        } else {
            "Deshecho: " + undone.categoryName + " " + Money.formatShort(undone.amountCents)
        }
        flashMessage(context, glanceId, message)
    }
}

/**
 * Shows [message] in the widget header, then clears it.
 * Kept well under the 10 s limit an app-widget broadcast gets.
 */
internal suspend fun flashMessage(context: Context, glanceId: GlanceId, message: String) {
    updateAppWidgetState(context, glanceId) { prefs -> prefs[WidgetMessageKey] = message }
    EfectivoWidget.update(context, glanceId)
    delay(MESSAGE_VISIBLE_MILLIS)
    updateAppWidgetState(context, glanceId) { prefs -> prefs.remove(WidgetMessageKey) }
    EfectivoWidget.update(context, glanceId)
}
