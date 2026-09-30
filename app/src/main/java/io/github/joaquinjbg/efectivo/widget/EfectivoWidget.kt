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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
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

/*
 * Layout, top to bottom:
 *
 *   [ Efectivo 90 ]        [ Hoy 42 ]      two separate cards
 *   [ Semana 68        Mes 490       ]     one wide card
 *   [ ☕  ] [ 🥐  ] [ ⛽  ] [ 🎉  ]        square category tiles
 *   [ 1 ][ 2 ][ 5 ][ 10 ][ 20 ][ 50 ]      small coin buttons, own row
 *   [   Otro…    ] [   Deshacer      ]
 *   [ historial del día                ]   takes whatever is left
 *
 * Two hard constraints shape this file:
 *  - Glance only ships container layouts for up to 10 children and drops the rest in
 *    silence, so each band lives in its own nested container.
 *  - RemoteViews cannot measure itself, so the sizes below are estimates in dp.
 */

private const val MAX_TODAY_ROWS = 20

private const val GAP_DP = 8f
private const val WIDGET_PADDING_DP = 20f

private const val HEADER_DP = 56f
private const val HEADER_COMPACT_DP = 26f
private const val TOTALS_ROW_DP = 46f
private const val ACTION_ROW_DP = 48f
private const val VERTICAL_SLACK_DP = 16f
private const val TODAY_LIST_MIN_DP = 70f

/** Category tiles are square; the side is clamped so they stay tappable but not huge. */
private const val CATEGORY_MIN_WIDTH_DP = 64f
private const val CATEGORY_SIDE_MIN_DP = 56f
private const val CATEGORY_SIDE_MAX_DP = 92f

/** Coins are just an amount, so they can be much tighter. */
private const val COIN_MIN_WIDTH_DP = 40f
private const val COIN_HEIGHT_DP = 42f

private const val MIN_COLUMNS = 2
private const val MAX_CATEGORY_COLUMNS = 6
private const val MAX_COIN_COLUMNS = 8
private const val PREFERRED_CATEGORY_COLUMNS = 4

/** Below this height the widget drops to a single header line and hides the totals. */
private const val COMPACT_BELOW_DP = 260f

private data class WidgetLayout(
    val compact: Boolean,
    val categoryColumns: Int,
    val categoryRows: Int,
    val categorySideDp: Float,
    val coinColumns: Int,
    val coinRows: Int,
    val showTodayList: Boolean,
) {
    val categoryLabelSp: Float get() = if (categorySideDp >= 76f) 12f else 10f
    val categoryAmountSp: Float get() = if (categorySideDp >= 76f) 18f else 15f
}

private fun columnsFor(usableWidth: Float, minWidth: Float, max: Int): Int =
    ((usableWidth + GAP_DP) / (minWidth + GAP_DP)).toInt().coerceIn(MIN_COLUMNS, max)

private fun layoutFor(
    widthDp: Float,
    heightDp: Float,
    categoryCount: Int,
    coinCount: Int,
): WidgetLayout {
    val compact = heightDp < COMPACT_BELOW_DP
    val usableWidth = (widthDp - WIDGET_PADDING_DP).coerceAtLeast(CATEGORY_MIN_WIDTH_DP)

    // Four across is the shape the layout is drawn for; narrow widgets get fewer.
    val categoryColumns = columnsFor(usableWidth, CATEGORY_MIN_WIDTH_DP, MAX_CATEGORY_COLUMNS)
        .coerceAtMost(PREFERRED_CATEGORY_COLUMNS)
    // Coins fill their row rather than leaving gaps at the end.
    val coinColumns = columnsFor(usableWidth, COIN_MIN_WIDTH_DP, MAX_COIN_COLUMNS)
        .coerceAtMost(maxOf(1, coinCount))

    // A square tile: the side comes from the column width, then gets clamped.
    val cellWidth = (usableWidth - (categoryColumns - 1) * GAP_DP) / categoryColumns
    val side = cellWidth.coerceIn(CATEGORY_SIDE_MIN_DP, CATEGORY_SIDE_MAX_DP)

    val coinRows = if (coinCount == 0) 0 else (coinCount + coinColumns - 1) / coinColumns
    val coinBand = if (coinRows == 0) 0f else coinRows * (COIN_HEIGHT_DP + GAP_DP)

    val chrome = (if (compact) HEADER_COMPACT_DP else HEADER_DP + TOTALS_ROW_DP) +
        ACTION_ROW_DP + VERTICAL_SLACK_DP + coinBand
    val forCategories = (heightDp - chrome).coerceAtLeast(0f)

    val wantedRows = if (categoryCount == 0) 0 else (categoryCount + categoryColumns - 1) / categoryColumns
    val fittingRows = (forCategories / (side + GAP_DP)).toInt()
    val categoryRows = minOf(wantedRows, maxOf(if (categoryCount == 0) 0 else 1, fittingRows))

    val categoryBand = categoryRows * (side + GAP_DP)
    val showTodayList = !compact && forCategories - categoryBand >= TODAY_LIST_MIN_DP

    return WidgetLayout(
        compact = compact,
        categoryColumns = categoryColumns,
        categoryRows = categoryRows,
        categorySideDp = side,
        coinColumns = coinColumns,
        coinRows = coinRows,
        showTodayList = showTodayList,
    )
}

