# UI Spec — Overview layout SoT (web / tablet / phone)

**Plan:** [plan.md](./plan.md) §4 + D15  
**Web mock:** [final-overview-page.png](./final-overview-page.png)  
**Goal:** After P7–P8, Overview **structure** matches this SoT. Styling = AM design system (not pixel-clone of mock pink/green).  
**Kill switch:** every **new** intel widget is GrowthBook-gated (D15).

---

## Breakpoints

| Band | Width |
|------|-------|
| Phone | `< 600` |
| Tablet | `600 – 1099` |
| Web | `≥ 1100` |

Primary file: `am_portfolio_ui/.../portfolio_overview_widget.dart` (`LayoutBuilder`).

---

## Feature flags (required)

| Key | Widget |
|-----|--------|
| `portfolio-intelligence-overview-v1` | Master — OFF hides all rows below that are “new” |
| `portfolio-intel-health-v1` | Health Score |
| `portfolio-intel-risk-v1` | Risk Radar |
| `portfolio-intel-xray-v1` | Portfolio X-Ray |
| `portfolio-intel-stress-v1` | Stress Test |
| `portfolio-intel-whatif-v1` | What-If Simulator |

### Fallback when flags OFF

| Condition | UI |
|-----------|-----|
| Master OFF | KPIs + Chart + Top Movers + **Allocation** (legacy). No intel API calls. |
| Master ON, X-Ray OFF | Keep **Allocation**; other flagged widgets per child flags |
| Master ON, X-Ray ON | **X-Ray only** (no Allocation duplicate) |
| Child OFF | Skip that card; reflow grid/stack (no blank spacer) |

KPIs, Performance chart, Top Movers: **not** behind intel flags.

---

## Web (≥1100) — Image 1 (X-Ray primary)

**Product SoT:** X-Ray occupies the former large Movers mid-row slot. Top Movers is compact (Gainers|Losers tabs) on the bottom row. Asset Class tab removed.

```text
┌─────────────────────────────────────────────────────────────┐
│  KPI: Total Return | Today P&L | Total Balance | Invested   │
├──────────────────────────────┬──────────────────────────────┤
│  Performance chart (flex 2)  │  Health Score (flex 1)       │
│  (EXISTING — do not restyle) │  gauge + 8 rows + Details    │
├──────────────────────────────┼──────────────────────────────┤
│  Portfolio X-Ray (flex 1)    │  Risk Radar (flex 1)         │
│  Sector | Industry | Cap     │  spider + findings/axes      │
│  matched peer height         │  matched peer height         │
├──────────────┬───────────────┴──────────────┬───────────────┤
│  Top Movers  │  Stress Test                 │  What-If      │
│  COMPACT     │  presets + custom            │  Simulator    │
└──────────────┴──────────────────────────────┴───────────────┘
```

Peers in mid rows stretch with pinned footer CTAs (not sparse fixed empty cards).

---

## Tablet (600–1099)

```text
┌─────────────────────────────────────────┐
│  KPI ×4 (one row, tighter padding)      │
├────────────────────┬────────────────────┤
│  Chart (flex 2)    │  Health (flex 1)   │
├────────────────────┼────────────────────┤
│  X-Ray (flex 2)    │  Risk (flex 1)     │
├────────────────────┴────────────────────┤
│  Movers compact (full width)            │
├────────────────────┬────────────────────┤
│  Stress            │  What-If           │
└────────────────────┴────────────────────┘
```

- Stress / What-If: **not** collapsed on tablet.  
- Health: compact 8 component rows; Details → modal.  
- Chart height ~320–360.

---

## Phone (&lt;600)

```text
┌──────────────────┐
│  KPI 2×2         │
│  Chart (~320)    │
│  Health          │  (gauge + top 4 components; rest in Details)
│  X-Ray           │  (Sector / Industry / Cap)
│  Risk            │
│  Movers compact  │  (Gainers/Losers tabs, short list)
│  Stress ▸        │  ExpansionTile, default collapsed
│  What-If ▸       │  ExpansionTile, default collapsed
└──────────────────┘
```

