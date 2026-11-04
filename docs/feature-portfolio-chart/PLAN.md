# PLAN — feature-portfolio-chart

| Field | Value |
|-------|--------|
| Kind | `feature` |
| Slug | `feature-portfolio-chart` |
| Lead repo | am-portfolio |
| Other repos | am-modern-ui; am-market; am-core-services (am-analysis) |
| Target env | `preprod` |
| Branch | `feature/portfolio-chart` (only if committing) |
| Delivery rating | `10/10` |
| Design rating | `10/10` |
| Scorecard overall | `9.6/10` |
| Agent satisfied | `yes` |

Execute gate: docs pack complete for user review. Agent satisfied ≠ product approved. **No product code** in this slice.

## Goal

Document the complete portfolio comparison chart: how Dashboard and Portfolio overview load series, which three backends own which data, every response field, history BUILDING job, and architecture.drawio — so anyone can follow the flow without reading the whole codebase.

## Source plan (pointer)

| Field | Value |
|-------|--------|
| Origin | `ide-plan` |
| Pointer | `c:\Users\user\.cursor\plans\portfolio_chart_endpoints_bacb2b96.plan.md` |
| Summary | Pack under `docs/feature-portfolio-chart/`; three-service map; shared `dashboardOverlayProvider`; no `buildPortfolioChart` HTTP verb; draw.io 7 sheets; link overview-chart TF pack |
| Catalog | Domain holdings/books → am-portfolio; modern-ui overlay chart; am-market historical-charts; am-analysis dashboard summary/performance |
| Context files | am-portfolio docs packs; modern-ui `am_dashboard_ui` overlay providers; am-analysis `AnalysisController`; am-market analysis historical-charts |
| Analysis source | am-portfolio `local`; am-modern-ui `local`; am-market `local`; am-analysis `local` |
| Coverage | `handles` for documentation scope |
| AM gaps | Filled in this pack (FLOW, contracts, data points, UI map, ops, draw.io) |

## Images

- `images/` — empty (UI previews not requested)

## Prerequisites (done / pending / blocked)

| Check | Status | Notes |
|-------|--------|--------|
| MCP needed / connected | done | Not required to author this docs pack |
| Downstream APIs exist | done | Verified in local trees |
| Postman collection found | pending | Chart matrix via dig evidence + MCP tools; no new collection in this slice |

## World-class feature map

### P0 — this slice

- SoT pack: three-service wiring, field catalogs, Dashboard vs Portfolio mounts, BUILDING job, architecture.drawio

### P1 — next

- Publish `GET /v1/portfolios/history/status` into am-specs OpenAPI / MCP
- Gateway docs: `/market` vs `/analysis` for `/v1/analysis/historical-charts`

### P2 — later or never

- Remove or rewire orphaned `historyStreamProvider` / `dashboard/performance` chart path
- Retire unused Bloc `PortfolioChartSection` or mark deprecated in UI

## First-run / virtual value UX

- Empty holdings → history `EMPTY` / no series (≥2 points required for a line)
- BUILDING → syncing banner on Portfolio overview; poll every ~3s
- Demo / paper books may show zero wealth until catch-up writes real snapshots (see chart-mcp-evidence historical defects)

## Latency & consistency

- Overlay reload on global timeframe change
- History BUILDING: UI polls `reload()` every 3s (envelope `historyStatus`)
- Index series independent of wealth; market outage → indices fail, wealth may still plot
- Analysis summary has portfolio-summary fallback on dashboard; **not** used for comparison chart Y
- No invented p99

## Current system (verified)

- **UI (both pages):** `dashboardOverlayProvider` → `DashboardChartWidget`. Dashboard: catalog `benchmarkComparison`. Portfolio: `PortfolioComparisonChartSection`.
- **Wealth:** `GET /v1/portfolios/history|intraday` (+ optional `/{id}/…`) on `portfolio.baseUrl` — `PortfolioController`, `PortfolioHistoryResponse`, catch-up via `SnapshotCatchUpService`.
- **Indices:** `GET /v1/analysis/historical-charts` on `marketData.baseUrl` — am-market `AnalysisController` (not am-core-services am-analysis).
- **KPIs:** `GET /v1/analysis/dashboard/summary` on `analysis.baseUrl` — am-analysis; beside chart, not the overlay series.
- **Legacy:** `GET /v1/analysis/dashboard/performance` → `PerformanceResponse.chartData`; `historyStreamProvider` has no live widget consumer for the comparison chart.

## Target journeys

1. User opens Dashboard → Performance Chart shows Overall + NIFTY 50 (%), TF from global bar.
2. User opens Portfolio overview → same overlay; sidebar book preferred; BUILDING banner if catch-up running.
3. User changes TF (1D ↔ 1M…) → reload: 1D uses intraday, else history; indices reload with `range=TF`.
4. User sees empty books → no wealth lines (or EMPTY); indices may still load.
5. Wrong mental model “analysis builds the chart” → docs clarify wealth=portfolio, indices=market.

## Identity & ownership

- JWT `userId` scopes portfolio history and analysis summary
- Series ids: `Overall`, portfolio UUID, index symbols (`NIFTY 50`, …)
- Snapshot keys: `snapshotId`, `snapshotDate`; `userId` stripped from history JSON

## State & money

