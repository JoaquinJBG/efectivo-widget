package io.github.joaquinjbg.efectivo.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.joaquinjbg.efectivo.data.QuickButton
import io.github.joaquinjbg.efectivo.ui.AppViewModelFactory
import io.github.joaquinjbg.efectivo.util.Money

@Composable
fun WidgetButtonsScreen(modifier: Modifier = Modifier) {
    val viewModel: WidgetButtonsViewModel = viewModel(factory = AppViewModelFactory)
    val state by viewModel.state.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<QuickButton?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf<QuickButton?>(null) }

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Botones del widget", style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = { creating = true }, modifier = Modifier.size(56.dp)) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Añadir botón",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(state.buttons, key = { _, b -> b.id }) { index, button ->
                ButtonRow(
                    button = button,
                    canMoveUp = index > 0,
                    canMoveDown = index < state.buttons.lastIndex,
                    onTap = { editing = button },
                    onToggle = { viewModel.setEnabled(button, it) },
                    onMoveUp = { viewModel.move(index, index - 1) },
                    onMoveDown = { viewModel.move(index, index + 1) },
                    onDelete = { confirmingDelete = button },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
    }

    if (creating) {
        QuickButtonEditorSheet(
            button = null,
            categories = state.categories,
            onDismiss = { creating = false },
            onSave = {
                viewModel.add(it)
                creating = false
            },
        )
    }

    editing?.let { button ->
        QuickButtonEditorSheet(
            button = button,
            categories = state.categories,
            onDismiss = { editing = null },
            onSave = {
                viewModel.update(it)
                editing = null
            },
        )
    }

    confirmingDelete?.let { button ->
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            title = { Text("¿Eliminar el botón?") },
            text = { Text(button.emoji + "  " + button.label) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(button.id)
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
private fun ButtonRow(
    button: QuickButton,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onTap: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(onClick = onTap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                button.emoji + "  " + button.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                (button.fixedAmountCents?.let { Money.formatShort(it) } ?: "Preguntar importe") +
                    (if (button.compact) " · pequeño" else ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(44.dp)) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = "Subir",
                tint = if (canMoveUp) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.outline
                },
            )
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(44.dp)) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = "Bajar",
                tint = if (canMoveDown) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.outline
                },
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(44.dp)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Eliminar",
                tint = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.size(4.dp))
        Switch(checked = button.enabled, onCheckedChange = onToggle)
    }
}
