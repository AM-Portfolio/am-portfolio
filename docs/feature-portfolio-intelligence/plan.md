# Portfolio Intelligence Overview — FINAL plan

**Status:** FINAL — awaiting **user review** before **P0** (branches). Do not fork parallel plans.  
**Docs home:** `am-portfolio/docs/feature-portfolio-intelligence/`  
**Calc SoT:** [E2E-BACKEND-PLAN.md](./E2E-BACKEND-PLAN.md)  
**API shapes:** [API-CONTRACTS.md](./API-CONTRACTS.md)  
**UI vs mockup:** [UI_SPEC.md](./UI_SPEC.md)  
**Diagram:** [architecture.drawio](./architecture.drawio) (page 1 = services, page 2 = Health)  
**Tracking:** [TODO.md](./TODO.md) · [REVIEW.md](./REVIEW.md) · [PREREQUISITES.md](./PREREQUISITES.md)  
**UI:** `am-modern-ui` / `am_portfolio_ui` — `/app/portfolio/:portfolioId/overview`  
**Backend:** `am-portfolio` (`portfolio-analytics` — **not** am-core-services)

**Design rule:** AM DS + glass; mockup = **layout + widgets** (see UI_SPEC).  
**Delivery:** P0 → PRE → BE (+ **PROD fix loop**) → UI → P9 report JSON → P11 Final.  
**PDF/email:** P9 data this release; P10 PDF+mail = **next release**.

---

## Branches (locked)

| Repo | Branch | Base |
|------|--------|------|
| **am-portfolio** | `hotfix/portfolio-intelligence-overview` | `origin/main` |
| **am-modern-ui** | `hotfix/portfolio-intelligence-overview` | `origin/main` |

Create both in **P0** before feature code. Same branch name on both repos.

---

## 0. Prerequisites

Complete [PREREQUISITES.md](./PREREQUISITES.md) before **P1** coding (Postman PROD, JWT, golden `portfolioId`, JBR, Loki/kubectl).  
P0 (branches) may run in parallel with PRE checkboxes.

| Area | Ready when |
|------|------------|
| Postman / curl PROD | `https://am.asrax.in/portfolio` + summary + advanced |
| Logs | `.am` Grafana probes or `kubectl -n am-apps-prod` |
| Deploy | Android Studio JBR + `am-portfolio\deploy-prod.ps1` + user confirm |
| Ownership | Engines in **am-portfolio** only |

---

## 1. Goal

Overview answers:

1. How healthy is my portfolio?  
2. What risks exist?  
3. What is my true exposure?  
4. What if the market moves badly?  
5. What if I change the portfolio?  
6. (This release) Build weekly/monthly **report data**; (next release) PDF + email.

**Keep:** KPI cards, Performance chart internals, Top Movers chrome, sidebar, design tokens.  
**Do not** redesign the Performance chart.

---

## 2. Locked decisions

| ID | Decision |
|----|----------|
| D1 | Accent = `ModuleColors.portfolio` + glass. Mockup = layout reference. |
| D2 | **Do not redesign** Performance chart (`PortfolioComparisonChartSection` / `DashboardChartWidget`). |
| D3 | Allocation → **Portfolio X-Ray** (one widget). No duplicate Allocation. |
| D4 | X-Ray tabs v1: Sector \| Industry \| Market Cap. Asset Class = disabled / coming soon. |
| D5 | Health/Risk/Stress/What-If = **deterministic** engines. Formulas in [E2E-BACKEND-PLAN.md](./E2E-BACKEND-PLAN.md). No LLM scores. |
| D6 | What-If = **stateless** POST; no Mongo writes; no new collections. |
| D7 | Extend `portfolio-analytics`. No new microservice. |
| D8 | `POST .../intelligence` = Health + Risk + X-Ray summary. Stress + What-If = separate POSTs. |
| D9 | Owner assert on all `{portfolioId}` analytics routes. |
| D10 | No Overview UI feature work until P3–P6 BE PROD gates are green (P0/P7 branch may exist empty). |
| D11 | Each BE slice: JBR Maven → **user confirm** → `deploy-prod.ps1` → PROD verify → fix loop. |
| D12 | CHANGE only `am-portfolio` + `am-modern-ui`. NO CHANGE `am-market`, `am-core-services`, `am-trade-management`. |
| D13 | Desktop layout = **final mockup** (see §4). |
| D14 | P9 = weekly/monthly **report JSON payload** only. P10 = PDF + email (**next release**). |

