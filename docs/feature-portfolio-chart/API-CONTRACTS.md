# API-CONTRACTS — portfolio chart

Gateway bases (empty services map): `https://{domain}/portfolio`, `/market`, `/analysis`.

---

## A. am-portfolio (`portfolio.baseUrl`)

Auth: JWT user context.

| Method | Path | Query | When UI calls | Response |
|--------|------|-------|---------------|----------|
| GET | `/v1/portfolios/history` | `timeFrame` (default service `1M`) | Overlay TF ≠ 1D, no preferred id | `PortfolioHistoryResponse` |
| GET | `/v1/portfolios/{portfolioId}/history` | `timeFrame` | Preferred / scoped book | same |
| GET | `/v1/portfolios/intraday` | — | TF = `1D`, aggregate | `List<IntradayDataPoint>` |
| GET | `/v1/portfolios/{portfolioId}/intraday` | — | TF = `1D`, scoped | same |
| GET | `/v1/portfolios/history/status` | — | Chart sync poll / ops | `PortfolioHistoryStatusResponse` (**not** in published OpenAPI yet) |
| POST | `/v1/portfolios/trigger-snapshot` | — | Ops EOD | async job |
| POST | `/v1/portfolios/dev/trigger-catchup` | `userId`, `mode` | Dev catch-up | job kick |

### Example history envelope (shape)

```json
{
  "points": [ { "snapshotDate": "2026-10-01", "totalUserWealth": 100000.0, "portfolios": [] } ],
  "historyStatus": "READY",
  "phase": "READY",
  "startedAt": null,
  "historyFrom": "2026-09-01",
  "historyTo": "2026-10-01",
  "coverageDays": 30
}
```

`timeFrame` codes (service): `1D`, `1W`, `1M`, `3M`, `6M`, `1Y`, `3Y`, `5Y`, `YTD`, `ALL` — see `PortfolioSnapshotService`.

---

## B. am-market (`marketData.baseUrl`)

**Not** am-core-services am-analysis. Same URL path prefix, different host.

| Method | Path | Query | When UI calls | Response |
|--------|------|-------|---------------|----------|
| GET | `/v1/analysis/historical-charts` | `symbols` (required, CSV), `range` (default `1D`), `isIndexSymbol` (default true) | Overlay indices | `HistoricalDataResponseV1` |
| GET | `/v1/analysis/historical-charts/{symbol}` | `range`, `isIndexSymbol` | Legacy alias | same |

### Example (shape)

```json
{
  "data": {
    "NIFTY 50": {
      "tradingSymbol": "NIFTY 50",
      "dataPoints": [ { "time": "...", "open": 1, "high": 1, "low": 1, "close": 1, "volume": 0 } ]
    }
  }
}
```

UI plots **`close`** (also accepts `price` / `lastPrice` / `value` in parser).

Default symbols: `NIFTY 50`, optionally `NIFTY BANK`, `SENSEX` (`OverlayChartIds`).

---

## C. am-analysis — core-services (`analysis.baseUrl`)

Controller: `com.am.analysis.controller.AnalysisController` → `/v1/analysis`.

| Method | Path | Query | modern-ui | Role |
|--------|------|-------|-----------|------|
| GET | `/v1/analysis/dashboard/summary` | — | `getSummary` / dashboard stream | **Live KPI cards** (not overlay Y) |
| GET | `/v1/analysis/dashboard/performance` | `timeFrame` default `1M` | `getPerformance` / `historyStreamProvider` | **Legacy** `chartData[]` — orphaned for comparison chart |
| GET | `/v1/analysis/{type}/{id}/performance` | `timeFrame` | `am_analysis` real service | Entity performance widgets |

### Performance series shape (legacy)

```json
{
  "portfolioId": "...",
  "timeFrame": "1M",
  "totalReturnPercentage": 1.2,
  "totalReturnValue": 100,
  "chartData": [ { "date": "2026-10-01", "value": 100000 } ]
}
```

`value` = total portfolio market value that day (analysis calculator).

Also used near dashboard (not overlay chart): portfolio-overviews, top-movers, recent-activity, allocation — see AnalysisController.
