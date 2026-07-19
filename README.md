# Sentinel AI

Sentinel AI is an Android application that checks links and incoming message notifications for phishing and scam signals. It combines local URL heuristics, an on-device TensorFlow Lite model, optional reputation evidence, and clear risk decisions before a user continues to a destination.

The project was developed for the ET AI Hackathon 2026 under the Digital Public Safety problem statement.

## Features

- **Click-time protection:** Inspects web links before handing them to a browser.
- **Notification scanning:** Evaluates supported messaging and communication notifications for scam indicators.
- **Manual scanner:** Accepts links, text, and supported file references from the app.
- **On-device ML:** Runs a 15-feature URL classifier through TensorFlow Lite.
- **Explainable risk results:** Returns a risk score, decision, reasons, and recommended action.
- **Local history:** Stores scan and threat records on the device for later review.
- **Protection controls:** Provides separate switches for notification, click, and text-selection protection.

See [Features](docs/features.md) for the complete feature description.

## How It Works

1. Sentinel AI receives a URL through the scanner, an Android web intent, shared content, or selected text. It can also observe notifications after the user grants notification access.
2. Local rules inspect URL structure or message content for signals such as deceptive domains, urgency language, redirects, and credential requests.
3. URL scans also extract 15 numeric features and run a bundled TensorFlow Lite model on the device. Available reputation evidence is combined with the local analysis.
4. The app presents an `ALLOW`, `WARN`, or `BLOCK` decision and records the event in local history.

See [Architecture](docs/architecture.md) for the component and data flows.

## Tech Stack

| Area | Technology |
| --- | --- |
| Language | Kotlin 2.0.21, Java 17 toolchain |
| Platform | Android, minimum API 26, compile/target API 34 |
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Architecture | Multi-module Android app, MVVM-style state, Kotlin coroutines and StateFlow |
| Dependency injection | Hilt |
| Local storage | Room and Android preferences |
| Background work | Android services and WorkManager |
| Networking | OkHttp, Retrofit, Gson |
| Machine learning | TensorFlow Lite 2.14 |
| Testing | JUnit, AndroidX Test, Espresso, MockWebServer |

## ML Model Summary

The URL classifier was trained from a dataset of more than 238,000 labeled URLs. Each URL is represented by 15 structural and lexical features, standardized with the bundled scaler, and passed to a TensorFlow model exported as TensorFlow Lite. The model accepts a `[1, 15]` float input and produces a phishing probability without requiring a cloud inference service.

Detailed features, inference steps, and limitations are documented in [ML Model](docs/ml-model.md).

## Screenshots

| Dashboard | Scan result | History |
| --- | --- | --- |
| Screenshot placeholder | Screenshot placeholder | Screenshot placeholder |

Replace these placeholders with final device captures before submission.

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
| [Architecture](docs/architecture.md) | Modules and protection pipelines |
| [ML Model](docs/ml-model.md) | Dataset, features, training, and inference |
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

