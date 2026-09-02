# Basket prod E2E deploy

## One command (run in PowerShell)

```powershell
powershell -ExecutionPolicy Bypass -File "c:\Users\Md Sahimuzzaman\Desktop\axrax-v1\am-portfolio\scripts\basket-prod-e2e.ps1"
```

This runs: git checkout `hotfix/basket-apis-latency` → commit → `mvn test` → push → `deploy-prod.ps1` → kubectl verify.

## Prerequisites

1. `GITHUB_TOKEN` in `%USERPROFILE%\.asrax\credentials.env` (with `write:packages` for GHCR)
2. `gh auth login` if push fails
3. Docker, Helm, kubectl with `%USERPROFILE%\.am\kubeconfig.vps`

## Image tag

`deploy-prod.ps1` uses `basket-v3-prod-20260902-<git-sha>` (not the old `20260901` tag).

## After deploy — Grafana probe

```powershell
powershell -ExecutionPolicy Bypass -File "c:\Users\Md Sahimuzzaman\Desktop\axrax-v1\am-portfolio\scripts\basket-prod-verify-grafana.ps1"
```

## If Cursor agent shell is blocked

Reinstall hooks from catalog (includes fail-open fix):

```powershell
cd "c:\Users\Md Sahimuzzaman\Desktop\axrax-v1\amctl"
npm run ai:install
```

Or temporarily disable **Cursor Settings → Hooks → beforeShellExecution**.
