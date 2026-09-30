package io.github.joaquinjbg.efectivo.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider

/**
 * ═══════════════════════════════════════════════════════════════════════
 *  ESTILO DEL WIDGET — este es el fichero para retocar el aspecto.
 * ═══════════════════════════════════════════════════════════════════════
 *
 *  Toca solo los números y los colores de aquí; no hay lógica en este
 *  fichero, así que no se puede romper nada tocándolo.
 *
 *  Las medidas van en **dp** (densidad independiente, ~1/160 de pulgada) y
 *  los tamaños de letra en **sp**. Subir un número = más grande.
 *
 *  Después de editar:
 *      gradlew assembleDebug
 *      adb install -r app\build\outputs\apk\debug\app-debug.apk
 *
 *  El widget se repinta solo; no hace falta quitarlo ni volver a ponerlo
 *  (salvo que cambies el tamaño inicial, que está en
 *  res/xml/efectivo_widget_info.xml).
 */
object WidgetStyle {

    // ─── COLORES ────────────────────────────────────────────────────────
    // Formato 0xAARRGGBB: AA = opacidad (FF = opaco), luego rojo, verde, azul.

    /** Fondo de todo el widget. */
    val background = ColorProvider(Color(0xFF0E0E11))

    /** Fondo de cada tarjeta y de cada botón. */
    val card = ColorProvider(Color(0xFF26262E))

    /** Verde de los importes de los botones. */
    val accent = ColorProvider(Color(0xFF35D07F))

    /** Texto principal. */
    val onDark = ColorProvider(Color(0xFFF2F2F5))

    /** Texto secundario: "Efectivo", "Hoy", "Semana", "Mes". */
    val muted = ColorProvider(Color(0xFF9A9AA6))

    // ─── ESPACIADO GENERAL ──────────────────────────────────────────────

    /** Margen entre el borde del widget y su contenido. */
    const val OUTER_PADDING = 100f

    /** Separación entre botones, y entre bandas (cabecera, totales, etc.). */
    const val GAP = 12f

    /** Redondeo de la esquina del widget entero. */
    const val WIDGET_CORNER = 20f

    /** Hueco entre el borde de una tarjeta y su texto (cabecera, totales, historial). */
    const val CARD_PADDING_H = 12f
    const val CARD_PADDING_V = 8f

    // ─── CABECERA (Efectivo / Hoy) ──────────────────────────────────────

    const val HEADER_HEIGHT = 56f
    const val HEADER_LABEL_SP = 11f
    const val HEADER_AMOUNT_SP = 20f
    const val HEADER_CORNER = 14f

    /** Versión de una sola línea, cuando el widget es muy bajo. */
    const val HEADER_COMPACT_HEIGHT = 26f

    /** Por debajo de esta altura total se usa la cabecera de una línea. */
    const val COMPACT_BELOW = 260f

    // ─── FILA DE TOTALES (Semana / Mes) ─────────────────────────────────

    const val TOTALS_HEIGHT = 46f
    const val TOTALS_LABEL_SP = 11f
    const val TOTALS_AMOUNT_SP = 16f

    // ─── BOTONES DE CATEGORÍA (Café, Desayuno…) ─────────────────────────

    /** Cuántos caben por fila como mucho. */
    const val CATEGORY_COLUMNS = 4

    /** Ancho mínimo de uno; si no caben, se ponen menos columnas. */
    const val CATEGORY_MIN_WIDTH = 64f

    /** Alto del botón: sube el máximo si los quieres más altos. */
    const val CATEGORY_HEIGHT_MIN = 54f
    const val CATEGORY_HEIGHT_MAX = 66f

    const val CATEGORY_LABEL_SP = 12f
    const val CATEGORY_AMOUNT_SP = 18f
    const val CATEGORY_CORNER = 16f

    /** Hueco a los lados del texto dentro de la baldosa. */
    const val CATEGORY_PADDING_H = 2f

    /** Con botones más pequeños que esto, la letra baja un escalón. */
    const val CATEGORY_SMALL_BELOW = 60f
    const val CATEGORY_LABEL_SMALL_SP = 10f
    const val CATEGORY_AMOUNT_SMALL_SP = 15f

    // ─── BOTONES DE MONEDA (1 €, 2 €, 5 €…) ─────────────────────────────

    const val COIN_HEIGHT = 42f
    const val COIN_MIN_WIDTH = 40f
    const val COIN_TEXT_SP = 15f
    const val COIN_CORNER = 12f

    // ─── FILA DE ACCIONES (Otro… / Deshacer) ────────────────────────────

    const val ACTION_HEIGHT = 48f
    const val ACTION_TEXT_SP = 15f
    const val ACTION_CORNER = 14f

    // ─── HISTORIAL DEL DÍA ──────────────────────────────────────────────

    const val HISTORY_TEXT_SP = 13f
    const val HISTORY_ROW_PADDING = 5f
    const val HISTORY_CORNER = 14f

    /** Cuántos gastos como mucho; el resto se queda fuera. */
    const val HISTORY_MAX_ROWS = 20

    // ─── AVISO DE LA CABECERA ("Café 2 €", "Deshecho: …") ───────────────

    const val MESSAGE_TEXT_SP = 14f

    /** Milisegundos que se queda el aviso antes de volver al saldo. */
    const val MESSAGE_VISIBLE_MILLIS = 2_500L
}
