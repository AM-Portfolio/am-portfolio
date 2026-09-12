# API Contracts — Portfolio Intelligence

**Base (PROD):** `https://am.asrax.in/portfolio`  
**Auth:** `Authorization: Bearer <JWT>` (owner of `portfolioId`)  
**Calc:** [E2E-BACKEND-PLAN.md](./E2E-BACKEND-PLAN.md)  
**Plan:** [plan.md](./plan.md) · **TODO:** [TODO.md](./TODO.md) · **UI:** [UI_SPEC.md](./UI_SPEC.md)  
**Note:** UI only calls these when GrowthBook intel flags are ON (plan D15).

Use these shapes for OpenAPI + Postman goldens. Field names may use camelCase in JSON.

---

## Common

| Header / path | Value |
|---------------|--------|
| `portfolioId` | Golden owner portfolio UUID |
| 403 | Non-owner `portfolioId` |

---

## POST `/v1/analytics/portfolio/{portfolioId}/intelligence`

**When:** Overview load (P3+)

### Request (body optional / empty object OK)

```json
{}
```

### Response 200 (minimum)

```json
{
  "portfolioId": "uuid",
  "asOf": "2026-09-08T12:00:00Z",
  "confidence": 0.0,
  "health": {
    "score": 0,
    "band": "Watch",
    "components": [
      { "id": "DIVERSIFICATION", "score": 0, "severity": "OK", "reason": "..." }
    ]
  },
  "risk": {
    "axes": [
      { "id": "CONCENTRATION", "riskScore": 0 }
    ],
    "findings": [
      { "code": "SECTOR_HIGH", "label": "Banking 31.4%", "severity": "HIGH" }
    ]
  },
  "xray": {
    "totalValue": 0.0,
    "sectorWeights": [
      { "name": "Financial Services", "weightPct": 0.0, "value": 0.0 }
    ],
    "industryWeights": [],
    "marketCapWeights": []
  }
}
```

**Asserts:** `health.score` in 0–100; `band` one of Critical\|Watch\|Healthy\|Strong; sector `weightPct` sum ≈ 100 (±0.5); slice `value` sums ≈ `xray.totalValue` (±0.02 after round2); `totalValue` is snapshot book NAV (INR).

---

## POST `/v1/analytics/portfolio/{portfolioId}/stress`

### Request

```json
{
  "preset": "NIFTY_DOWN_10",
  "custom": null
}
```

Presets: `NIFTY_DOWN_10`, `NIFTY_DOWN_20`, `BANKING_DOWN_20`, `IT_DOWN_15`, `CRASH_2008`.

**Batch (preferred for Overview):**

```json
{
  "presets": ["NIFTY_DOWN_10", "NIFTY_DOWN_20", "BANKING_DOWN_20", "IT_DOWN_15", "CRASH_2008"]
}
```

Unknown preset → **400**. Custom requires non-zero `shockPct`.

Custom (optional):

```json
{
  "preset": null,
  "custom": { "sector": "Information Technology", "shockPct": -15 }
}
```

### Response 200

```json
{
  "portfolioId": "uuid",
  "estimateLabel": "Scenario estimate",
  "scenarios": [
    {
      "id": "NIFTY_DOWN_10",
      "pctImpact": -8.4,
      "absImpact": -77800.0
    }
  ]
}
```

**Asserts:** For `NIFTY_DOWN_10` on long equity book, `pctImpact` &lt; 0. No Mongo write.

**Crash pack (CRASH_2008) factors (locked MVP):** apply approximate shocks — equity betaProxy 1.0 with market −40% equivalent pack: Banking −35%, IT −30%, residual −25% (document in StressEngine constants).

---

## POST `/v1/analytics/portfolio/{portfolioId}/what-if`

### Request — Add Investment

```json
{
  "mode": "ADD_INVESTMENT",
  "symbol": "RELIANCE",
  "amountInr": 200000
}
```

### Request — Modify Holding

```json
{
  "mode": "MODIFY_HOLDING",
  "symbol": "TCS",
  "targetWeightPct": 10.0
}
```

### Request — Switch Allocation

```json
{
  "mode": "SWITCH_ALLOCATION",
  "fromSector": "Financial Services",
  "toSector": "Information Technology",
  "moveWeightPct": 5.0
}
```

### Response 200

```json
{
  "mode": "ADD_INVESTMENT",
  "before": {
    "healthScore": 0,
    "weights": { "RELIANCE": 8.2 },
    "sectorWeights": { "Energy": 15.0 }
  },
  "after": {
    "healthScore": 0,
    "weights": { "RELIANCE": 13.6 },
    "sectorWeights": { "Energy": 20.1 }
  }
}
```

**Asserts:** `after` present; holdings in Mongo unchanged after call.

---

## Report preview (P9)

### POST `/v1/analytics/portfolio/{portfolioId}/report/preview`

```json
{ "period": "WEEKLY" }
```

`period`: `WEEKLY` \| `MONTHLY`

### Response 200 (payload for future PDF)

```json
{
  "period": "WEEKLY",
  "asOf": "2026-09-08T12:00:00Z",
  "summary": {},
  "health": {},
  "risk": {},
  "xray": {},
  "movers": null,
  "stressSnapshot": null
}
```

**Asserts:** `health.score` matches intelligence for same portfolio within ±1 when built in same request window. **No PDF bytes, no email.**

---

## Owner test

Non-owner JWT + victim `portfolioId` → **403** on all of the above.

**Implementation (P1):** `com.portfolio.api.security.PortfolioOwnerAssert#requireOwner` — call at the start of every `/v1/analytics/portfolio/{portfolioId}/**` handler. Advanced wired; intelligence / stress / what-if must reuse the same helper.
