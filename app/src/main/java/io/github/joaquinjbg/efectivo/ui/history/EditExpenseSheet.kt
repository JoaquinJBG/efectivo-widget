package io.github.joaquinjbg.efectivo.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.joaquinjbg.efectivo.data.Category
import io.github.joaquinjbg.efectivo.data.LedgerEntry
import io.github.joaquinjbg.efectivo.ui.common.AmountDisplay
import io.github.joaquinjbg.efectivo.ui.common.BigButton
import io.github.joaquinjbg.efectivo.ui.common.CategoryChips
import io.github.joaquinjbg.efectivo.ui.common.Keypad
import io.github.joaquinjbg.efectivo.ui.common.rememberAmountInput
import io.github.joaquinjbg.efectivo.util.Money

/** Hoja de edición que se abre al tocar una fila del historial. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseSheet(
    entry: LedgerEntry,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (amountCents: Long, categoryId: Long, note: String?) -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val input = rememberAmountInput(initial = Money.formatNumber(entry.amountCents))
    var selected by remember { mutableStateOf(entry.categoryId ?: 0L) }
    var note by remember { mutableStateOf(entry.note.orEmpty()) }

    val cents = input.cents
    val canSave = cents != null && cents > 0L

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Editar gasto", style = MaterialTheme.typography.titleLarge)
            AmountDisplay(input)
            CategoryChips(
                categories = categories,
                selectedId = selected,
                onSelect = { selected = it },
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Nota (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Keypad(input)
            BigButton(
                text = "Guardar",
                enabled = canSave,
                onClick = {
                    val amount = cents ?: return@BigButton
                    onSave(amount, selected, note)
                },
            )
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text("Eliminar", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
