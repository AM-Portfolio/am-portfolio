# Backend Improvement Plan v1 — Portfolio Intelligence

**Version:** 1  
**Status:** APPROVED for implementation sequencing (docs SoT)  
**Branch:** `hotfix/portfolio-intelligence-overview` (`am-portfolio`)  
**Created:** 2026-09-11  
**Goal:** Make Portfolio Intelligence APIs **industry-grade** on **correctness** and **low latency** (fast p95).  

**Related SoT:** [plan.md](./plan.md) · [TODO.md](./TODO.md) · [REVIEW.md](./REVIEW.md) · [E2E-BACKEND-PLAN.md](./E2E-BACKEND-PLAN.md) · [API-CONTRACTS.md](./API-CONTRACTS.md) · [PREREQUISITES.md](./PREREQUISITES.md) · [UI_SPEC.md](./UI_SPEC.md) · [architecture.drawio](./architecture.drawio)

---

## 0. Assurance statement

| Claim | Verdict |
|-------|---------|
| MVP engines + owner assert exist | Yes (local code) |
| Live preprod/prod stamps complete | No (REVIEW open) |
| History wired → Vol/Beta/Performance truthful | No (`historyPoints=0` shortcut) |
| Industry-grade **low latency** | No (Redis not shipped; Stress 5× fan-out; no SLOs) |

**Industry-grade = WS0–WS7 complete.**  

- WS1 (history) **without** WS7 (cache/timeouts/batch) makes Overview **slower** even if scores improve.  
- Do **not** ship history alone. Ship WS1 + WS7 together (or WS7 first for cache plumbing, then history behind the same path).

“High latency” in product language here means **high performance / low latency APIs**, not slow responses.

---

## 1. Folder coverage matrix

| File | Role | Gap vs live code / process |
|------|------|----------------------------|
| README.md | Index | Was stuck on `P-PRE`; cockpit is `UI-PARITY` + this v1 BE plan |
| plan.md | FINAL phase plan D1–D15 | Lists Redis as optional P11 — **v1 promotes Redis fail-open to required** |
| TODO.md | Execution cockpit | P3–P6/P9 live open; deferred history + latency guidance |
| REVIEW.md | Gates | Live stamps blank P2–P6/P9/P11/Final |
| E2E-BACKEND-PLAN.md | Calc SoT | Formulas match engines; omit Vol/Beta only; Performance invent is code gap |
| API-CONTRACTS.md | JSON shapes | No latency budgets (added in §5 below) |
| UI_SPEC.md | Layout | Stress multi-preset → BE fan-out |
| PREREQUISITES.md | PRE | Postman Intelligence folder still open |
| architecture.drawio | Diagram | Shows optional Redis — align to fail-open cache |
| PNGs | UI mock | Layout only |

---

## 2. Prior hardening plan rating

| Lens | Score | Note |
|------|-------|------|
| Correctness scaffolding | 7/10 | Right P0 (history + invent Perf) |
| Industry-grade latency | 5/10 | Redis was wrongly out of scope |
| Folder SoT delivery | Missing | This file closes that |

---

## 3. Shared request path

```text
JWT (gateway) → PortfolioAnalyticsController
  → PortfolioOwnerAssert.requireOwner
  → PortfolioIntelligenceSnapshotFactory
  → engines (Health / Risk / XRay / Stress / WhatIf)
```

Code home:

- `portfolio-api/.../PortfolioAnalyticsController.java`
- `portfolio-api/.../security/PortfolioOwnerAssert.java`
- `portfolio-analytics/.../intelligence/*`
- `portfolio-model/.../analytics/intelligence/*`

---

## 4. Per-API flow + calculation audit

### 4.1 `POST /v1/analytics/portfolio/{portfolioId}/intelligence`

**When:** Overview load when GrowthBook intel flags ON.  
**Body:** `{}`  
**Engines:** Snapshot → HealthScoreEngine + RiskRadarEngine + XRaySummaryBuilder → confidence.

