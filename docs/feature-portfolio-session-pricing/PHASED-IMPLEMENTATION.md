# Phased implementation — Portfolio session pricing

Execution order for [plan.md](./plan.md). Update [TODO.md](./TODO.md) after each phase.

**CLI:** `python -m am_cli.main` if `am` not on PATH.  
**API testing:** Postman MCP (`user-postman` → `runCollection` / request tools) against PROD env + JWT. Prefer MCP over ad-hoc chat paste. Curl is fallback when MCP cannot run a collection.

---

## Phase P0 — Branch and docs

1. Pull `origin/main` in am-portfolio (and am-modern-ui when UI starts).
2. Create `hotfix/portfolio-session-pricing`.
3. Keep this docs folder as SoT; do not edit unrelated plans.

**Done when:** branch exists; docs present; TODO Current phase = `P1-CALENDAR`.

---

## Phase P1 — Calendar client + CashSessionClock (backend)

**Code**

1. Extend `application.yml` `market-data.api` with:
   - `status-endpoint: /v1/market-calendar/status`
   - `timings-endpoint: /v1/market-calendar/timings`
2. Implement `MarketCalendarClient` calling those paths on `market-data.api.base-url`.
3. Implement `CashSessionClock` with 60s status cache; `lastSessionDate()` walks timings.
4. Fail-safe: calendar HTTP error → closed (`CALENDAR_UNAVAILABLE`).
5. Replace local clocks in:
   - `PortfolioHoldingsService`
   - `PortfolioMarketDataRedisService.isCashMarketHours` (delegate to clock)
   - `StockPriceUpdateConsumerService`
   - `PortfolioIntradayService` (holiday-aware; not weekday-only)

**Tests:** holiday/weekend/open/fail-closed unit tests.

**Done when:** unit tests green; no remaining hardcoded Mon–Fri-only session checks in those four call sites.

---

## Phase P2 — Freshness contract (backend)

1. Add `sessionDate` to `PortfolioHoldings`.
2. Fix `stampFreshness` / overlay: `asOf` from max price timestamp; `LIVE` only if open and tick age ≤60s.
3. Unit tests for LIVE / AS_OF / sessionDate.

**Done when:** holdings response contract documented in tests; false Live impossible when clock closed.

---

## Phase P3 — Closed-path pricing + day % / movers (backend)

1. Harden `MarketDataService.getMarketData` when closed (Mongo/Redis first; OHLC miss-only).
2. Closed day % = session close vs prior close; historical lookback if prevClose missing.
3. Ensure movers use same `getMarketData` path.

**Done when:** unit tests show non-zero closed day % when prior close differs; movers not empty for fixture with movers.

---

## Phase P4 — Summary + advanced coherence (backend)

1. **Summary cache hit must reprice** holdings before aggregating today P&L (same overlay as holdings). Critical gap from API audit.
2. Attach `asOf` / `priceFreshness` / `sessionDate` on summary meta ([API-CONTRACTS.md](./API-CONTRACTS.md)).
3. Advanced prefetch uses clock-aware `getMarketData`; movers sample % matches holdings.
4. Intraday already switched to `CashSessionClock` in P1 — smoke holiday closed.

**Done when:** P1–P4 unit suite green; ready to deploy.

---

## Phase D — Prod deploy + Postman MCP API verify loop (mandatory)

Run **only after P1–P4 backend is complete and committed**. Max **5** iterations.

### D.1 Build and push

1. Build image from hotfix tip (`am deploy` or Docker).
2. If org GHCR `permission_denied`, tag/push `ghcr.io/sahim99/am-portfolio:<tag>` (existing workaround).
3. Record image tag in TODO Phase D log.

### D.2 Deploy prod

1. `am deploy --env prod` **or** pause Argo auto-sync on `am-portfolio-prod`, then `kubectl set image` / helm with the pushed tag.
2. Wait rollout Ready: `kubectl -n am-apps-prod rollout status deployment/am-portfolio`.
3. Health: `GET https://am.asrax.in/portfolio/actuator/health` → 200.

### D.3 Calendar sanity (market-data, no code change)

1. `GET https://am.asrax.in/market-data/v1/market-calendar/status?exchange=NSE`
2. Record `open`, `reason`, `sessionStart`, `sessionEnd`.
3. If `open=false`, expect portfolio `priceFreshness=AS_OF`. If `open=true`, expect LIVE only with fresh ticks.

### D.4 API test matrix (Postman MCP)

Use Postman MCP against PROD collection/environment (JWT + `portfolioId=f969745c-f492-4b86-88ed-6588e9f28bb3`).

| # | API | Assert accuracy | Assert speed |
|---|-----|-----------------|--------------|
| 1 | `GET /v1/portfolios/holdings?portfolioId=…` cold | 200; equityHoldings non-empty; `priceFreshness` matches calendar; `sessionDate` present when AS_OF; spot sample prices sane | cold ≤ 2000ms (client; note CF RTT) |
| 2 | Same holdings warm (2nd call) | Same freshness; prices stable/consistent with overlay | warm ≤ 500ms preferred; if client >500ms due to RTT, check TTFB − TLS ≈ server ≤500ms |
| 3 | `GET /v1/portfolios/summary?portfolioId=…` | Freshness meta present; today P&L matches holdings after reprice | p95 reasonable vs baseline |
| 4 | `POST /v1/analytics/portfolio/{id}/advanced` with `featureToggles.includeMovers=true` | movers non-null; gainers/losers populated when book has session moves; % matches holdings day % for sample | completes ≤ 15s facade (+ movers isolation) |
| 5 | Sample 3 symbols | holdings `currentPrice` vs market-data OHLC/last session within 1 tick | n/a |

**Pass gates (closed day)**

- No `LIVE` when calendar `open=false`
- `sessionDate` = last NSE session
- Movers not sparse if session had movers
- Latency gates as above

**Pass gates (open day)**

- `LIVE` when ticks ≤60s old
- Overlay warm path without full holdings rebuild

### D.5 Loop

```text
for iter in 1..5:
  deploy
  Postman MCP run / curl matrix
  if all gates pass → stop; mark D done
  else fix root cause (calendar, freshness, OHLC miss, movers) → commit → next iter
```

Fill **Phase D log** in TODO.md every iteration.

**Done when:** gates pass or 5 iterations exhausted with documented remaining gaps.

---

## Phase U — modern-ui (after D backend gates)

1. Wire `sessionDate` through DTO/mapper; As-of label includes date.
2. Overview freshness line.
3. Gate 1D chart poll.
4. PR am-modern-ui.

**Done when:** UI PR opened; manual smoke on closed/open labels.

---

## Phase X — Exit

1. PRs linked; TODO Current phase = `DONE`.
2. Optional later: market-data prev-close TTL only if long-weekend misses remain.

---

## API contracts (response fields to verify)

### Holdings (additions)

```json
{
  "asOf": "2026-09-12T15:30:00",
  "priceFreshness": "AS_OF",
  "priceSource": "TICK",
  "sessionDate": "2026-09-12",
  "equityHoldings": []
}
```

### Calendar status (dependency)

```json
{
  "exchange": "NSE",
  "open": false,
  "reason": "WEEKEND",
  "sessionStart": "09:15:00",
  "sessionEnd": "15:30:00"
}
```
