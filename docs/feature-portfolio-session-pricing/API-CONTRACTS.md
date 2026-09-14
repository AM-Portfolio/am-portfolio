# API contracts — Portfolio session pricing

Assert these in unit tests and Phase D (Postman MCP).

## Dependency: market calendar

`GET {MARKET_DATA}/v1/market-calendar/status?exchange=NSE`

| Field | Closed day example | Open day example |
|-------|--------------------|------------------|
| `open` | `false` | `true` |
| `reason` | `WEEKEND` / `HOLIDAY` / `OUTSIDE_SESSION` | `OPEN` |
| `sessionStart` / `sessionEnd` | `09:15:00` / `15:30:00` | same |

## Holdings

`GET /v1/portfolios/holdings?portfolioId={id}`

```json
{
  "asOf": "2026-09-12T15:29:58",
  "priceFreshness": "AS_OF",
  "priceSource": "TICK",
  "sessionDate": "2026-09-12",
  "equityHoldings": [
    {
      "symbol": "TMCV",
      "currentPrice": 466.3,
      "todayGainLossPercentage": 14.81
    }
  ]
}
```

| Assert | Closed | Open + fresh ticks |
|--------|--------|--------------------|
| `priceFreshness` | `AS_OF` | `LIVE` |
| `sessionDate` | last NSE session | today’s session date |
| `asOf` | max price timestamp (not wall clock alone) | tick timestamp |
| Spot vs OHLC | within 1 tick of last session close | within 1 tick of LTP |

## Summary

`GET /v1/portfolios/summary?portfolioId={id}`

Must expose the same freshness meta (on summary root or nested `priceMeta`):

- `asOf`, `priceFreshness`, `sessionDate`
- `todayGainLoss` / `%` consistent with sum of holdings day P&L after reprice

**Critical:** cache hit must reprice like holdings overlay (no stale KPI blob).

## Advanced (movers)

`POST /v1/analytics/portfolio/{id}/advanced`

Body must include:

```json
{
  "featureToggles": {
    "includeMovers": true,
    "includeHeatmap": false,
    "includeSectorAllocation": false,
    "includeMarketCapAllocation": false
  },
  "featureConfiguration": { "moversLimit": 10 }
}
```

| Assert | Rule |
|--------|------|
| `analytics.movers` | non-null when toggled |
| gainers/losers | populated when book has session movers |
| sample % | matches holdings `todayGainLossPercentage` for same symbol (±0.05) |

No dedicated movers REST — advanced only.

## Intraday

`GET /v1/portfolios/{id}/intraday` (and all-portfolio variant)

| Calendar | Behavior |
|----------|----------|
| Closed / holiday | Do not pretend live session; last-session or empty candles per product rule; no weekday-only skip |
| Open | Historical charts + current prices as today |

## Latency (Phase D)

| Call | Gate |
|------|------|
| Holdings cold | ≤ 2000 ms client |
| Holdings warm | prefer ≤ 500 ms client; if CF RTT ~650 ms, server think (TTFB − TLS) ≤ 500 ms |
| Advanced movers | ≤ 15 s (+ movers isolation) |
| Calendar client (internal) | timeout ≤ 200 ms; cached 60 s |
