param([string]$RedisHost = "192.168.126.130")

Write-Host "Redis Real-time Profile Check" -ForegroundColor Cyan
Write-Host "==============================" -ForegroundColor Cyan
$uid = 85500

# Send a behavior to trigger StreamingApp
Write-Host "Sending test behavior..." -NoNewline
$body = @{userId=$uid; videoId=834124; videoCategory="food"; likeType=1; relayType=0; viewingTime=720.0} | ConvertTo-Json
$r = Invoke-RestMethod -Uri "http://localhost:8080/api/behavior" -Method Post -Body $body -ContentType "application/json" -TimeoutSec 10
if ($r -eq "ok") { Write-Host " ok" -ForegroundColor Green } else { Write-Host " failed: $r" -ForegroundColor Red }

Start-Sleep -Seconds 2

# Check Redis stats via python paramiko
$stats = & python -c @"
import paramiko
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('$RedisHost', 22, 'root', '123456')
stdin, stdout, stderr = c.exec_command("redis-cli hgetall profile:$uid:stats")
print(stdout.read().decode())
stdin2, stdout2, stderr2 = c.exec_command("redis-cli hgetall profile:$uid:cats")
print('---cats---')
print(stdout2.read().decode())
c.close()
"@ 2>&1 | Out-String

Write-Host ""
Write-Host "Profile Stats:" -ForegroundColor Yellow
$stats -split "`n" | ForEach-Object {
    if ($_.Trim().Length -gt 0 -and $_ -ne "---cats---") {
        Write-Host "  $_" -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "Tips:" -ForegroundColor Cyan
Write-Host "  Run this script multiple times to see counters increment" -ForegroundColor Gray
Write-Host "  redis-cli -h $RedisHost hgetall profile:$uid:cats" -ForegroundColor Gray
Write-Host "  redis-cli -h $RedisHost hgetall profile:$uid:stats" -ForegroundColor Gray