#### Snapshot money math

```text
value_i     = price_i × quantity_i
totalValue  = Σ value_i
weightPct_i = round2(value_i / totalValue × 100)   // HALF_UP
```

Price resolve: live market → else avgBuy → else **drop holding** (G8).

#### Health (mix weights from E2E / HealthScoreConstants)

| Component | Mix | Formula | Live today (`historyPoints=0`) |
|-----------|-----|---------|--------------------------------|
| Diversification | 0.20 | `0.5*min(100,n*8)+0.5*min(100,sectors*12)` | OK |
| Concentration | 0.20 | `100 - 2*top1 - 1.5*max(0,maxSector-20)` | OK |
| Performance | 0.15 | `50 + 5*(port-nifty) + 2*port` | **G1 invent:** null→0 → ~50 |
| Volatility | 0.15 | `100 - dailyVol*40` | Omitted (correct) |
| Liquidity | 0.10 | liquidShare Large+Mid | OK |
| Beta | 0.10 | beta curve | Omitted (correct) |
| Allocation | 0.05 | `100 - 1.2*max(0,maxSector-25)` | OK |
| Risk Resilience | 0.05 | `0.5*conc + 0.25*volPart + 0.25*betaPart`; missing vol/beta → **70** | Spec-OK (E2E §4.8) |

Omit Vol/Beta + renormalize: OK. Bands Critical≤39 / Watch≤64 / Healthy≤84 / Strong: OK. Golden 64 Watch fixture: OK.

#### Risk

- Axes: CONCENTRATION, SECTOR (`maxSectorPct*1.5`), DIVERSIFICATION, LIQUIDITY; VOL/BETA if history≥20.  
- Findings: TOP1≥25 HIGH; SECTOR≥30 HIGH / ≥20 MEDIUM; BETA thresholds when history present.  
- SECTOR axis formula is code-only — keep; document as locked MVP (G12).

#### X-Ray

Sum `weightPct` by sector / industry / marketCap; sort desc. Slice sum may drift ±0.5 from per-holding `round2` (contract allows).

#### Confidence

```text
empty book → 0.0
historyPoints ≥ 20 → 0.9
else → 0.55   // live always ~0.55 until WS1
```

---

### 4.2 `POST .../stress`

**Math:**

```text
pctImpact = Σ (weightPct/100) * shockPct * betaProxy
absImpact = totalValue * pctImpact / 100
```

| Preset | Behavior | Gap |
|--------|----------|-----|
| NIFTY_DOWN_10 / _20 | Uniform shock × betaProxy | betaProxy stuck **1.0** until history (G2) |
| BANKING_DOWN_20 | Sector −20 | Fuzzy bank/financial match (G9) |
| IT_DOWN_15 | Sector −15 | Fuzzy IT match (G9) |
| CRASH_2008 | −35 / −30 / −25 | Matches API-CONTRACTS |
| Unknown preset | Silent −10 | **G3 → must 400** |
| Custom | sector + shockPct | Missing shockPct → 0.0 (G4) |

No Mongo write: OK by construction. UI may fire **5 POSTs** (G5).

---

### 4.3 `POST .../what-if`

| Mode | Calc | Gap |
|------|------|-----|
| ADD_INVESTMENT | Add INR to holding or new row | New symbol → Unknown meta (G6) |
| MODIFY_HOLDING | Target weight; rescale others | OK if symbol exists |
| SWITCH_ALLOCATION | Move value from→to sector | Empty `to` invents `SWITCH_*` + **LARGE_CAP** (G7) |

Re-finalize weights; Health before/after. Unknown mode → 400. No Mongo write: OK.

---

### 4.4 `POST .../report/preview`

Rebuilds intelligence pieces; `movers` / `stressSnapshot` null. Period WEEKLY|MONTHLY. Inherits G1–G2 / confidence gaps. No PDF/email (P10 out of scope).

---

### 4.5 Legacy Overview companions (not intel engines)

