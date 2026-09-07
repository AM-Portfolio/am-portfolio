# Discover — post-implementation REVIEWER

**Purpose:** After Loop 2 code lands, use this file to **verify by code analysis + targeted QA** whether changes match [plan.md](./plan.md) (FINAL 10/10).  
**Who:** Human reviewer or agent running `am-code-review` stance.  
**When:** After M2–M6 claimed done; before merge / “ship”.  
**SoT:** [plan.md](./plan.md) §2 locked decisions · §9 DoD · modules M1–M6 · [TODO.md](./TODO.md)

**Last code review:** 2026-09-07 (Loop 2A+B landed; runtime QA not run)

---

## How to run this review

1. Confirm branch: `hotfix/basket-discover-ui` (or PR branch) under `am-modern-ui` (+ catalog changes under `am-portfolio` if M5).  
2. Diff scope: Discover widgets, `discover_view_state`, `etf_search_bar`, portfolio mobile sticky, catalog seed/service if touched.  
3. Walk **every checkbox** below against **code** (not screenshots alone). Mark `[x]` only when evidence cited (file + symbol).  
4. Emit verdict: **approve** | **approve-with-nits** | **request-changes** (Blocker/Major must be fixed).  
5. Fill **Findings** and **Checked / Not verified** at the bottom.

**Do not** invent passes. If untested in browser, mark **not verified**.

---

## A. Locked decisions (must hold in code)

| ID | Requirement | Code probe | Pass |
|----|-------------|------------|------|
| D1 | `ModuleColors.portfolio` / DS tokens; no gold theme fork | Grep Discover for hardcoded gold hex / new theme | [x] |
| D2 | CTA **Create basket →** → `openPreview` + telemetry | `basket_explorer` / cards / table CTA handlers | [x] |
| D3 | Sparkline only if `sparklineCloses.length >= 2`; no fake series | `discover_sparkline.dart`; no Random/hardcoded path points | [x] |
| D4 | **Mobile** period = dropdown on **same row** as theme chips | `discover_filter_bar.dart` LayoutBuilder / mobile branch | [x] |
| D5 | **Desktop** period = chips (not forced to dropdown) | Same file desktop branch | [x] |
| D6 | Mobile `< 600`: cards only, **no** `DiscoverBasketsTable` | `basket_explorer.dart` mobile branch | [x] |
| D7 | Desktop/tablet: Top picks + dense table | explorer ≥600 path | [x] |
| D8 | Mobile toggle once (`showInlineToggle: false` + sticky) | `portfolio_tab_content_widget` / mobile screen; no double `BasketModeToggle` | [x] |
| D9 | No client-invented basket rows | No hard-coded ETF lists in UI | [x] |
| D10 | Scope = plan modules only | Diff has no drive-by My Baskets / preview redesign | [x] |

---

## B. Module verification (code)

### M1 — Tokens / layout

- [x] `DiscoverLayout` (or equiv.) encodes card height / sectionGap / filter gap targets  
- [x] Mobile card padding/gaps lean toward `PreviewLayout` density (8–12), not huge `md` stacks  
- [x] `AppComponentSizes.tableRowHeightDense` used for desktop table rows  

### M2 — Filters

- [x] Mobile: one filter band — themes + **period PopupMenu/dropdown** + sort + Clear  
- [x] Desktop: theme overflow + performance chips + sort + Clear; tight vertical gap  
- [x] More overflow uses measurement (not blind wrap of all chips)  
- [x] Clear all resets theme/period/sort **and** search field API  

### M3 — Cards / table / sparkline

- [x] Desktop Match column = round % ring (Constituents separate — no mashed header)  
- [x] Table full width; dense rows; Create basket → text/outline not huge filled  
- [x] Mobile list cards denser; sparkline hidden when null/short  
- [x] Period label/CAGR sync: 1Y return / 3Y CAGR / 5Y CAGR  

### M4 — Orchestrator

