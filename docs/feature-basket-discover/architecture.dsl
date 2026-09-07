# Architecture (text) — UX + latency

Open **[architecture.drawio](./architecture.drawio)** in diagrams.net.

## Latency paths

```
Flutter explorer
  → POST /v1/basket/opportunities mode=DISCOVER
       → holdings resolve
       → ETF L1/L2 or parser batch (enrich skip if ≥95% ISIN)
       → NO bulk prices
       → overlap skipPriceFetch
  → list UI (match, returns, sparkline)

Preview / FULL
  → same ETF path + enrich
  → bulk prices (chunked market-data)
  → overlap with prices
```

## Improve-loop (M-L5)

```
test API → p95≤1s and quality OK?
  no → analyze stage timers → fix → build → user confirm deploy → test
  yes → done
```

## UX modules

M0–M6 per [plan.md](./plan.md). Latency M-L0–M-L5 per [plan-v2-latency.md](./plan-v2-latency.md).
