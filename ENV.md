# Local environment (am-portfolio)

Run **portfolio-app** on your laptop with secrets from Vault, without committing them.

## Prerequisites

- **Java 17**, **Maven**, **Node.js** (for `npm` / `dotenv-cli`)
- **Python 3** (for `npm run env:preprod` / `env:dev`)
- Network access to VPS-hosted MongoDB, Redis, and public preprod/dev API URLs (or override URLs for local services)

## Files

| File | Git | Purpose |
|------|-----|---------|
| `.env.template` | committed | Placeholders and comments — copy or generate into gitignored files |
| `.env.preprod` | ignored | Preprod → `npm run run:preprod` |
| `.env.dev` | ignored | Dev → `npm run run:dev` |

There is **no** root `.env` file. `dotenv-cli` loads `.env.preprod` or `.env.dev` only.

## End-to-end workflow

### 1. Refresh Vault backup (when secrets or URLs change)

From **am-auth** (needs `scripts/.env` with `VAULT_TOKEN` and VPS kubeconfig):

```bash
cd a:\InfraCode\AM-Portfolio-grp\am-auth
copy scripts\.env.template scripts\.env
# Edit scripts\.env
npm run vault:backup
```

Backup is written to the newest of:

- `a:\InfraCode\AM-Portfolio-grp\VPS\vault\backups\vps_vault_full_backup_<timestamp>.json` (typical when using repo-level VPS tooling)
- `a:\InfraCode\AM-Portfolio-grp\am-auth\vault\backups\vps_vault_full_backup_<timestamp>.json` (default `VAULT_BACKUP_DIR` in am-auth)

### 2. Generate gitignored env files

From **am-portfolio** (uses the **latest** `vps_vault_full_backup_*.json` in those directories):

```bash
cd a:\InfraCode\AM-Portfolio-grp\am-portfolio
npm run env:preprod    # writes .env.preprod from apps/preprod/*
npm run env:dev        # writes .env.dev from apps/dev/*
```

Explicit backup file:

```bash
npm run env:from-vault -- --env preprod --backup a:\InfraCode\AM-Portfolio-grp\VPS\vault\backups\vps_vault_full_backup_20260523_012147.json
```

Manual alternative:

```bash
copy .env.template .env.preprod
copy .env.template .env.dev
# Edit placeholders (YOUR_*)
```

After generation, edit overrides as needed (local ETF parser, local market-data, port).

### 3. Run locally

```bash
cd a:\InfraCode\AM-Portfolio-grp\am-portfolio
npm run run:preprod   # compile reactor (-am) then spring-boot:run with .env.preprod
npm run run:dev       # same with .env.dev
```

Split steps (skip recompile if already built):

```bash
npm run preprod:compile && npm run preprod:start
npm run dev:compile && npm run dev:start
```

App listens on `SERVER_PORT` (default **8072** in template/mapper — avoids Windows port 8080 conflicts). Swagger: `http://localhost:8072/swagger-ui.html`

## npm scripts

| Script | What it does |
|--------|----------------|
| `env:preprod` | Map Vault → `.env.preprod` |
| `env:dev` | Map Vault → `.env.dev` |
| `env:from-vault` | Same mapper; pass `-- --env preprod\|dev` and optional `--backup <path>` |
| `preprod:compile` / `dev:compile` | `mvn -pl portfolio-app -am compile -DskipTests` (builds app + dependencies) |
| `preprod:start` / `dev:start` | `dotenv-cli -e .env.* -- mvn -f portfolio-app/pom.xml spring-boot:run` |
| `run:preprod` / `run:dev` | compile + start |

## Vault → `.env` mapping

| Variable | Vault path | Notes |
|----------|------------|--------|
| `SPRING_PROFILES_ACTIVE` | — | `preprod` or `dev` |
| `SERVER_PORT` | — | Local HTTP port (not in Vault); default `8072` |
| `ETF_API_URL` | `apps/{env}/services/am-parser` → `URL` | Fallback: `https://am.asrax.in/parser` / `https://am-dev.asrax.in/parser` |
| `MARKET_DATA_API_URL` | `apps/{env}/services/am-market-data` → `URL` | In-cluster `http://am-market-data...` rewritten to public gateway URL |
| `BASKET_HOLDINGS_ENRICHMENT` | — | `true` = resolve constituent ISINs via market batch-search |
| `MARKET_DATA_CONNECT_TIMEOUT_MS` | — | HTTP connect timeout (ms) |
| `MARKET_DATA_READ_TIMEOUT_MS` | — | HTTP read timeout (ms); batch-search can be slow |
| `MONGODB_URL` | `apps/{env}/infra/mongodb` → `url` | Mapper appends `/portfolio` and query params if missing |
| `MONGODB_DATABASE` | — | `portfolio` |
| `KAFKA_BOOTSTRAP_SERVERS` | `apps/{env}/infra/kafka` | Mapper forces `kafka.asrax.in:9092` for laptop (Vault has in-cluster host) |
| `KAFKA_USERNAME` / `KAFKA_PASSWORD` | `apps/{env}/infra/kafka` | From Vault |
| `KAFKA_SECURITY_PROTOCOL` / `KAFKA_SASL_MECHANISM` | — | SASL SCRAM defaults |
| `KAFKA_ENABLED` | — | **`false`** for local run (no consumers) |
| `PORTFOLIO_TOPIC` | `apps/{env}/services/am-portfolio` | |
| `PORTFOLIO_CONSUMER_ID` | — | Consumer group id |
| `STOCK_TOPIC` | `apps/{env}/services/am-market-data` → `STOCK_PRICE_UPDATE_TOPIC` | |
| `STOCK_CONSUMER_ID` | — | |
| `MARKET_INDEX_TOPIC` / `MARKET_INDEX_CONSUMER_ID` | — | |
| `REDIS_HOSTNAME` / `REDIS_PORT` / `REDIS_PASSWORD` | `apps/{env}/infra/redis` | VPS Redis (e.g. port `8889`) |
| `JWT_SECRET` | `apps/{env}/services/am-auth` → `JWT_SECRET` | |
| `INTERNAL_SECRET_KEY` | `apps/{env}/services/am-auth` → `SECRET_KEY` | |
| `JWT_ALGORITHM` | — | `HS256` |
| `LOG_LEVEL` | `apps/{env}/services/am-auth` | Stored for parity with Helm; not all levels wired in `application.yml` locally |

Kubernetes deploys use Helm + Vault (`helm/vault-mappings.yaml`), not these `.env` files.

## Common local overrides

Edit `.env.preprod` or `.env.dev` after generation:

```properties
ETF_API_URL=http://localhost:8022
MARKET_DATA_API_URL=http://localhost:8092
KAFKA_ENABLED=false
SERVER_PORT=8072
```

## Blockers

| Issue | Mitigation |
|-------|------------|
| No `vps_vault_full_backup_*.json` | Run `npm run vault:backup` in am-auth; or pass `--backup` |
| Vault backup stale | Re-run backup after secret rotation |
| Mongo/Redis unreachable from laptop | VPN/firewall to VPS; verify host `mongodb.asrax.in` / `redis.asrax.in` |
| Basket enrichment timeouts | Point `MARKET_DATA_API_URL` at reachable API or set `BASKET_HOLDINGS_ENRICHMENT=false` |
| Port in use | Change `SERVER_PORT` |
