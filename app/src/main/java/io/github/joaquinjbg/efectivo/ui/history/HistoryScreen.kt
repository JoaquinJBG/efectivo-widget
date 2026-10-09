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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import io.github.joaquinjbg.efectivo.data.LedgerEntry
import io.github.joaquinjbg.efectivo.data.MovementType
import io.github.joaquinjbg.efectivo.ui.AppViewModelFactory
import io.github.joaquinjbg.efectivo.ui.common.euro
import io.github.joaquinjbg.efectivo.ui.theme.Accent
import io.github.joaquinjbg.efectivo.util.Dates
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    val viewModel: HistoryViewModel = viewModel(factory = AppViewModelFactory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<LedgerEntry?>(null) }
    var confirmingDelete by remember { mutableStateOf<LedgerEntry?>(null) }

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text(
            "Historial",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
        )

        FilterRow(active = filter, onSelect = viewModel::setFilter)

        if (state.days.isEmpty()) {
            Text(
                when (filter) {
                    MovementType.GASTO -> "Todavía no hay gastos"
                    MovementType.INGRESO -> "Todavía no has añadido efectivo"
                    null -> "Todavía no hay movimientos"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                state.days.forEach { day ->
                    item(key = "cabecera-" + day.date) { DayHeader(day) }
                    items(day.entries, key = { it.type.name + it.id }) { entry ->
                        EntryRow(
                            entry = entry,
                            onTap = { editing = entry },
                            onLongPress = { confirmingDelete = entry },
                            onDelete = { viewModel.delete(entry) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }

    editing?.let { entry ->
        when (entry.type) {
            MovementType.GASTO -> EditExpenseSheet(
                entry = entry,
                categories = state.categories,
                onDismiss = { editing = null },
                onSave = { cents, categoryId, note ->
                    viewModel.updateExpense(entry.id, cents, categoryId, note)
                    editing = null
                },
                onDelete = {
                    viewModel.delete(entry)
                    editing = null
                },
            )

            MovementType.INGRESO -> EditCashSheet(
                entry = entry,
                onDismiss = { editing = null },
                onSave = { cents, note ->
                    viewModel.updateCash(entry.id, cents, note)
                    editing = null
                },
                onDelete = {
                    viewModel.delete(entry)
                    editing = null
                },
            )
        }
    }

    confirmingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            title = {
                Text(
                    if (entry.type == MovementType.GASTO) "¿Eliminar el gasto?"
                    else "¿Eliminar la entrada de efectivo?"
                )
            },
            text = { Text(conceptOf(entry) + " · " + euro(entry.amountCents)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(entry)
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
private fun FilterRow(active: HistoryFilter, onSelect: (HistoryFilter) -> Unit) {
    val options = listOf(
        null to "Todo",
        MovementType.GASTO to "Gastos",
        MovementType.INGRESO to "Ingresos",
    )
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = active == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
                modifier = Modifier.height(48.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
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
        // Los dos totales por separado: sumarlos escondería la retirada del cajero.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (day.receivedCents != 0L) {
                Text(
                    "+" + euro(day.receivedCents),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Accent,
                )
            }
            if (day.spentCents != 0L) {
                Text(
                    euro(day.spentCents),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(
    entry: LedgerEntry,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
) {
    val isIncome = entry.type == MovementType.INGRESO
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(MaterialTheme.colorScheme.background)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                conceptOf(entry),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            val time = Instant.ofEpochMilli(entry.timestamp)
                .atZone(ZoneId.systemDefault()).toLocalTime().format(timeFormat)
            Text(
                if (entry.note.isNullOrBlank()) time else time + " · " + entry.note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (isIncome && entry.amountCents > 0) "+" + euro(entry.amountCents)
            else euro(entry.amountCents),
            style = MaterialTheme.typography.titleLarge,
            color = if (isIncome) Accent else MaterialTheme.colorScheme.onBackground,
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Eliminar",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Lo que se lee a la izquierda de la fila. */
private fun conceptOf(entry: LedgerEntry): String = when {
    entry.type == MovementType.GASTO ->
        (entry.categoryEmoji.orEmpty() + "  " + entry.categoryName.orEmpty()).trim()
    entry.isCorrection -> "✏️  Corrección"
    else -> "💶  Efectivo añadido"
}
