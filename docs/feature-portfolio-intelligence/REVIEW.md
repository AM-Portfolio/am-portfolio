# Portfolio Intelligence — REVIEW (verification SoT)

**How to use:** After each phase, check that phase section. Do **not** mark DONE in [TODO.md](./TODO.md) until REVIEW is complete.  
At the end, complete **Final E2E review**.

**Refs:** [plan.md](./plan.md) · [E2E-BACKEND-PLAN.md](./E2E-BACKEND-PLAN.md) · [PREREQUISITES.md](./PREREQUISITES.md)

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

- [ ] `am-portfolio` on `hotfix/portfolio-intelligence-overview` (from main)
- [ ] `am-modern-ui` on `hotfix/portfolio-intelligence-overview` (from main)
- [ ] JBR path confirmed for Maven
- [ ] Docs folder SoT present on portfolio branch

**2026-09-08:** Agent could not run git — Cursor hook `block_dangerous_shell.py` fail-closed (no output). Branches **not** created by agent. User/agent recheck after hook fix or manual create.

**Notes:** ________________________________

---

## PRE — Prerequisites

- [ ] Postman/curl PROD `https://am.asrax.in/portfolio` (summary + advanced)
- [ ] Bearer JWT valid for golden `portfolioId`
- [ ] Grafana/`observability.env` or kubectl logs reachable
- [ ] `deploy-prod.ps1` path confirmed
- [ ] Engines owned by **am-portfolio** only

**Notes:** ________________________________

---

## P1 — Ownership / security

- [ ] Non-owner `portfolioId` on `POST .../advanced` → **403**
- [ ] Same assert on intelligence / stress / what-if (when exist)
- [ ] Regression test fails if assert removed
- [ ] No IDOR via UUID guessing

**Notes:** ________________________________

---

## P2 — Snapshot + contracts

- [ ] Snapshot weights sum ~100% for fixture
- [ ] Data-quality flags present
- [ ] DTOs + OpenAPI; local JBR build green
- [ ] No new Mongo collections

**Notes:** ________________________________

---

## P3 — Health + Risk (BE) — PROD gate

- [ ] HealthScoreEngine golden tests pass (E2E-BACKEND-PLAN formulas)
- [ ] RiskRadarEngine golden tests pass
- [ ] Missing-history omits Vol/Beta / renormalizes
- [ ] Deployed via `deploy-prod.ps1` after user confirm
- [ ] `POST .../intelligence` 200; health 0–100; components[]; risk axes
- [ ] Repeat call stable ±1 within same window
- [ ] Errors searchable in Loki/kubectl

**PROD verified at:** ________ **By:** ________

---

## P4 — X-Ray fields (BE) — PROD gate

- [ ] Sector / Industry / Market Cap on intelligence
- [ ] PROD weights align with `.../advanced` within rounding
- [ ] Asset Class not shipped (disabled / coming soon only)

**PROD verified at:** ________ **By:** ________

---

## P5 — Stress (BE) — PROD gate

- [ ] Presets: NIFTY −10/−20, Banking −20, IT −15, Crash pack
- [ ] Negative shock → negative impact for long book
- [ ] Labeled **scenario estimates**
- [ ] No Mongo mutation

**PROD verified at:** ________ **By:** ________

---

## P6 — What-If (BE) — PROD gate

- [ ] Add / Modify / Switch before/after
- [ ] No Mongo write proven
- [ ] Unauthorized → 403

**PROD verified at:** ________ **By:** ________

### Backend band gate

- [ ] **P3–P6 PROD-signed** → UI (P7) may start

---

## P7 — UI desktop Overview

- [ ] Health / Risk / X-Ray / Stress / What-If from API (not hardcoded)
- [ ] Layout Image 1: KPIs → Chart|Health → Movers|Risk → X-Ray|Stress|What-If
- [ ] Performance chart visual unchanged
- [ ] AM tokens / glass / sidebar preserved

**Verified at:** ________ **By:** ________

---

## P8 — UI mobile / tablet

- [ ] Priority stack + collapsible Stress/What-If
- [ ] Breakpoints match existing Overview

**Verified at:** ________ **By:** ________

---

## P9 — Report payload (this release)

- [ ] Weekly + Monthly report DTO/builder
- [ ] Health in report matches intelligence for same asOf
- [ ] PROD preview/build verified
- [ ] **No PDF / no email** in this release

**PROD verified at:** ________ **By:** ________

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

---

## Final E2E review

### Journey

- [ ] Login → Portfolio → Overview loads
- [ ] Metrics + Performance chart still correct
- [ ] Health matches PROD intelligence
- [ ] Risk / X-Ray / Stress / What-If spot-checks pass
- [ ] What-If does not persist holdings
- [ ] Report payload (P9) available for weekly + monthly

### Sign-off

| Role | Name | Date | Result |
|------|------|------|--------|
| Implementer | | | Pass / Fail |
| Reviewer | | | Pass / Fail |

**Release decision:** Ready for merge / Not ready
