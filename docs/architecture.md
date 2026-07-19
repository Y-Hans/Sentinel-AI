# Architecture

## Application Structure

Sentinel AI is a multi-module Android application. Dependencies flow from the application and presentation modules toward shared core contracts.

| Module | Responsibility |
| --- | --- |
| `app` | Application startup, intent routing, URL and file scan orchestration, ML inference, reputation integration, and warning delivery |
| `core` | Domain models, event bus, validation, local Room storage, shared networking, and feature state |
| `agents` | Notification parsing, supported-app routing, message event construction, and scam rules |
| `services` | Guard and monitoring services plus background work |
| `ui` | Compose screens, navigation, view models, design components, settings, scanner, dashboard, and history |

The app uses Hilt for dependency injection, coroutines and flows for asynchronous state, and Room for persistent threat history.

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
  |-- Reputation evidence      |-- Content and URL signals
  `-- On-device URL ML         `-- Scam rule engine
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
          +--> Link or file heuristic analysis
          |        |
          |        +--> optional reputation providers
          |        |
          |        `--> ALLOW / WARN / BLOCK evidence decision
          |
          +--> URL feature extraction --> scaler --> TFLite inference
          |
          v
Result screen
  - ALLOW: Continue
  - WARN: Continue Anyway
  - BLOCK: no browser handoff
```

URL analysis emits a threat event for local history. The returned URL result also receives the ML-adjusted reported score before it is shown by the scan screen.

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
ThreatEventBus
          |
          +--> Local history
          `--> Warning notification for elevated risk
```

The pipeline uses message text, URL characteristics, urgency, financial or credential language, and known-contact status. Notification analysis is separate from the URL TFLite classifier.

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

## Reputation Boundary

The evidence layer supports three providers:

- OpenPhish downloads a feed and compares the scanned URL against it locally.
- VirusTotal can submit a URL for analysis only when a developer supplies an API key; the repository default leaves that key blank.
- A deterministic mock provider supplies stable safe, suspicious, and malicious fixtures for development and demonstrations.

Provider failures, timeouts, and unknown verdicts are retained as status evidence and do not lower a local risk score.

