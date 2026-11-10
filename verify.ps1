param([switch]$DeviceTests)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $tasks = @('assembleDebug', 'lintDebug')
    if ($DeviceTests) { $tasks += 'assembleDebugAndroidTest' }
    & .\gradlew.bat @tasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Gradle verification failed' }
    if ($DeviceTests) {
        $adb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe' } else { 'adb' }
        $devices = & $adb devices
        $deviceLine = $devices | Select-String '^emulator-\d+\s+device$' | Select-Object -First 1
        if (-not $deviceLine) { throw 'Start a dedicated Android emulator first' }
        $serial = ($deviceLine.Line -split '\s+')[0]
        Write-Host 'Clearing demo app data on the selected emulator for regression tests.'
        & $adb -s $serial shell pm clear com.example.lab7_backgroundmedia | Out-Null
        & $adb -s $serial install -r app\build\outputs\apk\debug\app-debug.apk
        if ($LASTEXITCODE -ne 0) { throw 'App installation failed' }
        & $adb -s $serial install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
        if ($LASTEXITCODE -ne 0) { throw 'Test installation failed' }
        $result = & $adb -s $serial shell am instrument -w com.example.lab7_backgroundmedia.test/androidx.test.runner.AndroidJUnitRunner
        $result | Write-Output
        if (($result -join "`n") -notmatch 'OK \(1 test\)') { throw 'Device regression failed' }
    }
} finally { Pop-Location }
