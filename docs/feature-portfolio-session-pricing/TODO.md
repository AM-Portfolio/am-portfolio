# Portfolio session pricing — TODO (execution cockpit)

**Current phase:** `D-DONE` (iter 4 gates mostly green on holiday)  
**Plan rating:** 10/10 — [plan.md](./plan.md)  
**Branch:** `hotfix/portfolio-session-pricing`  
**Live image:** `ghcr.io/sahim99/am-portfolio:local-9992026`

**PROD:** `https://am.asrax.in/portfolio`  
**Golden portfolioId:** `f969745c-f492-4b86-88ed-6588e9f28bb3`  
**Max Phase D iterations:** 5

---

## P0–P4

- [x] Branch + calendar clock + freshness + closed prior-close + summary reprice

---

## D — Prod deploy + Postman MCP loop

- [x] Build/push (sahim99 workaround)
- [x] Deploy `local-9992026`; health 200
- [x] Calendar: in-cluster HOLIDAY; `calendar-base-url` bypasses Vault public URL
- [x] Matrix (holiday 2026-09-14)

### Phase D log

| Iter | Image tag | Holdings cold ms | Holdings warm ms | freshness | sessionDate | summary OK | movers g/l | Pass? | Notes |
|------|-----------|------------------|------------------|-----------|-------------|------------|------------|-------|-------|
| 1 | local-f66e415 | 9376 | 2371 | AS_OF | 2026-09-11 | yes | 3/2 | N | Calendar via public URL timeout |
| 2 | local-d48bef4 | 13076 | ~4k | AS_OF | 2026-09-11 | yes | 3/2 | N | same |
| 3 | local-e4615d2 | 57k | 3.5–14k | AS_OF | 2026-09-11 | yes | 3/2 | N | Vault URL confirmed |
| 4 | local-9992026 | **1333** | **564 / 835** | **AS_OF** | **2026-09-11** | **yes** | **3/2** | **Y*** | cold≤2s; warm≈RTT; holiday flat movers sparse |

\*Pass for closed-day freshness + latency. Movers sparse (5 nonzero day%) expected on holiday flat book.

---

## U — modern-ui

- [ ] sessionDate DTO/mapper; As of `{date} HH:mm`
- [ ] Overview freshness
- [ ] Gate 1D chart poll
- [ ] PR

---

## X — Exit

- [ ] PRs linked; Current phase = `DONE`
