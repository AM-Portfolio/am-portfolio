# Portfolio session pricing — TODO (execution cockpit)

**Current phase:** `D-LOOP` (iter 4 image pushed; kube API down — pending set-image)  
**Plan rating:** 10/10 — [plan.md](./plan.md)  
**Branch:** `hotfix/portfolio-session-pricing`  
**Latest image:** `ghcr.io/sahim99/am-portfolio:local-9992026`

**PROD:** `https://am.asrax.in/portfolio`  
**Golden portfolioId:** `f969745c-f492-4b86-88ed-6588e9f28bb3`  
**Max Phase D iterations:** 5

---

## P0–P4

- [x] Branch + calendar clock + freshness + closed prior-close + summary reprice

---

## D — Prod deploy + Postman MCP loop

- [x] Build/push (sahim99 workaround; org GHCR denied)
- [ ] Deploy prod tip `local-9992026` (blocked: kube API `203.174.22.129:6443` refused)
- [x] Calendar root cause found: Vault `MARKET_DATA_API_URL=https://am.asrax.in/market` breaks calendar; fixed via `calendar-base-url=http://am-market-data:8080`
- [ ] Matrix gates after cutover

### Phase D log

| Iter | Image tag | Holdings cold ms | Holdings warm ms | freshness | sessionDate | summary OK | movers g/l | Pass? | Notes |
|------|-----------|------------------|------------------|-----------|-------------|------------|------------|-------|-------|
| 1 | local-f66e415 | 9376 | 2371 | AS_OF | 2026-09-11 | yes | 3/2 | N | Calendar timeout 200ms; hist repair every req |
| 2 | local-d48bef4 | 13076 | 3774–4220 | AS_OF | 2026-09-11 | yes | 3/2 | N | Still public MARKET_DATA_API_URL for calendar |
| 3 | local-e4615d2 | 57140 | 3503–13971 | AS_OF | 2026-09-11 | yes | 3/2 | N | Confirmed Vault URL; cold hist thrash |
| 4 | local-9992026 | — | — | — | — | — | — | — | Image pushed; **kubectl API refused** — set image when VPS up |
| 5 | | | | | | | | | |

**In-cluster calendar (wget):** `open=false reason=HOLIDAY` for 2026-09-14.

---

## U / X

- [ ] modern-ui after D gates
- [ ] Exit
