# Abre o emulador Android usado nos testes do app.
# Uso: .\emulator.ps1            (abre com Quick Boot)
#      .\emulator.ps1 -Cold      (ignora o snapshot e faz boot do zero)
#      .\emulator.ps1 -Wipe      (apaga os dados do emulador)
param(
    [string]$Avd = "Pixel_5_API_31",
    [switch]$Cold,
    [switch]$Wipe
)

$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$emulator = Join-Path $sdk "emulator\emulator.exe"

$emuArgs = @("-avd", $Avd, "-netdelay", "none", "-netspeed", "full")
if ($Cold) { $emuArgs += "-no-snapshot-load" }
if ($Wipe) { $emuArgs += "-wipe-data" }

Start-Process -FilePath $emulator -ArgumentList $emuArgs
Write-Host "Emulador '$Avd' iniciando. Acompanhe com: adb devices"
