# Feature: Portfolio Intelligence (Overview)

**Status:** FINAL docs pack — **P0 branches done**. Next: follow [TODO.md](./TODO.md) **Current phase** (`P-PRE`).  
**SoT folder:** `am-portfolio/docs/feature-portfolio-intelligence/`  
**Branches:** `hotfix/portfolio-intelligence-overview` on **am-portfolio** + **am-modern-ui**

## How to implement

1. Open [TODO.md](./TODO.md) → work **only** the Current phase.  
2. Implement using [plan.md](./plan.md) + linked calc/API/UI specs.  
3. Complete [REVIEW.md](./REVIEW.md) for that phase.  
4. Mark TODO `[x]`, advance Current phase, commit.  
5. Never start UI (P7) until P3–P6 PROD REVIEW is signed.  
6. All **new** widgets are GrowthBook-gated (plan D15 / [UI_SPEC.md](./UI_SPEC.md)).

## Files

| File | Purpose |
|------|---------|
| [plan.md](./plan.md) | **FINAL** phase plan, flags, layout, PROD loop |
| [TODO.md](./TODO.md) | **Execution cockpit** — update after every phase |
| [REVIEW.md](./REVIEW.md) | **Mandatory gate** — sign after every phase |
| [UI_SPEC.md](./UI_SPEC.md) | Web / tablet / phone layout + flag fallback |
| [final-overview-page.png](./final-overview-page.png) | Web mock SoT (Image 1) |
| [E2E-BACKEND-PLAN.md](./E2E-BACKEND-PLAN.md) | Exact Health/calc formulas |
| [API-CONTRACTS.md](./API-CONTRACTS.md) | Request/response JSON + asserts |
| [PREREQUISITES.md](./PREREQUISITES.md) | Postman PROD, logs, deploy |
| [architecture.drawio](./architecture.drawio) | Page 1 services · Page 2 Health |

## Delivery order

P0 (done) → **P-PRE** → P1–P6 BE (+ PROD loop) → **P7–P8 UI (flags)** → P9 report JSON → P11 Final.  
P10 PDF+email = **next release**.

## Feature flags (kill switch)

| Key | Role |
|-----|------|
| `portfolio-intelligence-overview-v1` | Master OFF = legacy Overview |
| `portfolio-intel-health-v1` | Health |
| `portfolio-intel-risk-v1` | Risk |
| `portfolio-intel-xray-v1` | X-Ray (else Allocation) |
| `portfolio-intel-stress-v1` | Stress |
| `portfolio-intel-whatif-v1` | What-If |

## Repo impact

| Repo | Action |
|------|--------|
| am-portfolio | CHANGE |
| am-modern-ui | CHANGE (P7–P8) |
| am-market / am-core-services / am-trade-management | NO CHANGE |
