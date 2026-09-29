package io.github.joaquinjbg.efectivo.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
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
import io.github.joaquinjbg.efectivo.data.ExpenseWithCategory
import io.github.joaquinjbg.efectivo.data.QuickButton
import io.github.joaquinjbg.efectivo.ui.MainActivity
import io.github.joaquinjbg.efectivo.ui.quick.QuickEntryActivity
import io.github.joaquinjbg.efectivo.util.Money
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.YearMonth

private const val COLUMNS = 3

/** Newer rows are pushed off the widget rather than building an unbounded RemoteViews tree. */
private const val MAX_TODAY_ROWS = 20

/**
 * How much the widget shows, decided from its real height.
 *
 * Roughly: up to 3 launcher rows [COMPACT], 4 rows [LARGE], 5 or more [FULL].
 */
private enum class WidgetLevel { COMPACT, LARGE, FULL }

// Rough heights used to work out how many button rows fit. RemoteViews cannot measure,
// so these are estimates of what each block costs once drawn.
private const val HEADER_COMPACT_DP = 22f
private const val HEADER_FULL_DP = 46f
private const val TOTALS_ROW_DP = 40f
private const val ACTION_ROW_DP = 46f
private const val BUTTON_ROW_DP = 58f
private const val TODAY_LIST_MIN_DP = 100f
private const val VERTICAL_SLACK_DP = 28f

private fun levelFor(heightDp: Float): WidgetLevel = when {
    heightDp >= 420f -> WidgetLevel.FULL
    heightDp >= 290f -> WidgetLevel.LARGE
    else -> WidgetLevel.COMPACT
}

object EfectivoWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = Graph.repository(context)
        val today = LocalDate.now()
        val month = YearMonth.from(today)

        // One combined flow instead of several: a redraw costs a single emission.
        val data = combine(
            repository.observeBalance(),
            repository.observeTotalOn(today),
            repository.observeTotalOfWeek(today),
            repository.observeTotalOfMonth(month),
            repository.observeEnabledQuickButtons(),
        ) { balance, dayTotal, weekTotal, monthTotal, buttons ->
            WidgetData(balance, dayTotal, weekTotal, monthTotal, buttons)
        }
        val todayExpenses = repository.observeExpensesOn(today)

        val initial = WidgetData(
            balanceCents = repository.balance(),
            todayCents = repository.totalOn(today),
            weekCents = repository.totalOfWeek(today),
            monthCents = repository.totalOfMonth(month),
            buttons = repository.enabledQuickButtons(),
        )
        val initialExpenses = repository.expensesOn(today)

        provideContent {
            val state by data.collectAsState(initial = initial)
            val expenses by todayExpenses.collectAsState(initial = initialExpenses)
            WidgetContent(
                data = state,
                todayExpenses = expenses,
                message = currentState(WidgetMessageKey),
            )
        }
    }
}

private data class WidgetData(
    val balanceCents: Long = 0L,
    val todayCents: Long = 0L,
    val weekCents: Long = 0L,
    val monthCents: Long = 0L,
    val buttons: List<QuickButton> = emptyList(),
)

@Composable
private fun WidgetContent(
    data: WidgetData,
    todayExpenses: List<ExpenseWithCategory>,
    message: String?,
) {
    val heightDp = LocalSize.current.height.value
    val level = levelFor(heightDp)
    val compact = level == WidgetLevel.COMPACT

    // Only draw the button rows that actually fit, so a small widget is not clipped.
    val reserved = (if (compact) HEADER_COMPACT_DP else HEADER_FULL_DP) +
        (if (compact) 0f else TOTALS_ROW_DP) +
        ACTION_ROW_DP +
        VERTICAL_SLACK_DP +
        (if (level == WidgetLevel.FULL) TODAY_LIST_MIN_DP else 0f)
    val visibleRows = maxOf(1, ((heightDp - reserved) / BUTTON_ROW_DP).toInt())

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetColors.background)
            .cornerRadius(20.dp)
            .padding(10.dp),
    ) {
        Header(data, message, compact = compact)

        if (!compact) {
            Spacer(GlanceModifier.height(6.dp))
            TotalsRow(data)
        }

        Spacer(GlanceModifier.height(8.dp))
        QuickButtonGrid(data.buttons.take(visibleRows * COLUMNS))

        if (level == WidgetLevel.FULL) {
            TodayList(todayExpenses, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.height(6.dp))
        }

        ActionRow()
    }
}

/** Tapping the header opens the app. */
@Composable
private fun Header(data: WidgetData, message: String?, compact: Boolean) {
    val context = LocalContext.current
    val openApp = GlanceModifier.clickable(
        actionStartActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    )

    if (message != null) {
        Text(
            text = message,
            modifier = GlanceModifier.fillMaxWidth().then(openApp),
            style = TextStyle(
                color = WidgetColors.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        return
    }

    if (compact) {
        // One thin line: balance on the left, today's spend on the right.
        Row(
            modifier = GlanceModifier.fillMaxWidth().then(openApp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = Money.format(data.balanceCents),
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = WidgetColors.onDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = "hoy " + Money.format(data.todayCents),
                style = TextStyle(color = WidgetColors.muted, fontSize = 13.sp),
            )
        }
        return
    }

    Row(
        modifier = GlanceModifier.fillMaxWidth().then(openApp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = "Efectivo",
                style = TextStyle(color = WidgetColors.muted, fontSize = 11.sp),
            )
            Text(
                text = Money.format(data.balanceCents),
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
                text = Money.format(data.todayCents),
                style = TextStyle(
                    color = WidgetColors.onDark,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

@Composable
private fun TotalsRow(data: WidgetData) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(WidgetColors.key)
            .cornerRadius(12.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TotalCell("Semana", data.weekCents, GlanceModifier.defaultWeight())
        TotalCell("Mes", data.monthCents, GlanceModifier.defaultWeight())
    }
}

@Composable
private fun TotalCell(label: String, cents: Long, modifier: GlanceModifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = TextStyle(color = WidgetColors.muted, fontSize = 12.sp),
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = Money.format(cents),
            style = TextStyle(
                color = WidgetColors.onDark,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun QuickButtonGrid(buttons: List<QuickButton>) {
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
}

/** Today's expenses, newest first: concept on the left, amount on the right. */
@Composable
private fun TodayList(expenses: List<ExpenseWithCategory>, modifier: GlanceModifier) {
    if (expenses.isEmpty()) {
        Text(
            text = "Sin gastos hoy",
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 8.dp),
            style = TextStyle(color = WidgetColors.muted, fontSize = 13.sp),
        )
        return
    }
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(expenses.take(MAX_TODAY_ROWS), itemId = { it.id }) { expense ->
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = expense.categoryEmoji + " " +
                        (expense.note?.takeIf { it.isNotBlank() } ?: expense.categoryName),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = WidgetColors.onDark, fontSize = 13.sp),
                )
                Text(
                    text = Money.format(expense.amountCents),
                    maxLines = 1,
                    style = TextStyle(
                        color = WidgetColors.onDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ActionRow() {
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
