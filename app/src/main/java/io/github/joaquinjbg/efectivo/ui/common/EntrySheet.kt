package io.github.joaquinjbg.efectivo.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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

/**
 * Bottom sheet used by Home for both "new expense" and "add cash".
 * The amount always comes from the custom [Keypad], never from the system IME.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntrySheet(
    title: String,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (cents: Long, categoryId: Long?, note: String?) -> Unit,
    confirmLabel: String = "Guardar",
    initialCategoryId: Long? = null,
    allowNegative: Boolean = false,
    /**
     * Categorías con importe ya definido. Al elegir una de esas sin haber tecleado nada,
     * el gasto se apunta y la hoja se cierra: no hay nada más que decidir.
     */
    fixedAmounts: Map<Long, Long> = emptyMap(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val input = rememberAmountInput()
    var selectedCategory by remember { mutableStateOf(initialCategoryId) }
    var note by remember { mutableStateOf("") }
    var negative by remember { mutableStateOf(false) }

    if (selectedCategory == null && categories.isNotEmpty()) {
        selectedCategory = categories.first().id
    }

    val cents = input.cents
    val canConfirm = cents != null && cents > 0L &&
        (categories.isEmpty() || selectedCategory != null)

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
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            AmountDisplay(input)

            if (allowNegative) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !negative,
                        onClick = { negative = false },
                        label = { Text("Añadir") },
                        modifier = Modifier.height(48.dp),
                    )
                    FilterChip(
                        selected = negative,
                        onClick = { negative = true },
                        label = { Text("Restar") },
                        modifier = Modifier.height(48.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            if (categories.isNotEmpty()) {
                CategoryChips(
                    categories = categories,
                    selectedId = selectedCategory,
                    onSelect = { categoryId ->
                        selectedCategory = categoryId
                        val fixed = fixedAmounts[categoryId]
                        // Si ya has tecleado un importe, manda el tuyo.
                        if (fixed != null && input.isEmpty) {
                            onConfirm(fixed, categoryId, note)
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Nota (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            Keypad(input)
            Spacer(Modifier.height(16.dp))
            BigButton(
                text = confirmLabel,
                enabled = canConfirm,
                onClick = {
                    val value = cents ?: return@BigButton
                    onConfirm(if (negative) -value else value, selectedCategory, note)
                },
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}
