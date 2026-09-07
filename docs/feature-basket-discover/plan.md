# Smart Baskets Discover — FINAL plan (10/10)

**Status:** FINAL Loop-1 SoT — confirm before Loop 2 code  
**Scorecard:** this document replaces prior drafts (rated **7.8/10** → rewritten to **10/10**)  
**UI branch:** `hotfix/basket-discover-ui` (`am-modern-ui`)  
**Docs:** this folder · Diagram: [architecture.drawio](./architecture.drawio) · Checkboxes: [TODO.md](./TODO.md) · **Post-impl verify:** [REVIEW.md](./REVIEW.md)

---

## 0. Senior review of prior plan (why rewrite)

| Gap in old plan | Impact | Fixed here |
|-----------------|--------|------------|
| Desktop-heavy; mobile under-specified | Mobile keeps 2 filter rows + tall cards | Full mobile module + DoD |
| No period-as-dropdown on theme row | Wastes vertical space | Locked UX |
| Weak sparkline policy | Risk of fake charts | Real-data only + repair path |
| No preview parity | Discover feels foreign vs Preview | Density tokens from `PreviewLayout` |
| No module map / file owners | Agents thrash | Modules M0–M6 |
| Thin DoD / E2E flows | Incomplete ship | Flow + QA gates |
| N=3 buried | UI polish without data | Module M5 called out as blocker for “many baskets” |

---

## 1. Goal

Deliver a **production Discover** experience on **web and mobile** that:

1. Uses **real** opportunities data (match, returns, sparkline when present).  
2. Separates **sector champions (Top picks)** from a **dense full list (All baskets / mobile cards)**.  
3. Maximizes **visible baskets per viewport** (compress filters, gaps, card/table chrome).  
4. Stays consistent with the rest of the basket funnel (**Preview → Customize → …**) via shared density tokens — not a gold theme fork.  
5. Does **not** invent market rows client-side; expands catalog/query when “all profitable baskets” is required.

---

## 2. Locked decisions (non-negotiable)

| ID | Decision |
|----|----------|
| D1 | Accent = `ModuleColors.portfolio` + `am_design_system` tokens. Gold mocks = **layout/density reference only**. |
| D2 | CTA = **Create basket →** → `BasketNavigation.openPreview` + existing telemetry. |
| D3 | Sparklines = **real** `sparklineCloses` only if `length >= 2`; else **hide** (empty size box). **Never** synthesize fake series in Flutter. |
| D4 | **Mobile** Performance = **dropdown** on the **same row** as theme chips (`Top picks`, `Nifty 50`, …). No second full Performance chip row. |
| D5 | **Desktop** Performance = keep compact **chip row** (1Y/3Y/5Y/All) beside sort (space allows). |
| D6 | Mobile `< 600`: **dense card list only** — **no** DataTable. |
| D7 | Desktop/tablet `≥ 600`: Top picks section + **dense All baskets table**. |
| D8 | Mobile Discover/My Baskets toggle lives in **portfolio sticky header only** (`showInlineToggle: false`). Do not double-render. |
| D9 | Default “profitable” for v1 table/list = **all** opportunities for query, ranked by selected period return (nulls last). Optional “Profitable only” chip = later. |
| D10 | Loop 2 code starts only after explicit user confirm. |

---

## 3. End-to-end flows

### 3.1 Happy path (web)

```text
Login → Portfolio → Baskets
  → Discover (inline title + mode toggle)
  → catalog load → opportunities(defaultQuery)
  → Top picks (sector champions) + All baskets table
  → Create basket → Preview (stepper + sticky Customize)
  → Customize → Final → Confirm
```

### 3.2 Happy path (mobile)

```text
Login → Portfolio → Baskets tab
  → sticky Discover | My Baskets
  → search + ONE filter row (themes + period ▾ + sort ▾ + Clear)
  → meta "N baskets · As of …"
  → dense vertical cards (max visible)
  → Create basket → Preview (compact hero + constituent cards + sticky bar)
```

### 3.3 Data path (why N=3 today)

```text
GET /v1/basket/catalog
  defaultThemeIds [nifty-50, bank, it]
  → defaultQuery = "NIFTYBEES,BANKBEES,ITBEES"   ← exactly 3
GET .../opportunities?query=...
  → List size 3
UI Top picks + All baskets both bind that list → duplicate + empty viewport
```

Seed: `am-portfolio/portfolio-basket/src/main/resources/basket-catalog.yml`  
Join logic: `BasketCatalogService.computeDefaultQuery`.

Theme chip (e.g. Auto) → theme.`query` (often index alias) → may resolve to 0–1 ETF until alias→fund map is solid.

### 3.4 Client shape (no refetch)

| Control | Refetch opportunities? | Effect |
|---------|------------------------|--------|
| Theme / Top picks / search multi-ISIN | **Yes** | New `query` |
| Period 1Y/3Y/5Y/All | No | Labels + return values + top-performer rank |
| Sort | No | Table/list order only (desktop table; mobile list) |
| Clear all | Resets theme→default, period→1Y, sort→Recommended, clears search field |

