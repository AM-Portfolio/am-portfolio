# Measure X-Ray intelligence accuracy + latency on DEV.
# Usage: $env:AM_TEST_TOKEN = "<Bearer without prefix>"; .\measure-xray-dev.ps1
$ErrorActionPreference = "Stop"
$token = $env:AM_TEST_TOKEN
if (-not $token) { throw "Set AM_TEST_TOKEN to a valid am-dev-realm access token" }
$book = if ($env:AM_TEST_PORTFOLIO_ID) { $env:AM_TEST_PORTFOLIO_ID } else { "5dd51534-1dd3-4377-996f-36a21c2cb6e0" }
$url = "https://am-dev.asrax.in/portfolio/v1/analytics/portfolio/$book/intelligence"
$headers = @{ Authorization = "Bearer $token"; "Content-Type" = "application/json" }

function Invoke-Intel {
  $sw = [System.Diagnostics.Stopwatch]::StartNew()
  $resp = Invoke-RestMethod -Uri $url -Method Post -Headers $headers -Body "{}" -TimeoutSec 60
  $sw.Stop()
  return @{ ms = $sw.ElapsedMilliseconds; body = $resp }
}

function Weight-Of($slices, $name) {
  if (-not $slices) { return 0 }
  $hit = $slices | Where-Object { $_.name -and ($_.name.ToLower() -eq $name.ToLower()) } | Select-Object -First 1
  if ($hit) { return [double]$hit.weightPct }
  return 0
}

Write-Host "Cold call..."
$cold = Invoke-Intel
$times = @()
Write-Host "Warm calls x3..."
1..3 | ForEach-Object { $times += (Invoke-Intel).ms }
$warm = $times | Sort-Object
$p95 = $warm[-1]

$x = $cold.body.xray
$secUnk = Weight-Of $x.sectorWeights "Unknown"
$indUnk = Weight-Of $x.industryWeights "Unknown"
$micro = Weight-Of $x.marketCapWeights "MICRO_CAP"
$unkCap = Weight-Of $x.marketCapWeights "UNKNOWN"
$capNames = @($x.marketCapWeights | ForEach-Object { $_.name }) -join ","

Write-Host "coldMs=$($cold.ms) warmMs=$($times -join ',') warmMaxMs=$p95"
Write-Host "sectorUnknownPct=$secUnk industryUnknownPct=$indUnk microCapPct=$micro unknownCapPct=$unkCap"
Write-Host "capSlices=$capNames confidence=$($cold.body.confidence)"
Write-Host "PASS_sector=$([bool]($secUnk -lt 20)) PASS_industry=$([bool]($indUnk -lt 20)) PASS_notAllMicro=$([bool]($micro -lt 99.9)) PASS_warm=$([bool]($p95 -le 2000)) PASS_cold=$([bool]($cold.ms -le 5000))"
