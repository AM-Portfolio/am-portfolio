# Portfolio Intelligence Overview — FINAL plan

**Status:** FINAL — **P0 done**. Next: **P-PRE** → P1…  
**Docs home:** `am-portfolio/docs/feature-portfolio-intelligence/`  
**Web mock SoT:** [final-overview-page.png](./final-overview-page.png)  
**Calc SoT:** [E2E-BACKEND-PLAN.md](./E2E-BACKEND-PLAN.md)  
**API shapes:** [API-CONTRACTS.md](./API-CONTRACTS.md)  
**UI layout:** [UI_SPEC.md](./UI_SPEC.md)  
**Diagram:** [architecture.drawio](./architecture.drawio) (page 1 = services, page 2 = Health)  
**Execution cockpit:** [TODO.md](./TODO.md) · **Gate:** [REVIEW.md](./REVIEW.md) · [PREREQUISITES.md](./PREREQUISITES.md)  
**UI:** `am-modern-ui` / `am_portfolio_ui` — `/app/portfolio/:portfolioId/overview`  
**Backend:** `am-portfolio` (`portfolio-analytics` — **not** am-core-services)

**Design rule:** AM DS + glass; mock = **layout + widgets** (not pixel-clone colors).  
**Kill switch:** all **new** Overview widgets behind GrowthBook flags (D15).  
**Delivery:** follow TODO only. After each phase → **REVIEW** → update **TODO** → next.  
**PDF/email:** P9 JSON this release; P10 = **next release**.

---

## Process rule (mandatory)

```text
Start phase N (TODO)
  → implement
  → complete REVIEW.md section N (checkboxes + notes + PROD stamp if BE)
  → mark TODO.md phase N [x]; set Current phase → N+1
  → only then start N+1
```

Do **not** mark TODO done until REVIEW for that phase is signed.  
Agent/human must update TODO + REVIEW in the same change set as the phase work (or immediately after).

---

## Branches (locked)

| Repo | Branch | Base | Status |
|------|--------|------|--------|
| **am-portfolio** | `hotfix/portfolio-intelligence-overview` | `origin/main` | Created / pushed (P0) |
| **am-modern-ui** | `hotfix/portfolio-intelligence-overview` | `origin/main` | Created / pushed (P0) |

---

## 0. Prerequisites

Complete [PREREQUISITES.md](./PREREQUISITES.md) Must-pass before **P1** (Postman PROD, JWT, golden `portfolioId`, JBR, Loki/kubectl).  
Also note GrowthBook flag names (D15) for P7 creation.

---

## 1. Goal / non-goal

**Goal:** Overview shows Health, Risk, X-Ray, Stress, What-If from **backend** engines; layout matches [final-overview-page.png](./final-overview-page.png) on web and derived tablet/phone in [UI_SPEC.md](./UI_SPEC.md); flags can hide new widgets instantly.

**Non-goal:** Redesign Performance chart; new microservice; What-If persistence; PDF/email this release; change am-market / am-core-services / am-trade-management.

---

## 2. Locked decisions

| ID | Decision |
|----|----------|
| D1 | Accent = `ModuleColors.portfolio` + glass. Mock = layout reference. |
| D2 | **Do not redesign** Performance chart internals. |
| D3 | Allocation → **Portfolio X-Ray** when X-Ray flag ON. No duplicate Allocation. |
| D4 | X-Ray tabs v1: Sector \| Industry \| Market Cap. Asset Class = disabled / coming soon. |
| D5 | Health/Risk/Stress/What-If = **deterministic** engines. Formulas in E2E-BACKEND-PLAN. No LLM scores. |
| D6 | What-If = **stateless** POST; no Mongo writes; no new collections. |
| D7 | Extend `portfolio-analytics`. No new microservice. |
| D8 | `POST .../intelligence` = Health + Risk + X-Ray summary. Stress + What-If = separate POSTs. |
| D9 | Owner assert on all `{portfolioId}` analytics routes. |
| D10 | No Overview UI feature work until P3–P6 BE PROD gates are green. |
| D11 | Each BE slice: JBR Maven → **user confirm** → `deploy-prod.ps1` → PROD verify → fix loop. |
| D12 | CHANGE only `am-portfolio` + `am-modern-ui`. |
| D13 | Layout SoT = three bands (Phone / Tablet / Web) per §4 + UI_SPEC. |
| D14 | P9 = weekly/monthly **report JSON** only. P10 PDF+email = next release. |
| D15 | **All new intel widgets behind GrowthBook flags** (master + per-widget). Fail-closed default in prod. Master OFF → legacy Overview (KPIs + chart + movers + Allocation). |

