# Efectivo

App Android personal para registrar gastos en efectivo desde un widget del escritorio.
Sin cuentas, sin red, sin nube: todo vive en una base de datos local del móvil.

El objetivo es la velocidad: apuntar un gasto desde el widget cuesta **un toque**
(dos si el importe es variable).

## Stack

| Pieza | Versión |
|---|---|
| AGP | 9.4.1 (Kotlin integrado 2.4.20) |
| Gradle | 9.6.0 |
| JDK | 17 |
| compileSdk / targetSdk | 37 |
| minSdk | 26 |
| Compose BOM | 2026.09.00 |
| Room | 2.8.5 |
| Glance (widget) | 1.2.0 |
| KSP | 2.3.12 |

## Estructura

```
app/src/main/java/io/github/joaquinjbg/efectivo/
  data/        Room: entidades, DAOs, repositorio. El saldo se calcula, nunca se guarda.
  ui/
    home/      Saldo, total de hoy y gastos del día
    history/   Gastos por día, con edición y borrado
    month/     Resumen mensual por categoría
    settings/  Botones del widget: añadir, editar, borrar, reordenar, activar
    quick/     Diálogo transparente que abre el widget
    common/    Teclado numérico propio, chips de categoría, hoja de entrada
  widget/      Widget Glance: rejilla de botones, deshacer, historial del día
  util/        Formato de euros, rangos de fechas, vibración, medición de tiempos
```

## Modelo de datos

- **Expense**: importe en céntimos, categoría, fecha, nota opcional, origen (`WIDGET` | `APP`).
- **CashMovement**: entradas de efectivo (positivo = retirada del cajero, negativo = corrección).
- **Category**: nombre, emoji, orden.
- **QuickButton**: botón del widget — etiqueta, emoji, categoría, importe fijo o "preguntar".

**Saldo = suma(CashMovement) − suma(Expense)**, calculado en el repositorio.

## Compilar e instalar

```bash
# tests + APK
gradlew testDebugUnitTest assembleDebug

# instalar por cable
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### Instalar por Wi-Fi

En el móvil: `Opciones de desarrollador` → `Depuración inalámbrica` → *Vincular dispositivo
con código*. Desde el PC:

```bash
adb pair <ip>:<puerto-de-vinculacion> <codigo>
adb connect <ip>:<puerto-de-conexion>
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

El puerto de conexión no es el de vinculación; `adb mdns services` lo encuentra.

## Medir el widget

El widget lleva instrumentación de tiempos, apagada por defecto:

```bash
adb shell setprop log.tag.EfectivoPerf DEBUG
adb logcat -v time | grep EfectivoPerf
```

Registra el ciclo toque → escritura → repintado, y el tamaño y reparto que
resuelve el widget en cada composición.

## Notas del widget (aprendidas a golpes)

- **Glance solo trae layouts de contenedor para 10 hijos como máximo** y descarta el resto
  en silencio, sin error. Si desaparecen elementos del final, es esto. Por eso la rejilla
  de botones vive en su propio contenedor anidado.
- **`SizeMode.Exact`, no `Responsive`.** Responsive obliga al launcher a elegir entre
  tallas declaradas, y el de MagicOS escogía una mucho menor que el hueco real.
- **RemoteViews no puede medirse a sí mismo**, así que el reparto vertical usa estimaciones
  en dp de lo que ocupa cada bloque. Los números están en `EfectivoWidget.kt`.
- **Vibrar desde un callback de widget** requiere `VibrationAttributes.USAGE_TOUCH`: el
  proceso está en segundo plano y Android descarta las vibraciones sin un *usage* válido.

## Fuera de alcance

Sincronización, copias de seguridad, varias monedas, presupuestos, login, tablets,
Play Store.
