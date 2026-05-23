#!/usr/bin/env python3
"""
Map VPS Vault backup JSON → .env.preprod or .env.dev (gitignored)

Usage (from am-portfolio/):
  npm run env:preprod
  npm run env:dev
  python scripts/map_env_from_vault_backup.py --env preprod
  python scripts/map_env_from_vault_backup.py --env dev --backup ../VPS/vault/backups/vps_vault_full_backup_*.json
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
WORKSPACE_ROOT = REPO_ROOT.parent

# Newest backup wins; searched in order
BACKUP_DIRS = (
    WORKSPACE_ROOT / "VPS" / "vault" / "backups",
    WORKSPACE_ROOT / "am-auth" / "vault" / "backups",
)

DEFAULT_SERVER_PORT = "8072"
DEFAULT_MARKET_CONNECT_MS = "5000"
DEFAULT_MARKET_READ_MS = "45000"


def _get(data: dict, path: str) -> dict:
    return data.get(path) or {}


def _mongo_portfolio_url(mongo: dict) -> str:
    base = (mongo.get("url") or "").strip()
    if not base:
        user = mongo.get("username", "admin")
        password = mongo.get("password", "")
        host = mongo.get("host", "localhost")
        port = mongo.get("port", "27017")
        base = f"mongodb://{user}:{password}@{host}:{port}"

    if "/portfolio" in base:
        if "authSource" not in base:
            sep = "&" if "?" in base else "?"
            base = f"{base}{sep}authSource=admin&directConnection=true"
        return base

    if "?" in base:
        path_part, query = base.split("?", 1)
        path_part = path_part.rstrip("/")
        return f"{path_part}/portfolio?{query}"

    return f"{base.rstrip('/')}/portfolio?authSource=admin&directConnection=true"


def _find_latest_backup(explicit: Path | None) -> Path:
    if explicit:
        if not explicit.is_file():
            raise SystemExit(f"Backup file not found: {explicit}")
        return explicit

    candidates: list[Path] = []
    for directory in BACKUP_DIRS:
        if directory.is_dir():
            candidates.extend(directory.glob("vps_vault_full_backup_*.json"))

    if not candidates:
        searched = ", ".join(str(d) for d in BACKUP_DIRS)
        raise SystemExit(
            f"No vps_vault_full_backup_*.json found. Searched:\n  {searched}\n"
            "Run from am-auth: npm run vault:backup\n"
            "Or pass: npm run env:from-vault -- --env preprod --backup <path>"
        )

    return sorted(candidates)[-1]


def build_env(env_name: str, data: dict) -> str:
    prefix = f"apps/{env_name}"
    mongo = _get(data, f"{prefix}/infra/mongodb")
    kafka = _get(data, f"{prefix}/infra/kafka")
    redis = _get(data, f"{prefix}/infra/redis")
    auth = _get(data, f"{prefix}/services/am-auth")
    parser = _get(data, f"{prefix}/services/am-parser")
    market = _get(data, f"{prefix}/services/am-market-data")
    portfolio = _get(data, f"{prefix}/services/am-portfolio")

    etf_url = parser.get("URL") or (
        "https://am.asrax.in/parser"
        if env_name == "preprod"
        else "https://am-dev.asrax.in/parser"
    )
    market_url = market.get("URL") or (
        "https://am.asrax.in/market"
        if env_name == "preprod"
        else "https://am-dev.asrax.in/market"
    )
    if market_url.startswith("http://am-market-data"):
        market_url = (
            "https://am.asrax.in/market"
            if env_name == "preprod"
            else "https://am-dev.asrax.in/market"
        )

    run_script = "run:preprod" if env_name == "preprod" else "run:dev"

    lines = [
        f"# Auto-mapped from Vault backup — apps/{env_name}/*",
        f"# Run: npm run {run_script}",
        "# Override ETF/market URLs below for local services (e.g. ETF_API_URL=http://localhost:8022)",
        "",
        f"SPRING_PROFILES_ACTIVE={env_name}",
        f"SERVER_PORT={DEFAULT_SERVER_PORT}",
        "",
        f"ETF_API_URL={etf_url}",
        f"MARKET_DATA_API_URL={market_url}",
        "BASKET_HOLDINGS_ENRICHMENT=true",
        f"MARKET_DATA_CONNECT_TIMEOUT_MS={DEFAULT_MARKET_CONNECT_MS}",
        f"MARKET_DATA_READ_TIMEOUT_MS={DEFAULT_MARKET_READ_MS}",
        "",
        f"MONGODB_URL={_mongo_portfolio_url(mongo)}",
        "MONGODB_DATABASE=portfolio",
        "",
        "# Vault kafka bootstrap is in-cluster; use kafka.asrax.in for laptop",
        "KAFKA_BOOTSTRAP_SERVERS=kafka.asrax.in:9092",
        f"KAFKA_USERNAME={kafka.get('username', 'kafkaUser')}",
        f"KAFKA_PASSWORD={kafka.get('password', '')}",
        "KAFKA_SECURITY_PROTOCOL=SASL_PLAINTEXT",
        "KAFKA_SASL_MECHANISM=SCRAM-SHA-256",
        "KAFKA_ENABLED=false",
        "",
        f"PORTFOLIO_TOPIC={portfolio.get('PORTFOLIO_TOPIC', 'am-portfolio')}",
        "PORTFOLIO_CONSUMER_ID=am-portfolio-group-1",
        f"STOCK_TOPIC={market.get('STOCK_PRICE_UPDATE_TOPIC', 'am-stock-price-update')}",
        "STOCK_CONSUMER_ID=am-stock-group.v2",
        "MARKET_INDEX_TOPIC=nse-stock-indices-update",
        "MARKET_INDEX_CONSUMER_ID=am-market-index-group-1",
        "",
        f"REDIS_HOSTNAME={redis.get('host', 'redis.asrax.in')}",
        f"REDIS_PORT={redis.get('port', '8889')}",
        f"REDIS_PASSWORD={redis.get('password', '')}",
        "",
        f"JWT_SECRET={auth.get('JWT_SECRET', '')}",
        f"INTERNAL_SECRET_KEY={auth.get('SECRET_KEY', '')}",
        "JWT_ALGORITHM=HS256",
        "",
        f"LOG_LEVEL={auth.get('LOG_LEVEL', 'INFO')}",
        "",
    ]
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Map Vault backup (apps/preprod/* or apps/dev/*) to .env.preprod / .env.dev"
    )
    parser.add_argument(
        "--env",
        choices=("preprod", "dev"),
        default="preprod",
        help="Vault apps/{env}/* prefix to map (default: preprod)",
    )
    parser.add_argument("--backup", type=Path, help="Path to vps_vault_full_backup_*.json")
    args = parser.parse_args()

    backup_path = _find_latest_backup(args.backup)

    with open(backup_path, encoding="utf-8") as f:
        payload = json.load(f)
    data = payload.get("data", {})

    env_path = REPO_ROOT / f".env.{args.env}"
    env_path.write_text(build_env(args.env, data), encoding="utf-8")
    print(f"Wrote {env_path}")
    print(f"Source: {backup_path}")
    print(f"Next: npm run run:{args.env}")


if __name__ == "__main__":
    main()
