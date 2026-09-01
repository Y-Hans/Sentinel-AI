# Development Guide

## Repository Structure

Sentinel AI is organized as a multi-module Android project enforcing separation of concerns and deterministic build boundaries:

- **`app/`**: Application entry point, Hilt dependency injection setup, intent routing activities (`ScanLoadingActivity`, `IntentRouterActivity`), and warning delivery.
- **`core/`**: Foundational domain module. Houses:
  - `RiskFusionEngine` (the central threat scoring authority).
  - `com.sentinel.ai.core.ml.url.*` (`UrlScanner`, 67-feature extractor, `HistGbmTreeEvaluator`, `SafeDomainAdjudicator`).
  - `com.sentinel.ai.core.ml.messages.*` (`MessageScanner`, `TextNormalizer`, `SenderParser`, 70-feature extractor, `DualTfidfVectorizer`, `FeatureScaler`, `MultiClassTreeEvaluator`, `MessageAdjudicator`).
  - `ThreatJournal` and Room SQLite persistence.
  - `BrowserLauncher` and `BrowserSelectionPolicy`.
- **`agents/`**: Notification interception, messaging app routing, `NotificationAgentCoordinator`, and `NotificationThreatAnalyzer`.
- **`services/`**: Android background services (`SentinelNotificationListener`).
- **`ui/`**: Jetpack Compose screens, ViewModels, Material 3 theme components, navigation graphs, scanner dashboard, and history logs.

---

## Build Commands

Sentinel AI uses Gradle with Kotlin DSL:

- **Debug Build**:
  ```bash
  ./gradlew assembleDebug
  ```
- **Offline Build**:
  ```bash
  ./gradlew assembleDebug --offline
  ```
- **Full Test Suite & Verification**:
  ```bash
  ./gradlew test
  ```
- **Full Build & Test in Offline Mode**:
  ```bash
  ./gradlew test assembleDebug --offline
  ```

---

## Testing Strategy & ML Parity Verification

Sentinel AI enforces strict unit testing across all modules:

1. **URL-ML Parity Suite (`:core:test` / `UrlParityTest`)**:
   - Tests 151 golden URL records across benign, suspicious, and malicious domains.
   - Verifies 100.0% label parity ($151 / 151$) against reference scikit-learn model outputs.
2. **Messages-ML Parity Suite (`:core:test` / `MessageParityTest`)**:
   - Tests 116 golden message records across benign, OTP, transactional, spam, and phishing messages.
   - Verifies 100.0% label parity ($116 / 116$) against reference multimodal pipeline outputs.
3. **Risk Fusion & Security Tests**:
   - Verifies `RiskFusionEngine` invariants, payload compound scoring, contextual dampening, and safe-link fast path routing (`GREEN + ALLOW`).
4. **Android Instrumentation Tests**:
   ```bash
   ./gradlew connectedAndroidTest
   ```

---

## Dependency Injection (Hilt)

- `MlModule` in `:core` provides `@Singleton` instances of `UrlScanner` and `MessageScanner`, loading asset models safely at application startup.
- `FusionModule` binds `DefaultRiskFusionEngine` as the `@Singleton` implementation of `RiskFusionEngine`.
- All production code receives scanners and engines via constructor injection (`@Inject`).

---

## UI Customization & Assets

- **Logo Asset**: The canonical user-maintained asset is located at `app/src/main/res/drawable-nodpi/logo.png`.
- **Security Tips**: Tips surfaced during scan loading states are configured in `SecurityTipProvider` within the `:ui` module.
