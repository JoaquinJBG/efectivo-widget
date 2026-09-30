package io.github.joaquinjbg.efectivo.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
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
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
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
import io.github.joaquinjbg.efectivo.data.WidgetTotals
import io.github.joaquinjbg.efectivo.ui.MainActivity
import io.github.joaquinjbg.efectivo.ui.quick.QuickEntryActivity
import io.github.joaquinjbg.efectivo.util.Money
import io.github.joaquinjbg.efectivo.util.Perf
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.YearMonth

/** Newer rows are pushed off the widget rather than building an unbounded RemoteViews tree. */
private const val MAX_TODAY_ROWS = 20

/**
 * How much the widget shows, decided from its real height.
 *
 * Roughly: up to 3 launcher rows [COMPACT], 4 rows [LARGE], 5 or more [FULL].
 */
private enum class WidgetLevel { COMPACT, LARGE, FULL }

// Rough heights used to share out the space. RemoteViews cannot measure itself,
// so these are estimates of what each block costs once drawn.
private const val MIN_COLUMNS = 2
private const val MAX_COLUMNS = 6

/** Wide enough that "Desayuno" and "50 €" are not cut off. */
private const val BUTTON_MIN_WIDTH_DP = 92f
private const val BUTTON_GAP_DP = 6f
private const val WIDGET_PADDING_DP = 20f
private const val HEADER_COMPACT_DP = 22f
private const val HEADER_FULL_DP = 46f
private const val TOTALS_ROW_DP = 40f
private const val ACTION_ROW_DP = 46f
private const val TODAY_LIST_MIN_DP = 120f
private const val VERTICAL_SLACK_DP = 28f

/** A button row never shrinks below this, and never grows past it. */
private const val BUTTON_ROW_MIN_DP = 52f
private const val BUTTON_ROW_MAX_DP = 132f

/** Height a row wants before the leftover space is offered to the day list. */
private const val BUTTON_ROW_COMFORTABLE_DP = 88f

private fun levelFor(heightDp: Float): WidgetLevel = when {
    heightDp >= 420f -> WidgetLevel.FULL
    heightDp >= 290f -> WidgetLevel.LARGE
    else -> WidgetLevel.COMPACT
}

/** How the button grid is laid out once the surrounding chrome has taken its share. */
private data class GridLayout(
    val columns: Int,
    val rows: Int,
    val rowHeightDp: Float,
    val showTodayList: Boolean,
) {
    /** Type scales with the buttons, so a tall widget reads from further away. */
    val labelSp: Float get() = when {
        rowHeightDp >= 110f -> 18f
        rowHeightDp >= 90f -> 16f
        rowHeightDp >= 68f -> 14f
        else -> 12f
    }

    val amountSp: Float get() = when {
        rowHeightDp >= 110f -> 28f
        rowHeightDp >= 90f -> 24f
        rowHeightDp >= 68f -> 20f
        else -> 16f
    }
}

private fun layoutFor(
    widthDp: Float,
    heightDp: Float,
    buttonCount: Int,
    level: WidgetLevel,
): GridLayout {
    val usableWidth = widthDp - WIDGET_PADDING_DP
    val columns = ((usableWidth + BUTTON_GAP_DP) / (BUTTON_MIN_WIDTH_DP + BUTTON_GAP_DP))
        .toInt()
        .coerceIn(MIN_COLUMNS, MAX_COLUMNS)

    val chrome = (if (level == WidgetLevel.COMPACT) HEADER_COMPACT_DP else HEADER_FULL_DP) +
        (if (level == WidgetLevel.COMPACT) 0f else TOTALS_ROW_DP) +
        ACTION_ROW_DP +
        VERTICAL_SLACK_DP
    val forGrid = (heightDp - chrome).coerceAtLeast(BUTTON_ROW_MIN_DP)

    val wantedRows = if (buttonCount == 0) 1 else (buttonCount + columns - 1) / columns
    val fittingRows = maxOf(1, (forGrid / BUTTON_ROW_MIN_DP).toInt())
    val rows = minOf(wantedRows, fittingRows)

    // The day list only earns space once every button row already has a comfortable height.
    val comfortable = rows * BUTTON_ROW_COMFORTABLE_DP
    val showTodayList = level == WidgetLevel.FULL &&
        forGrid - comfortable >= TODAY_LIST_MIN_DP

    val gridHeight = if (showTodayList) comfortable else forGrid
    val rowHeight = (gridHeight / rows).coerceIn(BUTTON_ROW_MIN_DP, BUTTON_ROW_MAX_DP)

    return GridLayout(columns, rows, rowHeight, showTodayList)
}

/**
 * Declared sizes the launcher picks from.
 *
 * SizeMode.Exact built one layout per orientation, and on a foldable — two screens with
 * very different proportions — the launcher kept showing the wide layout inside the narrow
 * slot, clipping the content on both edges. Responsive lets it choose a layout that fits.
 */