- [x] `basket_explorer.dart` ≤600 lines; discover/* files ≤600  
- [x] Vertical padding compressed so list/table starts earlier  
- [x] `dart analyze` clean on touched Discover paths  

### M5 — Data (if claimed done)

- [x] Catalog `defaultQuery` / market query returns **N ≫ 3** (cite seed or API sample) — seed `basket-catalog.yml` 9 `defaultThemeIds`  
- [x] Top picks logic = per category champion then top 3 (`DiscoverViewState.topPicks`)  
- [ ] PROD/opportunities include returns; `sparklineCloses` present or documented null  
- [ ] **Ops:** PROD Mongo still holds old catalog until `PUT /v1/basket/catalog` upsert (classpath seed only on empty)

### M6 — Tests / telemetry

- [x] Unit tests cover labels, sort, topPicks, layout constants (would fail if broken)  
- [x] `basket_open_preview` / empty-state telemetry still fired  

---

## C. Static analysis probes (agent commands)

Run from `am-modern-ui` (adjust paths if needed):

```text
# Fake sparkline / gold theme
rg -n "Random|fakeSpark|sparklineCloses\s*=" am_portfolio_ui/lib/features/basket/presentation/widgets/discover
rg -n "#[Ff][Ff][Dd]|gold|Gold" am_portfolio_ui/lib/features/basket/presentation/widgets/discover

# CTA + preview
rg -n "Create basket|openPreview|basket_open_preview" am_portfolio_ui/lib/features/basket

# Mobile table leak
rg -n "DiscoverBasketsTable|compactList" am_portfolio_ui/lib/features/basket/presentation/widgets/basket_explorer.dart

# Toggle mount
rg -n "showInlineToggle|BasketModeToggle" am_portfolio_ui/lib/features/portfolio am_portfolio_ui/lib/features/basket
```

**2026-09-07 results:** no Random/fake spark / gold hex in discover/; CTA → `openPreview` + `basket_open_preview`; mobile uses `compactList: true`, table only on desktop path; mobile sticky `BasketModeToggle` + `showInlineToggle: false` (web page keeps true).

---

## D. Runtime QA (mark not verified if not run)

### Desktop (`npm run run:app:prod` → localhost:9000)

- [ ] First viewport: filters + Top picks + All baskets heading (+ rows if N allows)  
- [ ] Period chips update return column/cards  
- [ ] Create basket → Preview  
- [ ] No duplicate Discover/My Baskets  

### Mobile (width &lt; 600)

- [ ] Single sticky mode toggle  
- [ ] Period control is dropdown on theme row  
- [ ] ≥3 cards visible when N≥3 (density)  
- [ ] No DataTable  
- [ ] Create basket → Preview compact  

---

## E. Severity rubric

| Severity | Examples |
|----------|----------|
| **Blocker** | Fake sparklines; broken Preview routing; secrets in diff; mobile shows table incorrectly; double toggle |
| **Major** | Period still second chip row on mobile; Match/Constituents mashed; Clear all skips search; N=3 claimed fixed but catalog unchanged |
| **Minor** | Spacing slightly off DoD; subtitle copy |
| **Nit** | Comment/import order |

---

## F. Verdict template (fill after review)

```text
Verdict: approve-with-nits (code); request-changes for ship until PROD catalog upsert + runtime QA

Blockers:
- none in Flutter code

Majors (ship gate):
- PROD Mongo catalog still N=3 until PUT /v1/basket/catalog with expanded seed (yml alone does not overwrite existing Mongo)
- Runtime DoD (§D) not verified in browser

Minors / Nits:
- Metal theme uses METALIETF (correct product); confirm returns/sparklines present for all 9 symbols in PROD

Checked (with file evidence):
- D1–D10 code probes (discover/* + basket_explorer + portfolio mobile sticky)
- M1–M4 density/filters/cards/table/orchestrator
- M5 seed yml + DiscoverViewState.topPicks sector champions
- flutter test test/discover_view_state_test.dart (5 passed)
- dart analyze Discover paths: No issues found

Not verified (and why):
- Browser DoD desktop/mobile
- Live PROD opportunities N and sparklineCloses after catalog upsert

Test gaps:
- No widget/golden tests for filter bar mobile layout
```

---

## G. Agent one-liner

```text
After Discover Loop 2, open am-portfolio/docs/feature-basket-discover/REVIEW.md.
Diff Discover + catalog files against plan.md locked decisions D1–D10 and modules M1–M6.
Run section C probes; fill section F verdict. Do not approve if Blocker/Major remain or if DoD was only claimed without code evidence.
```

---

## H. Latency v2 (plan-v2-latency.md)

| Probe | Pass |
|-------|------|
| `mode=DISCOVER` skips `basket.opp.stage=prices` (durationMs=0 / skipped) | [ ] |
| Flutter / Postman sends `"mode":"DISCOVER"` | [ ] |
| Warm Discover defaultQuery wall ≤1s (loop exit) | [ ] |
| Quality: N≫3, matchScore, returns/sparkline policy | [ ] |
| Flag `discover-fast-path-enabled=false` forces FULL | [ ] |
| Improve-loop iterated until green or report at 10 | [ ] |
