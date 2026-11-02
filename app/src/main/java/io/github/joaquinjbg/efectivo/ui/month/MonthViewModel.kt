package io.github.joaquinjbg.efectivo.ui.month

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.joaquinjbg.efectivo.data.CategoryShare
import io.github.joaquinjbg.efectivo.data.ExpenseRepository
import io.github.joaquinjbg.efectivo.data.ExpenseWithCategory
import io.github.joaquinjbg.efectivo.data.MonthlySummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class MonthViewModel(private val repository: ExpenseRepository) : ViewModel() {

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    val summary: StateFlow<MonthlySummary> = _month
        .flatMapLatest { repository.observeMonthlySummary(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            MonthlySummary(YearMonth.now(), 0L, emptyList()),
        )

    private val _selected = MutableStateFlow<CategoryShare?>(null)

    /** La categoría cuyos gastos se están viendo, o null con la hoja cerrada. */
    val selected: StateFlow<CategoryShare?> = _selected.asStateFlow()

    /** Gastos de [selected] en el mes visible; null mientras carga o con la hoja cerrada. */
    val selectedExpenses: StateFlow<List<ExpenseWithCategory>?> =
        combine(_selected, _month) { category, month -> category to month }
            .flatMapLatest { (category, month) ->
                if (category == null) flowOf(null)
                else repository.observeCategoryExpenses(category.categoryId, month)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun select(category: CategoryShare) {
        _selected.value = category
    }

    fun clearSelection() {
        _selected.value = null
    }

    fun previousMonth() {
        _selected.value = null
        _month.value = _month.value.minusMonths(1)
    }

    fun nextMonth() {
        _selected.value = null
        _month.value = _month.value.plusMonths(1)
    }
}