- Chart Y is wealth (absolute) then UI `%` from first positive point — not invested capital
- Catch-up writes EOD Mongo snapshots from holdings × historical prices
- Modes: FULL / GAP / MERGE_BROKER (`HistoryJobMode`)

## Isolation

- Per-user history; preferred portfolio id scopes path when set (not `all`)
- Index fetch does not leak portfolio holdings

## Failure modes

- No holdings → EMPTY / abort catch-up
- Market historical fetch fail → sparse or failed index series
- Analysis summary 5xx → dashboard may fall back to portfolio summary cards
- Analysis performance unused on overlay — failure invisible to comparison chart

## Corner-case matrix

| Case | Trigger | Expected | Covered by |
|------|---------|----------|------------|
| TF 1D | Global TF = 1D | `/intraday` wealth + historical-charts range=1D | UI-MAPPING / Test Plan row 2 |
| TF 1M | Global TF = 1M | `/history?timeFrame=1M` | row 1 |
| BUILDING | Catch-up running | Banner + poll 3s; toast on READY | FLOW / OPS |
| Preferred book | Sidebar / dashboard selector | `/{id}/history` or preferred legend | UI-MAPPING |
| <2 points | Sparse series | Line omitted for that id | overlay provider |
| historical-charts on wrong base | Call analysis host | 404/empty indices | OPS |
| Empty holdings | New user | EMPTY / no Overall line | FLOW |

## Out of scope

- Product code / OpenAPI harvest in this PR
- overview-chart-timeframe-sync UX fixes (separate pack)
- Basket / intelligence / stress widgets

## Open questions

- (none)

## Review roles

| Role | When | Looks for |
|------|------|-----------|
| Agent architect | Until Agent satisfied | Three-service accuracy, draw.io sheets |
| You (product / tech owner) | User review gate | Can a new eng follow the chart? |
| Optional `/review` | If asked | am-code-review |

## UI previews (modern-ui)

- Consent: `not-requested`
- Gate: n/a — docs pack
- Previews: `n/a — not requested`

## Services

| Service | Existing or new | Impl notes | Unit tests in this slice |
|---------|-----------------|------------|--------------------------|
| am-portfolio | existing | History / intraday / catch-up (documented) | Doc accuracy — no code |
| am-market | existing | historical-charts (documented) | Doc accuracy |
| am-analysis | existing | summary live; performance legacy (documented) | Doc accuracy |
| am-modern-ui | existing | overlay wiring (documented) | Doc accuracy |

## Test Plan (Postman or MCP)

**Phase 0 (first):** feed market `historical-data` + portfolio `history` / `intraday` / `history/status` into Postman (AM dig env).  
**Phase 1:** Gmail upload → backend builds intraday + daily historical from email asOf → re-hit portfolio APIs in Postman.  
**Phase 2 UI:** blocked until Phase 1 green.

Collection: Phase 0 Postman folders (market + portfolio chart)  
Shared Postman env: existing AM-* / dig

| Row | Request or MCP call | Method + path | Expected | Owning service | Result |
|-----|---------------------|---------------|----------|----------------|--------|
| 1 | History 1M | GET `/portfolio/v1/portfolios/history?timeFrame=1M` | 200 envelope with `points` / status | am-portfolio | not verified |
| 2 | Intraday | GET `/portfolio/v1/portfolios/intraday` | 200 list of candles | am-portfolio | not verified |
| 3 | Indices | GET `/market/v1/analysis/historical-charts?symbols=NIFTY%2050&range=1M` | 200 `data` map with `dataPoints` | am-market | not verified |
| 4 | Summary | GET `/analysis/v1/analysis/dashboard/summary` | 200 totals | am-analysis | not verified |
| 5 | Docs cross-check | Read DATA-POINTS vs Java models | Field names match | docs | pass |

## Deploy

Docs only — no deploy. Default env note: **preprod**.

## Agent scorecard

| Dimension | Score /10 | Evidence or gap |
|-----------|-----------|-----------------|
| Product / features | 9.5 | P0 docs + P1/P2 clear |
| Architecture / design | 10 | Three-service + 7-sheet draw.io |
| Data / identity / contracts | 10 | DATA-POINTS + API-CONTRACTS |
| State / money / consistency | 9.5 | Wealth vs % rebase documented |
| Reliability / failure | 9.5 | Failures sheet + OPS |
| Security / authz | 9 | JWT owner scope documented |
| Observability | 9 | Phases / BUILDING / probes |
| Testability | 9 | Doc accuracy rows |
| Operability / rollout | 9.5 | OPS + base-URL footgun |
| TODO / implementability | 10 | Docs-only TODO path |
| **Overall (mean)** | **9.6** | |

## Adversarial review

| Severity | Finding |
|----------|---------|
| Minor | Test Plan live rows remain `not verified` until dig probe re-run |
| Nit | Relative link to modern-ui pack assumes sibling workspace checkout |

- Blocker/Major count: 0
- Agent satisfied = yes

## Rating log (agent architect loop)

| Pass | Delivery | Design | Overall | Gaps then patched |
|------|----------|--------|---------|-------------------|
| 1 | 10 | 10 | 9.6 | Three-service split; Dashboard vs Portfolio mounts |

## User review gate

Review PLAN + architecture.drawio + TODO + reference markdowns. Confirm before any product Execute or commit.

## REPORT gate

Do not write `REPORT.md` for this docs pack until live Test Plan rows are verified (optional).