### 3.5 Create / Preview handoff

Discover does **not** show Preview stepper or sticky Customize bar.  
Parity = **density + typography + status colors**, not cloning Preview chrome onto Discover.

---

## 4. Modules (implementation map)

### M0 — Docs / gate (this folder)

Owners: `plan.md`, `TODO.md`, `architecture.drawio`, `architecture.dsl`, `README.md`  
Done when user confirms Loop 2.

### M1 — Design system / layout tokens

| Item | Spec |
|------|------|
| Reuse | `AppSpacing`, `AppRadii`, `AppComponentSizes.tableRowHeightDense` (56), `AmToggleChip`, `ModuleColors.portfolio` |
| Discover | `DiscoverLayout` — cardHeight ~180–196 (desktop), sectionGap ≤20, filterInternalGap ≤8 |
| Borrow from Preview | `PreviewLayout.sectionGap=8`, `cardPadding=12` for **mobile Discover cards** |
| Files | `discover_layout.dart`, optionally thin `DiscoverMobileLayout` constants |

### M2 — Filter bar (web + mobile)

**File:** `discover_filter_bar.dart` (+ explorer wiring)

| Surface | Layout |
|---------|--------|
| **Mobile** | Single row: `[themes… More] … [1Y ▾] [Sort ▾] [Clear]` — period = `PopupMenuButton` |
| **Desktop** | Theme overflow row + Performance chips + Sort + Clear (tight; ≤8px between bands) |

Theme overflow: `TextPainter` width budget + More menu (already started).

### M3 — Opportunity presentation

**Files:** `discover_opportunity_card.dart`, `discover_match_ring.dart`, `discover_sparkline.dart`, `discover_baskets_table.dart`, `discover_section_headers.dart`

| Surface | Behavior |
|---------|----------|
| Desktop Top picks | Grid: **one champion per featured theme** (Loop 2B data); until then document “top performers in segment” |
| Desktop All baskets | Full-width flex table; Match = **round % only**; Constituents separate; row ~56px |
| Mobile | `_buildListCard` denser: less pad, smaller ring (~36–40), sparkline if real, Create basket → |

### M4 — Orchestrator / chrome

**Files:** `basket_explorer.dart`, `discover_mode_toggle.dart`, `etf_search_bar.dart`, portfolio mobile sticky

- `showInlineToggle: false` on mobile host.  
- Search height `AppComponentSizes.inputHeight`; Clear all clears search.  
- Compress vertical padding above the list/table so content starts earlier.

### M5 — Catalog / opportunities data (blocker for “many baskets”)

**Files (backend):** `basket-catalog.yml`, `BasketCatalogService`, opportunities endpoints  

| Work | Outcome |
|------|---------|
| Expand `defaultThemeIds` and/or `marketQuery` of many ETF symbols/ISINs | Table/list N ≫ 3 |
| Per-theme opportunities (or batch) | Top picks = sector champions |
| Verify `sparklineCloses` on PROD opportunities | Real mini-charts |

Without M5, M2–M4 still improve density, but **All baskets (3)** remains.

### M6 — QA / telemetry

- Preserve `basket_open_preview`, `basket_opportunities_empty`.  
- DoD web + mobile (§9).  
- Unit tests: `DiscoverViewState` labels, sort, topPicks, layout constants.

---

## 5. Desktop target layout

```text
Smart Baskets + subtitle                    [Discover | My Baskets]
Search (48px)
[Top picks][Nifty 50]…[More]
Performance [1Y][3Y][5Y][All]   Sort by …   Clear all
Top picks — Top performers by sector          View all →
[ card ][ card ][ card ] … (sector champions; compact ~196px)
All baskets (N) · As of …
┌ dense table — many rows visible without huge empty region ┐
```

**Compression targets:** filter→Top picks gap ≤12; Top picks→table ≤20; table earlier in first viewport; Match column round % only.

---

## 6. Mobile target layout

```text
(portfolio sticky) [Discover | My Baskets]     ← once only
Search
[Top picks][Nifty 50]…[More]  [1Y ▾] [Sort ▾] Clear   ← ONE row
N baskets · As of …
┌ dense card ┐  ← preview-like padding; sparkline if real
┌ dense card ┐
… as many as fit …
```

**Problems in current mobile (fix in Loop 2A):**

| Issue | Fix |
|-------|-----|
| Performance chips on second row | Period **dropdown** on theme row |
| Tall cards / black space | Pad ~12; reduce gaps; smaller match ring |
| Only ~2 cards visible | Compress chrome + denser cards |
| Mock double Discover tabs | Ensure sticky-only toggle; no inline |
| No table | Correct — densify **list**; do not force DataTable |

---

## 7. Sparklines (mini charts) — full policy

### When real