Touch targets for primary actions ≥ 48px.

---

## Widget checklist

| Widget | Data | Must show | Flag |
|--------|------|-----------|------|
| KPI ×4 | summary | Existing | — |
| Chart | history/intraday | Unchanged interactions | — |
| Health | intelligence.health | Score, band, components, View Details | master + health |
| Risk | intelligence.risk | Axes + findings (or axis scores if no findings), View Risk Analysis | master + risk |
| Movers | advanced | Compact Gainers / Losers on Overview | — |
| X-Ray | intelligence.xray | Sector/Industry/Cap; **desktop/tablet: donut left + legend right**; phone stacked; Unknown honest | master + xray |
| Stress | stress API | Presets + custom (phone included when expanded); no fake 0% on failure | master + stress |
| What-If | what-if API | Compact empty After; weight ≤100 validation | master + whatif |
| Allocation | advanced | Only when X-Ray/master OFF | — |

### Layout heights (industry-grade pass)

- Chart|Health: **flex 2:1**, matched band height (phone 300 / tablet 320 / web **340**); Health `fillHeight` + pinned CTA.
- X-Ray|Risk: **flex 1:1**, fixed matched band (tablet 320 / web 340) + stretch; both `fillHeight`; legend scrolls inside X-Ray. **Do not** use `IntrinsicHeight` (crashes with X-Ray `ListView`).
- Chart bootstrap: skeleton plot (not empty spinner void).
- Bottom: 1:1:1 natural heights, start-aligned.
- Compact Movers: top **5** rows.

### Risk empty findings

When `findings[]` is empty, show up to **4** axes sorted by `riskScore` desc with score-band pills (**High ≥70 / Medium ≥40 / Good &lt;40**). Never invent narrative findings (e.g. Banking %). Spider is an N-gon from live axes only (4 until history backend adds Vol/Beta). Labeled spider with spokes, glow, vertex dots; visual radius floor so score `0` does not collapse the plot.
---

## Drill-downs (flag follows parent)

| Trigger | Phone | Tablet / Web |
|---------|-------|--------------|
| View Details (Health) | Full-screen sheet | Modal |
| View Risk Analysis | Full-screen sheet | Modal |
| See Top 10 | Existing movers modal | Existing modal |
| Explore Full X-Ray | Sheet | Modal / expand |
| Custom scenario / Simulate | Inline in card | Inline in card |

No new routes for MVP.

---

## Empty / error / loading

| State | Behavior |
|-------|----------|
| Intelligence loading | Skeletons on flagged cards; KPIs+chart live |
| Intelligence fail | Retry on intel cards only; never blank Overview |
| Stress/What-If fail | Inline error |
| Flag OFF | Widget not built; no fetch for that surface |

---

## Explicitly NOT required

- Exact mock pink/green palette  
- Exact mock typography / spacing  
- Mock sample numbers — use live API  
- Asset Class donut (removed from UI for now)  
- PDF / email (P10)

---

## Pass criteria

### Web
- [x] Rows match Image 1 grid when all flags ON (X-Ray\|Risk mid; compact Movers bottom)  
- [x] Health alone beside chart  
- [x] Compact Movers + Stress + What-If on bottom row  
- [x] Chart not redesigned  
- [x] Master OFF → Allocation + no intel widgets  

### Tablet
- [x] Chart\|Health + X-Ray\|Risk + Movers full + Stress\|What-If  
- [ ] Verified ~768 width *(manual resize after `run:app:9000:prod:intel`)*  

### Phone
- [x] Stack order: Chart → Health → X-Ray → Risk → compact Movers → Stress/What-If collapsed  
- [ ] Verified ~390 width *(manual resize)*  

### Flags
- [x] Keys in `FeatureFlagKeys` + GrowthBook  
- [x] Child OFF reflows cleanly  
- [x] No intel API when master OFF  
- [x] Debug dogfood: `--dart-define=AM_INTEL_FORCE_ON=true` (kDebugMode only) via `npm run run:app:9000:prod:intel`  
