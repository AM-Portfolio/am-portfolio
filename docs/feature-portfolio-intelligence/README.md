# Feature: Portfolio Intelligence (Overview)

**Status:** FINAL pack — **awaiting your review** before **P0**  
**SoT:** `am-portfolio/docs/feature-portfolio-intelligence/`  
**Branches (P0, not created yet):** `hotfix/portfolio-intelligence-overview` on am-portfolio + am-modern-ui

## Files

| File | Purpose |
|------|---------|
| [plan.md](./plan.md) | **FINAL** phase-by-phase plan + PROD loop |
| [E2E-BACKEND-PLAN.md](./E2E-BACKEND-PLAN.md) | Exact Health/calc formulas |
| [API-CONTRACTS.md](./API-CONTRACTS.md) | Request/response JSON + asserts |
| [UI_SPEC.md](./UI_SPEC.md) | Layout vs mockup (image 1) |
| [PREREQUISITES.md](./PREREQUISITES.md) | Postman PROD, logs, deploy |
| [TODO.md](./TODO.md) | Checklist P0 → P11 |
| [REVIEW.md](./REVIEW.md) | Per-phase + Final E2E |
| [architecture.drawio](./architecture.drawio) | Services + Health diagram |

## Delivery order

1. **You review** this folder  
2. You say **start P0** → create both hotfix branches  
3. P-PRE → P1–P6 BE (+ PROD fix loop) → P7–P8 UI → P9 report JSON → P11 Final  
4. P10 PDF+email = **next release**

## Repo impact

| Repo | Action |
|------|--------|
| am-portfolio | CHANGE |
| am-modern-ui | CHANGE (P7–P8) |
| am-market / am-core-services / am-trade-management | NO CHANGE |
