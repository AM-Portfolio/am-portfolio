# Feature docs — Smart Baskets Discover

| File | Role |
|------|------|
| **[plan.md](./plan.md)** | FINAL UX plan (web + mobile, M0–M6) |
| **[plan-v2-latency.md](./plan-v2-latency.md)** | **FINAL latency SoT** (Discover ≤1s, D-L1–13, improve-loop) |
| [TODO.md](./TODO.md) | UX + Latency v2 checkboxes |
| **[REVIEW.md](./REVIEW.md)** | Post-implementation verifier |
| [architecture.drawio](./architecture.drawio) | E2E diagram |
| [architecture.dsl](./architecture.dsl) | Text companion |

**Postman:** `am-portfolio/postman/AM_Basket_Current_PROD_Discover.postman_collection.json` (cloud: AM Basket — Current (PROD Discover)).

**DEV (2026-10-17):** Warm Discover pass — p95 ~398ms, match ITBEES=9.09 / NIFTYBEES=1.96.  
**Next:** PROD M-L4 when asked; keep Vault `MARKET_DATA_API_URL` in-cluster for Kind DEV.
