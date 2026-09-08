# UI Spec — Overview vs mockup (Image 1)

**Plan:** [plan.md](./plan.md) §4  
**SoT image:** final Overview mockup (Image 1)  
**Goal:** After P7–P8, Overview **structure** matches Image 1. Styling = **AM design system** (not pixel-clone of mock pink/green).

---

## Desktop layout (Image 1)

```text
┌─────────────────────────────────────────────────────────────┐
│  KPI: Total Return | Today P&L | Total Balance | Invested   │
├──────────────────────────────┬──────────────────────────────┤
│  Performance chart           │  Health Score                │
│  (EXISTING — do not restyle) │  gauge + 8 rows + Details    │
├──────────────────────────────┼──────────────────────────────┤
│  Top Movers                  │  Risk Radar                  │
│  Gainers | Losers            │  spider + findings chips     │
├──────────────┬───────────────┴──────────────┬───────────────┤
│  Portfolio   │  Stress Test                 │  What-If      │
│  X-Ray       │  presets + custom            │  Simulator    │
└──────────────┴──────────────────────────────┴───────────────┘
```

**Row summary:** KPIs → Chart|Health → Movers|Risk → X-Ray|Stress|What-If

---

## Mobile

Metrics → Health → Chart → Risk → Movers → X-Ray → Stress (collapsed) → What-If (collapsed).

---

## Widget checklist (P7 REVIEW)

| Widget | Data source | Must show |
|--------|-------------|-----------|
| KPI ×4 | summary API | Existing values/behavior |
| Chart | history/intraday | Unchanged interactions |
| Health | intelligence.health | Score, band, 8 components, View Details |
| Risk | intelligence.risk | Axes + severity chips, View Risk Analysis |
| Movers | advanced (or intel) | Gainers / Losers |
| X-Ray | intelligence.xray | Sector/Industry/Cap; Asset Class disabled |
| Stress | stress API | Presets + custom; “Scenario estimate” |
| What-If | what-if API | Add/Modify/Switch → before/after |

---

## Explicitly NOT required to match Image 1 pixels

- Exact pink/green mock palette (use `ModuleColors.portfolio`)
- Exact mock typography / spacing
- Mock sample numbers (78, 31.4%, …) — use live API
- Working Asset Class donut (coming soon only)
- PDF / email (P10 next release)

---

## Empty / error / loading

| State | Behavior |
|-------|----------|
| Intelligence loading | Skeletons on Health/Risk/X-Ray; KPIs+chart still work |
| Intelligence fail | Keep KPIs+chart; retry on intel cards |
| Stress/What-If fail | Inline error; do not blank Overview |

---

## Pass criteria

- [ ] Desktop rows match Image 1 grid above  
- [ ] Health alone beside chart (not Risk/What-If stacked there)  
- [ ] What-If on bottom row with X-Ray + Stress  
- [ ] No duplicate Allocation card  
- [ ] Chart not redesigned  
- [ ] All intel widgets bound to PROD APIs  