- Opportunities JSON includes `sparklineCloses: number[]` (typically ≤24 closes from parser performance).  
- UI: `DiscoverSparkline` / painter; color from **period return** sign (not from fake trend).  
- Period change updates **% label**, not the sparkline series (series is one stored window unless API adds period-scoped series later).

### When missing

1. Hide chart (do not draw noise).  
2. Diagnose PROD: parser performance → portfolio enrich → cache round-trip (`CachedEtfData`).  
3. **Do not** generate random or CSS-only decorative charts.

### How we “make” little charts if product insists and API empty

| Option | Effort | Allowed in Loop 2? |
|--------|--------|-------------------|
| A. Fix enrich so `sparklineCloses` populates | Backend | **Yes — preferred** |
| B. New API: OHLC downsample per period | Backend + UI | Later phase |
| C. Client invent series | — | **Forbidden** |

---

## 8. Preview / basket funnel consistency

| Concern | Preview | Discover target |
|---------|---------|-----------------|
| Density | `PreviewLayout` 8/12/40 | Match card/list padding & gaps |
| Stepper / sticky Customize | Yes | **No** on Discover |
| Status colors | held / sub / gap | Match ring semantic colors |
| Accent | Module portfolio | Same |
| Mobile list pattern | Combined constituent cards | Dense opportunity cards |

---

## 9. Definition of Done

### Desktop

- [ ] Filter band compact; All baskets heading + ≥3–5 rows in first viewport when N allows  
- [ ] Columns distinct; Match = round %  
- [ ] Top picks ≠ blind clone of first table rows (after M5: per sector)  
- [ ] Create basket → opens Preview  
- [ ] Sparklines only when real  

### Mobile

- [ ] Single Discover/My Baskets control (sticky)  
- [ ] Period dropdown on **same row** as themes  
- [ ] ≥3 dense cards visible on typical phone height when N≥3  
- [ ] No DataTable; meta As of line  
- [ ] Create basket → Preview compact flow works  

### Data

- [ ] Documented N from API; after M5, N ≫ 3 for default/market query  
- [ ] PROD sample opportunities include returns; sparklines present or explicitly null  

### Quality

- [ ] `dart analyze` clean on Discover module  
- [ ] Unit tests for view-state + layout constants  
- [ ] File length ≤600 for Discover widgets  

---

## 10. Phased delivery

| Phase | Scope | Exit |
|-------|--------|------|
| **Loop 1** | This FINAL plan + TODO + architecture | User confirm |
| **Loop 2A** | M1–M4 Flutter density (web+mobile dropdown) | DoD UI without requiring N≫3 |
| **Loop 2B** | M5 catalog/query + sector champions + sparkline verify | Many baskets + real charts where available |
| **Loop 2C** | M6 QA both viewports | Ship |

---

## 11. File ownership (Loop 2)

| Area | Paths |
|------|--------|
| Orchestrator | `am_portfolio_ui/.../widgets/basket_explorer.dart` |
| Discover UI | `.../widgets/discover/*` |
| State | `.../utils/discover_view_state.dart` |
| Search | `.../widgets/etf_search_bar.dart` |
| Mobile sticky | `.../portfolio/.../portfolio_mobile_screen.dart` |
| Catalog seed | `am-portfolio/portfolio-basket/src/main/resources/basket-catalog.yml` |
| Catalog service | `.../BasketCatalogService.java` |

---

## 12. Agent compression prompt (FINAL)

```text
Implement Loop 2A/2B per am-portfolio/docs/feature-basket-discover/plan.md (FINAL 10/10).

WEB: denser filters; shorter Top picks cards; full-width All baskets table earlier in viewport;
Match column = round % only; Create basket → = openPreview.

MOBILE (<600): ONE filter row = theme chips + period DROPDOWN (1Y▾) + Sort▾ + Clear;
dense cards (borrow PreviewLayout padding/gaps); NO table; sticky toggle only once;
maximize cards visible; hide sparkline unless sparklineCloses.length >= 2 — never fake charts.

DATA: if All baskets still N=3, expand catalog defaultQuery/market query (M5) — do not invent rows in UI.

Tokens: am_design_system + ModuleColors.portfolio. No gold theme fork.
Keep APIs/routing/telemetry. ≤600 lines per Discover file.
```

---

## 13. Prerequisites

```powershell
Set-Location "C:\Users\Md Sahimuzzaman\Desktop\axrax-v1\am-modern-ui"
npm run run:app:prod
```

Open http://localhost:9000/login — quote paths with spaces. Prefer `run:app:prod` over bare `AM_ENV=prod` only.  
Creds: `.env.prod` / `~/.asrax` — never commit.

**Mobile check:** browser responsive &lt;600 or device; Baskets tab sticky Discover.

---

## 14. Stop gate

**Do not write Flutter/catalog code until you explicitly confirm Loop 2** (e.g. “execute Loop 2A” or “do it”).  
This `plan.md` is the single FINAL product/engineering plan for Discover web + mobile.
