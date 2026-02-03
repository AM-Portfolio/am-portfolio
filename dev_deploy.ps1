# One-Step Development Deployment Script
# 1. Navigate to Root
Set-Location 'a:\InfraCode\AM-Portfolio-grp\am-portfolio\am-portfolio-data'

# 2. Build backend (Skip tests for speed)
Write-Host "Building Portfolio Backend..." -ForegroundColor Cyan
mvn clean package -DskipTests

if ($LASTEXITCODE -ne 0) {
    Write-Error "Build Failed! Aborting deployment."
    exit $LASTEXITCODE
}

# 3. Deploy Containers with Dev Overrides
Write-Host "Deploying Containers..." -ForegroundColor Cyan
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build

Write-Host "Deployment Complete!" -ForegroundColor Green
