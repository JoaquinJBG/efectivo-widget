package io.github.joaquinjbg.efectivo.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.joaquinjbg.efectivo.ui.history.HistoryScreen
import io.github.joaquinjbg.efectivo.ui.home.HomeScreen
import io.github.joaquinjbg.efectivo.ui.month.MonthScreen
import io.github.joaquinjbg.efectivo.ui.settings.WidgetButtonsScreen
import kotlinx.coroutines.launch

private enum class Destination(val label: String, val icon: ImageVector) {
    HOME("Inicio", Icons.Filled.Home),
    HISTORY("Historial", Icons.AutoMirrored.Filled.List),
    MONTH("Mes", Icons.Filled.CalendarMonth),
    SETTINGS("Botones", Icons.Filled.Settings),
}

/**
 * Las cuatro pantallas van en un pager, así que se cambia deslizando además de con la
 * barra de abajo. Con el NavHost anterior cada pestaña era un destino suelto y no había
 * forma de pasar de una a otra con el dedo.
 */
@Composable
fun EfectivoRoot() {
    val destinations = Destination.entries
    val pagerState = rememberPagerState(pageCount = { destinations.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) { page ->
            when (destinations[page]) {
                Destination.HOME -> HomeScreen()
                Destination.HISTORY -> HistoryScreen()
                Destination.MONTH -> MonthScreen()
                Destination.SETTINGS -> WidgetButtonsScreen()
            }
        }
    }
}
