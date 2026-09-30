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
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
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
import io.github.joaquinjbg.efectivo.Graph
import io.github.joaquinjbg.efectivo.R
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
 * Disposición, de arriba abajo:
 *
 *   [ Efectivo 90 ]        [ Hoy 42 ]      dos tarjetas sueltas
 *   [ Semana 68 ]          [ Mes 490 ]     otras dos
 *   [ ☕ ] [ 🥐 ] [ ⛽ ] [ 🎉 ]            baldosas de categoría
 *   [ 1€ ][ 2€ ][ 5€ ][ 10€ ][ 20€ ]       monedas, en su propia fila
 *   [ historial del día                ]   se queda con lo que sobre
 *
 * Dos límites de Glance condicionan todo este fichero:
 *
 *  - Solo trae layouts de contenedor para 10 hijos como máximo, y descarta el resto
 *    sin avisar. Por eso cada banda vive en su contenedor anidado, y por eso los
 *    huecos se hacen con Spacer y no encadenando padding.
 *  - RemoteViews no puede medirse a sí mismo, así que el reparto vertical se calcula
 *    con estimaciones en dp de lo que ocupa cada bloque.
 */

private data class WidgetLayout(
    val compact: Boolean,
    val categoryColumns: Int,
    val categoryRows: Int,
    val categorySideDp: Float,
    val coinColumns: Int,
    val coinRows: Int,
    val showTodayList: Boolean,
) {
    val categoryLabelSp: Float
        get() = if (categorySideDp >= WidgetStyle.CATEGORY_SMALL_BELOW) {
            WidgetStyle.CATEGORY_LABEL_SP
        } else {
            WidgetStyle.CATEGORY_LABEL_SMALL_SP
        }

    val categoryAmountSp: Float
        get() = if (categorySideDp >= WidgetStyle.CATEGORY_SMALL_BELOW) {
            WidgetStyle.CATEGORY_AMOUNT_SP
        } else {
            WidgetStyle.CATEGORY_AMOUNT_SMALL_SP
        }
}

private const val MIN_COLUMNS = 2
private const val MAX_CATEGORY_COLUMNS = 6
private const val MAX_COIN_COLUMNS = 8

/** A partir de aquí, una fila de monedas se parte en dos grupos (ver CoinRow). */
private const val MAX_COINS_PER_GROUP = 5

private fun columnsFor(usableWidth: Float, minWidth: Float, max: Int): Int =
    ((usableWidth + WidgetStyle.GAP) / (minWidth + WidgetStyle.GAP)).toInt().coerceIn(MIN_COLUMNS, max)