private val WIDGET_SIZES = setOf(
    DpSize(150.dp, 110.dp),
    DpSize(240.dp, 180.dp),
    DpSize(240.dp, 380.dp),
    // The reference screen is the outer one: 353 dp wide, so these must stay under it.
    DpSize(330.dp, 220.dp),
    DpSize(330.dp, 420.dp),
    DpSize(330.dp, 530.dp),
    DpSize(330.dp, 640.dp), // outer screen, full height
    DpSize(520.dp, 340.dp), // inner screen, landscape
    DpSize(520.dp, 640.dp), // inner screen, full height
)

object EfectivoWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(WIDGET_SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val t0 = Perf.now()
        val repository = Graph.repository(context)
        val today = LocalDate.now()
        val month = YearMonth.from(today)
        Perf.step("provideGlance: repositorio listo", t0)

        // Two subscriptions, not five: all four totals come from one SQL round trip.
        val data = combine(
            repository.observeWidgetTotals(today, month),
            repository.observeEnabledQuickButtons(),
        ) { totals, buttons -> WidgetData(totals, buttons) }
        val todayExpenses = repository.observeExpensesOn(today)

        // Seeded so the first frame shows real numbers instead of zeros. The day list is
        // not seeded: it only appears on the largest size and costs a query every redraw.
        val initial = WidgetData(
            totals = repository.widgetTotals(today, month),
            buttons = repository.enabledQuickButtons(),
        )
        Perf.step("provideGlance: consultas iniciales", t0)

        provideContent {
            Perf.step("provideGlance: composicion", t0)
            val state by data.collectAsState(initial = initial)
            val expenses by todayExpenses.collectAsState(initial = emptyList())
            WidgetContent(
                data = state,
                todayExpenses = expenses,
                message = currentState(WidgetMessageKey),
            )
        }
    }
}

private data class WidgetData(
    val totals: WidgetTotals = WidgetTotals(0L, 0L, 0L, 0L),
    val buttons: List<QuickButton> = emptyList(),
) {
    val balanceCents: Long get() = totals.balanceCents
    val todayCents: Long get() = totals.todayCents
    val weekCents: Long get() = totals.weekCents
    val monthCents: Long get() = totals.monthCents
}

@Composable
private fun WidgetContent(
    data: WidgetData,
    todayExpenses: List<ExpenseWithCategory>,
    message: String?,
) {
    val size = LocalSize.current
    val heightDp = size.height.value
    val level = levelFor(heightDp)
    val compact = level == WidgetLevel.COMPACT
    val grid = layoutFor(size.width.value, heightDp, data.buttons.size, level)
    Perf.mark(
        "widget ${size.width.value.toInt()}x${heightDp.toInt()}dp $level " +
            "${grid.columns}col ${grid.rows}fil ${grid.rowHeightDp.toInt()}dp/fila " +
            "lista=${grid.showTodayList}"
    )

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
        QuickButtonGrid(data.buttons.take(grid.rows * grid.columns), grid)

        // Kept directly under the grid: when the day list grew it used to push
        // "Deshacer" off the bottom of a tall widget.
        ActionRow()

        if (grid.showTodayList) {
            Spacer(GlanceModifier.height(8.dp))
            TodayList(todayExpenses, GlanceModifier.defaultWeight())
        }
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
private fun ColumnScope.QuickButtonGrid(buttons: List<QuickButton>, grid: GridLayout) {
    buttons.chunked(grid.columns).forEach { row ->
        // defaultWeight, not a fixed height: the declared size is only an estimate, so the
        // rows share out whatever the launcher actually handed us. With fixed heights a
        // taller slot left dead space and a shorter one dropped the last rows entirely.
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            row.forEach { button ->
                QuickButtonCell(
                    button = button,
                    grid = grid,
                    modifier = GlanceModifier.defaultWeight(),
                )
                Spacer(GlanceModifier.width(BUTTON_GAP_DP.dp))
            }
            repeat(grid.columns - row.size) {
                Spacer(GlanceModifier.defaultWeight())
            }
        }
        Spacer(GlanceModifier.height(BUTTON_GAP_DP.dp))
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
private fun QuickButtonCell(button: QuickButton, grid: GridLayout, modifier: GlanceModifier) {
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
            .fillMaxHeight()
            .background(WidgetColors.key)
            .cornerRadius(14.dp)
            .padding(horizontal = 4.dp)
            .then(action),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = button.emoji + " " + button.label,
            maxLines = 1,
            style = TextStyle(
                color = WidgetColors.onDark,
                fontSize = grid.labelSp.sp,
                textAlign = TextAlign.Center,
            ),
        )
        Text(
            text = amountLabel,
            maxLines = 1,
            style = TextStyle(
                color = WidgetColors.accent,
                fontSize = grid.amountSp.sp,
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
