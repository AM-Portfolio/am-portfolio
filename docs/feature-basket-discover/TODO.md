# Discover — TODO (aligned to FINAL plan.md)



Companion: [plan.md](./plan.md) · [architecture.drawio](./architecture.drawio)



**Legend:** `[ ]` pending · `[x]` done · `[~]` in progress



---



## M0 — Loop 1 docs



- [x] FINAL `plan.md` (10/10 web + mobile)

- [x] `TODO.md` modules

- [x] `architecture.drawio` / `architecture.dsl`

- [x] User confirms before Loop 2 code



---



## M1 — Tokens / layout



- [x] DiscoverLayout + PreviewLayout density alignment (mobile card pad/gaps)

- [x] tableRowHeightDense / card height targets documented in code constants



---



## M2 — Filters



- [x] Mobile: period **dropdown** on theme row + Sort + Clear

- [x] Desktop: compact theme row + Performance chips + Sort + Clear

- [x] More overflow via TextPainter (themes)

- [x] Clear all clears search + state



---



## M3 — Cards / table / list



- [x] Desktop: denser Top picks cards; All baskets earlier; Match = round % only

- [x] Mobile: denser compact cards; maximize visible count; no DataTable

- [x] Sparkline: real `sparklineCloses` only; hide if &lt;2 points

- [x] Create basket → → openPreview



---



## M4 — Chrome / orchestrator



- [x] Mobile: single sticky Discover/My Baskets (`showInlineToggle: false`)

- [x] Compress explorer vertical padding

- [x] Discover files ≤600 lines



---



## M5 — Data (many baskets + sector champions)



- [x] Expand catalog defaultThemeIds (9 themes → defaultQuery N≫3)

- [x] Top picks = best return per category, take 3 (sector champions)

- [ ] Verify PROD `sparklineCloses` / returns on opportunities

- [ ] Table/list bind full opportunity set (needs catalog upsert in Mongo — seed only on empty)

- [ ] Ops: upsert expanded catalog to PROD Mongo/Redis after deploy



---



## M6 — QA



- [ ] `npm run run:app:prod` web DoD (§9 plan)

- [ ] Mobile &lt;600 DoD (§9 plan)

- [ ] Preview handoff smoke

- [x] Unit tests view-state + layout

- [ ] Telemetry preserved

- [ ] **Pass [REVIEW.md](./REVIEW.md)** (code probes + verdict) before merge



---



## Out of scope



- [ ] Fake sparklines

- [ ] Gold theme rebrand

- [ ] Bookmark / ⋮

- [ ] Promo banner

- [ ] My Baskets redesign

- [ ] Period-scoped OHLC sparkline API (later)

---

## Latency v2 — [plan-v2-latency.md](./plan-v2-latency.md)

- [x] M-L0 Stage timers (`basket.opp.stage=*`)
- [x] M-L1 `mode=DISCOVER` skip prices + skipPriceFetch
- [x] M-L2 Warm ETFs on startup + catalog PUT
- [x] M-L3 Enrich skip when ≥95% ISINs
- [x] Flutter explorer sends `mode=DISCOVER`
- [ ] M-L4 PROD warm p95 ≤800ms / loop exit ≤1s
- [ ] M-L5 Improve-loop until ≤1s (Postman collection)
- [ ] M-L3b Parser perf batch if cold still >2s

