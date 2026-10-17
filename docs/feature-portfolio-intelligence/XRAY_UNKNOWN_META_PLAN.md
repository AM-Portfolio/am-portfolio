# Portfolio X-Ray — Unknown sector / bad cap — analysis & fix plan

**Branch:** `fix/xray-unknown-sector-meta`  
**Book:** Zerodha `5dd51534-1dd3-4377-996f-36a21c2cb6e0` (user `rjsahim7001@…`)  
**Date:** 2026-10-17  
**Status:** DEV verify complete (Sector/Industry/latency pass; Cap honest Unknown — Large/Mid needs market-data seed)

### Baseline (Phase 0 — pre-fix)

| Metric | Value |
|--------|-------|
| Sector Unknown | ~99.6% |
| Industry Unknown | ~99.6% |
| Cap | 100% MICRO_CAP (false default) |
| Book (screenshots) | Zerodha ~₹8.9L / 111 names |

### After (Phase 4 — 2026-10-17, image `39032038126`)

Measured book (token owner Sahim): Zerodha `552e95ce-30a3-4610-a5e7-4c3c4b6785b0` (111 equities, totalValue ≈ 894695 — same shape as screenshot book).

| Gate | Result |
|------|--------|
| Sector Unknown | **17.81%** (pass &lt; 20%) — top: Consumer Disc 19.4%, Financials 18.8%, IT 10.7%, … |
| Industry Unknown | **17.81%** (pass) |
| Cap | **0% Micro** / **100% UNKNOWN** — honesty pass; Large/Mid **not** filled (market batch-search returns null `marketCapType`/`marketCapValue` for TCS etc.) |
| Cold latency | **3199 ms** (pass ≤ 5000) |
| Warm latency | **1760–1827 ms** (pass ≤ 2000) |
| MARKET_DATA_API_URL | in-cluster OK |

**Cap follow-up (market-data):** seed `metadata.market_cap_type` / `market_cap_value` on securities Mongo (or fix indices APIs). Portfolio correctly refuses to invent Micro Cap.

---

## 1. What the UI shows (reproduced via API)

| Widget tab | Observed | API source |
|------------|----------|------------|
| Sector | ~99.6% Unknown; tiny Financial Services + Industrials | `POST /v1/analytics/portfolio/{id}/intelligence` → `xray.sectorWeights` |
| Industry | ~99.6% Unknown; tiny FS + Construction | same → `xray.industryWeights` |
| Cap | **100% Micro Cap** | same → `xray.marketCapWeights` + advanced `marketCapAllocation` |
| Total | ₹8.9L | `xray.totalValue` ≈ 894695 |

Advanced analytics matches intelligence:

- Unknown slice top stocks include **TITAN, TCS, KOTAKBANK, HDFCBANK** — i.e. large names are mislabeled Unknown, not “obscure” tickers.
- Cap segment: **Micro Cap 100%**, `numberOfStocks=110`, same top names — clearly a **default bucket**, not real classification.

`confidence` on intelligence ≈ **0.55** (meta coverage poor).

---

## 2. Root causes (end-to-end)

### A. Book equities have **no** sector / industry / marketCap stored

`GET /v1/portfolios/{id}` → `equityModels[]` keys only:

`symbol, assetType, quantity, avgBuyingPrice, investmentValue, brokerType, isin`

- **111/111** holdings: sector/industry/cap fields **null**
- Broker sync (Zerodha) never persisted classification metadata on the equity document

X-Ray builder (`XRaySummaryBuilder`) groups whatever is on the intelligence snapshot. Snapshot factory resolves:

```
EquityModel.sector/industry/marketCap  → else SecurityModel.metadata → else "Unknown" / UNKNOWN
```

With empty EquityModel meta, **everything depends on security master / market-cap Mongo**.

### B. Security master lookup fails or returns empty meta for almost all symbols

`PortfolioIntelligenceSnapshotFactory` calls `securityDetailsService.getSecurityDetails(symbols)` → `SecurityService.findBySymbols` (+ optional `MarketCapMongoService` backfill).

Evidence:

- Only **~2** names get a real sector (e.g. NIFTYBEES → Financial Services, RVNL → Industrials/Construction)
- Public market probes: `GET https://am-dev.asrax.in/market/v1/securities/{symbol}` → **500**; batch-search via public URL often **400/405**
- Portfolio pod was previously pointed at public market URL for other flows; intelligence path uses **Mongo security DB**, which appears **sparse / stale for this symbol set**

