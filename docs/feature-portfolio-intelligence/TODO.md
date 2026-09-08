# Portfolio Intelligence — TODO

**Order is mandatory.** Mark DONE only after the matching block in [REVIEW.md](./REVIEW.md) is checked.

Refs: [plan.md](./plan.md) · [E2E-BACKEND-PLAN.md](./E2E-BACKEND-PLAN.md) · [PREREQUISITES.md](./PREREQUISITES.md)

**Branches:** `hotfix/portfolio-intelligence-overview` on **am-portfolio** and **am-modern-ui**

---

## P0 — Environment / branches

- [x] Shell unblocked (removed `~\.cursor\hooks.json`); agent Shell OK
- [x] Create `hotfix/portfolio-intelligence-overview` from `origin/main` in **am-portfolio** (`1738b44` pushed)
- [x] Create `hotfix/portfolio-intelligence-overview` from `origin/main` in **am-modern-ui** (`9067598f` pushed)
- [ ] JBR path confirmed
- [ ] REVIEW P0 complete

**Manual (IDE terminal) if agent blocked:**

```text
cd am-portfolio
git fetch origin main
git checkout main
git merge --ff-only origin/main
git checkout -b hotfix/portfolio-intelligence-overview

cd ../am-modern-ui
git fetch origin main
git checkout main
git merge --ff-only origin/main
git checkout -b hotfix/portfolio-intelligence-overview
```

---

## P-PRE — Prerequisites

- [ ] Complete Must-pass in [PREREQUISITES.md](./PREREQUISITES.md)
- [ ] Postman/curl PROD: summary + advanced green
- [ ] JWT valid for golden portfolioId
- [ ] Loki or kubectl logs path confirmed
- [ ] REVIEW PRE signed
- [ ] **Ready for P1**

---

## P1 — Owner assert (BE)

- [ ] Owner assert on `POST .../advanced`
- [ ] Same pattern for intelligence / stress / what-if
- [ ] Regression test 403
- [ ] REVIEW P1 complete

---

## P2 — Snapshot + contracts (BE)

- [ ] `PortfolioIntelligenceSnapshot` + factory
- [ ] DTOs + OpenAPI
- [ ] Local JBR Maven build green
- [ ] REVIEW P2 complete

---

## P3 — Health + Risk API (BE) + PROD loop

- [ ] `HealthScoreEngine` + golden tests (E2E-BACKEND-PLAN)
- [ ] `RiskRadarEngine` + golden tests
- [ ] `POST .../intelligence` (Health + Risk minimum)
- [ ] Local build → user confirm → `.\deploy-prod.ps1`
- [ ] PROD validate intelligence JSON
- [ ] Fix loop until accurate
- [ ] REVIEW P3 complete
- [ ] **No UI yet**

---

## P4 — X-Ray fields (BE) + PROD loop

- [ ] Sector / Industry / Cap on intelligence
- [ ] Deploy-prod + PROD parity vs advanced
- [ ] Fix loop until accurate
- [ ] REVIEW P4 complete

---

## P5 — Stress API (BE) + PROD loop

- [ ] `StressEngine` + `POST .../stress`
- [ ] Deploy-prod + PROD shock checks
- [ ] REVIEW P5 complete

---

## P6 — What-If API (BE) + PROD loop

- [ ] `WhatIfEngine` Add / Modify / Switch (stateless)
- [ ] `POST .../what-if` + no Mongo write proof
- [ ] Deploy-prod + PROD before/after
- [ ] REVIEW P6 complete
- [ ] **Backend gate:** P3–P6 green → UI

---

## P7 — UI desktop Overview

- [ ] API clients intelligence / stress / what-if
- [ ] Health + Risk + X-Ray + Stress + What-If
- [ ] Layout per plan §4 (chart untouched)
- [ ] REVIEW P7 complete

---

## P8 — UI mobile / tablet

- [ ] Priority stack + collapsible Stress/What-If
- [ ] REVIEW P8 complete

---

## P9 — Report payload (this release)

- [ ] Weekly + Monthly `PortfolioReportDto` / builder
- [ ] Reuses Health/Risk/X-Ray/summary (same asOf parity)
- [ ] PROD loop on preview/build
- [ ] **No PDF / no email**
- [ ] REVIEW P9 complete

---

## P10 — PDF + email (NEXT RELEASE)

- [ ] Deferred — weekly/monthly PDF + mail schedule
- [ ] Do not block this hotfix Final E2E on P10

---

## P11 — Quality + Final E2E

- [ ] Optional Redis intel cache
- [ ] Micrometer + Grafana
- [ ] Flutter analyze + BE tests
- [ ] REVIEW Final E2E Pass
- [ ] market / core / trade unchanged

---

## Cross-repo

| Repo | Action |
|------|--------|
| am-portfolio | CHANGE |
| am-modern-ui | CHANGE (P7–P8) |
| am-market / am-core-services / am-trade-management | NO CHANGE |
