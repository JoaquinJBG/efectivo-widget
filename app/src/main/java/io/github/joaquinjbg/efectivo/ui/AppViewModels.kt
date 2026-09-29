package io.github.joaquinjbg.efectivo.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.joaquinjbg.efectivo.Graph
import io.github.joaquinjbg.efectivo.ui.history.HistoryViewModel
import io.github.joaquinjbg.efectivo.ui.home.HomeViewModel
import io.github.joaquinjbg.efectivo.ui.quick.QuickEntryViewModel

/** One factory for every screen; the repository is process-wide. */
val AppViewModelFactory = viewModelFactory {
    initializer { HomeViewModel(Graph.repository(this[APPLICATION_KEY]!!)) }
    initializer { HistoryViewModel(Graph.repository(this[APPLICATION_KEY]!!)) }
    initializer { QuickEntryViewModel(Graph.repository(this[APPLICATION_KEY]!!)) }
}
