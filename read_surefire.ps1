$reportDir = "portfolio-app\target\surefire-reports"
Get-ChildItem -Path $reportDir -Filter "*.txt" | ForEach-Object {
    Write-Host "=== $($_.Name) ==="
    Get-Content $_.FullName | Select-Object -Last 15
    Write-Host ""
}