object EfectivoWidget : GlanceAppWidget() {

    /**
     * Exact, not Responsive: Responsive makes the launcher pick from declared sizes and
     * this one kept choosing far smaller than the real slot.
     */
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val t0 = Perf.now()
        val repository = Graph.repository(context)
        val today = LocalDate.now()
        val month = YearMonth.from(today)

        val data = combine(
            repository.observeWidgetTotals(today, month),
            repository.observeEnabledQuickButtons(),
        ) { totals, buttons -> WidgetData(totals, buttons) }
        val todayExpenses = repository.observeExpensesOn(today)

        val initial = WidgetData(
            totals = repository.widgetTotals(today, month),
            buttons = repository.enabledQuickButtons(),
        )
        Perf.step("provideGlance: consultas iniciales", t0)

        provideContent {
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
    val categories: List<QuickButton> get() = buttons.filterNot { it.compact }
    val coins: List<QuickButton> get() = buttons.filter { it.compact }
}

@Composable
private fun WidgetContent(
    data: WidgetData,
    todayExpenses: List<ExpenseWithCategory>,
    message: String?,
) {
    val size = LocalSize.current
    val layout = layoutFor(
        widthDp = size.width.value,
        heightDp = size.height.value,
        categoryCount = data.categories.size,
        coinCount = data.coins.size,
    )
    Perf.mark(
        "widget ${size.width.value.toInt()}x${size.height.value.toInt()}dp " +
            "cat=${layout.categoryColumns}x${layout.categoryRows}@${layout.categorySideDp.toInt()}dp " +
            "monedas=${layout.coinColumns}x${layout.coinRows} lista=${layout.showTodayList}"
    )

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetColors.background)
            .cornerRadius(20.dp)
            .padding(10.dp),
    ) {
        Header(data, message, layout.compact)

        if (!layout.compact) {
            TotalsRow(data)
        }

        CategoryGrid(
            buttons = data.categories.take(layout.categoryRows * layout.categoryColumns),
            layout = layout,
        )

        CoinRow(data.coins, layout)

        ActionRow()

        if (layout.showTodayList) {
            TodayList(todayExpenses, GlanceModifier.defaultWeight())
        }
    }
}

