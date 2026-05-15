$modules = @(
    "portfolio-model",
    "portfolio-market-data",
    "portfolio-basket",
    "portfolio-service",
    "portfolio-api",
    "portfolio-redis",
    "portfolio-kafka",
    "portfolio-app",
    "portfolio-analytics",
    "portfolio-sdk"
)

foreach ($m in $modules) {
    $srcDir = Join-Path $m "src\main\java"
    $testDir = Join-Path $m "src\test\java"
    $hasSrc = Test-Path $srcDir
    $hasTest = Test-Path $testDir

    $srcCount = 0
    $testCount = 0
    if ($hasSrc) {
        $srcCount = (Get-ChildItem -Path $srcDir -Filter "*.java" -Recurse | Measure-Object).Count
    }
    if ($hasTest) {
        $testCount = (Get-ChildItem -Path $testDir -Filter "*.java" -Recurse | Measure-Object).Count
    }

    Write-Host ("{0,-25} src={1,-5} ({2,3} files)  test={3,-5} ({4,3} files)" -f $m, $hasSrc, $srcCount, $hasTest, $testCount)
}
