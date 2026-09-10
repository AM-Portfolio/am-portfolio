# Portfolio Intelligence — REVIEW (verification SoT)

**How to use:** After each phase, complete that section. Do **not** mark [TODO.md](./TODO.md) done until this section is signed.  
**Do not start the next phase until this phase is signed.**

**Refs:** [plan.md](./plan.md) · [UI_SPEC.md](./UI_SPEC.md) · [E2E-BACKEND-PLAN.md](./E2E-BACKEND-PLAN.md) · [API-CONTRACTS.md](./API-CONTRACTS.md) · [PREREQUISITES.md](./PREREQUISITES.md)

**Branches:** `hotfix/portfolio-intelligence-overview` (am-portfolio + am-modern-ui)

---

## Sign-off legend

| Mark | Meaning |
|------|---------|
| `[ ]` | Not verified |
| `[x]` | Verified |
| `N/A` | Waived with reason |

---

## P0 — Environment / branches

- [x] `am-portfolio` on `hotfix/portfolio-intelligence-overview` (from main, pushed)  
- [x] `am-modern-ui` on `hotfix/portfolio-intelligence-overview` (from main, pushed)  
- [ ] JBR path confirmed for Maven  
- [ ] Docs folder SoT present on portfolio branch (this pack)  

**Notes:** P0 branches created 2026-09-08 after hooks.json removed. Remaining: JBR + docs pack confirm.

**Signed by:** ________ **Date:** ________

---

## PRE — Prerequisites

- [ ] Postman/curl PROD `https://am.asrax.in/portfolio` (summary + advanced)  
- [ ] Bearer JWT valid for golden `portfolioId`  
- [ ] Grafana / `.am` probes or kubectl logs reachable  
- [ ] `deploy-prod.ps1` + user-confirm process confirmed  
- [ ] Engines owned by **am-portfolio** only  
- [ ] D15 flag key names noted for GrowthBook  

**Notes:** ________________________________

**Signed by:** ________ **Date:** ________

---

## P1 — Ownership / security

- [ ] Non-owner `portfolioId` on `POST .../advanced` → **403**  
- [ ] Same assert on intelligence / stress / what-if (when exist)  
- [ ] Regression test fails if assert removed  
- [ ] No IDOR via UUID guessing  

**Notes:** ________________________________

**Signed by:** ________ **Date:** ________

---

## P2 — Snapshot + contracts

- [ ] Snapshot weights sum ~100% for fixture  
- [ ] Data-quality flags present  
- [ ] DTOs + OpenAPI; local JBR build green  
- [ ] No new Mongo collections  

**Notes:** ________________________________

**Signed by:** ________ **Date:** ________

---

## P3 — Health + Risk (BE) — PROD gate

- [ ] HealthScoreEngine golden tests pass  
- [ ] RiskRadarEngine golden tests pass  
- [ ] Missing-history omits Vol/Beta / renormalizes  
- [ ] Deployed via `deploy-prod.ps1` after user confirm  
- [ ] `POST .../intelligence` 200; health 0–100; components[]; risk axes  
- [ ] Repeat call stable ±1 within same window  
- [ ] Errors searchable in Loki/kubectl  

**PROD verified at:** ________ **By:** ________

**Signed by:** ________ **Date:** ________

---

## P4 — X-Ray (BE) — PROD gate

- [ ] Sector / Industry / Cap on intelligence  
- [ ] PROD parity vs advanced allocation (documented tolerance)  
- [ ] Unauthorized → 403  

**PROD verified at:** ________ **By:** ________

**Signed by:** ________ **Date:** ________

---

## P5 — Stress (BE) — PROD gate

- [ ] Presets + custom scenario  
- [ ] Shock signs sensible for long book  
- [ ] Labeled as estimate (contract/UI copy ready)  

**PROD verified at:** ________ **By:** ________

**Signed by:** ________ **Date:** ________

---

## P6 — What-If (BE) — PROD gate

- [ ] Add / Modify / Switch before/after  
- [ ] No Mongo write proven  
- [ ] Unauthorized → 403  

**PROD verified at:** ________ **By:** ________

### Backend band gate

- [ ] **P3–P6 PROD-signed** → UI (P7) may start  

**Signed by:** ________ **Date:** ________

---

## P7 — UI web (≥1100) + flags

- [ ] Health / Risk / X-Ray / Stress / What-If from API (not hardcoded) when flags ON  
- [ ] Layout matches [final-overview-page.png](./final-overview-page.png) / UI_SPEC web grid  
- [ ] Performance chart visual unchanged  
- [ ] AM tokens / glass preserved  
- [ ] D15 keys in code + GrowthBook flags exist  
- [ ] Master OFF → Allocation + no intel widgets + no intel fetches  
- [ ] X-Ray ON → no duplicate Allocation  
- [ ] Verified @ ~1280 width  

**Verified at:** ________ **By:** ________

**Signed by:** ________ **Date:** ________

---

## P8 — UI tablet + phone

- [ ] Tablet 2-col layout per UI_SPEC (@ ~768)  
- [ ] Phone stack + collapsed Stress/What-If (@ ~390)  
- [ ] Drill-down sheets work and respect flags  
- [ ] Same master kill-switch behavior as P7  

**Verified at:** ________ **By:** ________

**Signed by:** ________ **Date:** ________

---

## P9 — Report payload (this release)

- [ ] Weekly + Monthly report DTO/builder  
- [ ] Health in report matches intelligence for same asOf  
- [ ] PROD preview/build verified  
- [ ] **No PDF / no email** in this release  

**PROD verified at:** ________ **By:** ________

**Signed by:** ________ **Date:** ________

---

## P10 — PDF + email (NEXT RELEASE)

- [ ] N/A for this hotfix Final E2E (deferred)  

---

## P11 — Quality / ops

- [ ] Engine + ownership tests green  
- [ ] Flutter analyze clean on touched files  
- [ ] Optional Redis fail-open (if shipped)  
- [ ] Micrometer / Grafana check  
- [ ] No secrets in git diff  
- [ ] market / core / trade **unchanged**  

**Notes:** ________________________________

**Signed by:** ________ **Date:** ________

---

## Final E2E review

### Journey

- [ ] Login → Portfolio → Overview loads  
- [ ] Metrics + Performance chart still correct  
- [ ] Flags ON: Health matches PROD intelligence  
- [ ] Risk / X-Ray / Stress / What-If spot-checks pass  
- [ ] What-If does not persist holdings  
- [ ] Web + tablet + phone usable per UI_SPEC  
- [ ] Master flag OFF → safe legacy Overview (Allocation back)  
- [ ] Report payload (P9) available for weekly + monthly  

### Sign-off

**Final E2E Pass — Signed by:** ________ **Date:** ________

Then set TODO **Current phase** → `DONE`.
