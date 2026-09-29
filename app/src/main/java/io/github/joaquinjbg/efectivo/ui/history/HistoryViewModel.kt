package io.github.joaquinjbg.efectivo.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.joaquinjbg.efectivo.data.Category
import io.github.joaquinjbg.efectivo.data.DaySection
import io.github.joaquinjbg.efectivo.data.Expense
import io.github.joaquinjbg.efectivo.data.ExpenseRepository
import io.github.joaquinjbg.efectivo.data.ExpenseWithCategory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val days: List<DaySection> = emptyList(),
    val categories: List<Category> = emptyList(),
)

class HistoryViewModel(private val repository: ExpenseRepository) : ViewModel() {

    val state: StateFlow<HistoryUiState> = combine(
        repository.observeExpensesByDay(),
        repository.observeCategories(),
    ) { days, categories ->
        HistoryUiState(days, categories)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun update(original: ExpenseWithCategory, amountCents: Long, categoryId: Long, note: String?) {
        viewModelScope.launch {
            repository.updateExpense(
                Expense(
                    id = original.id,
                    amountCents = amountCents,
                    categoryId = categoryId,
                    timestamp = original.timestamp,
                    note = note?.takeIf { it.isNotBlank() },
                    source = original.source,
                )
            )
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteExpense(id) }
    }
}
