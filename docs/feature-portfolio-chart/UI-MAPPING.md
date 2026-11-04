# UI-MAPPING — Dashboard vs Portfolio chart

## Shared stack

| Layer | Symbol / file |
|-------|----------------|
| State | `dashboardOverlayProvider(userId)` — `am_dashboard_ui/.../dashboard_overlay_provider.dart` |
| Widget | `DashboardChartWidget` — `.../dashboard_chart_widget.dart` |
| Adapter | `overlayStateToChartData` — `overlay_series_adapter.dart` |
| Repo | `DashboardRepository.getPortfolioHistory` / `getIndexHistory` |

**Not used for this chart:** `historyStreamProvider` → `GET /v1/analysis/dashboard/performance`.

---

## Dashboard page

| Step | Code |
|------|------|
| Slot | `DashboardWidgetId.benchmarkComparison` (legacy `portfolioWealthChart` remapped) |
| Builder | `dashboard_widget_catalog.dart` → `_buildBenchmarkComparison` → `DashboardChartWidget(userId:)` |
| Prefetch | `dashboardParallelKickoff` watches overlay when slot visible |
| Scope | `dashboard_portfolio_selector` → `setPreferredPortfolioId` |

---

## Portfolio overview page

| Step | Code |
|------|------|
| Mount | `portfolio_overview_widget.dart` — intel + legacy → `PortfolioComparisonChartSection` |
| Section | `portfolio_comparison_chart_section.dart` |
| userId | `AuthCubit` Authenticated user id |
| Scope | `setPreferredPortfolioId(portfolioId)` on build / id change |
| BUILDING | `_SyncingBanner` + snackbar “Portfolio synced. Chart is ready.” |
| Child | `DashboardChartWidget` with `ModuleColors.portfolio` |

**Unused overview path:** `PortfolioChartSection` + `PortfolioHistoryCubit` / intraday cubit (portfolio APIs only, no indices) — no overview call sites.

---

## Overlay load steps

1. Listen `appTimeFrameProvider` → `reload()` on code change.  
2. `_loadPortfolios`:
   - Client: `portfolioApiClientProvider`
   - Path: TF `1D` → `/v1/portfolios[/id]/intraday`; else `/v1/portfolios[/id]/history?timeFrame=`
   - Build Overall + per-book `%` series; set `historyBuilding` / phase; schedule reload in 3s if building  
3. `_loadIndices`:
   - Client: `marketApiClientProvider` (`marketData.baseUrl` else analysis else api)
   - `GET /v1/analysis/historical-charts?symbols=&range=`
4. Default selection: Overall + NIFTY 50 (+ preferred book via `mergeOverlaySelection`)

---

## Field → pixel

| API field | UI |
|-----------|-----|
| `totalUserWealth` / intraday `totalWealth` | Aggregate raw Y → `%` for Overall |
| Nested `close` / `value` | Per-book series |
| Index `close` | Index series `%` |
| `historyStatus` BUILDING | Banner + poll |
| `phase` | Banner subtitle (90D / 1Y / Queued) |
