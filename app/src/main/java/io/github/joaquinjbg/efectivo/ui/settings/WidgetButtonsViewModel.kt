package io.github.joaquinjbg.efectivo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.joaquinjbg.efectivo.data.Category
import io.github.joaquinjbg.efectivo.data.ExpenseRepository
import io.github.joaquinjbg.efectivo.data.QuickButton
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WidgetButtonsUiState(
    val buttons: List<QuickButton> = emptyList(),
    val categories: List<Category> = emptyList(),
)

class WidgetButtonsViewModel(private val repository: ExpenseRepository) : ViewModel() {

    val state: StateFlow<WidgetButtonsUiState> = combine(
        repository.observeQuickButtons(),
        repository.observeCategories(),
    ) { buttons, categories ->
        WidgetButtonsUiState(buttons, categories)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WidgetButtonsUiState())

    fun add(button: QuickButton) {
        viewModelScope.launch {
            val sortOrder = state.value.buttons.size
            repository.addQuickButton(button.copy(sortOrder = sortOrder))
        }
    }

    fun update(button: QuickButton) {
        viewModelScope.launch { repository.updateQuickButton(button) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteQuickButton(id) }
    }

    fun setEnabled(button: QuickButton, enabled: Boolean) {
        viewModelScope.launch { repository.updateQuickButton(button.copy(enabled = enabled)) }
    }

    fun move(from: Int, to: Int) {
        val current = state.value.buttons
        if (from !in current.indices || to !in current.indices) return
        val reordered = current.toMutableList().apply { add(to, removeAt(from)) }
        viewModelScope.launch { repository.reorderQuickButtons(reordered) }
    }
}
