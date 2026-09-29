package io.github.joaquinjbg.efectivo.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.joaquinjbg.efectivo.data.ExpenseWithCategory
import io.github.joaquinjbg.efectivo.ui.AppViewModelFactory
import io.github.joaquinjbg.efectivo.ui.common.BigButton
import io.github.joaquinjbg.efectivo.ui.common.EntrySheet
import io.github.joaquinjbg.efectivo.ui.common.euro
import io.github.joaquinjbg.efectivo.util.Haptics
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class Sheet { NONE, EXPENSE, CASH }

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val viewModel: HomeViewModel = viewModel(factory = AppViewModelFactory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sheet by remember { mutableStateOf(Sheet.NONE) }

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp))
        Text(
            "Efectivo disponible",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            euro(state.balanceCents),
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Hoy",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                euro(state.todayTotalCents),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("Gasto", onClick = { sheet = Sheet.EXPENSE }, modifier = Modifier.weight(1f))
            BigButton("Efectivo", onClick = { sheet = Sheet.CASH }, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (state.todayExpenses.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                "Todavía no hay gastos hoy",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.todayExpenses, key = { it.id }) { expense ->
                    ExpenseRow(expense)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }

    when (sheet) {
        Sheet.EXPENSE -> EntrySheet(
            title = "Nuevo gasto",
            categories = state.categories,
            onDismiss = { sheet = Sheet.NONE },
            onConfirm = { cents, categoryId, note ->
                viewModel.addExpense(cents, categoryId!!, note)
                Haptics.tick(context)
                sheet = Sheet.NONE
            },
        )

        Sheet.CASH -> EntrySheet(
            title = "Añadir efectivo",
            categories = emptyList(),
            confirmLabel = "Añadir",
            allowNegative = true,
            onDismiss = { sheet = Sheet.NONE },
            onConfirm = { cents, _, note ->
                viewModel.addCash(cents, note)
                Haptics.tick(context)
                sheet = Sheet.NONE
            },
        )

        Sheet.NONE -> Unit
    }
}

@Composable
private fun ExpenseRow(expense: ExpenseWithCategory) {
    Row(
        Modifier.fillMaxWidth().height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                (expense.categoryEmoji.ifBlank { "🧾" }) + "  " + (expense.categoryName.ifBlank { "Sin categoría" }),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            val time = Instant.ofEpochMilli(expense.timestamp)
                .atZone(ZoneId.systemDefault()).toLocalTime().format(timeFormat)
            Text(
                if (expense.note.isNullOrBlank()) time else "$time · ${expense.note}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            euro(expense.amountCents),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
