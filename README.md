# Sentinel AI

Sentinel AI is an Android application that protects users against phishing URLs, scams, and malicious communications in real time. It combines local heuristics, on-device machine learning (URL-ML Champion V7 and Messages-ML Champion V2), local scan history, and a centralized `RiskFusionEngine` before a user continues to a destination or opens a message.

The project was developed for the ET AI Hackathon 2026 under the Digital Public Safety problem statement.

## Features

- **Click-time protection:** Inspects web links before handing them to an external browser.
- **Notification scanning:** Evaluates incoming communications across supported messaging apps for scam and phishing indicators.
- **Manual scanner:** Accepts links, text, and supported file references for immediate offline scanning.
- **URL-ML Champion V7:** Pure-Kotlin 67-feature HistGradientBoosting tree ensemble with domain binning, safe-brand clamping, and calibrated thresholding.
- **Messages-ML Champion V2:** Pure-Kotlin 2,070-dimensional multimodal pipeline combining 70 deterministic tabular signals, dual TF-IDF vectorizers (1,500 word + 500 char_wb n-grams), standard scaling, and a 3-class HistGradientBoosting classifier.
- **Centralized Risk Fusion:** All heuristics and ML models emit isolated `ThreatEvidence`; the `RiskFusionEngine` is the sole authoritative decision-maker (`ALLOW`, `WARN`, `BLOCK`).
- **Explainable risk results:** Detailed evidence breakdown with severity, confidence, and recommended actions.
- **Durable local history:** Persists all scan records and threat events locally in SQLite/Room via `ThreatJournal`.
- **Granular protection controls:** Independent toggles for real-time protection, notification scanning, click protection, and text selection.

See [Features](docs/features.md) for the complete feature description.

## How It Works

1. **Input Interception:** Sentinel AI intercepts URLs via Android intent filters (browsers, share intents, text selection), manual input, or incoming notifications from supported messaging apps.
2. **Feature Extraction & Local Heuristics:** The engine normalizes inputs and evaluates explainable rule sets (structural abnormalities, deceptive domains, urgency language, credential harvesting, financial coercion).
3. **High-Performance On-Device ML:**
   - **URL Scanner:** Extracts 67 continuous lexical and structural features, applies exact binning thresholds, evaluates 350 gradient boosted trees, and adjudicates risk with brand-safe domain clamping ($\tau = 0.22588723$).
   - **Message Scanner:** Extracts 70 deterministic tabular features and 2,000 TF-IDF features (1,500 word unigrams/bigrams + 500 char_wb 3-5 n-grams), standardizes the 2,070-dimensional vector, evaluates 309 multiclass gradient boosted trees across 3 classes, and adjudicates non-benign risk ($\tau = 0.704$).
4. **Authoritative Risk Fusion:** `RiskFusionEngine` fuses all `ThreatEvidence` items into a single compound score ($0\text{--}100$) and issues an authoritative decision: `ALLOW` (Score $< 40$), `WARN` ($40\text{--}89$), or `BLOCK` ($\ge 90$).
5. **Safe Routing & Persistence:** Safe links (`GREEN + ALLOW`) proceed automatically via `BrowserSelectionPolicy \to BrowserLauncher`. Unsafe links require explicit user override or are hard-blocked. All events are durably recorded to the local database.

See [Architecture](docs/architecture.md) for detailed component and data flows.

## Tech Stack

| Area | Technology |
| --- | --- |
| Language | Kotlin 2.0.21, Java 17 toolchain |
| Platform | Android, minimum API 26, compile/target API 34 |
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Architecture | Multi-module Android app, MVVM, Kotlin Coroutines, StateFlow |
| Dependency Injection | Hilt (Dagger) |
| Local Storage | Room (SQLite) with durable suspending `ThreatJournal` persistence |
| Background Work | Android Services and WorkManager |
| Machine Learning | Pure Kotlin native runtime (HistGradientBoosting, Dual TF-IDF, Standard Scaler; zero external C++/Python runtime dependencies; 100% offline) |
| Testing | JUnit 4, Robolectric, MockK, AndroidX Test, Espresso |

## ML Model Summary

Sentinel AI runs two specialized, zero-dependency machine learning models natively on-device:

- **URL-ML Champion V7:** 67 continuous float features evaluated against 350 gradient-boosted decision trees using histogram-based searchsorted binning, exact log-odds sigmoid conversion, and safe-brand protection. Golden labels match 151/151 records; numeric parity is validated separately.
- **Messages-ML Champion V2:** 2,070-dimensional pipeline combining 70 deterministic tabular features with dual TF-IDF vectorization (1,500 word unigrams/bigrams + 500 character n-grams) and 64-bit standard scaling. Evaluates 309 trees (103 iterations $\times$ 3 classes) with softmax probabilities for `BENIGN`, `SUSPICIOUS_SPAM`, and `MALICIOUS`. Golden labels match 116/116 records; numeric drift remains documented in the integration contract.

Detailed feature specifications, binning algorithms, and validation metrics are documented in [ML Model](docs/ml-model.md).

## Getting Started

1. Clone the repository.
2. Open the `android-app` directory in Android Studio.
3. Allow Gradle to sync, then select the `app` run configuration.
4. Run on a device or emulator with Android 8.0 (API 26) or later.
5. Complete the in-app permission setup for the protection modes you want to demonstrate.

See [Setup](docs/setup.md) for detailed instructions and [Demo Guide](docs/demo.md) for a repeatable walkthrough.

## Documentation

| Document | Purpose |
| --- | --- |
| [Overview](docs/overview.md) | Problem, users, and solution summary |
| [Security](docs/SECURITY.md) | Security invariants, threat evidence, and risk fusion |
| [Development](docs/DEVELOPMENT.md) | Build instructions, testing, and architecture overview |
| [Architecture](docs/architecture.md) | Modules and protection pipelines |
| [ML Model](docs/ml-model.md) | Datasets, features, tree architectures, and inference contracts |
| [Features](docs/features.md) | User-facing capabilities and risk scoring |
| [Setup](docs/setup.md) | Local build and run instructions |
| [Demo Guide](docs/demo.md) | Hackathon demonstration sequence |
| [Privacy](docs/privacy.md) | Local processing and network boundaries |
| [Future Scope](docs/future-scope.md) | Planned technical extensions |

## Team

Repository contributors:

- Y-Hans
- Laksh Rewani
- Sagar Chhatani
