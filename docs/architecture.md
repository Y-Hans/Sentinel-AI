# Architecture

## Application Structure

Sentinel AI is a multi-module Android application designed around clear domain boundaries, offline execution, and strict separation between evidence collection and risk decision-making.

| Module | Responsibility |
| --- | --- |
| `app` | Application startup, Hilt DI graph, intent routing, scan loading screens, warning notifications, and UI orchestration. |
| `core` | Foundational domain layer: `RiskFusionEngine`, `UrlScanner` (URL-ML V7), `MessageScanner` (Messages-ML V2), `ThreatJournal` (Room persistence), and `BrowserLauncher` / `BrowserSelectionPolicy`. |
| `agents` | Notification parsing, messaging app routing, `NotificationAgentCoordinator`, and threat evidence extraction. |
| `services` | Foreground and background guard services (e.g. `SentinelNotificationListener`). |
| `ui` | Jetpack Compose screens, ViewModels, Material 3 theme, navigation, scanner dashboard, and scan history. |

The application uses **Hilt** for dependency injection, Kotlin Coroutines and StateFlow for reactive asynchronous state, and **Room** for local threat persistence.

---

## System Architecture Overview

```text
Android Entry Points
  |-- Web/Open/Share Intents (HTTP/HTTPS)
  |-- Selected Text Intents
  |-- In-App Manual Scanner (URL, Text, File)
  `-- Notification Listener Service
           |
           v
Input Routing & Normalization
           |
           +---------------------------------------+
           |                                       |
           v                                       v
URL & File Analysis Pipeline             Message Analysis Pipeline
  |-- Local Domain Heuristics              |-- Sender Header & DLT Parser
  |-- 20 Structural & Lexical Rules        |-- Normalizer (NFKD, Homoglyphs)
  `-- UrlScanner (ML Champion V7)          |-- Scam & Coercion Rules
      (67 continuous features, 350 trees)   `-- MessageScanner (ML Champion V2)
                                                (2,070-dim multimodal, 309 trees)
           |                                       |
           v                                       v
    ThreatEvidence (URL_ML, ...)           ThreatEvidence (NOTIF_HEURISTIC, ...)
           |                                       |
           +-------------------+-------------------+
                               |
                               v
                      RiskFusionEngine
               (Sole Authoritative Risk Authority)
                               |
                               v
                     Final ScanResult
                  (ALLOW / WARN / BLOCK)
                               |
           +-------------------+-------------------+
           |                                       |
           v                                       v
    ThreatJournal                     Enforcement & UI Dispatch
  (Room Persistence)                     |-- GREEN + ALLOW -> BrowserLauncher
           |                             |-- WARN -> Warning Screen / Notification
           v                             `-- BLOCK -> Hard Enforcement (No Bypass)
  Dashboard & History UI
```

---

## Click-Time URL Protection Pipeline

Click-time protection is triggered whenever a user opens a link or shares URL text:

```text
User opens link (Intent)
           |
           v
ScanLoadingActivity / IntentThreatAnalyzer
           |
           +--> Local URL Heuristic Rules (20 explainable rules)
           +--> UrlScanner (URL-ML Champion V7, 67 features)
           |
           v
    ThreatEvidence Items
           |
           v
    RiskFusionEngine.evaluate(evidenceList)
           |
           v
     Authoritative ScanResult
           |
           +--> Persist to ThreatJournal (Room SQLite)
           |
           v
     Decision Evaluation:
       - GREEN + ALLOW: Invokes BrowserLauncher immediately (Fast Path)
       - WARN: Displays ScanResultActivity with risk breakdown and override option
       - BLOCK: Displays ScanResultActivity with hard-block (no continue option)
```

---

## Notification Scanning Pipeline

When notification access is granted, incoming communications are evaluated before the user interacts with them:

```text
Incoming Notification posted
           |
           v
SentinelNotificationListener
           |
           v
NotificationAgentCoordinator
   - Evaluates supported package registry
   - Extracts sender header and message body
   - Normalizes text (NFKD, homoglyph mapping, zero-width removal)
   - Evaluates deterministic heuristic rules
   - Runs MessageScanner (Messages-ML Champion V2, 2,070 dims)
   - Deduplicates repeated notifications
           |
           v
    ThreatEvidence Items
           |
           v
    RiskFusionEngine.evaluate(evidenceList)
           |
           v
     Authoritative ScanResult
           |
           +--> Persist to ThreatJournal (Room SQLite)
           |
           v
     If elevated risk (WARN / BLOCK):
       Dispatches System Warning Notification via WarningNotificationDispatcher
```

---

## Machine Learning Integration Invariants

1. **Evidence Isolation:** ML scanners (`UrlScanner`, `MessageScanner`) emit only `ThreatEvidence`. They never produce final security decisions.
2. **Centralized Authority:** `RiskFusionEngine` is the single authority for threat scoring and `ALLOW`/`WARN`/`BLOCK` decisions.
3. **Pure Kotlin Native Execution:** Zero external C++ or Python runtimes. All tree traversal, binning, and TF-IDF operations run in native JVM/Kotlin.
4. **Offline by Design:** All weights, vocabularies, IDF vectors, and tree structures are bundled inside APK assets.
5. **Fail-Safe Invariant:** If ML evaluation fails (e.g. missing/corrupted assets or runtime exception), analyzers emit explicit `CRITICAL` ML-unavailable evidence. The `RiskFusionEngine` escalates the verdict to `BLOCK` ($\ge 90$). ML failure is explicit security evidence, not silent absence of evidence, and can never result in an unauthorized `ALLOW`.