| Call | Role |
|------|------|
| GET `/v1/portfolios/summary` | KPI band |
| GET `/v1/portfolios/holdings` | Holdings |
| POST `/v1/analytics/portfolio/{id}/advanced` ×2 | Movers + allocations |

Latency budget for Overview must account for these + intel.

---

## 5. Latency SLOs (locked)

Measure gateway → handler on preprod golden-sized book.

| API | p50 | p95 | Hard fail |
|-----|-----|-----|-----------|
| intelligence (warm cache) | ≤ 150 ms | ≤ 400 ms | p95 > 800 ms |
| intelligence (cold / miss) | ≤ 800 ms | ≤ 2000 ms | p95 > 3000 ms |
| stress single | ≤ 100 ms | ≤ 300 ms | — |
| stress **batch 5** | ≤ 150 ms | ≤ 400 ms | Preferred over 5× RTT |
| what-if | ≤ 200 ms | ≤ 500 ms | — |
| report/preview | ≤ 200 ms | ≤ 600 ms | — |

Closes TODO deferred “Intelligence endpoint latency guidance.”

---

## 6. Gap register

| ID | Sev | Area | Evidence | Acceptance |
|----|-----|------|----------|------------|
| G1 | P0 | Performance invent | `HealthScoreEngine` null→0 | Omit Performance + renormalize when returns missing |
| G2 | P0 | historyPoints=0 | `SnapshotFactory` line finalize(...,0,nulls) | Wire ≥20 returns; populate vol/beta/returns |
| G3 | P0 | Unknown stress preset | `StressEngine.applyPreset` default | HTTP 400 |
| G4 | P0 | Custom shockPct | primitive double | Reject missing/invalid |
| G5 | P0 | Stress 5× fan-out | UI + single preset API | Batch stress endpoint |
| G6 | P2 | What-If ADD meta | Unknown sector/mcap | Enrich via SecurityDetails when possible |
| G7 | P2 | SWITCH LARGE_CAP invent | `WhatIfEngine.applySwitch` | UNKNOWN or reject empty to |
| G8 | P1 | Silent price drop | factory skip price≤0 | Count dropped; log; confidence signal |
| G9 | P1 | Sector aliases | Stress matchers | Canonical Banking/IT vs X-Ray labels |
| G10 | P1 | Double Mongo read | assert + factory | Pass owned portfolio into factory |
| G11 | P1 | not-found → 400 | IllegalArgumentException | Map to 404 |
| G12 | P2 | SECTOR axis undocumented | RiskRadarEngine | Document in E2E / this file |
| G13 | P1 | Thin tests | only Health golden strong | Risk/XRay/Stress/WhatIf/Service/controller |
| G14 | P1 | No Micrometer | — | Timers + Grafana |
| G15 | P0 | No Redis intel cache | TODO P11 open | Fail-open cache + TTL |
| G16 | P0 | History timeout hang risk | market historical | Deadline → omit path |
| G17 | P1 | Postman intel folder | PREREQUISITES | Collection folder + asserts |
| G18 | P1 | Live REVIEW stamps | REVIEW.md | Preprod smoke signed |
| G19 | P2 | Confidence hardcodes | 0.55/0.9 | Document; optionally derive from coverage |
| G20 | P1 | Single-flight | concurrent Overview | Coalesce builds per portfolioId |

---

## 7. Workstreams (implementation order)

### WS0 — Docs + Postman harness

- [x] This file (`BACKEND-IMPROVEMENT-PLAN-v1.md`)  
- [ ] Link from README (same change set)  
- [ ] Postman: folder **Portfolio Intelligence** on `postman/AMPortfolio_Complete.postman_collection.json`  
  - `{{base_url}}` = `https://am-preprod.asrax.in/portfolio` via shared env  
  - `{{access_token}}` never committed  
  - Discover `portfolioId` via `GET /v1/portfolios/list`  
  - Overview + intelligence + stress presets + what-if + report + AuthZ 403 on foreign golden id  
  - Soft timing notes for SLO dogfood  

