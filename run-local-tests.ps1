# run-local-tests.ps1
# Automates starting the local Docker-compose infrastructure and running integration tests.

Clear-Host
Write-Host "=======================================================================" -ForegroundColor Cyan
Write-Host "         AM Portfolio: Automated Local Integration Testing             " -ForegroundColor Cyan
Write-Host "=======================================================================" -ForegroundColor Cyan

# 1. Start Docker containers if they are not already up
Write-Host "`n[1/3] Ensuring local Docker infrastructure is running..." -ForegroundColor Yellow
if (Get-Command docker -ErrorAction SilentlyContinue) {
    # Check if our containers are already running
    $runningContainers = docker ps --format "{{.Names}}"
    $requiredContainers = @("local-mongodb", "local-redis", "local-kafka")
    $missingContainers = @()

    foreach ($container in $requiredContainers) {
        if ($runningContainers -notcontains $container) {
            $missingContainers += $container
        }
    }

    if ($missingContainers.Count -gt 0) {
        Write-Host "Containers ($($missingContainers -join ', ')) are missing or stopped. Starting infrastructure..." -ForegroundColor DarkYellow
        & docker compose -f docker-compose-infra.yml up -d
        if ($LASTEXITCODE -ne 0) {
            Write-Host "[ERROR] Failed to start Docker Compose infrastructure. Please verify Docker Desktop is running." -ForegroundColor Red
            exit 1
        }
        # Give services a few seconds to initialize
        Write-Host "Waiting 5 seconds for services to boot up cleanly..." -ForegroundColor DarkYellow
        Start-Sleep -Seconds 5
    } else {
        Write-Host "MongoDB, Redis, and Kafka containers are already running cleanly." -ForegroundColor Green
    }
} else {
    Write-Host "[ERROR] Docker command not found! Please ensure Docker Desktop is running and added to your PATH." -ForegroundColor Red
    exit 1
}

# 2. Set environment variables for compilation (bundled JBR and Maven)
Write-Host "`n[2/3] Setting Java environment (Android Studio JetBrains Runtime)..." -ForegroundColor Yellow
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$mvnPath = "C:\Users\Md Sahimuzzaman\Desktop\axrax-v1\maven\apache-maven-3.9.6\bin\mvn.cmd"

if (-not (Test-Path $mvnPath)) {
    Write-Host "[ERROR] Maven executable not found at: $mvnPath" -ForegroundColor Red
    exit 1
}
Write-Host "Using JAVA_HOME: $env:JAVA_HOME" -ForegroundColor Green
Write-Host "Using Maven CLI: $mvnPath" -ForegroundColor Green

# 3. Compile and Run tests
Write-Host "`n[3/3] Running integration tests with Maven..." -ForegroundColor Yellow
& $mvnPath clean test -pl portfolio-app
$testExitCode = $LASTEXITCODE

if ($testExitCode -eq 0) {
    Write-Host "`n=======================================================================" -ForegroundColor Green
    Write-Host " [SUCCESS] All integration tests compiled and passed successfully!" -ForegroundColor Green
    Write-Host "=======================================================================" -ForegroundColor Green
} else {
    Write-Host "`n=======================================================================" -ForegroundColor Red
    Write-Host " [FAILURE] Some integration tests failed or there was a build error." -ForegroundColor Red
    Write-Host "=======================================================================" -ForegroundColor Red
    exit $testExitCode
}
