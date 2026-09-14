# Portfolio session pricing — TODO (execution cockpit)

**Current phase:** `D-READY`  
**Plan rating:** 10/10 — [plan.md](./plan.md)  
**Rule:** Mark `[x]` only after phase checklist in [PHASED-IMPLEMENTATION.md](./PHASED-IMPLEMENTATION.md).  
**Refs:** [plan.md](./plan.md) · [API-CONTRACTS.md](./API-CONTRACTS.md) · [PHASED-IMPLEMENTATION.md](./PHASED-IMPLEMENTATION.md)

**PROD:** `https://am.asrax.in/portfolio`  
**Calendar:** `https://am.asrax.in/market-data/v1/market-calendar/status?exchange=NSE`  
**Golden portfolioId:** `f969745c-f492-4b86-88ed-6588e9f28bb3`  
**Postman env:** `~/.asrax/postman/AM-Portfolio-PROD-LOCAL.postman_environment.json`  
**API MCP:** Postman (`runCollection`)  
**Branch:** `hotfix/portfolio-session-pricing`  
**Max Phase D iterations:** 5

---

## P0 — Branch / docs

- [x] Docs pack 10/10 (plan, TODO, PHASED, API-CONTRACTS)
- [x] Branch `hotfix/portfolio-session-pricing` from holdings-movers tip (am-portfolio)
- [ ] Branch for am-modern-ui when Phase U starts

---

## P1 — Calendar client + CashSessionClock

- [x] status/timings endpoints in `application.yml`
- [x] `MarketCalendarClient` (≤200ms timeout, 60s status cache)
- [x] `CashSessionClock` fail-closed
- [x] Replace clocks: holdings, Redis TTL, Kafka consumer, **intraday**
- [x] Unit: holiday/weekend/open/fail-closed

---

## P2 — Freshness

- [x] `sessionDate` on holdings (+ summary meta)
- [x] `asOf` from price timestamps; `LIVE` only open + tick age ≤60s
- [x] Unit: LIVE / AS_OF / sessionDate (holdings + clock tests)

---

## P3 — Closed pricing + day % / movers

- [x] Closed getMarketData: Mongo/Redis → OHLC miss; no 6h reject when closed
- [x] Session close vs prior close; historical lookback if collapsed
- [x] Movers same path (shared getMarketData)
- [ ] Unit: non-zero closed day % when prior differs (assert in Phase D)

---

## P4 — Summary + advanced coherence

- [x] Summary **reprice on cache hit** (like holdings overlay)
- [x] Freshness fields on summary
- [x] Advanced uses shared clock-aware MarketData path
- [x] Suite green → ready for D

---

## D — Prod deploy + Postman MCP loop

- [ ] Build/push image; record tag
- [ ] Deploy prod; Ready; health 200
- [ ] Calendar status recorded
- [ ] Matrix: holdings cold/warm, summary, advanced movers, 3-symbol parity ([API-CONTRACTS.md](./API-CONTRACTS.md))
- [ ] Gates pass or 5 iters documented

### Phase D log

| Iter | Image tag | Holdings cold ms | Holdings warm ms | freshness | sessionDate | summary OK | movers g/l | Pass? | Notes |
|------|-----------|------------------|------------------|-----------|-------------|------------|------------|-------|-------|
| 1 | | | | | | | | | |
| 2 | | | | | | | | | |
| 3 | | | | | | | | | |
| 4 | | | | | | | | | |
| 5 | | | | | | | | | |

---

## U — modern-ui

- [ ] sessionDate DTO/mapper; As of `{date} HH:mm`
- [ ] Overview freshness
- [ ] Gate 1D chart poll
- [ ] PR

---

## X — Exit

- [ ] Closed-day + open-day gates green (or deferred closed-day documented)
- [ ] PRs linked; Current phase = `DONE`
