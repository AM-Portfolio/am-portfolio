# DATA-POINTS — portfolio chart field catalog

Sources of truth: Java models cited below. UI-derived fields marked **Dart**.

---

## A. History envelope — `PortfolioHistoryResponse`

`portfolio-model/.../history/PortfolioHistoryResponse.java`

| Field | Type | Meaning |
|-------|------|---------|
| `points` | `List<PortfolioSnapshotModel>` | EOD series |
| `historyStatus` | `READY` \| `BUILDING` \| `EMPTY` | Chart sync state |
| `phase` | `HistoryJobPhase` | `QUEUED`, `BUILDING_90D`, `BUILDING_1Y`, `READY`, `FAILED` |
| `startedAt` | String | Job start (ISO) |
| `historyFrom` / `historyTo` | String | Coverage window |
| `coverageDays` | int | Days covered |

### Status-only — `PortfolioHistoryStatusResponse`

Same status/phase/window fields plus:

| Field | Meaning |
|-------|---------|
| `mode` | `HistoryJobMode` FULL / GAP / MERGE_BROKER |
| `targetPortfolioId` | Scoped catch-up target |
| `failureReason` | FAILED detail |

Dart: `history.isBuilding` ⇔ `historyStatus == BUILDING`.

---

## B. EOD point — `PortfolioSnapshotModel`

`am-common-data-model/.../PortfolioSnapshotModel.java`

| Field | Meaning |
|-------|---------|
| `snapshotId` | Snapshot id |
| `snapshotDate` | Calendar day (X-axis) |
| `createdAt` | Write time |
| `userId` | **JsonIgnored** on history output |
| `totalUserWealth` | Aggregate close / primary Y |
| `totalUserWealthOpen` / `High` / `Low` | User-level OHLC |
| `totalUserInvestment` | Cost basis total |
| `totalUserGainLoss` / `Percentage` | Unrealized style totals |
| `sessionDayGainLoss` / `Percentage` | Session day P&L |
| `portfolios` | Nested book slices |

### Nested — `PortfolioSnapshotEntryModel`

| Field | Meaning |
|-------|---------|
| `portfolioId` / `portfolioName` / `brokerType` | Book identity |
| `open` / `high` / `low` / `close` | Book OHLC (`close` → per-book Y) |
| `totalInvestment` / `totalGainLoss` / `Percentage` | Book P&L |
| `sessionDayGainLoss` / `Percentage` | Session |
| `holdings` | **JsonIgnored** on API |

---

## C. Intraday — `IntradayDataPoint`

| Field | Meaning |
|-------|---------|
| `timestamp` | Candle time e.g. `09:15` |
| `totalWealth` | Aggregate Y |
| `changeFromOpen` / `changeFromOpenPct` | vs session open / prior EOD anchor |
| `isLive` | Last live candle flag |
| `portfolios[]` | `PortfolioIntradayEntry`: `portfolioId`, `portfolioName`, `brokerType`, `value`, `changeFromOpen`, `changeFromOpenPct` |

---

## D. Market index candle — `OHLCVTPoint` (via historical-charts)

| Field | Meaning |
|-------|---------|
| `time` | Timestamp |
| `open` / `high` / `low` / **`close`** / `volume` | OHLC; UI uses **close** |

Envelope: `HistoricalDataResponseV1.data[symbol].dataPoints[]`.

---

## E. am-analysis performance (legacy overlay)

`PerformanceResponse` / `DataPoint`:

| Field | Meaning |
|-------|---------|
| `portfolioId`, `timeFrame` | Echo |
| `totalReturnPercentage`, `totalReturnValue` | Period return |
| `chartData[].date` | Day |
| `chartData[].value` | Market value that day |
| `errorMessage` | Optional |

**Not** consumed by `DashboardChartWidget` overlay today.

### Dashboard summary (KPI, not series)

`DashboardSummary`: `totalValue`, `totalInvested`, `totalGainLoss` (+ %), `dayChange` (+ %), counts, `portfolioBreakdown[]`, best/worst performer, `currency`, `isComplete`.

---

## F. UI-derived (**Dart**)

| Concept | Location | Rule |
|---------|----------|------|
| `OverlayPoint` | `overlay_chart_models.dart` | `xLabel` + `value` |
| `%` rebase | `toPercentPoints` | `((v - firstPositive) / firstPositive) * 100` |
| Series ids | `OverlayChartIds` | `Overall`, book UUID, `NIFTY 50`, … |
| Caps | `maxVisibleLines = 10`, defaults Overall + NIFTY 50 | |
| Plot gate | overlay provider | need ≥ 2 finite positive points after filter |
