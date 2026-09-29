package io.github.joaquinjbg.efectivo.ui.quick

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.joaquinjbg.efectivo.ui.AppViewModelFactory
import io.github.joaquinjbg.efectivo.ui.common.AmountDisplay
import io.github.joaquinjbg.efectivo.ui.common.BigButton
import io.github.joaquinjbg.efectivo.ui.common.CategoryChips
import io.github.joaquinjbg.efectivo.ui.common.Keypad
import io.github.joaquinjbg.efectivo.ui.common.rememberAmountInput
import io.github.joaquinjbg.efectivo.ui.theme.EfectivoTheme
import io.github.joaquinjbg.efectivo.util.Haptics
import io.github.joaquinjbg.efectivo.util.Money

/**
 * Transparent dialog launched straight from the widget. Uses the custom [Keypad]
 * so there is no system-keyboard pop-up delay.
 */
class QuickEntryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preselected = intent.getLongExtra(EXTRA_CATEGORY_ID, -1L).takeIf { it > 0 }

        setContent {
            EfectivoTheme {
                QuickEntryDialog(
                    preselectedCategoryId = preselected,
                    onClose = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_CATEGORY_ID = "io.github.joaquinjbg.efectivo.extra.CATEGORY_ID"

        fun intent(context: Context, categoryId: Long?): Intent =
            Intent(context, QuickEntryActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                if (categoryId != null) putExtra(EXTRA_CATEGORY_ID, categoryId)
            }
    }
}

@Composable
private fun QuickEntryDialog(preselectedCategoryId: Long?, onClose: () -> Unit) {
    val viewModel: QuickEntryViewModel = viewModel(factory = AppViewModelFactory)
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val input = rememberAmountInput()
    var selected by remember { mutableStateOf(preselectedCategoryId) }
    var note by remember { mutableStateOf("") }

    if (selected == null && categories.isNotEmpty()) {
        selected = categories.first().id
    }

    val cents = input.cents
    val canConfirm = cents != null && cents > 0L && selected != null

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Nuevo gasto", style = MaterialTheme.typography.titleLarge)
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
                    text = if (cents != null && cents > 0L) {
                        "Guardar " + Money.format(cents)
                    } else {
                        "Guardar"
                    },
                    enabled = canConfirm,
                    onClick = {
                        val amount = cents ?: return@BigButton
                        val categoryId = selected ?: return@BigButton
                        viewModel.save(amount, categoryId, note)
                        Haptics.tick(context)
                        onClose()
                    },
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
