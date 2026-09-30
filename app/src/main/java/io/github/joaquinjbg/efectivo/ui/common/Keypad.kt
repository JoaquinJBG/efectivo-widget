package io.github.joaquinjbg.efectivo.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Teclado numérico propio: 0-9, coma y borrar.
 * A propósito no es el del sistema, para que no haya que esperar a que aparezca.
 */
@Composable
fun Keypad(
    input: AmountInput,
    modifier: Modifier = Modifier,
    onKey: () -> Unit = {},
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(",", "0", "<"),
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { key ->
                    KeypadKey(
                        key = key,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when (key) {
                                "," -> input.comma()
                                "<" -> input.backspace()
                                else -> input.digit(key.toInt())
                            }
                            onKey()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(key: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (key == "<") "Borrar" else key },
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().height(64.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (key == "<") {
                Icon(
                    Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Text(
                    key,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(0.dp),
                )
            }
        }
    }
}
