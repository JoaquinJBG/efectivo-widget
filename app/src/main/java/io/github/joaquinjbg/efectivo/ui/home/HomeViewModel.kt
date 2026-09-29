package io.github.joaquinjbg.efectivo.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.joaquinjbg.efectivo.data.Category
import io.github.joaquinjbg.efectivo.data.ExpenseRepository
import io.github.joaquinjbg.efectivo.data.ExpenseSource
import io.github.joaquinjbg.efectivo.data.ExpenseWithCategory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeUiState(
    val balanceCents: Long = 0,
    val todayTotalCents: Long = 0,
    val todayExpenses: List<ExpenseWithCategory> = emptyList(),
    val categories: List<Category> = emptyList(),
)

class HomeViewModel(private val repository: ExpenseRepository) : ViewModel() {

    private val today = LocalDate.now()

    val state: StateFlow<HomeUiState> = combine(
        repository.observeBalance(),
        repository.observeTotalOn(today),
        repository.observeExpensesOn(today),
        repository.observeCategories(),
    ) { balance, todayTotal, todayExpenses, categories ->
        HomeUiState(balance, todayTotal, todayExpenses, categories)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun addExpense(amountCents: Long, categoryId: Long, note: String?) {
        viewModelScope.launch {
            repository.addExpense(amountCents, categoryId, note, ExpenseSource.APP)
        }
    }

    fun addCash(amountCents: Long, note: String?) {
        viewModelScope.launch { repository.addCash(amountCents, note) }
    }
}