### WS1 — History wiring (correctness P0)

- Load ~60 trading days via `MarketDataService.getHistoricalData`  
- Portfolio value series + NIFTY same calendar  
- Compute `historyPoints`, `portRetPct`, `niftyRetPct`, sample `dailyVolPct`, `beta`  
- If returns &lt; 20: `historyPoints=0`, **omit Vol + Beta + Performance**, renormalize  
- If history≥20 but vol/beta null: **do not** default 0 / 1 — omit instead  
- Update E2E omit rule to include Performance when series missing  
- Unit tests: golden with history; 19 vs 20 boundary; omit-perf  

**Gate:** Do not merge WS1 without WS7 timeouts + cache path ready.

### WS2 — Validation / errors (P0)

- Unknown preset → 400  
- Custom `shockPct` required / validated  
- OpenAPI 400/401 on intel routes  
- Factory not-found → 404  
- Canonical Banking / IT matchers aligned with X-Ray names  

### WS3 — AuthZ / single load / no-write (P1)

- Pass `PortfolioModelV1` from `requireOwner` into `buildFromPortfolio`  
- Controller tests: intelligence / stress / what-if / report → 403/200 + bad id 400  
- Mock verify no portfolio save on stress/what-if  

### WS4 — Engine tests + Micrometer (P1)

- Unit tests: RiskRadar, XRay, Stress (all presets + crash), WhatIf (3 modes), confidence, report period  
- Micrometer: `portfolio.intel.intelligence|stress|whatif|report`  
- Structured logs: portfolioId, durationMs, holdingsCount, historyPoints, confidence, cacheHit  

### WS5 — What-If meta (P2)

- ADD: enrich symbol via `SecurityDetailsService` when available  
- SWITCH: no LARGE_CAP invent; UNKNOWN or 400 if empty target sector  

### WS6 — Live gate

**Cockpit:** execute and tick [TODO.md](./TODO.md) **WS6 — Preprod deploy + accuracy loop** (W6.0–W6.5).

- Deploy hotfix to preprod  
- Run Postman correctness + SLO checks  
- Sign REVIEW P2–P6/P9/P11 live stamps; close TODO deferred history + latency  

### WS7 — Low latency (required for industry-grade)

1. **Redis fail-open cache** (`portfolio-redis`): key `intel:v1:{portfolioId}:{asOfBucket}`; TTL 60–120s; Redis down → compute fresh  
2. **Single-flight** coalesce concurrent intelligence builds per portfolioId  
3. **History deadlines**; on timeout → omit path (no hang)  
4. **Batch stress** `POST .../stress` body with `presets: [...]` (keep single preset back-compat)  
5. Parallel history chunks via existing market client; avoid N+1 security fallback on hot path when bulk OK  
6. Eliminate double Mongo read (with WS3)  
7. Grafana panel from Micrometer; record p50/p95 in REVIEW  

---

## 8. Out of scope (v1)

- P10 PDF + email  
- GrowthBook console flag creation (ops)  
- am-market / am-core-services / am-trade-management code changes  
- UI density / Flutter parity (already on UI-PARITY track)  
- Committing JWTs or secrets  

---

## 9. Definition of Done (industry-grade)

- [ ] G1–G4, G5, G15, G16 closed  
- [ ] History wired; no invented Performance  
- [ ] Redis fail-open cache live  
- [ ] Batch stress available  
- [ ] Engine + controller tests green  
- [ ] Preprod Postman correctness green  
- [ ] Preprod p95 SLOs met (§5)  
- [ ] REVIEW live stamps signed; TODO deferred history + latency closed  
- [ ] market / core / trade unchanged  

---

## 10. Next action after this doc

1. Implement **WS0 Postman** + **WS7 cache/timeouts scaffolding** + **WS1 history** (coupled).  
2. Then WS2 → WS3 → WS4 → WS5 → WS6.  
3. Update TODO Current phase to a BE improvement track when coding starts (e.g. `BE-IMPROVE-v1`).
