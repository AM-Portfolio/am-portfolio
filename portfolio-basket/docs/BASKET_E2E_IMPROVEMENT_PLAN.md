# Basket Feature — E2E Analysis & Improvement Plan

**Date:** 2026-08-08  
**Scope:** Local UI + Dev APIs (`am-dev.asrax.in`)  
**Constraint:** Prefer **no am-parser code changes**. Do not break working Top-picks / Nifty / Bank / IT flow.

---

## 1. What your screenshots show (diagnosis)

### Image 1 — Customize Basket (IT ETF)

| Symptom | Likely cause | Layer |
|---|---|---|
| Assets clipped; hard to scroll past footer | `ListView` exists in `Expanded`, but **bottom safe padding** under fixed footer is missing / web scrollbar weak | **UI only** — [`manual_basket_creator_page.dart`](am-modern-ui/am_portfolio_ui/lib/features/basket/presentation/pages/manual_basket_creator_page.dart) |
| Price column `-`, Qty `0` after Calculate | `lastPrice` null from market-data for those symbols → qty calc floors to 0 | **Portfolio + market-data**, not parser |
| Payable ≠ empty qty | Footer sums only rows with `lastPrice`; partial prices → confusing totals | **UI/UX + price enrichment** |

### Image 2 — Gold chip → “No baskets matched”

| Symptom | Likely cause | Layer |
|---|---|---|
| Gold selected, empty state | Catalog query `GOLDBEES` resolves the ETF **instrument**, but **`holdings` is null** in Dev (`holdings: None` for GOLDBEES and most gold ETFs). Opportunities skip / return `[]`. | **Dev data gap** (seed coverage ~72%; equity ETFs seeded, many commodities missing holdings) — **not a Flutter bug** |
| Gold “not visible properly” | Theme chips already use horizontal `SingleChildScrollView`, but **no scroll affordance** (fade/chevron). On narrow widths Gold sits at the edge and feels clipped. | **UI polish** |

### Image 3 — Top picks working

Confirms core E2E path is healthy after Dev scale + seed: IT / Nifty 50 / Bank cards with real held/missing counts.

---

## 2. Auto-seed / parser — recommendation

| Question | Answer |
|---|---|
| Is auto-seed needed? | **No for now.** One-time Dev seed (done) is enough. Re-seed only if Dev Mongo is wiped. |
| Touch am-parser code? | **Prefer not.** Gold empty is missing `etf_holdings` rows for GOLDBEES — fix by **ops data copy/fetch**, not parser code. |
| Parser connection mismatch? | **No.** Portfolio → `https://am-dev.asrax.in/parser` is correct; equity holdings work. |
| When would parser code change be needed? | Only if you want auto-bootstrap on empty DB (product decision). Out of scope while Dev stays seeded. |

**Gold data fix (ops, no parser code):** copy GOLDBEES (and other missing) holdings from Prod → Dev, or run Dev `POST /v1/etf/fetch-holdings/GOLDBEES` after confirming Moneycontrol path works. Same pattern as the 196 holdings import.

---

## 3. Postman MCP

Your Postman MCP config lives in **Gemini** `mcp_config.json`, not Cursor’s MCP catalog (this chat still sees **0 MCP servers**).

**To use Postman MCP in Cursor:**

1. Add the same `postman` server block to Cursor MCP settings (Features → MCP), or point Cursor at that config.
2. Restart Cursor / reload MCP.
3. Use collection [`AM_Basket_Dev_Manual.postman_collection.json`](am-portfolio/postman/AM_Basket_Dev_Manual.postman_collection.json) (already created).
4. **Security:** the API key in that config was exposed in chat — **rotate the Postman API key** after enabling MCP.

Until MCP is connected in Cursor, manual import of that collection is the easiest path.

---

## 4. Easiest E2E test plan (like a real user)

### A. Happy path (5–10 min) — UI + Postman

1. **Login** local UI → open portfolio → **Baskets**.  
2. Confirm **Top picks** shows ≥1 card (Nifty/Bank/IT).  
3. Postman: `GET catalog`, `POST opportunities` Top picks → non-empty.  
4. Click **Preview basket** on IT → Customize Basket.  
5. Set amount → **Calculate** → expect prices + qty (if market-data healthy).  
6. Scroll full asset list → Pay footer still visible.  
7. Postman: `POST preview` with `INF204KB14I2` (Nifty 50 ISIN) → composition length > 0.

### B. Theme chips

1. Click each chip: Nifty 50, Bank, IT → cards.  
2. Click **Gold** → today empty until holdings seeded; after seed → ≥1 gold ETF card.  
3. Scroll chips horizontally → Gold / PSU Bank reachable with visible cue.

