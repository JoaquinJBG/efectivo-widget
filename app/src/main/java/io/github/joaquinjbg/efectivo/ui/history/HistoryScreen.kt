package io.github.joaquinjbg.efectivo.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.joaquinjbg.efectivo.data.DaySection
import io.github.joaquinjbg.efectivo.data.ExpenseWithCategory
import io.github.joaquinjbg.efectivo.ui.AppViewModelFactory
import io.github.joaquinjbg.efectivo.ui.common.euro
import io.github.joaquinjbg.efectivo.util.Dates
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    val viewModel: HistoryViewModel = viewModel(factory = AppViewModelFactory)
    val state by viewModel.state.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<ExpenseWithCategory?>(null) }
    var confirmingDelete by remember { mutableStateOf<ExpenseWithCategory?>(null) }

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text(
            "Historial",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = 16.dp),
        )

        if (state.days.isEmpty()) {
            Text(
                "Todavía no hay gastos registrados",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                state.days.forEach { day ->
                    item(key = "header-" + day.date) { DayHeader(day) }
                    items(day.expenses, key = { it.id }) { expense ->
                        ExpenseRow(
                            expense = expense,
                            onTap = { editing = expense },
                            onLongPress = { confirmingDelete = expense },
                            onDelete = { viewModel.delete(expense.id) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }

    editing?.let { expense ->
        EditExpenseSheet(
            expense = expense,
            categories = state.categories,
            onDismiss = { editing = null },
            onSave = { cents, categoryId, note ->
                viewModel.update(expense, cents, categoryId, note)
                editing = null
            },
            onDelete = {
                viewModel.delete(expense.id)
                editing = null
            },
        )
    }

    confirmingDelete?.let { expense ->
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            title = { Text("¿Eliminar el gasto?") },
            text = {
                Text(expense.categoryName + " · " + euro(expense.amountCents))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(expense.id)
                    confirmingDelete = null
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = null }) { Text("Cancelar") }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
private fun DayHeader(day: DaySection) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 20.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Dates.dayLabel(day.date),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            euro(day.totalCents),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExpenseRow(
    expense: ExpenseWithCategory,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(MaterialTheme.colorScheme.background)
                .combinedClickable(onClick = onTap, onLongClick = onLongPress),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    expense.categoryEmoji + "  " + expense.categoryName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                val time = Instant.ofEpochMilli(expense.timestamp)
                    .atZone(ZoneId.systemDefault()).toLocalTime().format(timeFormat)
                Text(
                    if (expense.note.isNullOrBlank()) time else time + " · " + expense.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                euro(expense.amountCents),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            // Visible, como en el widget: deslizar funcionaba, pero nada lo indicaba.
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Eliminar gasto",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
}
