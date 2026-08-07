# am-portfolio

Java monorepo for the portfolio service (`portfolio-app` and supporting modules).

## Prerequisites

- Java 17, Maven, Node.js
- Python 3 (only for Vault → env mapping)
- Network access to VPS MongoDB / Redis (or local overrides)

Local secrets live in gitignored `.env.dev` / `.env.preprod`. See [ENV.md](ENV.md) for Vault backup → env generation.

## Quick start (dev)

```bash
cd am-portfolio
npm run env:dev          # once: map Vault backup → .env.dev
npm run run:dev          # compile reactor + start (first time / after code changes)
```

App: http://localhost:8072  
Swagger: http://localhost:8072/swagger-ui.html  
Health: http://localhost:8072/actuator/health

Local Kafka is off by default (`KAFKA_ENABLED=false` in generated env).
Local tracing export is off by default (`TRACING_ENABLED=false`). `OTEL_SDK_DISABLED` alone does not stop Spring Micrometer from trying `localhost:4318`.

## Scripts: with build vs without build

### With build (compile then start)

Use after a fresh clone, dependency changes, or when you changed Java sources across modules.

| Command | What it does |
|---------|----------------|
| `npm run run:dev` | `dev:compile` then `dev:start` (loads `.env.dev`) |
| `npm run run:preprod` | `preprod:compile` then `preprod:start` (loads `.env.preprod`) |
| `npm run dev:compile` | Reactor compile only: `mvn -pl portfolio-app -am compile -DskipTests` |
| `npm run preprod:compile` | Same compile for preprod workflow |

`*:compile` builds `portfolio-app` **and** required modules (`-am`). That is the long step you see walking modules like `portfolio-market-data`.

### Without build (start only)

Use when classes are already compiled and you only want to restart the app.

| Command | What it does |
|---------|----------------|
| `npm run run:dev -- --skip-build` | Start only (same as skip compile). Alias flag: `--no-build` |
| `npm run run:preprod -- --skip-build` | Start only with `.env.preprod` |
| `npm run start:dev` | Alias → `dev:start` |
| `npm run start:preprod` | Alias → `preprod:start` |
| `npm run dev:start` | `spring-boot:run` with `.env.dev` (no `dev:compile`) |
| `npm run preprod:start` | `spring-boot:run` with `.env.preprod` (no `preprod:compile`) |

Examples:

```bash
# First run or after code changes
npm run run:dev

# Restart only (skip reactor compile)
npm run run:dev -- --skip-build
npm run start:dev
npm run dev:start
```

Note: `dev:start` / `spring-boot:run` can still do a **light** Maven compile of `portfolio-app` if classes are stale. It does **not** run the full `-am` reactor compile that `run:dev` / `dev:compile` do.

## Env / Vault scripts

| Command | What it does |
|---------|----------------|
| `npm run env:dev` | Map latest Vault backup → `.env.dev` |
| `npm run env:preprod` | Map latest Vault backup → `.env.preprod` |
| `npm run env:from-vault -- --env dev --backup <path>` | Explicit backup file |

Details and variable mapping: [ENV.md](ENV.md).

## Other npm scripts

| Command | What it does |
|---------|----------------|
| `npm run build` / `build:all` | Full `mvn clean install -DskipTests` |
| `npm run build:common` | Build `am-common-data` only |
| `npm run build:module -- <module>` | Build one module with `-am -amd` |
| `npm test` | `mvn test` |
| `npm run docker:build` | `docker build -t am-portfolio:latest .` |
| `npm run docker:run` | Run image on port 8080 |
| `npm run sdk:generate:*` | OpenAPI SDK generation (java / python / flutter / all) |

## Port already in use

Default local port is **8072** (`SERVER_PORT` in `.env.dev`). If start fails with “port already in use”, stop the old Java process on that port, then run `npm run start:dev` again.

## amctl (optional)

Native laptop run can also use `am run` after copying `.env.dev` → `.env`. Local KIND/Docker is `am deploy --env local` (not VPS/`--env dev`). Prefer the npm scripts above for day-to-day local API work.
