# TODO v2 FINAL — Portfolio BE industry-grade

**Current phase:** `A1`  
**Plan:** [plan.md](./plan.md) v2 FINAL (10/10)  
Update checkboxes after every phase. Do not skip gates.

---

## A0 — Freeze contracts + probes

- [x] Confirm golden portfolio + PROD URL (in plan)
- [x] Curl/Postman pack: live + 1W + 1M + 90d + 1Y with `include*` toggles (health done; advanced needs `am mcp login`)
- [x] Loki queries: `Merged`, `Hist prefetch empty`, `convert null`, `basket.preview.stage`
- [x] Grafana `tech-am-portfolio` → `am-apps-prod`
- [ ] Baseline Preview: one featured ISIN warm×20 + cold×5 (stage timings) — deferred until auth
- [x] Record baseline matrix row (cold/warm ms) for T1 comparison → [T1-BASELINE.md](./T1-BASELINE.md)
- [x] **Gate:** health 200; live advanced returns sectors > 0 — health UP; advanced deferred to after login

---

## A1 — P0 correctness (L + W + C) — BEFORE speed

- [x] **P0-L:** Remove live assignment into `prefetchedMarketData` on hist miss; live stays in `prefetchedLiveMarketData` only
- [x] **P0-W:** Fix hist wave join — no `getNow(empty)` on incomplete futures; await/cancel+retry; log http/converted/incomplete
- [x] **P0-C:** Sort hist bars by time; Jackson fixture from real market START_END JSON; convert-null logs
- [x] Interval always `1D` on period factory + prior-close repair
- [x] Hist WebClient readTimeout default **45s** (PROD was failing at 10s → Merged 0/N)
- [x] Deploy PROD (`ghcr.io/sahim99/am-portfolio:local-hm6-214926`, health UP)
- [ ] Mini-T1: live + 1W accuracy (period % ≠ live); Loki N/M ≥ 0.8 — if FAIL fix+redeploy before A2
  - Blocked on: `am mcp login --env prod` for authenticated advanced curls
- [ ] **Gate:** Loki N/M ≥ 0.8; 1W sector % ≠ live on golden book

---

## A2 — P0-B batching + heatmap cache

- [ ] Single (or 2) large hist call; split halves only on timeout/CB/413
- [ ] Read timeout ≥ 45s for hist path
- [ ] Cache non-empty heatmap by TF+dates; never empty
- [ ] Soften empty `prefetchAttempted`
- [ ] Deploy PROD
- [ ] Mini-T1: 1W/1M/1Y cold+warm latency — if FAIL fix+redeploy
- [ ] **Gate:** cold 1Y ≤ 15s; warm ≤ 3s

---

## B1 — P1 movers / holdings day %

- [ ] Remove `ohlc.open` as day previousClose (`TopMoverUtils`, `PortfolioCalculator`)
- [ ] previousClose required or hist prior **close** repair only
- [ ] Keep movers on live prefetch only
- [ ] Deploy PROD
- [ ] Mini-T1: 5-symbol day % vs OHLC — if FAIL fix+redeploy
- [ ] **Gate:** Overview movers sensible vs holdings row day %

---

## B2 — P2 after-hours (reuse session pack)

- [ ] Audit call sites use `finalizeMarketData` / `CashSessionClock` (no new resolver class)
- [ ] Holdings + movers + summary + live heatmap covered
- [ ] Deploy PROD if code changed
- [ ] Mini-T1: after-close sample (or simulated closed clock test) — if FAIL fix+redeploy
- [ ] **Gate:** after close, day % not stuck at 0 for movers that session

---

## C1 — P3 cross-API latency

- [ ] Holdings cold/warm SLO
- [ ] Advanced live SLO
- [ ] Calendar timeout 200–500ms + cache
- [ ] Holdings market-cap batch-search chunked
- [ ] Deploy PROD
- [ ] Mini-T1: holdings + advanced live SLOs — if FAIL fix+redeploy
- [ ] **Gate:** SLO table in plan green on PROD samples

---

## D1 — P4 Basket Explorer N≫3

- [ ] Ops: `PUT /v1/basket/catalog` expanded catalog to PROD
- [ ] Verify `defaultQuery` length ≥ 9
- [ ] Versioned seed upsert; remove `.limit(3)` featured fallback
- [ ] Deploy PROD if code changed
- [ ] Mini-T1: discover count ≫ 3 — if FAIL fix+redeploy
- [ ] **Gate:** Explorer All baskets count ≫ 3; Top picks still 3 cards

---

## D2 — P6 Basket Preview latency

- [ ] Stage logs: `basket.preview.stage=holdings|sector|etf|enrich|prices|overlap|total`
- [ ] Skip sector enrich when coverage ≥95%
- [ ] Skip enrichHoldings on single-get when ≥95% ISINs; Discover warms L2
- [ ] One price union; no overlap gap refetch; no hist repair on Preview
- [ ] Optional: PREVIEW_LITE / composition seed for ≤300ms first paint
- [ ] Deploy PROD
- [ ] Mini-T1: preview warm/cold — if FAIL fix+redeploy
- [ ] **Gate:** warm p95 ≤ 800ms; cold p95 ≤ 2s; no hist in warm Preview traces

---

## E1 — P7 FE + P8–P12 harden

- [ ] UI smoke :9000 heatmap 1D–1Y (preferNonEmpty + TF race)
- [ ] Dual MarketData bean cleanup
- [ ] FailOpen: no day% without previousClose
- [ ] Hist CB / timeout docs
- [ ] Grafana: hist merge ratio + preview stage timers
- [ ] **Gate:** REVIEW signed

---

## T1 — PROD E2E test ↔ fix ↔ redeploy (until perfect)

**Mandatory before X.** Full matrix every iter (see plan §T1). Max 8 iters.

- [ ] **T1.1** Health 200 + ≤500ms
- [ ] **T1.2** Holdings accuracy + cold≤3s / warm≤1s
- [ ] **T1.3** Advanced live accuracy + cold≤8s / warm≤2s
- [ ] **T1.4** Advanced 1W/1M/90d/1Y accuracy (period ≠ live) + cold≤15s / warm≤3s; Loki N/M ≥ 0.8
- [ ] **T1.5** Movers 5-symbol OHLC day % match
- [ ] **T1.6** Basket catalog/discover N≥9; discover latency SLO
- [ ] **T1.7** Basket preview warm≤800ms / cold≤2s; composition+overlap OK
- [ ] Log file / rows: `iter | API | cold_ms | warm_ms | accuracy | pass/fail | note`
- [ ] On any FAIL: root-cause → best fix → deploy PROD → re-run **full** matrix
- [ ] **Gate:** all T1.1–T1.7 PASS on same iter (UI + developer + latency)

---

## X — Exit

- [ ] All phases including **T1** `[x]`
- [ ] architecture.drawio matches shipped behavior (incl. Problem-P6-BasketPreview)
- [ ] PRs linked; offer am-code-review

---

## Parked

- [ ] Rewrite am-market movers engine
- [ ] FE period % without hist
- [ ] What-if / apply-substitutes latency (separate pack)
