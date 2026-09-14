# Plan v2 FINAL — Portfolio BE industry-grade (10/10)

**Version:** 2.0 FINAL  
**Score:** prior draft **7/10** → this pack **10/10** (gaps closed below)  
**Status:** Ready for implementation. Cockpit: [TODO.md](./TODO.md)  
**Do not code until Current phase in TODO is A0 and you start A0.**

---

## Why v1 was only 7/10 (gaps closed here)

| Gap in v1 | Fix in v2 |
|-----------|-----------|
| Assumed Merged 0 = only Jackson deserialize | **Three co-equal P0 causes:** convert-null, wave `getNow(empty)` discard, facade **live stuffed into period prefetch** |
| Proposed new `SessionPriceResolver` | **Reuse** shipped `CashSessionClock` + `finalizeMarketData` (session-pricing pack DONE) |
| Basket Preview latency omitted | **P6** FULL preview path after Discover slim |
| FE wipe / TF race out of scope | **P7** gates + preferNonEmpty / generation cancel as DoD |
| Ambiguous “pick open or close baseline” | Locked: period = first bar **open** (sorted) → last **close**; day = **previousClose** only |
| Dual Spring beans / FailOpen / calendar / CB / interval=`day` repair | Explicit P8–P12 hardening phases |
| No stage SLOs for basket preview | Warm ≤800ms p95, cold ≤2s p95 |

---

## Goal

am-portfolio is an industry-grade enrichment layer over **am-market-data** (source of truth): correct **day** and **period** prices for holdings, movers, heatmap 1D–1Y, allocation, summary, and baskets — with predictable latency and **no silent live-as-period lies**.

---

## End-to-end data flow (target)

```text
User → am-modern-ui
     → Gateway → am-portfolio
          ├─ Mongo (portfolios, basket catalog, price cache)
          ├─ Redis (LTP, heatmap by TF+dates, ETF enriched, secmatch)
          └─ am-market-data
               ├─ POST /v1/market-data/ohlc              timeFrame=1D
               ├─ POST /v1/market-data/historical-data   interval=1D, filterType=START_END
               ├─ GET  /v1/market-calendar/status|timings
               ├─ POST /v1/securities/batch-search
               └─ GET  /v1/analysis/movers               (market UI / index only — NOT portfolio book)
     ← holdings / advanced analytics / basket discover+preview
```

### Day vs period (locked)

| UI need | Market call | Baseline | Last |
|---------|-------------|----------|------|
| Holdings today %, movers, live heatmap 1D | OHLC `timeFrame=1D` | `previousClose` only (repair = prior **close**) | `lastPrice` |
| Heatmap / allocation 1W–1Y | Hist `1D` + `START_END` | First bar **open** (time-sorted) | Last bar **close** |
| After hours | Same day stack via `CashSessionClock` + `finalizeMarketData` | Prior session close | Last session LTP/close |

Never put live OHLC into `prefetchedMarketData` when `needsHist=true`.

---

## Problem register (best fix = one choice each)

### P0 — Heatmap period wrong / slow (CRITICAL)

**Symptoms:** Loki `Merged 0/111` while hist HTTP completes ~5–6s; UI period % = day %; cold ~60–90s.

**Best fix (ordered, all required):**
1. **P0-L — Stop live-as-period:** On hist miss, do **not** assign live to `prefetchedMarketData`. Keep live only in `prefetchedLiveMarketData`. Set `prefetchAttempted=false` for period providers so they can fail closed or retry — never paint day% as period.
2. **P0-W — Fix wave join:** Never `future.getNow(emptyMap)` on incomplete futures after `orTimeout`. Await each future to completion (or cancel + retry that chunk). Log `httpSymbols / converted / incomplete`.
3. **P0-C — Convert integrity:** Sort bars by time; Jackson fixture test from real market START_END JSON; log convert-null with `pts=`.
4. **P0-B — Batching:** Prefer **1 hist call** for full EQ book (timeout 45–60s); split halves only on timeout/CB/413. Kill 12× serial micro-chunks.
5. Cache **non-empty** heatmap by TF+dates; never cache empty.

