# OPS — portfolio chart

## Status cheat sheet

| Symptom | Likely cause | Check |
|---------|--------------|-------|
| Cloudflare / empty app | dig Kind / tunnel down | Docker engine; `am-dev.asrax.in` 200 |
| Chart syncing forever | Catch-up stuck BUILDING | `GET /portfolio/v1/portfolios/history/status`; job logs |
| Wealth flat / Paper-only zeros | Snapshots not built or wrong books | history `points`; catch-up FULL |
| Overall 1D flat / LTP-only | Empty `stock_price_history` + charts timeout | Warm admin (below); not Kafka-on alone |
| Indices missing, wealth OK | Market historical-charts fail **or wrong base URL** | Call `/market/v1/analysis/historical-charts` not `/analysis/...` |
| KPI cards empty, chart OK | am-analysis summary | `/analysis/v1/analysis/dashboard/summary` |
| Expecting performance line from analysis | Wrong mental model | Overlay does **not** use `dashboard/performance` |

## Base-URL footgun

Path `/v1/analysis/historical-charts` is implemented on **am-market**.  
modern-ui uses `marketApiClientProvider` → `config.api.marketData.baseUrl` (fallback analysis URL — misconfig sends index calls to core analysis and breaks overlays).

| Concern | Correct prefix |
|---------|----------------|
| Wealth history | `/portfolio` |
| Index charts | `/market` |
| Dashboard summary / legacy performance | `/analysis` |

## Catch-up / EOD

- Dev: `POST /v1/portfolios/dev/trigger-catchup?userId=&mode=FULL|GAP|MERGE_BROKER`
- EOD: `POST /v1/portfolios/trigger-snapshot`
- Code: `PortfolioHistoryJobService`, `SnapshotCatchUpService`, `PortfolioHistoryScheduler`

## Intraday candles empty (`stock_price_history`)

Kafka `KAFKA_ENABLED=true` only starts the consumer. History ticks are written **during market hours** when `am-stock-price-update` messages arrive. Dig often has an empty topic / after-hours → Mongo empty → `/intraday` falls back to full-book `historical-charts` and can timeout (flat LTP).

| Action | Endpoint | What it does |
|--------|----------|--------------|
| **Warm session candles into portfolio Mongo** | `POST /portfolio/v1/portfolios/admin/intraday-candles/warm?universe=NIFTY_500` | Chunk-fetches am-market `historical-charts?range=1D` for Nifty 500 (or `symbols=RELIANCE,TCS`) and upserts `stock_price_history`. Header `X-Internal-Secret`. Returns `202` + `jobId`. |
| Poll warm job | `GET /portfolio/v1/portfolios/admin/intraday-candles/warm/{jobId}` | Same secret; status / ticks written |
| Live stream (market hours) | `POST /market/v1/admin/scheduler/market/open` | Upstox WS → Kafka LTP → minute ticks (not a full-day backfill) |
| Daily history (Influx) | `POST /market/v1/admin/sync/historical` | **Daily** OHLC into am-market — **does not** fill portfolio `stock_price_history` |

Dig smoke: warm a few symbols first (`symbols=RELIANCE,INFY,TCS`), confirm Mongo ticks, then full `NIFTY_500` if market charts are healthy.

**Holdings gap (soggy `/intraday`):** Nifty 500 warm misses ETFs/non-index names. If `09:15→09:20` drops tens of %, pull holdings symbols and warm them:

```text
GET /portfolio/v1/portfolios/holdings?portfolioId=<id>
POST /portfolio/v1/portfolios/admin/intraday-candles/warm?symbols=<CSV of equity/ETF tickers>
```

Also clear Redis keys `portfolio:intraday:v12:<userId>:*`. Code seeds LTP into `lastKnown` for uncandled holdings so missing candles cannot price as 0.

## OpenAPI gap

`GET /v1/portfolios/history/status` exists in `PortfolioController` but may be missing from published `am-specs` OpenAPI / MCP — harvest in a follow-up (PLAN P1).

## Dig probe examples

```text
GET https://am-dev.asrax.in/portfolio/v1/portfolios/history?timeFrame=1M
GET https://am-dev.asrax.in/portfolio/v1/portfolios/intraday
GET https://am-dev.asrax.in/market/v1/analysis/historical-charts?symbols=NIFTY%2050&range=1M
GET https://am-dev.asrax.in/analysis/v1/analysis/dashboard/summary
POST https://am-dev.asrax.in/portfolio/v1/portfolios/admin/intraday-candles/warm?symbols=RELIANCE,INFY
  Header: X-Internal-Secret: <app.jwt.internal-secret>
GET https://am-dev.asrax.in/portfolio/v1/portfolios/admin/intraday-candles/warm/{jobId}
```

Evidence pack (historical): [../chart-mcp-evidence/REPORT.md](../chart-mcp-evidence/REPORT.md).

## Local Kind

Operator kubeconfig: `~/.asrax/kubeconfig.dev.local` (context on `am-dev-apps`).  
If UI shows 530: Docker engine + cloudflared / CoreDNS (see infra KUBEAPI_PROXY notes).

## Dig verify — Phase 1 (Gmail → charts) — backend gate before UI

1. **Postman (Phase 0)** — `am-specs/postman`: market `historical-data` (CSV + from/to/interval=day); portfolio `history?timeFrame=1Y`, `history/status`, `intraday`, `{id}/history`.
2. **Gmail extract** — note `metadata.as_of_date`.
3. Poll `GET /v1/portfolios/history/status` until READY (or BUILDING_90D then later).
4. `GET /v1/portfolios/intraday` — session series.
5. `GET /v1/portfolios/history?timeFrame=1Y` — daily points from email asOf (within max-backfill 730d).
6. Optional single book: `GET /v1/portfolios/{portfolioId}/history?timeFrame=1Y`.

**UI Phase 2 blocked** until steps 3–5 pass on dig.
