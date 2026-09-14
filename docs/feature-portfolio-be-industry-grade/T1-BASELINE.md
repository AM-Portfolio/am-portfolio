# A0 / T1 baseline log (PROD)

Golden portfolio: `f969745c-f492-4b86-88ed-6588e9f28bb3`  
API: `https://am.asrax.in/portfolio`  
Image under test: `ghcr.io/sahim99/am-portfolio:local-hm6-214926`

## Iter 2 (2026-09-14) — accuracy gate

| API | cold_ms | warm_ms | result | note |
|-----|---------|---------|--------|------|
| T1.1 health | 1558 | — | PASS | UP |
| T1.2 holdings | 1300 | — | PASS | 111 equities |
| T1.3 advanced live | 1521 | 1226 | PASS | 13 sectors; movers 3/2; top pct 2.32 |
| T1.4 advanced 1W | 45500 | 793 | PASS* | 13 sectors; sector mix differs from live; Loki N/M 106/111 |
| T1.4 advanced 1M | 47578 | 862 | PASS | pct 4.49 ≠ live 2.32 |
| T1.4 advanced 1Y | 54269 | 1139 | PASS | pct 86.67 ≠ live 2.32 |
| T1.6 basket catalog | 812 | — | PASS | themes=9 |
| T1.6 basket discover | — | — | SKIP | use `POST /v1/basket/opportunities` (not `/discover`) |

\*1W first-sector pct can look similar to live (~2.3) after rank sort; full sector map and Loki hist merge confirm period path.

## Developer evidence

- Before hm6: `Merged 0/111` + `TimeoutException … 10000ms` + live-as-period fallback
- After hm6: `Merged 106/111` on advanced period requests

## Remaining (A2 / later T1)

- Cold hist still ~45–54s (SLO ≤15s) — large-batch hist + cache
- Sector allocation field often empty in advanced payload (separate from heatmap tiles)
