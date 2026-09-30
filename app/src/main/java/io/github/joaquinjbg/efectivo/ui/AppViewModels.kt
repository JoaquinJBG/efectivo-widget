package io.github.joaquinjbg.efectivo.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.joaquinjbg.efectivo.Graph
import io.github.joaquinjbg.efectivo.ui.history.HistoryViewModel
import io.github.joaquinjbg.efectivo.ui.home.HomeViewModel
import io.github.joaquinjbg.efectivo.ui.month.MonthViewModel
import io.github.joaquinjbg.efectivo.ui.quick.QuickEntryViewModel
import io.github.joaquinjbg.efectivo.ui.settings.WidgetButtonsViewModel

/** Una sola factoría para todas las pantallas; el repositorio es único por proceso. */
val AppViewModelFactory = viewModelFactory {
    initializer { HomeViewModel(Graph.repository(this[APPLICATION_KEY]!!)) }
    initializer { HistoryViewModel(Graph.repository(this[APPLICATION_KEY]!!)) }
    initializer { MonthViewModel(Graph.repository(this[APPLICATION_KEY]!!)) }
    initializer { QuickEntryViewModel(Graph.repository(this[APPLICATION_KEY]!!)) }
    initializer { WidgetButtonsViewModel(Graph.repository(this[APPLICATION_KEY]!!)) }
}