private fun layoutFor(
    widthDp: Float,
    heightDp: Float,
    categoryCount: Int,
    coinCount: Int,
): WidgetLayout {
    val compact = heightDp < WidgetStyle.COMPACT_BELOW
    val usableWidth = (widthDp - WidgetStyle.OUTER_PADDING).coerceAtLeast(WidgetStyle.CATEGORY_MIN_WIDTH)

    // Cuatro por fila es la forma para la que está pensado; si no cabe, menos.
    val categoryColumns = columnsFor(usableWidth, WidgetStyle.CATEGORY_MIN_WIDTH, MAX_CATEGORY_COLUMNS)
        .coerceAtMost(WidgetStyle.CATEGORY_COLUMNS)
    // Las monedas llenan su fila en vez de dejar huecos al final.
    val coinColumns = columnsFor(usableWidth, WidgetStyle.COIN_MIN_WIDTH, MAX_COIN_COLUMNS)
        .coerceAtMost(maxOf(1, coinCount))

    // Baldosa cuadrada: el lado sale del ancho de columna y luego se acota.
    val cellWidth = (usableWidth - (categoryColumns - 1) * WidgetStyle.GAP) / categoryColumns
    val side = cellWidth.coerceIn(WidgetStyle.CATEGORY_HEIGHT_MIN, WidgetStyle.CATEGORY_HEIGHT_MAX)

    val coinRows = if (coinCount == 0) 0 else (coinCount + coinColumns - 1) / coinColumns
    val coinBand = if (coinRows == 0) 0f else coinRows * (WidgetStyle.COIN_HEIGHT + WidgetStyle.GAP)

    val chrome = (if (compact) WidgetStyle.HEADER_COMPACT_HEIGHT else WidgetStyle.HEADER_HEIGHT + WidgetStyle.TOTALS_HEIGHT) +
        WidgetStyle.GAP + coinBand
    val forCategories = (heightDp - chrome).coerceAtLeast(0f)

    val wantedRows = if (categoryCount == 0) 0 else (categoryCount + categoryColumns - 1) / categoryColumns
    val fittingRows = (forCategories / (side + WidgetStyle.GAP)).toInt()
    val categoryRows = minOf(wantedRows, maxOf(if (categoryCount == 0) 0 else 1, fittingRows))

    val categoryBand = categoryRows * (side + WidgetStyle.GAP)
    val showTodayList = !compact

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
     * Exact, no Responsive. Con Responsive el launcher elige entre unos tamaños que hay
     * que declarar, y este escogía uno mucho menor que el hueco real: estiraba un layout
     * de 240x180 sobre 333x700. Exact compone para el tamaño de verdad.
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
            WidgetContent(data = state, todayExpenses = expenses)
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
private fun WidgetContent(data: WidgetData, todayExpenses: List<ExpenseWithCategory>) {
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
            .background(WidgetStyle.background)
            .cornerRadius(WidgetStyle.WIDGET_CORNER.dp)
            .padding(WidgetStyle.OUTER_PADDING.dp),
    ) {
        Header(data, layout.compact)

        if (!layout.compact) {
            TotalsRow(data)
        }

        CategoryGrid(
            buttons = data.categories.take(layout.categoryRows * layout.categoryColumns),
            layout = layout,
        )

        CoinRow(data.coins, layout)

        if (!layout.compact) {
            // Separador real: un padding(top) dentro de TodayList lo anulaba su propio
            // relleno interno, que es la trampa de siempre en Glance.
            Spacer(GlanceModifier.height(WidgetStyle.HISTORY_TOP_GAP.dp))
            TodayList(todayExpenses, GlanceModifier.defaultWeight())
        }
    }
}

