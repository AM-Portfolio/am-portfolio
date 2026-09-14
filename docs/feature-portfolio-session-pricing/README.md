# Feature: Portfolio session pricing (live vs last session)

**Status:** Spec pack ready for implementation  
**SoT folder:** `am-portfolio/docs/feature-portfolio-session-pricing/`  
**Scope:** `am-portfolio` + `am-modern-ui` only (no am-market code changes)  
**Branches (suggested):** `hotfix/portfolio-session-pricing` on am-portfolio + am-modern-ui

## How to implement

1. Open [TODO.md](./TODO.md) and work only the **Current phase**.
2. Follow [PHASED-IMPLEMENTATION.md](./PHASED-IMPLEMENTATION.md) for phase gates, deploy, and Postman MCP API loop.
3. Use [plan.md](./plan.md) for gaps, target behavior, and technical design.
4. After each phase: mark TODO items `[x]`, commit, advance Current phase.
5. **Phase D (deploy + API loop) is mandatory** after backend phases complete. Do not start UI (Phase U) until Phase D closed-day API gates pass (or document exception if market is open and closed-day asserts are deferred to next closed day).

## Files

| File | Purpose |
|------|---------|
| [plan.md](./plan.md) | **10/10** final plan — rating, per-API flows, phases |
| [TODO.md](./TODO.md) | Execution cockpit — update after every phase |
| [PHASED-IMPLEMENTATION.md](./PHASED-IMPLEMENTATION.md) | Phase-by-phase impl, prod deploy, Postman MCP verify loop |
| [API-CONTRACTS.md](./API-CONTRACTS.md) | Response fields + Phase D asserts |

## Delivery order

`P0` branch → `P1` calendar clock → `P2` freshness → `P3` closed pricing → `P4` summary reprice + advanced → **`D` deploy+API loop** → `U` UI → `X` exit.

## Exit

On a non-market day: golden portfolio shows last trading day prices and day moves with `AS_OF` (never false `LIVE`).  
On cash open: `LIVE` only with fresh ticks; warm holdings stay fast without full rebuild.
