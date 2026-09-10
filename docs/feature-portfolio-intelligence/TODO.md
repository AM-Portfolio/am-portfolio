# Portfolio Intelligence — TODO (execution cockpit)

**Current phase:** `P-PRE`  
**Rule:** Mark items `[x]` only after the matching section in [REVIEW.md](./REVIEW.md) is signed.  
**After every phase:** (1) complete REVIEW → (2) check boxes here → (3) set **Current phase** to next → (4) commit.

**Branches:** `hotfix/portfolio-intelligence-overview` on **am-portfolio** + **am-modern-ui**

**Refs:** [plan.md](./plan.md) · [UI_SPEC.md](./UI_SPEC.md) · [API-CONTRACTS.md](./API-CONTRACTS.md) · [E2E-BACKEND-PLAN.md](./E2E-BACKEND-PLAN.md) · [PREREQUISITES.md](./PREREQUISITES.md) · [final-overview-page.png](./final-overview-page.png)

**Flags (D15):** `portfolio-intelligence-overview-v1` (master) · `portfolio-intel-health-v1` · `portfolio-intel-risk-v1` · `portfolio-intel-xray-v1` · `portfolio-intel-stress-v1` · `portfolio-intel-whatif-v1`

---

## P0 — Environment / branches

**Goal:** Hotfix branches ready.  
**Repos:** am-portfolio, am-modern-ui  
**Depends on:** —

### Checklist

- [x] Shell unblocked for agent git  
- [x] `hotfix/portfolio-intelligence-overview` on **am-portfolio** (from `origin/main`, pushed)  
- [x] `hotfix/portfolio-intelligence-overview` on **am-modern-ui** (from `origin/main`, pushed)  
- [ ] JBR path confirmed (`C:\Program Files\Android\Android Studio\jbr`)  
- [ ] REVIEW P0 signed  

### DoD

Both remotes have the branch; docs SoT on portfolio branch; REVIEW P0 done.

### Verify

`git branch -vv` on both repos shows tracking remote hotfix.

### After done

Set **Current phase** → `P-PRE`.

---

## P-PRE — Prerequisites

**Goal:** Ops + PROD access ready before BE code.  
**Repos:** ops / laptop  
**Depends on:** REVIEW P0  

### Checklist

- [ ] [PREREQUISITES.md](./PREREQUISITES.md) Must-pass complete  
- [ ] Postman/curl PROD summary + advanced green  
- [ ] JWT valid for golden `portfolioId`  
- [ ] Loki / `.am` Grafana probes or kubectl logs path confirmed  
- [ ] Operator confirms deploy via `deploy-prod.ps1` + user confirm  
- [ ] GrowthBook: note D15 flag key names (create flags in P7)  
- [ ] REVIEW PRE signed  

### DoD

Can call PROD portfolio APIs and deploy portfolio with JBR.

### After done

Set **Current phase** → `P1`.

---

## P1 — Owner assert (BE)

**Goal:** No IDOR on analytics `portfolioId`.  
**Repos:** am-portfolio  
**Depends on:** REVIEW PRE  

### Checklist

- [ ] Owner assert on `POST .../advanced`  
- [ ] Same pattern ready for intelligence / stress / what-if  
- [ ] Regression test → 403 for non-owner  
- [ ] Local JBR Maven build green  
- [ ] REVIEW P1 signed  

### DoD

Non-owner request denied; test fails if assert removed.

### After done

Set **Current phase** → `P2`.

---

## P2 — Snapshot + contracts (BE)

**Goal:** In-memory snapshot + DTOs/OpenAPI.  
**Repos:** am-portfolio  
**Depends on:** REVIEW P1  

### Checklist

- [ ] `PortfolioIntelligenceSnapshot` + factory  
- [ ] DTOs + OpenAPI per [API-CONTRACTS.md](./API-CONTRACTS.md)  
- [ ] Local JBR Maven build green  
- [ ] No new Mongo collections  
- [ ] REVIEW P2 signed  

### DoD

Snapshot weights ~100% on fixture; OpenAPI publishes new schemas.

### After done

Set **Current phase** → `P3`.

---

## P3 — Health + Risk API (BE) + PROD loop

**Goal:** Accurate Health + Risk on PROD.  
**Repos:** am-portfolio  
**Depends on:** REVIEW P2  
**UI:** none  

### Checklist

- [ ] `HealthScoreEngine` + golden tests ([E2E-BACKEND-PLAN.md](./E2E-BACKEND-PLAN.md))  
- [ ] `RiskRadarEngine` + golden tests  
- [ ] `POST .../intelligence` returns Health + Risk  
- [ ] Local build → user confirm → `.\deploy-prod.ps1`  
- [ ] PROD validate intelligence JSON (API-CONTRACTS asserts)  
- [ ] Fix loop until accurate (max 5)  
- [ ] REVIEW P3 signed (PROD stamp)  

### DoD

PROD intelligence 200; score 0–100; components present; stable ±1 same window.

### After done

Set **Current phase** → `P4`.

---

## P4 — X-Ray fields (BE) + PROD loop

**Goal:** Sector / Industry / Cap on intelligence.  
**Repos:** am-portfolio  
**Depends on:** REVIEW P3  

