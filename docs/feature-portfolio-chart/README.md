# Feature: Portfolio chart (overlay comparison)

**Status:** Documentation / architecture pack (SoT for how Dashboard + Portfolio overview charts load).  
**SoT folder:** `am-portfolio/docs/feature-portfolio-chart/`  
**Kind:** `feature` (docs pack — no product code in this slice)

## How to read

1. [PLAN.md](./PLAN.md) — goal, three-service catalog, journeys, scorecard  
2. [FLOW.md](./FLOW.md) — build job + UI read path  
3. [DATA-POINTS.md](./DATA-POINTS.md) — every chart field  
4. [API-CONTRACTS.md](./API-CONTRACTS.md) — HTTP catalog by service  
5. [UI-MAPPING.md](./UI-MAPPING.md) — Dashboard vs Portfolio mounts  
6. [OPS.md](./OPS.md) — BUILDING, base-URL footguns, probes  
7. [architecture.drawio](./architecture.drawio) — 7 sheets (Context … Failures)

## Three backends (do not collapse)

| Series / concern | Service | Gateway prefix | Endpoint |
|------------------|---------|----------------|----------|
| Wealth / Overall / book lines | **am-portfolio** | `/portfolio` | `/v1/portfolios/history`, `/intraday`, `/history/status` |
| NIFTY / SENSEX overlays | **am-market** | `/market` | `/v1/analysis/historical-charts` |
| Dashboard KPI cards | **am-analysis** (core-services) | `/analysis` | `/v1/analysis/dashboard/summary` |
| Legacy single performance line | **am-analysis** | `/analysis` | `/v1/analysis/dashboard/performance` (orphaned in modern-ui overlay) |

Same path prefix `/v1/analysis/...` exists on **market** and **core analysis** — routing is by **host/baseUrl**, not path alone.

## Related packs

- TF / 1D UX: [am-modern-ui/docs/overview-chart-timeframe-sync/PLAN.md](../../../am-modern-ui/docs/overview-chart-timeframe-sync/PLAN.md)  
- Dig evidence samples: [../chart-mcp-evidence/REPORT.md](../chart-mcp-evidence/REPORT.md)

## Files

| File | Purpose |
|------|---------|
| [PLAN.md](./PLAN.md) | am-requirement plan |
| [TODO.md](./TODO.md) | Execution cockpit |
| [FLOW.md](./FLOW.md) | End-to-end flows |
| [API-CONTRACTS.md](./API-CONTRACTS.md) | HTTP contracts |
| [DATA-POINTS.md](./DATA-POINTS.md) | Field catalogs |
| [UI-MAPPING.md](./UI-MAPPING.md) | Flutter wiring |
| [OPS.md](./OPS.md) | Ops / debug |
| [architecture.drawio](./architecture.drawio) | Multi-sheet architecture |
| [images/](./images/) | Attachments only |
