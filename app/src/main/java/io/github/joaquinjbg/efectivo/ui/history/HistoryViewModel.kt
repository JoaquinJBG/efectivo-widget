package io.github.joaquinjbg.efectivo.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.joaquinjbg.efectivo.data.Category
import io.github.joaquinjbg.efectivo.data.DaySection
import io.github.joaquinjbg.efectivo.data.ExpenseRepository
import io.github.joaquinjbg.efectivo.data.LedgerEntry
import io.github.joaquinjbg.efectivo.data.MovementType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** null = ver gastos e ingresos mezclados. */
typealias HistoryFilter = MovementType?

data class HistoryUiState(
    val days: List<DaySection> = emptyList(),
    val categories: List<Category> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val repository: ExpenseRepository) : ViewModel() {

    private val _filter = MutableStateFlow<HistoryFilter>(null)
    val filter: StateFlow<HistoryFilter> = _filter

    val state: StateFlow<HistoryUiState> = _filter
        .flatMapLatest { active ->
            combine(
                repository.observeLedgerByDay(active),
                repository.observeCategories(),
            ) { days, categories -> HistoryUiState(days, categories) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setFilter(value: HistoryFilter) {
        _filter.value = value
    }

    fun updateExpense(id: Long, amountCents: Long, categoryId: Long, note: String?) {
        viewModelScope.launch { repository.updateExpense(id, amountCents, categoryId, note) }
    }

    fun updateCash(id: Long, amountCents: Long, note: String?) {
        viewModelScope.launch { repository.updateCashMovement(id, amountCents, note) }
    }

    fun delete(entry: LedgerEntry) {
        viewModelScope.launch {
            when (entry.type) {
                MovementType.GASTO -> repository.deleteExpense(entry.id)
                MovementType.INGRESO -> repository.deleteCashMovement(entry.id)
            }
        }
    }
}