So: no book meta + thin security master ⇒ **Unknown**.

### C. Cap defaults to **MICRO_CAP** when type is missing

Two layers:

1. `SecurityDetailsService.parseMarketCapType(null|empty|bad)` → **`MarketCapType.MICRO_CAP`** (same fallthrough in `MarketCapType.fromMarketCap(String)`).
2. Snapshot factory: if equity/security has a typed cap, that wins; if truly absent it returns `"UNKNOWN"`. Live Cap pie at **100% Micro Cap** means holdings are getting a **real `MICRO_CAP` enum** from market-cap Mongo / parse default — not the UNKNOWN path.

So Cap looks “classified” while Sector/Industry stay Unknown: docs exist for symbols but **sector/industry empty**, **cap type null → MICRO_CAP**.

### D. UI hides Unknown on Sector tab only

`portfolio_xray_panel.dart` filters `name == unknown` **only for Sector** (`_tab == 0`). Industry keeps Unknown 99.6%. Chart still paints the yellow ring from full weights before/beside that filter — Sector list looks “tiny % only” while total stays ₹8.9L.

---

## 3. Fix plan (phased)

### Phase 0 — Prove meta sources (1–2h)

1. On Kind DEV, from portfolio pod / Mongo: count securities with non-empty `metadata.sector` for symbols in this book (TCS, RELIANCE, …).
2. Confirm `MarketCapMongoService.getBySymbols` hit rate for the same list.
3. Confirm in-cluster market: `http://am-market-data-dev…/v1/securities/...` vs public 500.
4. Log once in snapshot factory: `symbols=N securityHits=M marketCapHits=K unknownSector=U`.

**Exit:** Know whether gap is empty Mongo, bad symbol keys (ISIN vs NSE), or dead market API.

### Phase 1 — Stop lying on market cap (quick win)

1. Change null/unknown → **`UNKNOWN` / “Unknown”** slice, **never** default to `MICRO_CAP`.
2. Unit test: missing meta → Cap pie shows Unknown, not Micro Cap.
3. Deploy DEV; Cap tab must not show 100% Micro for blue-chip books.

### Phase 2 — Enrich sector/industry for X-Ray / advanced

**Preferred pipeline (request or warm):**

1. Prefer EquityModel meta if present.
2. Else Mongo `SecurityService.findBySymbols` (batch).
3. Else `MarketCapMongo` document sector/industry/cap.
4. Else **in-cluster** market-data batch API (not public nginx).
5. Optional: ETF client only for true ETF symbols (already special-cased for NIFTYBEES).

**Also:**

- Warm/cron: backfill `equityModels[].sector|industry|marketCap` on portfolios after broker sync (persist once).
- Symbol normalize: broker codes / ISIN-shaped symbols must resolve to NSE trading symbols before lookup (reuse `TradingSymbolResolver`).
- Do **not** call per-symbol ETF probes on the hot path (already avoided in heatmap for latency).

### Phase 3 — Data ops

1. Re-seed / refresh securities + market_cap collections for NSE universe on DEV (and Contabo if shared Mongo is empty).
2. Fix Vault `MARKET_DATA_API_URL` for DEV to in-cluster (same issue as Discover batch-search 405).
3. Invalidate intelligence Redis after backfill so X-Ray does not serve stale Unknown pies.

### Phase 4 — Verify

| Check | Pass |
|-------|------|
| Intelligence sector Unknown weight | ≪ 20% for this Zerodha book (or justified leftovers) |
| Cap | Mix of Large/Mid/Small; Unknown only if truly missing |
| TITAN/TCS/HDFCBANK | Not in Unknown; not Micro Cap |
| Advanced sectorAllocation | Matches intelligence within rounding |
| Regression | Discover ≤1s warm path unchanged |

---

## 4. Out of scope (this branch)

- UI redesign of X-Ray tabs  
- Prod deploy until DEV green  
- Full market-data service rewrite (only URL + seed + null-cap default required for pass)

---

## 5. Suggested implementation order

1. Phase 1 (cap default) — small PR, immediate truthfulness  
2. Phase 0 measurements on DEV Mongo  
3. Phase 2 enrich + persist on sync  
4. Phase 3 seed / Vault  
5. Phase 4 verify with same Bearer book  

**Ask before:** large DB seed scripts or changing broker sync schema (`am-ask-before-design` / `am-db-design`).