---

## 3. Cross-repo map

See [architecture.drawio](./architecture.drawio) page 1.

```text
UI (P7–P8) → Traefik JWT → portfolio-api
                    ↓
            portfolio-analytics (Snapshot → engines)
           ↙         ↓           ↘
    portfolio-service   market-data client → am-market-data (NO CHANGE)
         Mongo READ ← trade Kafka (NO CHANGE)
```

| Repo | Action |
|------|--------|
| am-portfolio | CHANGE (primary) |
| am-modern-ui | CHANGE (P7–P8 after BE green) |
| am-market / am-core-services / am-trade-management | NO CHANGE |

---

## 4. Final Overview layout (mockup)

### Desktop (≥800) — **Image 1 SoT**

| Row | Content |
|-----|---------|
| 1 | 4 KPI cards (KEEP) |
| 2 | Performance chart (flex ~2, KEEP visual) \| **Health Score only** |
| 3 | Top Movers \| **Risk Radar** |
| 4 | Portfolio X-Ray \| Stress Test \| **What-If** |

### Mobile (&lt;800)

Stack: Metrics → Health → Chart → Risk → Movers → X-Ray → Stress (collapse) → What-If (collapse).

---

## 5. UI ↔ Backend (how widgets get data)

| UI widget | API | When | Notes |
|-----------|-----|------|--------|
| KPI cards | `GET /v1/portfolios/summary` | load | Unchanged |
| Performance chart | history / intraday | load / TF | Unchanged internals |
| Top Movers | `POST .../advanced` (transition) or intelligence movers | load | Keep advanced until P7 wires intelligence |
| Health + Risk + X-Ray summary | `POST /v1/analytics/portfolio/{id}/intelligence` | load | New |
| Stress | `POST .../stress` | on Run | New |
| What-If | `POST .../what-if` | on Simulate | New; no persistence |
| Report (P9) | preview/build report payload API or internal builder | on demand / job | JSON only this release |

**UI must not invent Health/Risk scores** — render DTO fields only.

---

## 6. How backend calculates

1. Build **PortfolioIntelligenceSnapshot** (in-memory): holdings from Mongo + prices/history via `portfolio-market-data` → am-market-data.  
   `weightPct = value / totalValue * 100` (money-based; 15 or 150 stocks same rule).  
2. Engines on Snapshot: Health, Risk, X-Ray (reuse allocation providers), Stress, What-If (clone).  
3. **Exact Health formulas, bands, omit/renormalize:** [E2E-BACKEND-PLAN.md](./E2E-BACKEND-PLAN.md) (SoT).  
4. Constants live in `HealthScoreConstants` at implement time — document any knob change in E2E file + golden tests.

**am-market:** HTTP reuse only — **no market code change**.

---

