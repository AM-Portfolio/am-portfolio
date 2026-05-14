$totalMissed = 0
$totalCovered = 0
Get-ChildItem -Filter jacoco.csv -Recurse | ForEach-Object {
    $csv = Import-Csv $_.FullName
    foreach ($row in $csv) {
        $totalMissed += [int]$row.INSTRUCTION_MISSED
        $totalCovered += [int]$row.INSTRUCTION_COVERED
    }
}
$total = $totalMissed + $totalCovered
$percent = 0.0
if ($total -gt 0) {
    $percent = ($totalCovered / $total) * 100
}
Write-Host "Total Missed: $totalMissed"
Write-Host "Total Covered: $totalCovered"
Write-Host "Overall Coverage: $($percent.ToString('F2'))%"
