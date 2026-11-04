# Portfolio BE — industry-grade plan (final, with deploy loop)

| Field | Value |
|-------|--------|
| **Branch** | `hotfix/heatmap-etf-sector-unknown` |
| **Verify book** | Groww `bca384d2-c60c-431f-bf4a-108298592a97` |
| **Token** | `~/.asrax/credentials.d/portfolio-dev-verify.env` (`PORTFOLIO_DEV_BEARER`, `PORTFOLIO_ID`) — never commit |
| **DoD** | Phase 1 holdings loop GREEN + Phase 2 heatmap loop GREEN |

## Locked decisions

- Never map Jio Platforms (`INE0BJO01010` / Platforms) → `JIOFIN`
- No MD → `priceSource=COST_BASIS`, sector `Private / Unlisted`, still on Holdings + Heatmap
- ETF/scheme (`INF…`) → `Exchange Traded Funds (ETFs)` without N× ETF HTTP on LIVE
- After every code change: **unit test → `am deploy --env dev` → curl → fix → redeploy** (max 5 iters/phase)

## Master loop

```text
code → unit tests → am deploy --env dev → wait ready / bust heatmap cache
  → curl Groww API with Bearer → asserts pass?
       YES → next phase / PR
       NO  → log field expected vs actual → smallest fix → repeat
```

### Phase 1 holdings GREEN

- Platforms: `priceSource=COST_BASIS`, price ≈ 1119 (not ≈ 213), sector Private/Unlisted, `productClass=UNLISTED`
- Tata Gold `INF…`: sector ETF label
- WIPRO / IRFC / OLAELEC: still live LTP

### Phase 2 heatmap GREEN

- All five holdings present
- Gold not Unknown
- Platforms present (~73% weight)
- Sector weights ≈ 100% vs holdings total

## Phases

0. Token + baseline + this plan  
1. Holdings heal + quote ladder + classifier → **loop until GREEN**  
2. Heatmap cost fallback + classifier + normalize → **loop until GREEN**  
3. PR to `main` with verify evidence  

Follow-ups: market-data `INF…` seed, Prometheus, Flutter badges.
