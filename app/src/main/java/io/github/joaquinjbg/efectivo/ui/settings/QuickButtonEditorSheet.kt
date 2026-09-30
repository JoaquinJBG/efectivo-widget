package io.github.joaquinjbg.efectivo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.joaquinjbg.efectivo.data.Category
import io.github.joaquinjbg.efectivo.data.QuickButton
import io.github.joaquinjbg.efectivo.ui.common.AmountDisplay
import io.github.joaquinjbg.efectivo.ui.common.BigButton
import io.github.joaquinjbg.efectivo.ui.common.CategoryChips
import io.github.joaquinjbg.efectivo.ui.common.Keypad
import io.github.joaquinjbg.efectivo.ui.common.rememberAmountInput
import io.github.joaquinjbg.efectivo.util.Money

/** Create or edit one widget button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickButtonEditorSheet(
    button: QuickButton?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (QuickButton) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var label by remember { mutableStateOf(button?.label.orEmpty()) }
    var emoji by remember { mutableStateOf(button?.emoji ?: "🧾") }
    var categoryId by remember {
        mutableStateOf(button?.categoryId ?: categories.firstOrNull()?.id)
    }
    var askAmount by remember { mutableStateOf(button?.fixedAmountCents == null) }
    var compact by remember { mutableStateOf(button?.compact ?: false) }
    val input = rememberAmountInput(
        initial = button?.fixedAmountCents?.let { Money.formatNumber(it) }.orEmpty()
    )

    if (categoryId == null && categories.isNotEmpty()) {
        categoryId = categories.first().id
    }

    val cents = input.cents
    val canSave = label.isNotBlank() &&
        emoji.isNotBlank() &&
        (!compact || categoryId != null) &&
        (askAmount || (cents != null && cents > 0L))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (button == null) "Nuevo botón" else "Editar botón",
                style = MaterialTheme.typography.titleLarge,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { if (it.length <= 4) emoji = it },
                    label = { Text("Emoji") },
                    singleLine = true,
                    modifier = Modifier.width(110.dp),
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Etiqueta") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (compact) {
                // Las monedas comparten categoría; si cada una tuviera la suya, el
                // resumen del mes se llenaría de "1 €", "2 €"...
                Text("Categoría", style = MaterialTheme.typography.bodyMedium)
                CategoryChips(
                    categories = categories,
                    selectedId = categoryId,
                    onSelect = { categoryId = it },
                )
            } else {
                Text(
                    "La etiqueta es la categoría: los gastos de este botón se " +
                        "apuntarán como \"" + label.trim().ifBlank { "…" } + "\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                Modifier.fillMaxWidth().height(56.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Preguntar importe", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = askAmount, onCheckedChange = { askAmount = it })
            }

            Row(
                Modifier.fillMaxWidth().height(56.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Botón pequeño", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Fila compacta, solo el importe (monedas y billetes)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = compact, onCheckedChange = { compact = it })
            }

            if (!askAmount) {
                AmountDisplay(input)
                Keypad(input)
            }

            BigButton(
                text = "Guardar",
                enabled = canSave,
                onClick = {
                    // Para un botón normal el repositorio crea o renombra su categoría,
                    // así que este valor solo cuenta para las monedas.
                    val category = categoryId ?: 0L
                    onSave(
                        QuickButton(
                            id = button?.id ?: 0L,
                            label = label.trim(),
                            emoji = emoji.trim(),
                            categoryId = category,
                            fixedAmountCents = if (askAmount) null else cents,
                            sortOrder = button?.sortOrder ?: 0,
                            enabled = button?.enabled ?: true,
                            compact = compact,
                        )
                    )
                },
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}
