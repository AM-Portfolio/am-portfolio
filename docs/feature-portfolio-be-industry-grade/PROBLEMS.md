# Root problems v2 FINAL (ranked)

Evidence: code, in-cluster curls, PROD Loki, Grafana `tech-am-portfolio`, basket Discover latency SoT.

**Plan score journey:** v1 draft **7/10** → v2 FINAL **10/10** (see [plan.md](./plan.md)).

---

## P0 — Period heatmap: empty hist merge + live-as-period (CRITICAL)

### Symptom
Period TF returns tiles but % = live day %. Cold hist 60–90s. Loki: `Merged 0/111` while each hist request **Completed successfully**.

### Co-equal root causes (all must be fixed)

| ID | Cause | Evidence |
|----|--------|----------|
| **P0-L** | Facade assigns **live** into `prefetchedMarketData` when hist empty | `PortfolioAnalyticsFacade` “falling back to live… for structure” + `prefetchAttempted=true` |
| **P0-W** | Wave join uses `getNow(empty)` after `orTimeout` → discards in-flight OK chunks | `MarketDataService.fetchHistoricalChunks` |
| **P0-C** | Convert may yield null (unsorted bars / bind) | convert-null logs; need real JSON fixture |
| **P0-B** | 12× serial micro-chunks | ~6s × 12 ≈ 70s even when market is fine |

In-cluster curl proves market START_END works in 1–6s for 2–10 symbols.

### Best fix
1. Never live-as-period primary.  
2. Fix wave await.  
3. Sort + fixture convert.  
4. One large hist call.  
5. Cache non-empty heatmap only.

---

## P1 — Movers / holdings day % ≠ market UI

### Best fix
Day % = `(lastPrice − previousClose) / previousClose`. Delete `ohlc.open` day fallback in `TopMoverUtils` + `PortfolioCalculator`. Repair missing prevClose with hist prior **close** only. Keep movers on live prefetch.

Market `GET /v1/analysis/movers` is **index** scoped — compare symbol-level OHLC, not that list 1:1.

---

## P2 — After-hours inconsistency

### Best fix
Reuse **CashSessionClock** + **finalizeMarketData** (session-pricing pack already shipped). Do not invent `SessionPriceResolver`. Close gaps at call sites only.

---

## P3 — Latency across APIs

### Best fix
Large hist batches; Redis-first OHLC; calendar 200–500ms; chunked securities batch-search; isolate intelligence hist timeout.

---

## P4 — Basket Explorer shows 3

### Best fix
PROD catalog upsert (seed-on-empty left 3 themes). Versioned seed; remove `.limit(3)`. FE Top picks remain 3 by design.

---

## P6 — Basket Preview slow (NEW)

### Symptom
Click Preview → multi-second spinner.

### Root cause
Discover strips composition → FE seed dead → FULL `POST /v1/basket/preview`: serial sector enrich (1–3s) → ETF enrich → prices for 50–150 symbols → optional hist repair.

### Best fix
Skip sector enrich when coverage high; warm ETF L2 from Discover; one price union; no hist on Preview; optional PREVIEW_LITE / seed.

**SLO:** warm ≤800ms p95; cold ≤2s p95.

---

## P7 — FE heatmap / allocation wipe

preferNonEmpty + TF generation cancel; smoke after A1/A2.

---

## P8–P12 — Hardening

Dual beans · FailOpen stale day% · repair interval `1D` · calendar timeout · hist CB tuning.

---

## Delivery priority

A1 (P0-L/W/C) → A2 (P0-B) → B1 → B2 → C1 → D1 → **D2 Preview** → E1 → X