---

## 3. Cross-repo map

See [architecture.drawio](./architecture.drawio) page 1.

```text
UI (P7–P8, flag-gated) → Traefik JWT → portfolio-api
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

## 4. Layout SoT (web / tablet / phone)

**Image:** [final-overview-page.png](./final-overview-page.png)  
**Detail:** [UI_SPEC.md](./UI_SPEC.md)

### Breakpoints

| Band | Width | Role |
|------|-------|------|
| **Phone** | `< 600` | Single column; 2×2 KPIs; Stress/What-If collapsed |
| **Tablet** | `600 – 1099` | Two-column pairs |
| **Web** | `≥ 1100` | Full Image 1 four-row grid |

### Web (≥1100)

```text
Row1: KPI ×4
Row2: Performance chart (~2) | Health Score (~1)
Row3: Top Movers             | Risk Radar
Row4: X-Ray | Stress Test | What-If Simulator
```

### Tablet (600–1099)

```text
Row1: KPI ×4
Row2: Chart | Health
Row3: Movers | Risk
Row4: X-Ray (full width)
Row5: Stress | What-If
```

### Phone (&lt;600)

```text
Metrics 2×2 → Health → Chart → Risk → Movers → X-Ray
→ Stress (collapsed) → What-If (collapsed)
```

Widgets only render when their flags allow (D15). Layout reflows — no empty holes.

---

## 4b. Feature flags (D15)

| GrowthBook / Dart key | Gates |
|----------------------|--------|
| `portfolio-intelligence-overview-v1` | **Master.** OFF → hide all new intel UI; skip intel API calls |
| `portfolio-intel-health-v1` | Health card + Details |
| `portfolio-intel-risk-v1` | Risk Radar + Analysis |
| `portfolio-intel-xray-v1` | X-Ray (replaces Allocation when ON) |
| `portfolio-intel-stress-v1` | Stress Test |
| `portfolio-intel-whatif-v1` | What-If Simulator |

**Rules:** Master OFF ⇒ all children OFF. X-Ray or master OFF ⇒ keep **Allocation**. KPIs + chart + Movers stay (existing). Prod `defaultValue: false` if GB unreachable. Implement via `FeatureFlagKeys` + `FeatureFlagService` in `am_common` (same pattern as `news-ui-enabled`).

---

## 5. UI ↔ Backend

| UI widget | API | Flag | Notes |
|-----------|-----|------|--------|
| KPI cards | summary | — | Unchanged |
| Chart | history / intraday | — | Unchanged internals |
| Top Movers | advanced (transition) | — | Existing |
| Health + Risk + X-Ray | `POST .../intelligence` | master + child | New |
| Stress | `POST .../stress` | master + stress | New; on Run |
| What-If | `POST .../what-if` | master + whatif | New; no persistence |
| Report (P9) | report preview/build | — | JSON only |

**UI must not invent Health/Risk scores** — render DTO fields only.

---

## 6. How backend calculates

1. Build **PortfolioIntelligenceSnapshot** (in-memory): holdings + prices/history via `portfolio-market-data`.  
   `weightPct = value / totalValue * 100`.  
2. Engines: Health, Risk, X-Ray, Stress, What-If.  
3. Exact formulas: [E2E-BACKEND-PLAN.md](./E2E-BACKEND-PLAN.md).  
4. Constants in `HealthScoreConstants` — any knob change documented + golden tests.

**am-market:** HTTP reuse only — **no market code change**.

---

## 7. APIs

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/v1/analytics/portfolio/{portfolioId}/intelligence` | Health + Risk + X-Ray |
| POST | `/v1/analytics/portfolio/{portfolioId}/stress` | Scenario estimates |
| POST | `/v1/analytics/portfolio/{portfolioId}/what-if` | Before/after simulation |
| POST | report preview/build (P9) | Weekly + Monthly **payload** |

All `{portfolioId}` routes: owner assert.

---

## 8. Modules / files

### Backend (am-portfolio)

| Module | Touch |
|--------|-------|
| portfolio-analytics | PRIMARY — Snapshot, engines, ReportPayloadBuilder (P9) |
| portfolio-model | DTOs + OpenAPI |
| portfolio-api | Controllers + owner assert |
| portfolio-service | REUSE enrich/calculator |
| portfolio-market-data | Light helpers if needed |
| portfolio-redis | Optional intel cache (P11) |
| portfolio-app | Bean wiring |
| portfolio-basket | NONE |