### C. Similarity / match score (user intent)

1. Use portfolio with known holdings (Zerodha).  
2. Top picks: match % should reflect held vs missing (as in image 3).  
3. Postman optional: `POST opportunities` with `userHoldings` inline (collection folder 3) → non-zero `matchScore` without DB.

### D. Failure / empty states

1. Invalid search → clear empty/error.  
2. Gold before holdings seed → empty state copy should say “Holdings unavailable” not only “No baskets matched” (UX improvement).

### E. Pass criteria

| Check | Pass |
|---|---|
| Top picks | Cards render |
| Customize scroll | All assets reachable above footer |
| Prices after Calculate | Not `-` for liquid NSE names |
| Gold after data fix | ≥1 opportunity |
| Parser | Untouched |

---

## 5. Gaps vs a modern trading “Smart Basket” (senior view)

### Keep (working — low risk)

- Catalog-driven themes  
- Opportunities + match scores for equity ETFs  
- Preview → Customize → Calculate quantities  
- Parser as holdings source of truth  

### Gaps (prioritized)

| Priority | Gap | Tradeoff |
|---|---|---|
| **P0** | Customize Basket list not usable (scroll/footer) | Small UI fix; almost no risk |
| **P0** | Missing / null prices → Qty 0 / `-` | May need market-data symbol mapping; careful not to slow Calculate |
| **P1** | Gold (and other themes) empty when holdings missing | Ops seed; optional clearer empty copy |
| **P1** | Theme chip scroll affordance | CSS/Flutter fade — low risk |
| **P2** | Empty state too generic | Copy + link to search |
| **P2** | Preview cold latency (~10s) | Cache already exists; show skeleton/progress |
| **P3** | Professional polish (sector chips, sticky header, density) | Don’t redesign APIs |
| **P3** | Pay & Invest → trade wiring | Product/trade integration; separate epic |
| **Out** | Parser auto-seed / parser code | Avoid unless product mandates |

---

## 6. Implementation plan (phased — don’t break current)

### Phase 0 — Ops (no code)

- [ ] Confirm GOLDBEES holdings in Dev (`POST /parser/v1/etf/holdings` → non-null `holdings`).  
- [ ] If null: copy from Prod or fetch-holdings for gold symbols only.  
- [ ] Rotate Postman API key; wire Postman MCP into **Cursor** if desired.

### Phase 1 — UI reliability (safe)

1. **Customize Basket scroll**  
   - Add bottom padding to asset `ListView` ≥ footer height.  
   - Ensure parent `Column` + `Expanded` + footer pattern; on web, enable visible scrollbar.  
2. **Theme chips**  
   - Keep horizontal scroll; add edge fade / “more” chevron so Gold doesn’t feel cut off.  
3. **Empty state**  
   - If API returns `[]` but catalog theme selected: “No holdings data for this ETF yet” vs generic “No baskets matched”.

### Phase 2 — Data honesty (prices / qty)

1. Trace Calculate path: which symbols return null LTP from market-data.  
2. Map alternate symbols / show “Price unavailable” badge instead of silent `0`.  
3. Disable **Pay & Invest** when payable is 0 due to missing prices (prevent false confidence).

### Phase 3 — Professional polish (optional)

- Sticky table header while scrolling assets.  
- Denser rows, clearer Held vs Need vs Qty.  
- Loading skeletons on opportunities/preview.  
- Do **not** change opportunity JSON contract unless UI + Postman updated together.

### Phase 4 — E2E harness

- Postman collection (already present) + short checklist in this doc.  
- Optional: one Playwright/Flutter driver script later — not required for first ship.

---

## 7. Recommended next coding step

**Only Phase 1 UI fixes** in `am-modern-ui` (scroll + chip affordance + clearer Gold empty copy).  
**No parser.**  
**Gold data** via ops seed when you’re ready.

Confirm and we implement Phase 1 in Agent mode.

---

## 8. Quick reference — current architecture

```text
UI (localhost:9000)
  → GET  /portfolio/v1/basket/catalog     (themes; portfolio Mongo/Redis)
  → POST /portfolio/v1/basket/opportunities
       → POST /parser/v1/etf/holdings     (constituents)
       → market-data prices               (qty / LTP)
  → POST /portfolio/v1/basket/preview
  → Customize Basket (client calculate-quantities)
```

Parser stays read-only consumer of Mongo ETF data. Portfolio owns UX + opportunity assembly. Market-data owns LTP.