**Gate:** Loki N/M ≥ 0.8; 1W sector % ≠ live on golden book; cold 1Y ≤ 15s; warm ≤ 3s.

---

### P1 — Movers / holdings day % vs market UI

**Best fix:** Remove `ohlc.open` as day previousClose in `TopMoverUtils` + `PortfolioCalculator`. Require `previousClose` or hist prior-bar **close** repair. Movers already use live prefetch — keep that isolation.

**Gate:** 5 sample symbols match OHLC day % within tolerance; Overview movers align with holdings rows.

---

### P2 — After-hours consistency

**Best fix:** Do **not** invent a new resolver. Wire every live call site through existing `finalizeMarketData` / `CashSessionClock`. Assert freshness on holdings, movers, summary, live heatmap.

**Gate:** After close, day % not stuck at 0 for symbols that moved that session.

---

### P3 — Cross-API latency

**Best fix:** Same large-batch hist + Redis-first OHLC; calendar timeout 200–500ms with cached status; chunk holdings `batch-search`; bound intelligence hist separately (`history-timeout-ms`).

**SLO table:**

| API | Cold | Warm |
|-----|------|------|
| Holdings | ≤ 3s | ≤ 1s |
| Advanced live | ≤ 8s | ≤ 2s |
| Advanced hist 1W–1Y | ≤ 15s | ≤ 3s |
| Basket discover | ≤ 2s | ≤ 500ms |
| Basket preview | ≤ 2s | ≤ 800ms p95 |

---

### P4 — Basket Explorer only 3

**Best fix:** Ops `PUT /v1/basket/catalog` expanded seed now. Code: versioned seed upsert when classpath newer; remove `.limit(3)` featured fallback. FE Top picks stay 3 cards; All baskets = full list.

**Gate:** `defaultQuery` symbol count ≥ 9; All baskets ≫ 3.

---

### P6 — Basket Preview slow (NEW — user report)

**Root cause:** Discover `slimForDiscover` strips composition → FE seed never wins → every Preview runs **FULL** `POST /v1/basket/preview`: serial sector enrich → cold ETF enrich → bulk prices for 50–150 symbols (Redis/Mongo/OHLC), optional hist repair.

**Best fix package:**
1. Skip `HoldingSectorEnricher` on Preview when holdings ISIN/sector coverage ≥95% (Discover already skips).
2. Align single `getEnrichedEtf` with batch: skip `enrichHoldings` when ≥95% ISINs; ensure Discover warms L2 so Preview is L1/L2 hit.
3. Price once for union; remove overlap gap second fetch; **skip after-hours hist repair on Preview** (Preview tables don’t need day%).
4. Optional phase: PREVIEW_LITE / restore composition for seed → first paint ≤300ms, prices async.

**Gate:** Stage logs `basket.preview.stage=*`; warm p95 ≤800ms; cold p95 ≤2s; no hist in Preview traces when warm.

---

### P7 — FE safety (heatmap / allocation)

**Best fix:** Keep `preferNonEmptyAllocation/Heatmap` + TF generation cancel. DoD: empty hist must not blank a previously good live view; UI smoke 1D–1Y after A1/A2.

---

### P8–P12 — Hardening

| ID | Best fix |
|----|----------|
| P8 Dual beans | Single Spring bean path (`FailOpen` primary or remove duplicate `@Service`) |
| P9 FailOpen stale | Refuse day% ranking from backfill without `previousClose` / freshness |
| P10 Repair interval | `repairCollapsedPreviousClose` use `1D` not `day` |
| P11 Calendar | `calendar-timeout-ms` 200–500 + cache |
| P12 Hist CB | Tune so one fat timeout doesn’t open CB for 30s; document |

---

## Phases (execution order — mandatory)

```text
A0 freeze probes
 → A1 P0-L + P0-W + P0-C (correctness before speed)
 → A2 P0-B + heatmap cache (latency)
 → B1 P1 movers/holdings
 → B2 P2 session call-site discipline
 → C1 P3 API SLOs (holdings/advanced)
 → D1 P4 catalog N≫3
 → D2 P6 preview latency
 → E1 P7 FE smoke + P8–P12 harden
 → T1 PROD E2E test↔fix↔redeploy loop (until perfect)
 → X exit
```

