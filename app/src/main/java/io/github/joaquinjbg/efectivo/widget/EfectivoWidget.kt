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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.action.actionParametersOf
import androidx.glance.background
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
import io.github.joaquinjbg.efectivo.util.Money
import java.time.LocalDate

private const val COLUMNS = 3

object EfectivoWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = Graph.repository(context)
        provideContent {
            val balance by repository.observeBalance().collectAsState(initial = 0L)
            val today by repository.observeTotalOn(LocalDate.now()).collectAsState(initial = 0L)
            val buttons by repository.observeEnabledQuickButtons()
                .collectAsState(initial = emptyList())
            val message = currentState(WidgetMessageKey)

            WidgetContent(
                balanceCents = balance,
                todayCents = today,
                buttons = buttons,
                message = message,
            )
        }
    }
}

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

        Row(modifier = GlanceModifier.fillMaxWidth()) {
            ActionCell(
                label = "Otro…",
                modifier = GlanceModifier.defaultWeight(),
                background = WidgetColors.key,
                textColor = WidgetColors.onDark,
                action = GlanceModifier,
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
    val action = if (button.fixedAmountCents != null) {
        GlanceModifier.clickable(
            actionRunCallback<QuickAddAction>(
                actionParametersOf(WidgetParams.buttonId to button.id)
            )
        )
    } else {
        GlanceModifier
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
