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

## Web (≥1100) — Image 1

```text
┌─────────────────────────────────────────────────────────────┐
│  KPI: Total Return | Today P&L | Total Balance | Invested   │
├──────────────────────────────┬──────────────────────────────┤
│  Performance chart           │  Health Score                │
│  (EXISTING — do not restyle) │  gauge + 8 rows + Details    │
├──────────────────────────────┼──────────────────────────────┤
│  Top Movers                  │  Risk Radar                  │
│  Gainers | Losers            │  spider + findings + Details │
├──────────────┬───────────────┴──────────────┬───────────────┤
│  Portfolio   │  Stress Test                 │  What-If      │
│  X-Ray       │  presets + custom            │  Simulator    │
└──────────────┴──────────────────────────────┴───────────────┘
```

---

## Tablet (600–1099)

```text
┌─────────────────────────────────────────┐
│  KPI ×4 (one row, tighter padding)      │
├────────────────────┬────────────────────┤
│  Chart             │  Health            │
├────────────────────┼────────────────────┤
│  Movers            │  Risk              │
├────────────────────┴────────────────────┤
│  X-Ray (full width)                     │
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
│  Health          │  (gauge + top 4 components; rest in Details)
│  Chart (~320)    │
│  Risk            │
│  Movers          │  (Gainers/Losers stacked or tabbed)
│  X-Ray           │
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
| Risk | intelligence.risk | Axes + findings, View Risk Analysis | master + risk |
| Movers | advanced | Gainers / Losers | — |
| X-Ray | intelligence.xray | Sector/Industry/Cap; Asset Class disabled | master + xray |
| Stress | stress API | Presets + custom; “Scenario estimate” | master + stress |
| What-If | what-if API | Add/Modify/Switch → before/after | master + whatif |
| Allocation | advanced | Only when X-Ray/master OFF | — |

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
- Working Asset Class donut  
- PDF / email (P10)

---

## Pass criteria

### Web
- [ ] Rows match Image 1 grid when all flags ON  
- [ ] Health alone beside chart  
- [ ] What-If on bottom row with X-Ray + Stress  
- [ ] Chart not redesigned  
- [ ] Master OFF → Allocation + no intel widgets  

### Tablet
- [ ] 2-col pairs + X-Ray full width + Stress|What-If  
- [ ] Verified ~768 width  

### Phone
- [ ] Stack order per above; Stress/What-If collapsed by default  
- [ ] Verified ~390 width  

### Flags
- [ ] Keys in `FeatureFlagKeys` + GrowthBook  
- [ ] Child OFF reflows cleanly  
- [ ] No intel API when master OFF  