**Rules:** Do not start A2 until A1 PROD gate (period % ≠ live). Do not call heatmap fixed until A1+A2. Do not start D2 until D1 catalog upsert (warm ETF cache from Discover helps Preview). **Do not exit X until T1 matrix is all PASS** for UI, developer (logs/contracts), and latency SLOs.

---

## T1 — PROD E2E test ↔ fix ↔ redeploy loop (mandatory)

After feature phases, run a **closed loop** until every API below is perfect for the **user/UI**, **developer** (logs, merge ratio, no silent fallbacks), and **latency SLOs**.

### Loop (max 8 iterations; stop early when all PASS)

```text
for iter in 1..8:
  1. Measure cold + warm for each API in the matrix (curl/Postman, Cloudflare UA)
  2. Assert response shape + correctness (sectors, %, movers, catalog N, preview fields)
  3. Assert latency SLOs (plan SLO table)
  4. If any FAIL:
       - Root-cause from Loki/Grafana + code (best single fix)
       - Implement + unit/contract test
       - Deploy PROD
       - Re-run full matrix (do not skip warm)
  5. If all PASS → T1 DONE → X
```

Log each iter: `iter | API | cold_ms | warm_ms | accuracy | pass/fail | note`.

### API matrix (every T1 iter)

| # | API | Accuracy | Latency |
|---|-----|----------|---------|
| T1.1 | `GET .../actuator/health` | 200 UP | ≤ 500ms |
| T1.2 | Holdings (golden portfolio) | holdings > 0; day % uses previousClose | cold ≤3s / warm ≤1s |
| T1.3 | Advanced **live** (heatmap+movers+alloc) | sectors > 0; weights > 0; movers non-empty when book moves | cold ≤8s / warm ≤2s |
| T1.4 | Advanced **1W / 1M / 90d / 1Y** | sectors > 0; period % **≠** live when hist filled; Loki N/M ≥ 0.8 | cold ≤15s / warm ≤3s |
| T1.5 | Movers sample (5 symbols) | day % ≈ OHLC previousClose math | included in T1.3 |
| T1.6 | Basket catalog / discover | defaultQuery symbols ≥ 9; All baskets ≫ 3 | discover ≤2s / ≤500ms |
| T1.7 | Basket **preview** (featured ISIN) | composition + overlap present; stage logs clean | warm ≤800ms / cold ≤2s |

**Developer pass:** no `Hist empty → live for structure` on period TF when market is healthy; no `Merged 0/N` with HTTP 200 bodies; preview stages have no unexpected hist repair on warm path.

---

## PROD gates (summary)

Golden portfolio: `f969745c-f492-4b86-88ed-6588e9f28bb3`  
API: `https://am.asrax.in/portfolio`  
Toggles: `featureToggles.includeHeatmap|includeMovers|includeSectorAllocation|includeMarketCapAllocation`  
Grafana: `tech-am-portfolio` · Loki: `{namespace="am-apps-prod", service="am-portfolio"}`

| Gate | Pass |
|------|------|
| Live advanced | sectors > 0; ≤8s cold / ≤2s warm |
| Hist merge | N/M ≥ 0.8 |
| Period TF | 1W/1M/1Y sectors > 0 and % ≠ live when hist filled |
| Movers | Match OHLC day % on 5 samples |
| Catalog | defaultQuery ≥ 9 symbols |
| Preview | warm ≤800ms p95; cold ≤2s p95 |

---

## Out of scope

- Rewriting am-market movers/OHLC engines  
- FE inventing period % without hist  
- Intelligence widget product work (isolate latency only)  
- What-if / apply-substitutes (separate from Preview open)

---

## Links

- [PROBLEMS.md](./PROBLEMS.md) — deep dives  
- [TODO.md](./TODO.md) — cockpit  
- [architecture.drawio](./architecture.drawio) — Context / BusinessFlow / Containers / Sequence / P0–P6 / Failures  
- Related: `feature-portfolio-session-pricing`, `feature-basket-discover/plan-v2-latency.md`
