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
- [x] JBR path confirmed for Maven (`...\Android Studio\jbr\bin\java.exe`)  
- [x] Docs folder SoT present on portfolio branch (this pack, incl. `final-overview-page.png`)  

**Notes:** P0 branches created 2026-09-08 after hooks.json removed. Docs FINAL pack pushed `69d1cbd`.

**Signed by:** agent+user ________ **Date:** 2026-09-10 (JBR/docs — full P0 sign when you confirm)

---

## PRE — Prerequisites

- [x] Preprod curl `https://am-preprod.asrax.in/portfolio` — summary + advanced **200** (2026-09-10)  
- [x] Bearer JWT valid for golden `portfolioId` `c7ef8e22-9d98-44fd-a500-b7bf770597b5` (owner `9ee1b823-…`)  
- [x] Grafana / `.am` probes reachable  
- [x] Deploy process = user confirm → script (`deploy-prod.ps1` for prod; preprod via existing deploy path)  
- [x] Engines owned by **am-portfolio** only  
- [x] D15 flag key names noted for GrowthBook  

**Notes:** PRE validated on **preprod** (JWT from `am-preprod-realm`). Prod gateway was 503 earlier; P3+ PROD loops still planned per D11 when ready. Token not stored in git.

**Signed by:** agent (smoke) + user (token/env) **Date:** 2026-09-10

---

## P1 — Ownership / security

- [x] Non-owner `portfolioId` on `POST .../advanced` → **403** (unit: `PortfolioAnalyticsControllerTest`)  
- [x] Same assert helper ready for intelligence / stress / what-if (`PortfolioOwnerAssert`)  
- [x] Regression test fails if assert removed (facade never called on 403)  
- [x] No IDOR via UUID guessing (404 if missing; 403 if other owner)  

**Notes:** Deploy to preprod not done in P1 — verify live 403 after next deploy. Local JBR build + tests green 2026-09-10.

**Signed by:** agent **Date:** 2026-09-10

---

## P2 — Snapshot + contracts

- [x] Snapshot + factory implemented (`PortfolioIntelligenceSnapshotFactory`)  
- [x] DTOs + controller OpenAPI; local JBR build green  
- [x] No new Mongo collections  
- [ ] Live weight sum ~100% on golden portfolio after deploy  

**Notes:** Code complete 2026-09-10. Live stamp after deploy.

**Signed by:** agent (code) **Date:** 2026-09-10

---

## P3 — Health + Risk (BE) — PROD gate

- [x] HealthScoreEngine golden tests pass (64 Watch fixture)  
- [x] RiskRadarEngine implemented  
- [x] Missing-history omits Vol/Beta / renormalizes (`historyPoints=0` shortcut)  
- [ ] Deployed after user confirm  
- [ ] Live `POST .../intelligence` 200 validated  
- [ ] Repeat call stable ±1  
- [ ] Errors searchable in Loki  

**PROD/preprod verified at:** ________ **By:** ________

**Signed by:** agent (code) **Date:** 2026-09-10 — **live gate open**

---

## P4 — X-Ray (BE) — PROD gate

- [x] Sector / Industry / Cap on intelligence (`XRaySummaryBuilder`)  
- [ ] Live parity vs advanced after deploy  
- [x] Unauthorized → 403 (owner assert on intelligence)  

**PROD/preprod verified at:** ________ **By:** ________

**Signed by:** agent (code) **Date:** 2026-09-10 — **live gate open**

---

## P5 — Stress (BE) — PROD gate

- [x] Presets + custom scenario (`StressEngine`)  
- [ ] Live shock signs after deploy  
- [x] Labeled Scenario estimate  

**PROD/preprod verified at:** ________ **By:** ________

**Signed by:** agent (code) **Date:** 2026-09-10 — **live gate open**

---

## P6 — What-If (BE) — PROD gate

- [x] Add / Modify / Switch before/after (`WhatIfEngine`)  
- [x] No Mongo write (in-memory clone only)  
- [x] Unauthorized → 403 on route  

**PROD/preprod verified at:** ________ **By:** ________

### Backend band gate

- [x] **P3–P6 code ready** → UI shipped behind flags (default OFF)  
- [ ] **Live P3–P6 signed** after deploy  

**Signed by:** agent (code) **Date:** 2026-09-10

---

## P7 — UI web (≥1100) + flags

- [x] Widgets + API clients when flags ON  
- [x] Layout code matches UI_SPEC web grid  
- [x] Performance chart visual unchanged  
- [x] D15 keys in `FeatureFlagKeys` (fail-closed)  
- [x] Master OFF → Allocation path in code  
- [ ] GrowthBook flags created in console  
- [ ] Verified @ ~1280 with live BE  

**Verified at:** ________ **By:** ________

**Signed by:** agent (code) **Date:** 2026-09-10 — **visual gate open**

---

## P8 — UI tablet + phone

- [x] Tablet 2-col layout in code  
- [x] Phone stack + collapsed Stress/What-If in code  
- [x] Drill-down sheets present  
- [ ] Device verify @768 / @390  

**Verified at:** ________ **By:** ________

**Signed by:** agent (code) **Date:** 2026-09-10 — **device gate open**

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
