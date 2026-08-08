# AM-Parser Dev Outage — Basket Opportunities Empty

**Status:** Open (Dev)  
**Date:** 2026-08-07  
**Symptom:** Baskets UI loads theme chips quickly but shows **“No baskets matched”**.  
**Env:** `am-dev.asrax.in` (`am-apps-dev`)

---

## Summary

| Layer | Status | Notes |
|---|---|---|
| Portfolio catalog | OK | `GET /portfolio/v1/basket/catalog` → 200 (~1.5s) |
| Portfolio opportunities | Empty 200 | Returns `[]` in ~300ms when parser is down |
| **am-parser (Dev)** | **DOWN** | Ingress exists; **no pods** in `am-apps-dev` |
| am-parser (Prod) | Running | Pod in `am-apps-prod` only |

**Root cause:** Dev gateway returns `503 no available server` for all `/parser/*` routes. Portfolio calls `POST {ETF_API_URL}/v1/etf/holdings`, gets 503, enrichment resolves 0 ETFs → empty opportunities.

Portfolio **cannot** fix this alone — holdings data comes from am-parser.

---

## Endpoint map

### Portfolio (UI / gateway)

Base: `https://am-dev.asrax.in/portfolio`

| Method | Path | Role | Auth |
|---|---|---|---|
| GET | `/v1/basket/catalog` | Theme chips + defaultQuery | Optional / as UI sends |
| POST | `/v1/basket/opportunities` | Basket cards | Bearer JWT (UI) |
| POST | `/v1/basket/preview` | Single ETF preview | Bearer JWT |
| POST | `/v1/basket/calculate-quantities` | Qty calc | Bearer JWT |

### Real am-parser (dependency)

Configured in portfolio as `ETF_API_URL` → Dev gateway:

`https://am-dev.asrax.in/parser`

| Method | Path | Role |
|---|---|---|
| GET | `/health` | Liveness |
| GET | `/v1/etf/search?query=…&limit=…` | Symbol/name search |
| POST | `/v1/etf/holdings` | **Used by opportunities** (batch holdings) |
| GET | `/v1/etf/holdings/{symbol}` | Legacy (deprecated) |

Portfolio log line when broken:

```text
POST ETF holdings lookup: N items -> https://am-dev.asrax.in/parser/v1/etf/holdings
Failed batch ETF holdings lookup: 503 Service Unavailable: "no available server"
enrichment.cache=MISS batchSize=N resolved=0
No ETF resolved for query 'NIFTYBEES' after batch lookup
HTTP POST /v1/basket/opportunities -> 200 (~310 ms)
```

---

## Measured timings (2026-08-07, Dev)

| Endpoint | HTTP | Time | Result |
|---|---|---|---|
| `GET …/parser/health` | **503** | ~0.9s | `no available server` |
| `GET …/parser/v1/etf/search?query=NIFTYBEES&limit=1` | **503** | ~1.9s | `no available server` |
| `GET …/portfolio/v1/basket/catalog` | **200** | ~1.5s | Full themes + `NIFTYBEES,BANKBEES,ITBEES` |
| `POST …/portfolio/v1/basket/opportunities` | **200** | ~0.3s | `[]` (fast empty — parser fail-fast) |

**Slow path (when parser is healthy):** opportunities can take longer because of parser holdings + market-data enrichment.  
**Current “fast empty”:** not a performance win — parser is unreachable, so portfolio gives up quickly with no cards.

---

## kubectl evidence

```text
# Dev: no parser pods
kubectl … -n am-apps-dev get pods | grep parser   → (none)

# Prod only
am-apps-prod   am-parser-…   2/2 Running

# Ingress still points Dev host at parser service
am-apps-dev   am-parser   traefik   am-dev.asrax.in
```

---

## UI correlation

1. Local UI (`localhost:9000`) → APIs on `am-dev.asrax.in`
2. Network tab: `catalog` OK → chips render
3. Network tab: `POST /portfolio/v1/basket/opportunities` with Bearer token → **200 + empty body** → “No baskets matched”
4. Not a missing-token issue — auth reaches portfolio; portfolio then fails calling parser

---

## How to re-check (with your token)

Do **not** commit JWTs. Pass token via env:

```powershell
cd am-portfolio
$env:AM_DEV_TOKEN = "<paste Bearer token from DevTools, without 'Bearer ' prefix>"
$env:AM_PORTFOLIO_ID = "7b43596e-12ef-4ddf-bff6-01c17c2f059a"
.\scripts\check-basket-parser-dev.ps1
```

Script prints HTTP status + wall time for catalog, opportunities, and real parser endpoints.

---

## Fix (ops)

1. Deploy / scale **am-parser** into `am-apps-dev` until pods are Ready.
2. Re-run `.\scripts\check-basket-parser-dev.ps1` — parser must be **200**, not 503.
3. Reload Baskets UI — Top picks should show cards.
4. Optional: confirm Redis L2 `basket:etf:enriched:*` warms after first successful call.

---

## Related code

- Portfolio ETF client: `portfolio-basket/.../EtfApiClient.java` → `POST /v1/etf/holdings`
- Enrichment: `EnrichedEtfService.java` (L1 Caffeine + L2 Redis fail-open)
- Catalog (independent of parser): `BasketCatalogService.java`
- Config: `etf.url` / `ETF_API_URL` in `application.yml` / Vault
