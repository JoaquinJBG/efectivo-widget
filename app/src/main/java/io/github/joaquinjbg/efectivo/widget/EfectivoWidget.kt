package io.github.joaquinjbg.efectivo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.action.actionParametersOf
import androidx.glance.background
import androidx.glance.LocalContext
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.joaquinjbg.efectivo.Graph
import io.github.joaquinjbg.efectivo.data.QuickButton
import io.github.joaquinjbg.efectivo.ui.quick.QuickEntryActivity
import io.github.joaquinjbg.efectivo.util.Money
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

private const val COLUMNS = 3

object EfectivoWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = Graph.repository(context)
        // One combined flow instead of three: a redraw costs a single emission.
        val data = combine(
            repository.observeBalance(),
            repository.observeTotalOn(LocalDate.now()),
            repository.observeEnabledQuickButtons(),
        ) { balance, today, buttons -> WidgetData(balance, today, buttons) }
        val initial = WidgetData(
            balanceCents = repository.balance(),
            todayCents = repository.totalOn(LocalDate.now()),
            buttons = repository.enabledQuickButtons(),
        )

        provideContent {
            val state by data.collectAsState(initial = initial)
            WidgetContent(
                balanceCents = state.balanceCents,
                todayCents = state.todayCents,
                buttons = state.buttons,
                message = currentState(WidgetMessageKey),
            )
        }
    }
}

private data class WidgetData(
    val balanceCents: Long = 0L,
    val todayCents: Long = 0L,
    val buttons: List<QuickButton> = emptyList(),
)

@Composable
private fun WidgetContent(
    balanceCents: Long,
    todayCents: Long,
    buttons: List<QuickButton>,
    message: String?,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetColors.background)
            .cornerRadius(20.dp)
            .padding(10.dp),
    ) {
        Header(balanceCents, todayCents, message)
        Spacer(GlanceModifier.height(8.dp))

        buttons.chunked(COLUMNS).forEach { row ->
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                row.forEach { button ->
                    QuickButtonCell(button, GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(6.dp))
                }
                repeat(COLUMNS - row.size) {
                    Spacer(GlanceModifier.defaultWeight())
                }
            }
            Spacer(GlanceModifier.height(6.dp))
        }

        val context = LocalContext.current
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            ActionCell(
                label = "Otro…",
                modifier = GlanceModifier.defaultWeight(),
                background = WidgetColors.key,
                textColor = WidgetColors.onDark,
                action = GlanceModifier.clickable(
                    actionStartActivity(QuickEntryActivity.intent(context, categoryId = null))
                ),
            )
            Spacer(GlanceModifier.width(6.dp))
            ActionCell(
                label = "Deshacer",
                modifier = GlanceModifier.defaultWeight(),
                background = WidgetColors.key,
                textColor = WidgetColors.onDark,
                action = GlanceModifier.clickable(actionRunCallback<UndoAction>()),
            )
        }
    }
}

@Composable
private fun Header(balanceCents: Long, todayCents: Long, message: String?) {
    if (message != null) {
        Text(
            text = message,
            modifier = GlanceModifier.fillMaxWidth(),
            style = TextStyle(
                color = WidgetColors.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    } else {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "Efectivo",
                    style = TextStyle(color = WidgetColors.muted, fontSize = 11.sp),
                )
                Text(
                    text = Money.format(balanceCents),
                    style = TextStyle(
                        color = WidgetColors.onDark,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Hoy",
                    style = TextStyle(color = WidgetColors.muted, fontSize = 11.sp),
                )
                Text(
                    text = Money.format(todayCents),
                    style = TextStyle(
                        color = WidgetColors.onDark,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

@Composable
private fun QuickButtonCell(button: QuickButton, modifier: GlanceModifier) {
    val amountLabel = button.fixedAmountCents
        ?.let { Money.formatShort(it) }
        ?: "…"
    val context = LocalContext.current
    val action = if (button.fixedAmountCents != null) {
        GlanceModifier.clickable(
            actionRunCallback<QuickAddAction>(
                actionParametersOf(WidgetParams.buttonId to button.id)
            )
        )
    } else {
        // "Ask for amount" buttons open the quick-entry dialog with their category preselected.
        GlanceModifier.clickable(
            actionStartActivity(QuickEntryActivity.intent(context, button.categoryId))
        )
    }
    Column(
        modifier = modifier
            .background(WidgetColors.key)
            .cornerRadius(14.dp)
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .then(action),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = button.emoji + " " + button.label,
            maxLines = 1,
            style = TextStyle(
                color = WidgetColors.onDark,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            ),
        )
        Text(
            text = amountLabel,
            maxLines = 1,
            style = TextStyle(
                color = WidgetColors.accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
private fun ActionCell(
    label: String,
    modifier: GlanceModifier,
    background: ColorProvider,
    textColor: ColorProvider,
    action: GlanceModifier,
) {
    Column(
        modifier = modifier
            .background(background)
            .cornerRadius(14.dp)
            .padding(vertical = 12.dp)
            .then(action),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            maxLines = 1,
            style = TextStyle(
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
