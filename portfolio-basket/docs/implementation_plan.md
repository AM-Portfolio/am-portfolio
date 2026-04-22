# Implementation Plan: Smart Investment Basket Replication

## Goal Description
Implement a proactive "Basket Recommendation Engine" that runs in the background to match user portfolios against ETFs/Mutual Funds (>80% overlap) and a detailed "Basket Preview Page" to visualize the replication opportunity.

## User Review Required
> [!IMPORTANT]
> **New Module**: `portfolio-basket` (Java).
> **UI Design**: The "Basket Preview Page" will feature **Glassmorphism**, **Entry Animations**, and a premium feel using `am_design_system`.
> **Phased Delivery**: Work will be split into 4 phases. Each phase MUST pass automated checks before moving to the next.
> **Testing**: We will create a `test-basket-data.json` (Mock Data) to simulate scenarios (80% match, sector substitution) and run **Automated API Tests** (curl/scripts) to verify logic without UI.

## Proposed Changes

### [Phase 1: Python Data Layer & Mock Setup]
*   **Action**: Update `am-parser-service` to expose `GET /api/v1/etf/holdings/bulk`.
*   **Mock Data**: Create `scripts/mock_etf_data.json` with 2 dummy ETFs:
    *   `ETF_A` (10 stocks, simple).
    *   `ETF_B` (20 stocks, diverse sectors).
*   **Verification**: Run `curl` to verify endpoint returns compressed JSON map.

### [Phase 2: Java Backend Logic & API]
*   **Module**: Create `portfolio-basket` (Maven Module).
*   **Core Components**:
    1.  **`BasketRecommendationScheduler`**:
        *   **Frequency**: Daily (post-market).
        *   **Process**:
            *   Fetches **Bulk ETF Holdings** (Cached Map) from Python Service.
            *   Fetches **All Active User Portfolios**.
            *   **Parallel Processing**: Uses `users.stream().parallel()` to process thousands of users concurrently.
            *   **Optimization**: Pre-computes ETF Sector Maps to ensure matching is O(1) or O(N) rather than O(N*M).
    2.  **`BasketEngineService`**:
        *   **Overlap Algorithm**:
            *   **Direct Match**: `HashSet.contains(isin)` for O(1).
            *   **Sector Substitution**: If direct match fails, look up `UserSectorMap.get(etfStock.sector)`.
            *   Returns a `BasketOpportunity` object if Score >= 80%.
    3.  **`BasketController`**:
        *   `GET /api/v1/basket/opportunities`: Returns pre-calculated list.
        *   `POST /api/v1/basket/preview`: On-demand calculation for detailed view.
*   **API Output Spec**:
    *   `matchScore`: Double (e.g., 85.5).
    *   `heldItems`: List of stocks user has.
    *   `missingItems`: List of stocks needed (Display Only).
    *   `substitutes`: List `{ "required": "Infosys", "userHas": "TCS", "reason": "Same Sector (IT)" }`.
*   **Automated Verification**: Create `scripts/test_basket_api.sh` to hit the local Java API with sample payloads and assert 200 OK + correct JSON structure.

### [Phase 3: UI Notification System (am_common)]
*   **Package**: `am_common`.
*   **Components**: `NotificationEntity`, `NotificationProvider`, `NotificationBell` widget.
*   **Design**: Clean, badge-based notification center.

### [Phase 4: Basket Preview Page (Flutter)]
*   **Page**: `BasketPreviewPage`.
*   **Features**:
    *   **Hero Section**: Glassmorphic Card showing "85% Match" with an animated Radial Gauge.
    *   **Gap Analysis**: Two lists side-by-side (or tabbed): "You Have" (Green ticks) vs "You Need" (Orange circles).
    *   **Smart Swaps**: Highlight Sector Substitutes with a special icon/animation (e.g., "TCS ⇄ Infosys").
    *   **Interactivity**: Clicking a stock shows details (Sector/Price).
*   **Design**:
    *   Use `BackdropFilter` for glass effects.
    *   `AnimatedOpacity` for listing items on load.
*   **Verification**: Run `flutter test` for widget behavior and `flutter drive` for integration tests (if applicable) to ensure UI components render without error. verify_phase_4.sh will trigger these.

## Verification Workflow
For **EACH PHASE**:
1.  **Build**: Run `mvn clean install` (Java) or `flutter build` (UI) to ensure no compilation errors.
2.  **Automated Verification**: Run the dedicated validation script (e.g., `scripts/verify_phase_1.sh`).
    *   This script MUST perform all checks (curl API, parse JSON, assert values).
    *   **NO** manual Postman or browser checks allowed.
    *   If the script passes, the phase is marked [x].
3.  **Performance Check**: Scripts must verify response times are within limits (e.g., < 200ms).

## Automated API Test Plan
We will create a `tests/api/basket_tests.http` (or shell script) to run:
1.  **Test 1**: Verify `Bulk Holdings` returns > 0 ETFs.
2.  **Test 2**: POST `Preview` with `Perfect Match Portfolio` -> Expect 100% Score.
3.  **Test 3**: POST `Preview` with `Partial Match Portfolio` -> Expect ~50% Score + Missing Items.
4.  **Test 4**: POST `Preview` with `Sector Swap Portfolio` -> Expect High Score + Substitution Field populated.
