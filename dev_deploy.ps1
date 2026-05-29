# One-Step Development Deployment Script
# 1. Navigate to Root
Set-Location $PSScriptRoot

# 2. Configure Environment for Java 17 and Maven
$env:JAVA_HOME = "C:\Users\Md Sahimuzzaman\.jdks\ms-17.0.18"
$env:Path = "C:\Users\Md Sahimuzzaman\maven\apache-maven-3.9.6\bin;C:\Users\Md Sahimuzzaman\.jdks\ms-17.0.18\bin;" + $env:Path

Write-Host "Configured Environment:" -ForegroundColor Yellow
Write-Host "JAVA_HOME: $env:JAVA_HOME"
Write-Host "Using Maven from C:\Users\Md Sahimuzzaman\maven"

# 3. Build backend (Skip tests for speed)
Write-Host "Building Portfolio Backend..." -ForegroundColor Cyan
mvn clean package -DskipTests

if ($LASTEXITCODE -ne 0) {
    Write-Error "Build Failed! Aborting deployment."
    exit $LASTEXITCODE
}

# 4. Deploy Containers with Dev Overrides
Write-Host "Deploying Containers..." -ForegroundColor Cyan
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build

Write-Host "Deployment Complete!" -ForegroundColor Green
