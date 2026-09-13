# Prerequisites — before Portfolio Intelligence BE (P1+)

**Gate:** Do not start **P1** until the **Must-pass** section is green (P0 branches already exist).  
**Fallbacks** are OK when MCP is down (Postman desktop + `.am` Grafana probes).  
**Related:** [TODO.md](./TODO.md) · [REVIEW.md](./REVIEW.md) · [plan.md](./plan.md) D15 flags.

---

## Must-pass (P-PRE / P1 ready)

### Docs

- [x] Folder SoT: `docs/feature-portfolio-intelligence/`
- [x] `plan.md` · `TODO.md` · `REVIEW.md` · `architecture.drawio` (no duplicate XML/md diagram)

### Ownership decision

- [x] Engines live in **am-portfolio** only (Dashboard/core do not own Health/Risk/Stress/What-If)

### Postman / API loop (P-PRE)

| Asset | Path |
|-------|------|
| Overview collection | `postman/AMPortfolio_Overview_Dev.postman_collection.json` |
| PROD env | `postman/AM_Basket_PROD.postman_environment.json` (`portfolioBase=https://am.asrax.in/portfolio`) |
| **PRE smoke base (this workstream)** | `https://am-preprod.asrax.in/portfolio` |
| **Golden portfolioId (preprod)** | `c7ef8e22-9d98-44fd-a500-b7bf770597b5` |

- [x] Fresh **Bearer JWT** (preprod realm) — never commit  
- [x] Golden `portfolioId` (owner = JWT `sub`)  
- [x] Preprod smoke OK (2026-09-10):
  - [x] `GET .../v1/portfolios/summary?portfolioId=…` → 200  
  - [x] `POST .../v1/analytics/portfolio/{id}/advanced` → 200  
- [ ] Empty Postman folder ready: `Portfolio Intelligence PREPROD` (for new APIs later)
- [ ] Operator confirms: will use **user confirm → deploy-prod.ps1** for **prod** loops (not `am deploy` if auth fails) — standing rule accepted for P-PRE

### Build / deploy

- [x] `am-portfolio/deploy-prod.ps1` exists
- [x] Use Android Studio JBR for Maven: `C:\Program Files\Android\Android Studio\jbr`
- [x] Operator confirms: will use **user confirm → deploy script** (prod = `deploy-prod.ps1`)

### Logs / Grafana (for PROD fix loops)

| Tool | Path |
|------|------|
| Load env | `axrax-v1/.am/load-env.ps1` |
| Creds | `axrax-v1/.am/credentials.d/observability.env` (`GRAFANA_URL`, `GRAFANA_SERVICE_ACCOUNT_TOKEN`) |
| Probes | `.am/probe-prom-only.js`, `.am/probe-prod-deep.js` |
| Wrapper | `.am/mcp-grafana-wrapper.js` |

- [x] `observability.env` present on laptop
- [x] At least one log path works today: **Loki via Grafana** (`.am` probes 2026-09-10). kubectl context optional.

---

## MCP connection check (this session)

Checked Cursor dynamic MCP namespaces:

| MCP | Status | Action |
|-----|--------|--------|
| `user-postman` | **error** (discovery failed) | Re-auth in Cursor MCP settings / run `mcp_auth`; until then use **Postman desktop** |
| `user-asrax` | **loading / error** | Re-auth; Grafana probes via `.am` scripts still usable |
| `user-am-mcp-server` | **error** | Re-auth if needed for Java MCP; **not required for P0** |

- [ ] Postman MCP green **or** desktop Postman confirmed (either satisfies PRE)
- [ ] Grafana access green via MCP **or** `.am` probe/Loki (either satisfies PRE)

**P0 does not block on MCP** if Postman desktop + kubectl/Loki work.

---

## Waived / N/A

| Item | Status |
|------|--------|
| am-market code changes | N/A — NO CHANGE |
| am-core-services engines | N/A — keep client-only |
| New Mongo collections | N/A — none for MVP |

---

## Exit → P1

When Must-pass Postman smoke + log path are checked:

1. Sign **PRE** in [REVIEW.md](./REVIEW.md)  
2. Tick P-PRE in [TODO.md](./TODO.md); set Current phase → `P1`  
3. Start **P1 — Owner assert** on analytics `{portfolioId}`