### Frontend (am_portfolio_ui) — P7–P8

**CREATE:** `widgets/intelligence/` — health, risk, stress, what-if cards + models/repo; flag helpers.

**MODIFY:** `portfolio_overview_widget.dart` (three-band layout + flag gates); Allocation ↔ X-Ray; `FeatureFlagKeys` / providers in `am_common`.

**DO NOT MODIFY (visual):** chart section styling.

### Database

No new collections for MVP.

---

## 9. PROD verify loop (P3–P6, P9)

```text
1. Local JBR build → mvn clean install -DskipTests
2. User confirm → am-portfolio\deploy-prod.ps1
3. Wait rollout
4. PROD API + golden JWT (API-CONTRACTS.md)
5. Assert correctness (not only HTTP 200)
6. FAIL → logs → fix → goto 1 (max 5 loops)
7. PASS → REVIEW + TODO → next phase
```

---

## 10. Phase-by-phase implementation

### P0 — Branches — **DONE**

Both hotfixes created from `origin/main` and pushed. Remaining: JBR confirm + REVIEW P0 sign-off.

### P-PRE — Prerequisites

- Must-pass in PREREQUISITES  
- Note D15 flag names for GrowthBook  
- REVIEW PRE  

**Exit:** Ready for P1.

### P1 — Owner assert (BE)

- Owner on `POST .../advanced` + pattern for intel routes  
- Regression 403  
- REVIEW P1  

### P2 — Snapshot + contracts (BE)

- Snapshot factory + DTOs/OpenAPI  
- Local JBR build  
- REVIEW P2  

### P3 — Health + Risk + PROD loop

- Engines + `POST .../intelligence` (Health+Risk min)  
- PROD loop; **no UI**  
- REVIEW P3  

### P4 — X-Ray fields + PROD loop

- Sector/Industry/Cap on intelligence  
- PROD parity vs advanced  
- REVIEW P4  

### P5 — Stress + PROD loop

- `POST .../stress`; Scenario estimate label  
- REVIEW P5  

### P6 — What-If + PROD loop

- Stateless Add/Modify/Switch  
- No Mongo write proof  
- REVIEW P6 → **BE band gate:** P3–P6 green → UI  

### P7 — UI web (≥1100) + flags

- Add D15 keys/providers; create GrowthBook flags (default OFF)  
- API clients; Health/Risk/X-Ray/Stress/What-If widgets  
- Image 1 grid; chart untouched  
- Master OFF → legacy + Allocation  
- Verify @ **1280**; kill-switch demo  
- REVIEW P7  

### P8 — UI tablet + phone

- Tablet 2-col; phone stack + collapse Stress/What-If  
- Drill-down sheets (flag-gated)  
- Verify @ **768** and **390**  
- REVIEW P8  

### P9 — Report JSON

- Weekly + Monthly payload; same engines  
- No PDF/email  
- REVIEW P9  

### P10 — PDF + email — **NEXT RELEASE**

Does not block this hotfix Final E2E.

### P11 — Quality + Final E2E

- Tests, analyze, optional Redis fail-open  
- Final REVIEW: web/tablet/phone + master flag OFF/ON  
- market / core / trade unchanged  

---

## 11. Acceptance criteria

- [ ] Health/Risk/X-Ray/Stress/What-If from backend; formulas match E2E-BACKEND-PLAN  
- [ ] Owner denial on foreign `portfolioId`  
- [ ] Single X-Ray when flag ON; Allocation when flag OFF; chart unchanged  
- [ ] All new widgets flag-gated; master OFF restores safe Overview  
- [ ] Web / tablet / phone match UI_SPEC (§4)  
- [ ] Stress labeled estimate; What-If no Mongo write  
- [ ] P9 report payload ready; no PDF/mail required  
- [ ] PROD loops signed for P3–P6 (and P9)  
- [ ] TODO updated after each phase; REVIEW signed each phase  

---

## 12. Risks

- Missing history → omit Vol/Beta, renormalize (E2E).  
- Overview height → collapse Stress/What-If on phone.  
- Bad intel deploy → flip master flag OFF (no hotfix needed for hide).  
- Report PDF deferred — do not block Final on P10.  

---

## 13. Start gate

| Step | Status |
|------|--------|
| Docs pack FINAL | This file |
| P0 branches | Done — sign REVIEW P0 |
| Next | **P-PRE** then P1 |

**Agent:** open [TODO.md](./TODO.md) and execute **Current phase** only.
