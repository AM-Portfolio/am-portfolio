# Smart Baskets Discover — plan v2 (opportunities latency) FINAL 10/10

**Status:** FINAL Loop latency SoT  
**Companion:** [plan.md](./plan.md) (UX) · [TODO.md](./TODO.md) · [REVIEW.md](./REVIEW.md) · [architecture.drawio](./architecture.drawio)

## 0. Scorecard (7.8 → 10)

| Gap in prior Cursor plan | Fixed here |
|--------------------------|------------|
| Soft minInv / composition rules | D-L6 / D-L7 locked |
| Match parity undefined | D-L5 (count-based match = same without prices) |
| No measurement protocol | D-L11 |
| No improve-loop | D-L13 / M-L5 |
| No rollback flag | D-L10 |
| Enrich skip unsafe | D-L8 ≥95% ISIN rule |

## 1. Goal

Discover `POST /v1/basket/opportunities` with `mode=DISCOVER` and expanded defaultQuery (~8–9 ETFs):

| Scenario | Target |
|----------|--------|
| Warm p95 | **≤ 800ms** (loop exit **≤ 1s**) |
| Cold p95 | **≤ 2s** |
| Redis down | **≤ 3s** (no bulk prices) |
| FULL / Preview | Unchanged accuracy |

## 2. Locked decisions D-L1…D-L13

| ID | Lock |
|----|------|
| D-L1 | `mode`: `DISCOVER` \| `FULL`; default **FULL** |
| D-L2 | Flutter explorer sends `DISCOVER` |
| D-L3 | DISCOVER never calls bulk `getMarketData` / `fetchPrices*` |
| D-L4 | Overlap `skipPriceFetch=true` on DISCOVER |
| D-L5 | Parity: etfIsin, totalItems, heldCount, missingCount, matchScore vs FULL when ISINs present |
| D-L6 | DISCOVER `minimumInvestmentAmount` = **50000.0** floor |
| D-L7 | List may omit composition lastPrice |
| D-L8 | Skip enrich if ≥95% valid ISINs; else enrich |
| D-L9 | Warm featured theme ETFs on startup + after catalog PUT |
| D-L10 | `basket.opportunities.discover-fast-path-enabled` (default true) |
| D-L11 | 2 warmup + 20 samples; stage logs `basket.opp.stage=*` |
| D-L12 | Cold >2s → parser perf batch (M-L3b) |
| D-L13 | Improve-loop: test → analyze → fix → deploy(confirm) → retest until ≤1s (max 10) |

## 3. Root cause

FULL path: ETF batch → enrichHoldings (batch-search) → **prices for 150–250 symbols** → overlap (gap re-fetch). Expanding catalog to 9 ETFs made cold path ~24s.

## 4. Callers

| Caller | mode |
|--------|------|
| Flutter Discover | DISCOVER |
| Preview / substitutes / create / Newman full | FULL (default) |
| Scheduler | FULL |

## 5. Modules

- **M-L0** Stage timers (`holdings|etf_batch|enrich|prices|overlap|total`)
- **M-L1** Discover fast path (skip prices + skipPriceFetch)
- **M-L2** Catalog ETF warm
- **M-L3** Enrich skip ≥95% ISIN
- **M-L3b** Parser perf (if cold still >2s)
- **M-L4** QA / REVIEW
- **M-L5** Improve-loop until ≤1s

## 6. Agent prompt

```text
Implement/verify Discover opportunities latency per plan-v2-latency.md.
mode=DISCOVER skips prices; Flutter sends DISCOVER; warm ETFs; enrich skip ≥95% ISIN.
Test PROD with Postman collection AM Basket — Current (PROD Discover).
Loop D-L13 until warm ≤1s. Deploy only after user confirm.
```
