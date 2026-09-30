package io.github.joaquinjbg.efectivo.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * El "2" viene de una prueba: se cambio el nombre para que el launcher viera un provider
 * nuevo, por si tenia cacheados los limites de redimensionado del anterior. No era eso,
 * pero el nombre se queda: volver al viejo obligaria a quitar y anadir el widget otra vez.
 */
class EfectivoWidgetReceiver2 : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EfectivoWidget
}