## 7. APIs

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/v1/analytics/portfolio/{portfolioId}/intelligence` | Health + Risk + X-Ray summary |
| POST | `/v1/analytics/portfolio/{portfolioId}/stress` | Scenario estimates |
| POST | `/v1/analytics/portfolio/{portfolioId}/what-if` | Before/after simulation |
| POST or internal | report build (P9) | Weekly + Monthly **payload** (no PDF) |

All `{portfolioId}` routes: `portfolio.owner == UserContext.userId`.

Reuse: summary, holdings, history, intraday, advanced (movers during transition).

---

## 8. Modules / files

### Backend (am-portfolio)

| Module | Touch |
|--------|-------|
| portfolio-analytics | PRIMARY — Snapshot factory, Health/Risk/Stress/WhatIf engines, ReportPayloadBuilder (P9) |
| portfolio-model | DTOs + OpenAPI |
| portfolio-api | Controllers + owner assert |
| portfolio-service | REUSE enrich/calculator |
| portfolio-market-data | Light helpers for NIFTY/history if needed |
| portfolio-redis | Optional intel cache (P11) |
| portfolio-app | Bean wiring |
| portfolio-basket | NONE |

### Frontend (am_portfolio_ui) — P7–P8 only

**CREATE:** `widgets/intelligence/portfolio_health_card.dart`, `portfolio_risk_radar_card.dart`, `portfolio_stress_card.dart`, `portfolio_what_if_card.dart` + models/repo methods.

**MODIFY:** `portfolio_overview_widget.dart` (layout), Allocation → X-Ray panel, endpoints/cubit.

**DO NOT MODIFY (visual):** chart section / dashboard chart widget styling.

### Database

No new collections / indexes for MVP. What-If and report payload: compute-only (P9 may add optional store later — default **stateless**).

---

## 9. PROD verify loop (mandatory on P3–P6, P9)

**Goal:** Response is **correct**, not only “deployed”. Repeat until golden checks pass (max **5** iterations per phase, then escalate to user).

```text
1. Local JBR build: JAVA_HOME=Android Studio jbr → mvn clean install -DskipTests
2. Deploy: user confirm once per phase (or user said “auto-deploy this phase”)
   → am-portfolio\deploy-prod.ps1  (not am deploy if auth fails)
3. Wait rollout ready (kubectl/helm as in deploy script)
4. Call PROD API with golden JWT + portfolioId (see API-CONTRACTS.md)
5. Assert vs golden rules:
   - HTTP 200 (or expected 403 for owner tests)
   - weightPct sums ~100 (±0.5)
   - health.score 0–100; components length 6–8 (omit vol/beta if no history)
   - stress: negative market shock → negative impact for long book
   - what-if: before/after present; Mongo holdings unchanged
