package io.github.joaquinjbg.efectivo.ui.quick

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.joaquinjbg.efectivo.Graph
import io.github.joaquinjbg.efectivo.data.Category
import io.github.joaquinjbg.efectivo.data.ExpenseRepository
import io.github.joaquinjbg.efectivo.data.ExpenseSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class QuickEntryViewModel(private val repository: ExpenseRepository) : ViewModel() {

    val categories: StateFlow<List<Category>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Writes on the process-wide scope, not [viewModelScope]: the activity finishes
     * immediately after confirming and would otherwise cancel the write.
     */
    fun save(amountCents: Long, categoryId: Long, note: String?) {
        Graph.appScope.launch {
            repository.addExpense(
                amountCents = amountCents,
                categoryId = categoryId,
                note = note,
                source = ExpenseSource.WIDGET,
            )
        }
    }
}