/** Two separate cards: balance on the left, today's spend on the right. Opens the app. */
@Composable
private fun Header(data: WidgetData, message: String?, compact: Boolean) {
    val context = LocalContext.current
    val openApp = GlanceModifier.clickable(
        actionStartActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    )

    if (message != null) {
        Text(
            text = message,
            maxLines = 1,
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(bottom = GAP_DP.dp)
                .then(openApp),
            style = TextStyle(
                color = WidgetColors.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        return
    }

    if (compact) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(bottom = GAP_DP.dp)
                .then(openApp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = Money.format(data.totals.balanceCents),
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = WidgetColors.onDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = "hoy " + Money.format(data.totals.todayCents),
                style = TextStyle(color = WidgetColors.muted, fontSize = 13.sp),
            )
        }
        return
    }

    Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = GAP_DP.dp).then(openApp)) {
        HeaderCard(
            label = "Efectivo",
            value = Money.format(data.totals.balanceCents),
            modifier = GlanceModifier.defaultWeight().padding(end = GAP_DP.dp),
        )
        HeaderCard(
            label = "Hoy",
            value = Money.format(data.totals.todayCents),
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

@Composable
private fun HeaderCard(label: String, value: String, modifier: GlanceModifier) {
    Column(
        modifier = modifier
            .background(WidgetColors.key)
            .cornerRadius(14.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, style = TextStyle(color = WidgetColors.muted, fontSize = 11.sp))
        Text(
            text = value,
            maxLines = 1,
            style = TextStyle(
                color = WidgetColors.onDark,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun TotalsRow(data: WidgetData) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(bottom = GAP_DP.dp)
            .background(WidgetColors.key)
            .cornerRadius(14.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TotalCell("Semana", data.totals.weekCents, GlanceModifier.defaultWeight())
        TotalCell("Mes", data.totals.monthCents, GlanceModifier.defaultWeight())
    }
}

@Composable
private fun TotalCell(label: String, cents: Long, modifier: GlanceModifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = TextStyle(color = WidgetColors.muted, fontSize = 12.sp),
        )
        Spacer(GlanceModifier.padding(end = 6.dp))
        Text(
            text = Money.format(cents),
            maxLines = 1,
            style = TextStyle(
                color = WidgetColors.onDark,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

/** Square tiles, one per non-coin button. */
@Composable
private fun CategoryGrid(buttons: List<QuickButton>, layout: WidgetLayout) {
    if (buttons.isEmpty()) return
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        buttons.chunked(layout.categoryColumns).forEach { row ->
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height((layout.categorySideDp + GAP_DP).dp),
            ) {
                row.forEachIndexed { index, button ->
                    CategoryTile(
                        button = button,
                        layout = layout,
                        modifier = GlanceModifier
                            .defaultWeight()
                            .padding(
                                end = if (index < layout.categoryColumns - 1) GAP_DP.dp else 0.dp,
                                bottom = GAP_DP.dp,
                            ),
                    )
                }
                repeat(layout.categoryColumns - row.size) {
                    Spacer(GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(button: QuickButton, layout: WidgetLayout, modifier: GlanceModifier) {
    val context = LocalContext.current
    val action = if (button.fixedAmountCents != null) {
        GlanceModifier.clickable(
            actionRunCallback<QuickAddAction>(
                actionParametersOf(WidgetParams.buttonId to button.id)
            )
        )
    } else {
        GlanceModifier.clickable(
            actionStartActivity(QuickEntryActivity.intent(context, button.categoryId))
        )
    }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(WidgetColors.key)
            .cornerRadius(16.dp)
            .padding(horizontal = 2.dp)
            .then(action),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = button.emoji + " " + button.label,
            maxLines = 1,
            style = TextStyle(
                color = WidgetColors.onDark,
                fontSize = layout.categoryLabelSp.sp,
                textAlign = TextAlign.Center,
            ),
        )
        Text(
            text = button.fixedAmountCents?.let { Money.formatShort(it) } ?: "…",
            maxLines = 1,
            style = TextStyle(
                color = WidgetColors.accent,
                fontSize = layout.categoryAmountSp.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/** Coins and notes: just the amount, in a tight row of their own. */
@Composable
private fun CoinRow(coins: List<QuickButton>, layout: WidgetLayout) {
    if (coins.isEmpty()) return
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        coins.chunked(layout.coinColumns).forEach { row ->
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height((COIN_HEIGHT_DP + GAP_DP).dp),
            ) {
                row.forEachIndexed { index, coin ->
                    CoinButton(
                        coin = coin,
                        modifier = GlanceModifier
                            .defaultWeight()
                            .padding(
                                end = if (index < layout.coinColumns - 1) GAP_DP.dp else 0.dp,
                                bottom = GAP_DP.dp,
                            ),
                    )
                }
                repeat(layout.coinColumns - row.size) {
                    Spacer(GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

@Composable
private fun CoinButton(coin: QuickButton, modifier: GlanceModifier) {
    val context = LocalContext.current
    val action = if (coin.fixedAmountCents != null) {
        GlanceModifier.clickable(
            actionRunCallback<QuickAddAction>(
                actionParametersOf(WidgetParams.buttonId to coin.id)
            )
        )
    } else {
        GlanceModifier.clickable(
            actionStartActivity(QuickEntryActivity.intent(context, coin.categoryId))
        )
    }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(WidgetColors.key)
            .cornerRadius(12.dp)
            .then(action),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = coin.fixedAmountCents?.let { Money.formatShort(it) } ?: coin.label,
            maxLines = 1,
            style = TextStyle(
                color = WidgetColors.onDark,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
private fun ActionRow() {
    val context = LocalContext.current
    Row(modifier = GlanceModifier.fillMaxWidth().height(ACTION_ROW_DP.dp)) {
        ActionCell(
            label = "Otro…",
            modifier = GlanceModifier.defaultWeight().padding(end = GAP_DP.dp),
            action = GlanceModifier.clickable(
                actionStartActivity(QuickEntryActivity.intent(context, categoryId = null))
            ),
        )
        ActionCell(
            label = "Deshacer",
            modifier = GlanceModifier.defaultWeight(),
            action = GlanceModifier.clickable(actionRunCallback<UndoAction>()),
        )
    }
}

@Composable
private fun ActionCell(label: String, modifier: GlanceModifier, action: GlanceModifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(WidgetColors.key)
            .cornerRadius(14.dp)
            .then(action),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            maxLines = 1,
            style = TextStyle(
                color = WidgetColors.onDark,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/** Today's expenses, newest first: concept on the left, amount on the right. */
@Composable
private fun TodayList(expenses: List<ExpenseWithCategory>, modifier: GlanceModifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = GAP_DP.dp)
            .background(WidgetColors.key)
            .cornerRadius(14.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        if (expenses.isEmpty()) {
            Text(
                text = "Sin gastos hoy",
                style = TextStyle(color = WidgetColors.muted, fontSize = 13.sp),
            )
            return@Column
        }
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
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
}
