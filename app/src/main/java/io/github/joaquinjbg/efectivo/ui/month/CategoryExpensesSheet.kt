package io.github.joaquinjbg.efectivo.ui.month

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.joaquinjbg.efectivo.data.CategoryShare
import io.github.joaquinjbg.efectivo.data.ExpenseWithCategory
import io.github.joaquinjbg.efectivo.ui.common.euro
import io.github.joaquinjbg.efectivo.util.Dates
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dayTimeFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Dates.SPANISH)

/**
 * Los gastos de una categoría en el mes que se está viendo, con su nota si la tienen.
 * Solo lectura: para editar o borrar está el historial.
 *
 * [expenses] es null mientras carga. El total sale de esa misma lista, así que coincide
 * con el de la fila del desglose, que suma las mismas filas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryExpensesSheet(
    category: CategoryShare,
    month: YearMonth,
    expenses: List<ExpenseWithCategory>?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Header(category, month, expenses)
            Spacer(Modifier.size(8.dp))
            when {
                expenses == null -> Unit
                expenses.isEmpty() -> Text(
                    "Sin gastos en este mes",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                else -> LazyColumn(Modifier.fillMaxWidth()) {
                    items(expenses, key = { it.id }) { expense ->
                        ExpenseRow(expense)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(category: CategoryShare, month: YearMonth, expenses: List<ExpenseWithCategory>?) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                category.emoji + "  " + category.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            val count = expenses?.size
            Text(
                Dates.monthLabel(month) + when (count) {
                    null -> ""
                    1 -> " · 1 gasto"
                    else -> " · $count gastos"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expenses != null) {
            Text(
                euro(expenses.sumOf { it.amountCents }),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun ExpenseRow(expense: ExpenseWithCategory) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                dayTimeText(expense.timestamp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (!expense.note.isNullOrBlank()) {
                Text(
                    expense.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            euro(expense.amountCents),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** "Mar 3 nov · 18:42", en la zona horaria del sistema. */
private fun dayTimeText(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(dayTimeFormat)
        .replace(".", "")
        .replaceFirstChar { it.uppercase(Dates.SPANISH) }
