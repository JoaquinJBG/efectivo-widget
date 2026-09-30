package io.github.joaquinjbg.efectivo.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.joaquinjbg.efectivo.util.Money

/** Estado de edición del teclado numérico. Guarda el texto tal cual: "12,50". */
class AmountInput(initial: String = "") {

    var text by mutableStateOf(initial)
        private set

    val cents: Long?
        get() = Money.parse(text)

    val isEmpty: Boolean
        get() = text.isEmpty()

    /** Lo que se ve en pantalla; si no hay nada escrito, "0". */
    val display: String
        get() = if (text.isEmpty()) "0" else text

    fun digit(d: Int) {
        val commaIndex = text.indexOf(',')
        if (commaIndex >= 0) {
            if (text.length - commaIndex - 1 >= 2) return
        } else {
            val whole = text.ifEmpty { "0" }
            if (whole.length >= 6) return
            if (text == "0") {
                text = d.toString()
                return
            }
        }
        text += d.toString()
    }

    fun comma() {
        if (text.contains(',')) return
        text = if (text.isEmpty()) "0," else "$text,"
    }

    fun backspace() {
        if (text.isNotEmpty()) text = text.dropLast(1)
    }

    fun clear() {
        text = ""
    }
}

@Composable
fun rememberAmountInput(initial: String = ""): AmountInput {
    val saved = rememberSaveable { mutableStateOf(initial) }
    val input = remember { AmountInput(saved.value) }
    saved.value = input.text
    return input
}
