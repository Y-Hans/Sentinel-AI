# Architecture

## Application Structure

Sentinel AI is a multi-module Android application. Dependencies flow from the application and presentation modules toward shared core contracts.

| Module | Responsibility |
| --- | --- |
| `app` | Application startup, intent routing, URL and file scan orchestration, ML inference, and warning delivery |
| `core` | Domain models, event bus, validation, local Room storage, shared networking, and feature state |
| `agents` | Notification parsing, supported-app routing, message event construction, and scam rules |
| `services` | Guard and monitoring services plus background work |
| `ui` | Compose screens, navigation, view models, design components, settings, scanner, dashboard, and history |

The app uses Hilt for dependency injection, coroutines and flows for asynchronous state, and Room with durable suspending ThreatJournal persistence for threat history.

## System View

```text
Android entry points
  |-- Web/open/share intents
  |-- Selected text
  |-- Manual scanner
  `-- Notification listener
           |
           v
Input routing and normalization
           |
           +------------------------+
           |                        |
           v                        v
URL/file protection          Message protection
  |-- Local heuristics         |-- Notification parser
  `-- On-device URL ML         |-- Content and URL signals
                               `-- Scam rule engine
           |                        |
           +-----------+------------+
                       v
              ThreatEventBus
                       |
          +------------+-------------+
          |                          |
          v                          v
    Result/warning UI          ThreatJournal + Room
                                      |
                                      v
                                History screens
```

## Intent-Based Protection Flow

Click-time protection is implemented through Android intent filters. Sentinel AI can receive `http` and `https` view intents, shared text, selected text, and supported file references.

```text
User opens or shares a link
          |
          v
IntentRouterActivity
  - checks the click-protection switch
  - identifies URL or file payload
          |
          v
ScanLoadingActivity / ScannerViewModel
          |
          v
ScanRepository
          |
          v
URL Normalization
          |
          v
Heuristic Analysis
          |
          v
ML Analysis
          |
          v
Result Fusion
          |
          v
Final ScanResult
          |
          +--> Threat Event / UI
          |
          v
Scan History Update
          |
          v
Local Room Memory
```

URL analysis emits a threat event for local history. The returned URL result receives the fused score from local heuristics and ML predictions before it is shown by the scan screen.

## Notification Pipeline

Notification protection requires the user to grant Android notification-listener access. The listener ignores group summaries, disabled protection modes, and packages outside the supported registry.

```text
Supported app posts notification
          |
          v
SentinelNotificationListener
          |
          v
NotificationAgentCoordinator
  - parse sender and message text
  - normalize the event
  - extract URL and content signals
  - apply scam scoring rules
  - suppress near-duplicate notifications
          |
          v
ONE Authoritative ScanResult
          |
          +--------------------------------------------+
          |                                            |
          v                                            v
    ThreatJournal                        WarningNotificationDispatcher
          |                                            |
          v                                            v
     Room Database                         WarningNotificationHelper
          |                                            |
          v                                            v
  In-Memory StateFlows                        System Warning Notification
          |
          v
  Dashboard / History
```

The pipeline uses message text, URL characteristics, urgency, financial or credential language, and known-contact status. Notification analysis is separate from the URL TFLite classifier.

Persistence and warning delivery are directly executed by `NotificationAgentCoordinator` via `ThreatJournal` and `WarningNotificationDispatcher`. Sentinel's own transient subscriber lifecycle (`ThreatEventSubscriberService`) has been removed, ensuring that a detected notification threat cannot disappear before durable Room persistence or warning dispatch due to process lifecycle interruptions. `ThreatEventBus` remains strictly for optional reactive UI observation and is not on the critical persistence path.

## ML Inference Flow

```text
Normalized URL
      |
      v
15 structural and lexical features
      |
      v
Bundled mean/scale transformation
      |
      v
Float32 tensor [1, 15]
      |
      v
TensorFlow Lite model
      |
      v
Float32 phishing probability [1, 1]
      |
      v
Blend with evidence-based URL score
```

The model and scaler are packaged in `app/src/main/assets`, so inference does not require a model server. See [ML Model](ml-model.md) for the feature contract and scoring formula.

## Final ScanResult

The final authoritative ScanResult is produced exclusively by local heuristics and the on-device ML model.
While scan history is retained locally in Room for user review, historical scans DO NOT alter the score of future URLs.