### Checklist

- [ ] X-Ray summary fields on intelligence response  
- [ ] Deploy-prod + PROD parity vs advanced allocation  
- [ ] Fix loop until accurate  
- [ ] REVIEW P4 signed  

### DoD

PROD X-Ray slices match advanced (within documented tolerance).

### After done

Set **Current phase** → `P5`.

---

## P5 — Stress API (BE) + PROD loop

**Goal:** Scenario estimates on PROD.  
**Repos:** am-portfolio  
**Depends on:** REVIEW P4  

### Checklist

- [ ] `StressEngine` + `POST .../stress`  
- [ ] Deploy-prod + PROD shock sign/magnitude checks  
- [ ] Label: Scenario estimate  
- [ ] REVIEW P5 signed  

### DoD

Negative market shock → negative impact for long book on golden portfolio.

### After done

Set **Current phase** → `P6`.

---

## P6 — What-If API (BE) + PROD loop

**Goal:** Stateless simulation on PROD.  
**Repos:** am-portfolio  
**Depends on:** REVIEW P5  

### Checklist

- [ ] `WhatIfEngine` Add / Modify / Switch  
- [ ] `POST .../what-if` + before/after  
- [ ] Prove **no** Mongo holdings write  
- [ ] Deploy-prod + PROD validate  
- [ ] REVIEW P6 signed  
- [ ] **Backend band gate:** P3–P6 PROD-signed → UI allowed  

### DoD

Before/after present; holdings unchanged after what-if.

### After done

Set **Current phase** → `P7`. **Do not start P7 until band gate checked.**

---

## P7 — UI web (≥1100) + feature flags

**Goal:** Image 1 Overview with kill switches.  
**Repos:** am-modern-ui (`am_common` keys + `am_portfolio_ui`)  
**Depends on:** P3–P6 REVIEW signed  

### Checklist

- [ ] Add D15 keys to `FeatureFlagKeys` + providers  
- [ ] Create GrowthBook flags (default **OFF** in prod)  
- [ ] API clients: intelligence / stress / what-if  
- [ ] Widgets: Health, Risk, X-Ray, Stress, What-If (flag-gated)  
- [ ] Web layout per [UI_SPEC.md](./UI_SPEC.md) / [final-overview-page.png](./final-overview-page.png)  
- [ ] Chart visual **untouched**  
- [ ] Master OFF → KPIs + chart + movers + **Allocation**; no intel fetches  
- [ ] X-Ray ON → Allocation removed  
- [ ] Skeletons / error / retry per UI_SPEC  
- [ ] REVIEW P7 signed  

### DoD

@ **1280** width matches web SoT when flags ON; master OFF restores legacy safely.

### Verify

Flag ON/OFF toggle in GrowthBook (or local default) without redeploy of hide path.

### After done

Set **Current phase** → `P8`.

---

## P8 — UI tablet + phone

**Goal:** Usable 768 / 390 layouts with same flags.  
**Repos:** am-modern-ui  
**Depends on:** REVIEW P7  

### Checklist

- [ ] Tablet `600–1099` 2-col layout per UI_SPEC  
- [ ] Phone `<600` stack; Stress/What-If **collapsed** by default  
- [ ] Drill-down sheets (Health / Risk / X-Ray) flag-gated  
- [ ] Verify @ **768** and **390**  
- [ ] REVIEW P8 signed  

### DoD

No horizontal overflow; collapsed phone Overview height acceptable; flags still work.

### After done

Set **Current phase** → `P9`.

---

## P9 — Report payload (this release)

**Goal:** Weekly + Monthly JSON for future PDF.  
**Repos:** am-portfolio  
**Depends on:** REVIEW P6 (engines)  

### Checklist

- [ ] Weekly + Monthly `PortfolioReportDto` / builder  
- [ ] Reuses Health/Risk/X-Ray/summary (same asOf parity)  
- [ ] PROD loop on preview/build  
- [ ] **No PDF / no email**  
- [ ] REVIEW P9 signed  

### After done

Set **Current phase** → `P11` (P10 deferred).

---

## P10 — PDF + email (NEXT RELEASE)

- [ ] Deferred — do not block this hotfix Final E2E  

---

## P11 — Quality + Final E2E

**Goal:** Ship-ready sign-off.  
**Repos:** both  
**Depends on:** REVIEW P7–P9 (as applicable)  

### Checklist

- [ ] Engine + ownership tests green  
- [ ] Flutter analyze clean on touched files  
- [ ] Optional Redis intel cache fail-open (if shipped)  
- [ ] Micrometer / Grafana check  
- [ ] Final E2E: web + tablet + phone journeys  
- [ ] Final E2E: master flag OFF mid-session → safe Overview  
- [ ] No secrets in git diff  
- [ ] market / core / trade **unchanged**  
- [ ] REVIEW Final E2E signed  

### After done

Set **Current phase** → `DONE`.

---

## Cross-repo

| Repo | Action |
|------|--------|
| am-portfolio | CHANGE |
| am-modern-ui | CHANGE (P7–P8) |
| am-market / am-core-services / am-trade-management | NO CHANGE |