/** Two separate cards: balance on the left, today's spend on the right. Opens the app. */
@Composable
private fun Header(data: WidgetData, compact: Boolean) {
    val context = LocalContext.current
    val openApp = GlanceModifier.clickable(
        actionStartActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ),
        rippleOverride = R.drawable.widget_sin_onda,
    )

    if (compact) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(bottom = WidgetStyle.GAP.dp)
                .then(openApp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = Money.format(data.totals.balanceCents),
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = WidgetStyle.onDark,
                    fontSize = WidgetStyle.TOTALS_AMOUNT_SP.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = "hoy " + Money.format(data.totals.todayCents),
                style = TextStyle(color = WidgetStyle.muted, fontSize = WidgetStyle.HISTORY_TEXT_SP.sp),
            )
        }
        return
    }

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(bottom = WidgetStyle.HEADER_BOTTOM_GAP.dp)
            .then(openApp),
    ) {
        HeaderCard(
            label = "Efectivo",
            value = Money.format(data.totals.balanceCents),
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(WidgetStyle.HEADER_GAP.dp))
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
            .background(WidgetStyle.card)
            .cornerRadius(WidgetStyle.HEADER_CORNER.dp)
            .padding(
                horizontal = WidgetStyle.CARD_PADDING_H.dp,
                vertical = WidgetStyle.CARD_PADDING_V.dp,
            ),
    ) {
        Text(label, style = TextStyle(color = WidgetStyle.muted, fontSize = WidgetStyle.HEADER_LABEL_SP.sp))
        Text(
            text = value,
            maxLines = 1,
            style = TextStyle(
                color = WidgetStyle.onDark,
                fontSize = WidgetStyle.HEADER_AMOUNT_SP.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun TotalsRow(data: WidgetData) {
    // Dos tarjetas separadas, no una sola partida en dos: antes compartian fondo y
    // por eso no habia forma de meter separacion entre "Semana" y "Mes".
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(bottom = WidgetStyle.TOTALS_BOTTOM_GAP.dp),
    ) {
        TotalCell(
            label = "Semana",
            cents = data.totals.weekCents,
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(WidgetStyle.TOTALS_GAP.dp))
        TotalCell(
            label = "Mes",
            cents = data.totals.monthCents,
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

@Composable
private fun TotalCell(label: String, cents: Long, modifier: GlanceModifier) {
    Column(
        modifier = modifier
            .background(WidgetStyle.card)
            .cornerRadius(WidgetStyle.HEADER_CORNER.dp)
            .padding(
                horizontal = WidgetStyle.CARD_PADDING_H.dp,
                vertical = WidgetStyle.CARD_PADDING_V.dp,
            ),
    ) {
        Text(
            text = label,
            maxLines = 1,
            style = TextStyle(color = WidgetStyle.muted, fontSize = WidgetStyle.TOTALS_LABEL_SP.sp),
        )
        Text(
            text = Money.format(cents),
            maxLines = 1,
            style = TextStyle(
                color = WidgetStyle.onDark,
                fontSize = WidgetStyle.TOTALS_AMOUNT_SP.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

/** Baldosas de categoría: una por cada botón que no sea moneda. */
@Composable
private fun CategoryGrid(buttons: List<QuickButton>, layout: WidgetLayout) {
    if (buttons.isEmpty()) return
    Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = WidgetStyle.CATEGORY_GAP.dp)) {
        buttons.chunked(layout.categoryColumns).forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) Spacer(GlanceModifier.height(WidgetStyle.CATEGORY_GAP.dp))
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(layout.categorySideDp.dp),
            ) {
                row.forEachIndexed { index, button ->
                    if (index > 0) Spacer(GlanceModifier.width(WidgetStyle.CATEGORY_GAP.dp))
                    CategoryTile(
                        button = button,
                        layout = layout,
                        modifier = GlanceModifier.defaultWeight(),
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
            ),
            rippleOverride = R.drawable.widget_sin_onda,
        )
    } else {
        GlanceModifier.clickable(
            actionStartActivity(QuickEntryActivity.intent(context, button.categoryId)),
            rippleOverride = R.drawable.widget_sin_onda,
        )
    }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(WidgetStyle.card)
            .cornerRadius(WidgetStyle.CATEGORY_CORNER.dp)
            .padding(horizontal = WidgetStyle.CATEGORY_PADDING_H.dp)
            .then(action),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = button.emoji + " " + button.label,
            maxLines = 1,
            style = TextStyle(
                color = WidgetStyle.onDark,
                fontSize = layout.categoryLabelSp.sp,
                textAlign = TextAlign.Center,
            ),
        )
        Text(
            text = button.fixedAmountCents?.let { Money.formatShort(it) } ?: "…",
            maxLines = 1,
            style = TextStyle(
                color = WidgetStyle.accent,
                fontSize = layout.categoryAmountSp.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/** Monedas y billetes: solo el importe, en una fila aparte más apretada. */
@Composable
private fun CoinRow(coins: List<QuickButton>, layout: WidgetLayout) {
    if (coins.isEmpty()) return
    Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = WidgetStyle.COIN_GAP.dp)) {
        coins.chunked(layout.coinColumns).forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) Spacer(GlanceModifier.height(WidgetStyle.COIN_GAP.dp))
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(WidgetStyle.COIN_HEIGHT.dp),
            ) {
                // Glance solo trae layouts de hasta 10 hijos, y 6 monedas con un
                // separador entre cada dos serian 11. Se parten en dos grupos, y
                // dentro de cada uno ya caben los separadores: asi todas miden lo
                // mismo y no sobra medio hueco en los extremos de la fila.
                if (row.size > MAX_COINS_PER_GROUP && row.size % 2 == 0) {
                    val half = row.size / 2
                    CoinGroup(row.take(half), GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(WidgetStyle.COIN_GAP.dp))
                    CoinGroup(row.drop(half), GlanceModifier.defaultWeight())
                } else {
                    row.forEachIndexed { index, coin ->
                        if (index > 0) Spacer(GlanceModifier.width(WidgetStyle.COIN_GAP.dp))
                        CoinButton(coin = coin, modifier = GlanceModifier.defaultWeight())
                    }
                    repeat(layout.coinColumns - row.size) {
                        Spacer(GlanceModifier.defaultWeight())
                    }
                }
            }
        }
    }
}

/** Media fila de monedas, con sus propios separadores. */
@Composable
private fun CoinGroup(coins: List<QuickButton>, modifier: GlanceModifier) {
    Row(modifier = modifier.fillMaxHeight()) {
        coins.forEachIndexed { index, coin ->
            if (index > 0) Spacer(GlanceModifier.width(WidgetStyle.COIN_GAP.dp))
            CoinButton(coin = coin, modifier = GlanceModifier.defaultWeight())
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
            ),
            rippleOverride = R.drawable.widget_sin_onda,
        )
    } else {
        GlanceModifier.clickable(
            actionStartActivity(QuickEntryActivity.intent(context, coin.categoryId)),
            rippleOverride = R.drawable.widget_sin_onda,
        )
    }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(WidgetStyle.card)
            .cornerRadius(WidgetStyle.COIN_CORNER.dp)
            .then(action),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = coin.fixedAmountCents
                ?.let { Money.formatShort(it, spaced = WidgetStyle.COIN_SPACE_BEFORE_EURO) }
                ?: coin.label,
            maxLines = 1,
            style = TextStyle(
                color = WidgetStyle.onDark,
                fontSize = WidgetStyle.COIN_TEXT_SP.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/** Los gastos de hoy, del más reciente al más antiguo. */
@Composable
private fun TodayList(expenses: List<ExpenseWithCategory>, modifier: GlanceModifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(WidgetStyle.card)
            .cornerRadius(WidgetStyle.HEADER_CORNER.dp)
            .padding(
                horizontal = WidgetStyle.CARD_PADDING_H.dp,
                vertical = WidgetStyle.HISTORY_ROW_PADDING.dp,
            ),
    ) {
        if (expenses.isEmpty()) {
            Text(
                text = "Sin gastos hoy",
                style = TextStyle(color = WidgetStyle.muted, fontSize = WidgetStyle.HISTORY_TEXT_SP.sp),
            )
            return@Column
        }
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            items(expenses.take(WidgetStyle.HISTORY_MAX_ROWS), itemId = { it.id }) { expense ->
                HistoryRow(expense)
            }
        }
    }
}

/** Un gasto: concepto, importe y una ✕ que borra solo ese. */
@Composable
private fun HistoryRow(expense: ExpenseWithCategory) {
    val note = expense.note?.takeIf { it.isNotBlank() }
    val amount = Money.format(expense.amountCents)

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = WidgetStyle.HISTORY_ROW_PADDING.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = expense.categoryEmoji + " " + expense.categoryName,
                maxLines = 1,
                style = TextStyle(
                    color = WidgetStyle.onDark,
                    fontSize = WidgetStyle.HISTORY_TEXT_SP.sp,
                ),
            )
            if (!note.isNullOrBlank()) {
                Text(
                    text = note,
                    maxLines = 1,
                    style = TextStyle(
                        color = WidgetStyle.noteText,
                        fontSize = WidgetStyle.HISTORY_NOTE_SP.sp,
                    ),
                )
            }
        }
        Text(
            text = amount,
            maxLines = 1,
            style = TextStyle(
                color = WidgetStyle.onDark,
                fontSize = WidgetStyle.HISTORY_TEXT_SP.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            text = "✕",
            maxLines = 1,
            // El hueco alrededor es la zona táctil: el aspa sola es imposible de acertar.
            modifier = GlanceModifier
                .padding(
                    horizontal = WidgetStyle.HISTORY_DELETE_PADDING_H.dp,
                    vertical = WidgetStyle.HISTORY_DELETE_PADDING_V.dp,
                )
                .clickable(
                    actionRunCallback<DeleteExpenseAction>(
                        actionParametersOf(WidgetParams.expenseId to expense.id)
                    ),
                    rippleOverride = R.drawable.widget_sin_onda,
                ),
            style = TextStyle(
                color = WidgetStyle.danger,
                fontSize = WidgetStyle.HISTORY_DELETE_SP.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
