# Portfolio Intelligence — TODO (execution cockpit)

**Current phase:** `UI-PARITY` (Overview density pass landed on am-modern-ui; dogfood via `AM_INTEL_FORCE_ON` / GrowthBook)  
**PRE env:** `https://am-preprod.asrax.in/portfolio`  
**PROD env:** `https://am.asrax.in/portfolio`  
**Golden portfolioId (prod dogfood):** `afe250bc-38d3-4165-888e-d1bd620c5016`  
**Golden portfolioId (preprod):** `c7ef8e22-9d98-44fd-a500-b7bf770597b5`  
**Rule:** Mark `[x]` only after [REVIEW.md](./REVIEW.md) signed for that phase.

**Branches:** `hotfix/portfolio-intelligence-overview` on **am-portfolio** + **am-modern-ui**

**Refs:** [plan.md](./plan.md) · [UI_SPEC.md](./UI_SPEC.md) · [API-CONTRACTS.md](./API-CONTRACTS.md) · [E2E-BACKEND-PLAN.md](./E2E-BACKEND-PLAN.md) · [final-overview-page.png](./final-overview-page.png)

**Flags (D15):** `portfolio-intelligence-overview-v1` · `portfolio-intel-health-v1` · `portfolio-intel-risk-v1` · `portfolio-intel-xray-v1` · `portfolio-intel-stress-v1` · `portfolio-intel-whatif-v1`  
**Local dogfood:** `npm run run:app:9000:prod:intel` (`AM_INTEL_FORCE_ON=true`, debug only)

---

## P0 — Environment / branches — DONE

- [x] Branches created/pushed both repos  
- [x] JBR confirmed  
- [x] REVIEW P0  

---

## P-PRE — Prerequisites — DONE (preprod)

- [x] Preprod summary + advanced 200  
- [x] Grafana probes OK  
- [x] REVIEW PRE  

---

## P1 — Owner assert — DONE

- [x] `PortfolioOwnerAssert` on advanced  
- [x] Helper ready for intel routes  
- [x] `PortfolioAnalyticsControllerTest` 403/200/404  
- [x] JBR build  
- [x] REVIEW P1  

---

## P2 — Snapshot + contracts — DONE (local)

- [x] `PortfolioIntelligenceSnapshot` + factory  
- [x] DTOs under `com.portfolio.model.analytics.intelligence`  
- [x] Local JBR build green  
- [x] No new Mongo collections  
- [x] REVIEW P2 (local) — **live OpenAPI after deploy**  

---

## P3 — Health + Risk — DONE (local) / PROD loop PENDING

- [x] `HealthScoreEngine` + golden test (64 Watch)  
- [x] `RiskRadarEngine`  
- [x] `POST .../intelligence` (Health + Risk + X-Ray)  
- [x] Owner assert on intelligence  
- [ ] User confirm → deploy (preprod/prod)  
- [ ] Live PROD/preprod validate intelligence JSON  
- [x] REVIEW P3 code; **PROD stamp open**  

**Shortcut:** `historyPoints=0` → Vol/Beta omitted + mix renormalized until history wired.

---

## P4 — X-Ray — DONE (local) / deploy PENDING

- [x] X-Ray on intelligence response (`XRaySummaryBuilder`)  
- [ ] Live parity check vs advanced after deploy  
- [x] REVIEW P4 code; **live stamp open**  

---

## P5 — Stress — DONE (local) / deploy PENDING

- [x] `StressEngine` + `POST .../stress`  
- [x] estimateLabel Scenario estimate  
- [ ] Live shock checks after deploy  
- [x] REVIEW P5 code; **live stamp open**  

---

## P6 — What-If — DONE (local) / deploy PENDING

- [x] `WhatIfEngine` Add/Modify/Switch  
- [x] `POST .../what-if` (no Mongo write)  
- [ ] Live before/after after deploy  
- [x] REVIEW P6 code; **BE band gate for UI = code ready** (flags OFF until GB on)  

---

## P7 — UI web + flags — DONE (local) + UI parity pass

- [x] D15 keys + fail-closed providers in `am_common`  
- [x] API clients intelligence / stress / what-if  
- [x] Health / Risk / X-Ray / Stress / What-If widgets  
- [x] Web ≥1100 layout per UI_SPEC (X-Ray\|Risk mid; compact Movers + Stress + What-If)  
- [x] Master OFF → Allocation + no intel fetches  
- [x] UI parity: X-Ray donut+legend (Sector/Industry/Cap only), Stress multi-preset table, Health N/100 grid, Risk spider|findings/axes, What-If deltas, compact Movers, intel skeleton  
- [x] Debug force: `AM_INTEL_FORCE_ON` + `run:app:9000:prod:intel`  
- [ ] GrowthBook flags created in console (ops)  
- [x] REVIEW P7 code; **visual @1280 after flags ON / force-on**  

---

## P8 — UI tablet + phone — DONE (local)

- [x] Tablet 600–1099: Chart\|Health + X-Ray\|Risk + Movers full + Stress\|What-If  
- [x] Phone &lt;600: Chart → Health → X-Ray → Risk → compact Movers → Stress/What-If collapsed  
- [x] Drill-down sheets stubs  
- [ ] Verify @768 / @390 on device/browser  
- [x] REVIEW P8 code; **device stamp open**  

---

## P9 — Report payload — DONE (local) / deploy PENDING

- [x] `POST .../report/preview` WEEKLY|MONTHLY  
- [x] Reuses intelligence pieces  
- [x] No PDF / no email  
- [ ] Live after deploy  
- [x] REVIEW P9 code; **live stamp open**  

---

## P10 — PDF + email — DEFERRED (next release)

- [x] N/A this hotfix  

---

## P11 — Quality / Final E2E — PARTIAL

- [x] Engine + ownership unit tests green locally  
- [x] Flutter analyze on new paths (agent)  
- [ ] Optional Redis intel cache — not shipped  
- [ ] Micrometer / Grafana after deploy  
- [ ] Final E2E web+tablet+phone with flags ON  
- [ ] Master OFF kill-switch on live  
- [ ] market / core / trade unchanged (verify at PR)  
- [ ] REVIEW Final E2E — **blocked on deploy + GB flags**  

---

## Next operator actions

1. Confirm deploy target: **preprod** (`deploy.ps1` / helm preprod) and/or **prod** (`deploy-prod.ps1`)  
2. Create GrowthBook D15 flags (default OFF)  
3. Smoke intelligence/stress/what-if/report on golden portfolio  
4. Flip master + child flags ON for dogfood  
5. Sign remaining REVIEW live stamps → set Current phase `DONE`

---

## Cross-repo

| Repo | Action |
|------|--------|
| am-portfolio | CHANGE (engines + APIs) |
| am-modern-ui | CHANGE (P7–P8 UI + flags) |
| am-market / am-core-services / am-trade-management | NO CHANGE |

---

## Deferred (post second-pass polish — not blocking UI density)

- [ ] A11y: Semantics / keyboard focus on X-Ray & Movers tabs  
- [ ] Perf: Stress 5× parallel POSTs → lazy/batch / “on Run”  
- [ ] Perf: Slim advanced when Allocation hidden (movers-only path)  
- [ ] Architecture: Analytics cubit emit guard on rapid portfolio switch  
- [ ] UX: GrowthBook late-adopt legacy→intel flash  
- [ ] Backend: Populate `historyPoints` for VOL/BETA risk axes + confidence  
- [ ] Docs: Intelligence endpoint latency guidance  
