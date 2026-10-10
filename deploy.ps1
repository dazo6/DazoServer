$ErrorActionPreference = 'Stop'
$oldJar = 'D:/DazoServer/DazoServer-1.0-SNAPSHOT.jar'
$newJar = 'D:/DazoServer-Worksapce/target/DazoServer-1.0-SNAPSHOT.jar'
$backupJar = $oldJar + '.backup-' + (Get-Date -Format yyyyMMdd-HHmmss)
try {
 Copy-Item -LiteralPath $oldJar -Destination $backupJar
 $listeners = @(netstat -ano -p tcp | Select-String '^\s*TCP\s+0\.0\.0\.0:18080\s+\S+\s+LISTENING\s+(\d+)')
 if ($listeners.Count -gt 1) { throw 'Multiple listeners on 18080' }
 if ($listeners.Count -eq 1) {
 $serviceId = [int]$listeners[0].Matches[0].Groups[1].Value
 if ((Get-Process -Id $serviceId).ProcessName -ne 'java') { throw 'Listener is not Java' }
 Stop-Process -Id $serviceId -ErrorAction Stop
 Wait-Process -Id $serviceId -Timeout 30 -ErrorAction SilentlyContinue
 $remainingProcess = Get-Process -Id $serviceId -ErrorAction SilentlyContinue
 if ($remainingProcess -and !$remainingProcess.HasExited) { throw 'Old service is still running; aborting replacement' }
 }
 Copy-Item -LiteralPath $newJar -Destination $oldJar -Force
 Start-Process -FilePath $env:ComSpec -ArgumentList '/c','D:\DazoServer\run.bat' -WorkingDirectory D:/DazoServer -WindowStyle Hidden -RedirectStandardOutput D:/DazoServer/logs/deploy-xgplayer.stdout.log -RedirectStandardError D:/DazoServer/logs/deploy-xgplayer.stderr.log
 $auth = @{}
 foreach ($line in Get-Content D:/DazoServer/application.properties) {
  if ($line -match '^dashboard\.auth\.(user-id|password)\s*=(.*)$') { $auth[$Matches[1]] = $Matches[2].Trim() }
 }
 $loginBody = @{userId=$auth['user-id'];password=$auth['password']} | ConvertTo-Json
 $healthy = $false
 for ($attempt = 0; $attempt -lt 60; $attempt++) {
  Start-Sleep -Seconds 2
  try {
   $null = Invoke-WebRequest http://127.0.0.1:18080/api/auth/login -Method Post -ContentType application/json -Body $loginBody -SessionVariable checkSession -UseBasicParsing -TimeoutSec 3
   $page = Invoke-WebRequest http://127.0.0.1:18080/dashboard/ -WebSession $checkSession -UseBasicParsing -TimeoutSec 3
   $playerScript = Invoke-WebRequest http://127.0.0.1:18080/dashboard/pages/artist-gallery.js -WebSession $checkSession -UseBasicParsing -TimeoutSec 3
   $playerCss = Invoke-WebRequest http://127.0.0.1:18080/dashboard/vendor/xgplayer/index.min.css -WebSession $checkSession -UseBasicParsing -TimeoutSec 3
   if ($page.StatusCode -eq 200 -and $page.Content.Contains('/dashboard/vendor/xgplayer/index.min.js') -and $playerScript.Content.Contains('GalleryVideoPlayer') -and $playerCss.Headers['Content-Type'] -match 'css') { $healthy = $true; break }
  } catch { }
 }
 if (!$healthy) { throw "Startup verification failed; review logs and restore backup $backupJar through run.bat if needed" }
 if ((Get-FileHash -LiteralPath $oldJar).Hash -ne (Get-FileHash -LiteralPath $newJar).Hash) { throw 'Deployed artifact hash mismatch' }
 "Verified deployment through run.bat; backup: $backupJar" | Set-Content D:/DazoServer/logs/deploy-xgplayer.result.log
} catch {
 $_ | Out-String | Set-Content D:/DazoServer/logs/deploy-xgplayer.result.log
 exit 1
}