6. If FAIL → Loki/kubectl logs → fix code → goto 1
7. If PASS → tick REVIEW + TODO → next phase
```

**Contracts:** [API-CONTRACTS.md](./API-CONTRACTS.md). **UI visual pass** is separate (P7–P8 REVIEW / UI_SPEC) after APIs are green.

---

## 10. Phase-by-phase implementation

### P-PRE — Prerequisites

- [ ] [PREREQUISITES.md](./PREREQUISITES.md) Must-pass green  
- [ ] REVIEW PRE signed  
**Exit:** Ready for P1 after P0 branches exist.

---

### P0 — Environment setup (branches)

**Goal:** Coding environment ready on both services.

| Step | Action |
|------|--------|
| 1 | `am-portfolio`: from `origin/main` → create `hotfix/portfolio-intelligence-overview` |
| 2 | `am-modern-ui`: from `origin/main` → create `hotfix/portfolio-intelligence-overview` |
| 3 | Confirm JBR path; docs folder present on portfolio branch |
| 4 | REVIEW P0: both branches exist |

**No feature engine/UI code required in P0** (docs commits OK).

**Exit:** Both hotfixes checked out; ready for P1 on portfolio.

---

### P1 — Owner assert (security)

- Owner check on `POST .../advanced`  
- Same pattern ready for intelligence / stress / what-if  
- Regression test 403  
- Local JBR build  

**Exit:** REVIEW P1; optional PROD smoke.

---

### P2 — Snapshot + contracts

- `PortfolioIntelligenceSnapshot` + factory  
- DTOs in `portfolio-model` + OpenAPI  
- Local JBR build  
- No new Mongo collections  

**Exit:** REVIEW P2.

---

### P3 — Health + Risk API + PROD loop

- `HealthScoreEngine` + golden tests ([E2E-BACKEND-PLAN.md](./E2E-BACKEND-PLAN.md))  
- `RiskRadarEngine` + golden tests  
- `POST .../intelligence` (Health + Risk minimum)  
- **PROD loop** until accurate  
- **No UI yet**  

**Exit:** REVIEW P3 PROD gate.

---

### P4 — X-Ray on intelligence + PROD loop

- Sector / Industry / Market Cap on intelligence response (reuse allocation providers)  
- PROD parity vs `.../advanced` allocation  
- **No Flutter X-Ray rename yet**  

**Exit:** REVIEW P4.

---

### P5 — Stress API + PROD loop

- `StressEngine` presets + custom  
- `POST .../stress`  
- PROD: shock signs/magnitudes sensible  
- Label: **Scenario estimate**  

**Exit:** REVIEW P5.

---

### P6 — What-If API + PROD loop

- `WhatIfEngine` Add / Modify / Switch (stateless)  
- `POST .../what-if`  
- Prove no Mongo write  
- PROD before/after validated  

**Exit:** REVIEW P6. **Backend band gate:** P3–P6 green → start UI.

---

### P7 — UI desktop Overview

- Clients for intelligence / stress / what-if  
- Health, Risk, X-Ray (evolve Allocation), Stress, What-If  
- Layout per §4  
- Chart **untouched**  

**Exit:** REVIEW P7 visual vs mockup.

---

### P8 — UI mobile / tablet

- Priority stack + collapsible Stress/What-If  
- Existing breakpoints  

**Exit:** REVIEW P8.

---

### P9 — Report payload (this release)

**Goal:** Weekly + Monthly **portfolio report data** (JSON/DTO) using same engines (Health, Risk, X-Ray, summary, optional movers/stress snapshot).

| Item | Decision |
|------|----------|
| Weekly window | Last 7 trading days |
| Monthly window | Calendar month to date (or last complete month — implement as calendar month) |
| Output | `PortfolioReportDto` via builder; optional `POST .../report/preview` |
| PDF | **Not in P9** |
| Email | **Not in P9** |
| Assert | Report Health matches intelligence for same snapshot/asOf |

PROD loop on preview/build endpoint or fixture dump.

**Exit:** REVIEW P9 — payload complete for PDF templates next release.

---

### P10 — PDF + email (**NEXT RELEASE** — do not start in this hotfix unless scoped later)

- Weekly PDF + Monthly PDF templates  
- Email send (Vault-backed mail config)  
- Cron: weekly + monthly (Asia/Kolkata)  
- Opt-in preference  
- **Does not change** Health math — render P9 payload only  

**Exit:** Deferred; track in next release TODO.

---

### P11 — Quality + Final E2E

- Optional Redis `portfolio:intel:*`  
- Micrometer + Grafana  
- Flutter analyze + BE tests  
- [REVIEW.md](./REVIEW.md) Final E2E Pass  
- Confirm market / core / trade unchanged  

**Exit:** Feature ready to merge when REVIEW Final signed.

---

## 11. Execution order (summary)

```text
P0 branches → P-PRE → P1 owner → P2 snapshot
→ P3 Health/Risk (+PROD) → P4 X-Ray (+PROD) → P5 Stress (+PROD) → P6 What-If (+PROD)
→ P7–P8 UI → P9 report JSON (+PROD) → P11 Final E2E
P10 PDF+mail = NEXT RELEASE
```

---

## 12. Acceptance criteria

- [ ] Health/Risk/X-Ray/Stress/What-If from backend; formulas match E2E-BACKEND-PLAN  
- [ ] Owner denial on foreign `portfolioId`  
- [ ] Single X-Ray widget; chart unchanged  
- [ ] Stress labeled estimate; What-If no Mongo write  
- [ ] Desktop + mobile usable per §4  
- [ ] P9 weekly + monthly report payload ready; no PDF/mail required this release  
- [ ] PROD loops signed for P3–P6 (and P9)  
- [ ] Engine + ownership tests run  

---

## 13. Risks

- Missing history → omit Vol/Beta, renormalize (E2E rules).  
- Overview height → collapse Stress/What-If on mobile.  
- Sector string inconsistency → normalize where possible.  
- Report PDF/mail deferred — do not block Overview Final on P10.  

---

## 14. Start gate (WAIT HERE)

| Step | Owner | Status |
|------|--------|--------|
| 1 | **You review** this FINAL pack (plan + API-CONTRACTS + UI_SPEC + E2E-BACKEND-PLAN) | **Waiting** |
| 2 | You say **“start P0”** / approve | Blocked until then |
| 3 | Agent: create both `hotfix/portfolio-intelligence-overview` branches | Not started |
| 4 | P-PRE checkboxes → **P1** owner assert | After P0 |

**Do not create branches or write feature code until step 2.**
