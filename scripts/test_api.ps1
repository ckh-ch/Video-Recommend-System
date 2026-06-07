param([string]$BaseUrl = "http://localhost:8080")

Write-Host "Video Recommend System - API Test" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host ""

$userId = 85500
$passed = 0
$total = 0

function Test-Result {
    param([string]$Name, [ScriptBlock]$Block)
    $script:total++
    Write-Host ("[$script:total/6] $Name") -NoNewline
    try {
        $result = & $Block
        Write-Host "  PASS" -ForegroundColor Green
        $script:passed++
        if ($result) { Write-Host "   $result" -ForegroundColor Gray }
    } catch {
        Write-Host "  FAIL" -ForegroundColor Red
        Write-Host "   Error: $_" -ForegroundColor Red
    }
}

Test-Result -Name "Hot Recommend" -Block {
    $r = Invoke-RestMethod -Uri "$BaseUrl/api/recommend/hot?limit=3" -TimeoutSec 10
    if ($r.Count -eq 0) { throw "empty result" }
    "returned $($r.Count) videos"
}

Test-Result -Name "Personalized Recommend" -Block {
    $r = Invoke-RestMethod -Uri "$BaseUrl/api/recommend/personalized/$userId?limit=5" -TimeoutSec 10
    if ($r.Count -eq 0) { throw "empty result" }
    "returned $($r.Count) videos"
}

Test-Result -Name "Category Recommend" -Block {
    $r = Invoke-RestMethod -Uri "$BaseUrl/api/recommend/category/food?limit=3" -TimeoutSec 10
    if ($r.Count -eq 0) { throw "empty result" }
    "returned $($r.Count) food videos"
}

Test-Result -Name "User Profile" -Block {
    $r = Invoke-RestMethod -Uri "$BaseUrl/api/users/$userId/profile" -TimeoutSec 10
    if ($r.userId -ne $userId) { throw "userId mismatch" }
    "activeLevel=$($r.activeLevel), totalWatch=$($r.totalWatchCount)"
}

Test-Result -Name "Video Hot List" -Block {
    $r = Invoke-RestMethod -Uri "$BaseUrl/api/videos/hot?limit=3" -TimeoutSec 10
    if ($r.Count -eq 0) { throw "empty result" }
    "returned $($r.Count) videos"
}

Test-Result -Name "Behavior Report + Kafka + Redis" -Block {
    $body = @{userId=$userId; videoId=834124; videoCategory="food"; likeType=1; relayType=0; viewingTime=720.0} | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$BaseUrl/api/behavior" -Method Post -Body $body -ContentType "application/json" -TimeoutSec 10
    if ($r -ne "ok") { throw "behavior post failed: $r" }
    "posted, then check Redis on VM for profile:$userId:stats updates"
}

Write-Host ""
Write-Host "Results: $passed/$total passed" -ForegroundColor $(if ($passed -eq $total) { "Green" } else { "Red" })
