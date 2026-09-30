# Instala el APK en el móvil.
#
# Usa la ruta completa de adb en vez de depender del PATH (la terminal de VS Code
# suele arrastrar un PATH antiguo), y elige el móvil ignorando los emuladores.

$ErrorActionPreference = "Stop"

$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:USERPROFILE\Android\Sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"
$apk = "app\build\outputs\apk\debug\app-debug.apk"

if (-not (Test-Path $adb)) {
    Write-Host "No encuentro adb en: $adb" -ForegroundColor Red
    Write-Host "Revisa que el SDK de Android siga instalado ahi." -ForegroundColor Yellow
    exit 1
}

if (-not (Test-Path $apk)) {
    Write-Host "No hay APK compilado todavia." -ForegroundColor Red
    Write-Host "Ejecuta primero: .\gradlew assembleDebug" -ForegroundColor Yellow
    exit 1
}

# Dispositivos en estado "device" (listos). Se descartan los emuladores.
$listado = & $adb devices
$conectados = $listado |
    Select-Object -Skip 1 |
    Where-Object { $_ -match "\sdevice$" } |
    ForEach-Object { ($_ -split "\s+")[0] } |
    Where-Object { $_ -and $_ -notlike "emulator-*" }

if (-not $conectados) {
    Write-Host "No hay ningun movil conectado." -ForegroundColor Red
    Write-Host ""
    Write-Host "Por Wi-Fi (el puerto cambia al reiniciar el movil):" -ForegroundColor Yellow
    Write-Host "  1. En el movil: Opciones de desarrollador -> Depuracion inalambrica"
    Write-Host "  2. Aqui:        $adb mdns services"
    Write-Host "  3. Luego:       $adb connect <ip>:<puerto>"
    Write-Host ""
    Write-Host "Por cable: conectalo y acepta el aviso de depuracion USB." -ForegroundColor Yellow
    exit 1
}

$movil = $conectados | Select-Object -First 1
if ($conectados.Count -gt 1) {
    Write-Host "Hay varios dispositivos; se usa $movil" -ForegroundColor Yellow
}

Write-Host "Instalando en $movil ..." -ForegroundColor Cyan
& $adb -s $movil install -r $apk

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "Listo. El widget se actualiza solo en unos segundos." -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "La instalacion ha fallado (codigo $LASTEXITCODE)." -ForegroundColor Red
    exit $LASTEXITCODE
}
