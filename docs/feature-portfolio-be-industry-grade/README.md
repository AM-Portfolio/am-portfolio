# Feature: Portfolio BE industry-grade (API + market-data contract)

**Status:** Plan v2 FINAL (**10/10**) — ready to implement from [TODO.md](./TODO.md)  
**SoT:** `am-portfolio/docs/feature-portfolio-be-industry-grade/`

## How to implement

1. Read [plan.md](./plan.md) v2 (why v1 was 7/10, locked fixes, phases).  
2. Read [PROBLEMS.md](./PROBLEMS.md) for evidence.  
3. Execute only **Current phase** in [TODO.md](./TODO.md); mark `[x]` when gate passes.  
4. Use [architecture.drawio](./architecture.drawio) (includes Problem-P6-BasketPreview).  
5. Do not claim heatmap/preview fixed without PROD gates.  
6. After feature phases, run **T1** (PROD E2E test ↔ fix ↔ redeploy) until the API matrix is perfect for UI, developer, and latency.

## Files

| File | Purpose |
|------|---------|
| [plan.md](./plan.md) | **v2 FINAL 10/10** plan |
| [TODO.md](./TODO.md) | Phase cockpit |
| [PROBLEMS.md](./PROBLEMS.md) | Root causes + best fixes |
| [T1-BASELINE.md](./T1-BASELINE.md) | PROD E2E matrix log (test↔fix↔redeploy) |

## Related packs

- `feature-portfolio-session-pricing` (CashSessionClock — reuse, don’t rebuild)  
- `feature-basket-discover` / `plan-v2-latency.md` (Discover vs Preview FULL)  
- `feature-portfolio-intelligence` (isolate latency only)
