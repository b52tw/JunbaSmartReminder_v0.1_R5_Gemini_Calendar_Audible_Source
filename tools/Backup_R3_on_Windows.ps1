# Junba R3 -> R4: non-destructive personal-data export for an installed *debug* APK.
# Requires: USB debugging enabled, Android platform-tools adb on PATH, user-authorized phone.
# Never reads/stores your Gemini API Key. Never removes the app or modifies device data.
$ErrorActionPreference = 'Stop'
try {
    if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
        throw '找不到 adb。請先安裝 Android platform-tools，並將 adb 加入 PATH。'
    }
    Write-Host '請先以 USB 連線 vivo，開啟 USB 偵錯並允許這部電腦。'
    $devices = (& adb devices) -join "`n"
    Write-Host $devices
    if ($LASTEXITCODE -ne 0 -or $devices -notmatch '\sdevice\s*($|\r|\n)') {
        throw 'ADB 尚未取得手機授權；請核對 USB 線、手機授權及 adb devices。'
    }
    $raw = (& adb shell run-as tw.junba.reminder cat shared_prefs/junba.xml 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0 -or $raw -notmatch '<map') {
        throw ('無法取得 R3 資料。若顯示 not debuggable 或 package unknown，請勿解除安裝；請先求助。' + "`n" + $raw)
    }
    [xml]$xml = $raw
    $node = $xml.SelectSingleNode('/map/string[@name="notes"]')
    if (-not $node) { throw '找不到記事清單；請確認已安裝舊版 Junba App。' }
    $notes = ConvertFrom-Json -InputObject $node.InnerText
    $links = @()
    $linkNode = $xml.SelectSingleNode('/map/string[@name="native_links"]')
    if ($linkNode -and $linkNode.InnerText) { $links = @(ConvertFrom-Json -InputObject $linkNode.InnerText) }
    $backup = [ordered]@{
        version = 4
        notes = @($notes)
        native_links = @($links)
    }
    $json = ConvertTo-Json -InputObject $backup -Depth 50
    $path = Join-Path (Get-Location) 'Junba_R3_to_R4_backup.json'
    [System.IO.File]::WriteAllText($path,$json,(New-Object System.Text.UTF8Encoding($false)))
    Write-Host '完成！已備份至：' $path
    Write-Host '請先保留檔案；安裝 R4 後，至「設定 → 匯入備份」選取。'
} catch {
    Write-Host ('備份未完成：' + $_.Exception.Message) -ForegroundColor Red
    Write-Host '為避免資料遺失，請勿解除安裝舊 App。'
    exit 1
}
