$modules = 'portfolio-model', 'portfolio-market-data', 'portfolio-basket', 'portfolio-service', 'portfolio-api', 'portfolio-redis', 'portfolio-kafka', 'portfolio-app', 'portfolio-analytics', 'portfolio-sdk', 'am-common-data'
foreach ($mod in $modules) {
    $srcCount = 0
    $testCount = 0
    if (Test-Path "$mod\src\main\java") {
        $srcCount = (Get-ChildItem -Path "$mod\src\main\java" -Recurse -Filter *.java -File | Measure-Object).Count
    }
    if (Test-Path "$mod\src\test\java") {
        $testCount = (Get-ChildItem -Path "$mod\src\test\java" -Recurse -Filter *.java -File | Measure-Object).Count
    }
    Write-Host "$mod - Src Files: $srcCount, Test Files: $testCount"
}
