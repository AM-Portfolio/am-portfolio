$results = @()
Get-ChildItem -Filter jacoco.csv -Recurse | ForEach-Object {
    $module = $_.Directory.Parent.Parent.Parent.Name
    $csv = Import-Csv $_.FullName
    $moduleMissed = 0
    $moduleCovered = 0
    
    foreach ($row in $csv) {
        $moduleMissed += [int]$row.INSTRUCTION_MISSED
        $moduleCovered += [int]$row.INSTRUCTION_COVERED
    }
    
    $total = $moduleMissed + $moduleCovered
    $percent = 0.0
    if ($total -gt 0) {
        $percent = ($moduleCovered / $total) * 100
    }
    
    $results += [PSCustomObject]@{
        Module = $module
        Missed = $moduleMissed
        Covered = $moduleCovered
        Total = $total
        Coverage = [math]::Round($percent, 2)
    }
}

$results | Sort-Object Coverage | Format-Table -AutoSize
