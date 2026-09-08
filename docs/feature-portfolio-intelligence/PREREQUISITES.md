# Prerequisites — before Portfolio Intelligence code

**Gate:** Do not start P0 until the **Must-pass** section is green.  
**Fallbacks** are OK when MCP is down (Postman desktop + `.am` Grafana probes).

---

## Must-pass (P0 ready)

### Docs

- [x] Folder SoT: `docs/feature-portfolio-intelligence/`
- [x] `plan.md` · `TODO.md` · `REVIEW.md` · `architecture.drawio` (no duplicate XML/md diagram)

### Ownership decision

- [x] Engines live in **am-portfolio** only (Dashboard/core do not own Health/Risk/Stress/What-If)

### Build / deploy

- [x] `am-portfolio/deploy-prod.ps1` exists
- [x] Use Android Studio JBR for Maven: `C:\Program Files\Android\Android Studio\jbr`
- [ ] Operator confirms: will use **user confirm → deploy-prod.ps1** (not `am deploy` if auth fails)

### Postman / PROD API loop

| Asset | Path |
|-------|------|
| Overview collection | `postman/AMPortfolio_Overview_Dev.postman_collection.json` |
| PROD env | `postman/AM_Basket_PROD.postman_environment.json` (`portfolioBase=https://am.asrax.in/portfolio`) |

- [ ] Import collection + PROD env (Postman app **or** MCP when healthy)
- [ ] Fresh **Bearer JWT** (replace expired tokens)
- [ ] Golden `portfolioId` (owner = JWT user)
- [ ] PROD smoke OK:
  - [ ] `GET .../v1/portfolios/summary?portfolioId=…`
  - [ ] `POST .../v1/analytics/portfolio/{id}/advanced`
- [ ] Empty Postman folder ready: `Portfolio Intelligence PROD` (for new APIs later)

### Logs / Grafana (for PROD fix loops)

| Tool | Path |
|------|------|
| Load env | `axrax-v1/.am/load-env.ps1` |
| Creds | `axrax-v1/.am/credentials.d/observability.env` (`GRAFANA_URL`, `GRAFANA_SERVICE_ACCOUNT_TOKEN`) |
| Probes | `.am/probe-prom-only.js`, `.am/probe-prod-deep.js` |
| Wrapper | `.am/mcp-grafana-wrapper.js` |

- [ ] `observability.env` present on laptop
- [ ] At least one log path works today: **Loki via Grafana** OR `kubectl -n am-apps-prod logs … portfolio`

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

## Exit → P0

When Must-pass Postman smoke + log path are checked:

1. Sign **PRE** in [REVIEW.md](./REVIEW.md)  
2. Tick P-PRE in [TODO.md](./TODO.md)  
3. Start **P0 — Owner assert** on analytics `{portfolioId}`
