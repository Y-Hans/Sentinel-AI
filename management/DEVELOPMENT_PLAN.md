# DEVELOPMENT_PLAN.md
# Sentinel AI — Engineering Development Plan
**Version:** 1.0.0
**Author Role:** CTO
**Hackathon:** ET AI Hackathon 2026 — Problem Statement 6
**References:**
- `project_context.md` — Product Vision, Feature Set, Architecture
- `ANDROID_ARCHITECTURE.md` v1.0.0 — Package Structure, MVVM, DI, ADRs
- `EVENT_SCHEMA.md` v1.0.0 — Universal Event Schema, Lifecycle, Payload Contracts
- `REPOSITORY_ARCHITECTURE.md` v1.0.0 — Full Stack Structure, Data Flow, Neo4j Model
- `WHATSAPP_AGENT_SPEC.md` v1.0.0 — WhatsApp Agent V1 Design
**Status:** Approved for Hackathon Execution
**Last Updated:** 2026-06-23

---

## Executive Summary

Sentinel AI is an always-on, multi-channel fraud prevention guardian for Android users. It detects digital arrest scams, phishing attacks, financial fraud, and social engineering campaigns in real time — before victims can be harmed.

This document is the master engineering development plan, owned by the CTO. It governs six sequential build phases covering the full MVP stack: Android client, FastAPI backend, multi-agent intelligence pipeline, fraud classifiers, system overlays, and the investigation engine.

All phases are scoped for the hackathon MVP (ET AI Hackathon 2026). Each phase produces independently deployable, demonstrable deliverables. Dependencies between phases are explicitly modelled to allow parallel work streams where the architecture permits.

**Target Demo Scenario:**
```
Fraudulent WhatsApp message received
     → Sentinel AI intercepts notification
     → Event normalized and submitted to backend
     → Fraud Intelligence agents classify threat
     → Master agent issues risk verdict (< 3 seconds)
     → Overlay alert fires on device
     → Investigation Engine renders explanation
     → User protected before any harmful action
```

---

## Team Roster & Role Definitions

| Role | Code | Scope |
|---|---|---|
| Android Lead | AND | Android app architecture, agents, services, UI |
| Backend Lead | BE | FastAPI, routing, orchestration, APIs |
| AI/ML Engineer | AI | Classifiers, NLP models, agent logic, prompt engineering |
| Data Engineer | DE | PostgreSQL, Neo4j, Redis, Celery, schema contracts |
| QA Engineer | QA | Unit, integration, regression, demo fixtures |
| DevOps | OPS | Docker, CI/CD, environment config, deployment |

---

## Phase Dependency Graph

```
Phase 1 — Foundation
     │
     ├──────────────────────────────────────────┐
     ▼                                          ▼
Phase 2 — Collection Agents          (Android team parallel track)
     │
     ▼
Phase 3 — Fraud Intelligence
     │
     ▼
Phase 4 — Master Agent
     │
     ├──────────────────────────────────────────┐
     ▼                                          ▼
Phase 5 — Overlay Protection         Phase 6 — Investigation Engine
```

Phases 5 and 6 depend on Phase 4 but are parallelizable with each other once Phase 4 backend contracts are stable.

---

## Phase 1 — Foundation

**Objective:** Establish the full project skeleton: Android app scaffold, FastAPI backend scaffold, shared schema contract, local database, CI/CD pipeline, and Docker environment. No agent logic. No AI. Pure infrastructure.

**Duration:** Days 1–2 (16 engineer-hours)

---

### 1.1 Deliverables

#### D1.1 — Monorepo Scaffold

Initialize the `sentinel-ai/` monorepo as defined in `REPOSITORY_ARCHITECTURE.md §1`:

```
sentinel-ai/
├── android/
├── backend/
├── shared/
│   └── schemas/
│       └── versions/
│           └── v1.0.0/
│               ├── base_event.json
│               ├── sms_payload.json
│               ├── call_payload.json
│               ├── whatsapp_payload.json
│               ├── telegram_payload.json
│               ├── gmail_payload.json
│               ├── url_analysis.json
│               ├── attachment_analysis.json
│               ├── risk_assessment.json
│               └── investigation_report.json
├── docker-compose.yml
├── Makefile
├── .env.example
└── README.md
```

#### D1.2 — Event Schema v1.0.0

Publish the complete Universal Event Schema (`shared/schemas/versions/v1.0.0/`) as defined in `EVENT_SCHEMA.md`. All fields, enums, lifecycle states, and validation rules from §3–§10 must be codified. This is the shared contract; no downstream phase can begin without it.

Schema artefacts required:
- Base event envelope (§3)
- Source block with privacy mode fields (§4)
- Content block with heuristic signals (§5)
- Channel payloads: SMS, CALL, WHATSAPP, TELEGRAM, GMAIL (§6.1–§6.5)
- Enrichment blocks: URL analysis, attachment analysis, risk assessment, investigation report (§7.1–§7.4)

#### D1.3 — Android App Scaffold

Initialize the Android project (`android/`) with the full package structure per `ANDROID_ARCHITECTURE.md §1.1`:

- `SentinelApp.kt` — `@HiltAndroidApp` Application class
- `core/di/` — Hilt modules: `AppModule`, `NetworkModule`, `DatabaseModule`, `AgentModule`, `ServiceModule`
- `core/network/` — `RetrofitBuilder`, `OkHttpProvider`, `AuthInterceptor`, `LoggingInterceptor`, `RetryInterceptor`
- `core/security/` — `EncryptedPrefs` (Keystore-backed), `KeystoreManager`, `CertificatePinner`
- `core/coroutines/` — `DispatcherProvider`, `CoroutineScopes`
- `agents/base/` — `BaseAgent.kt`, `AgentResult.kt`
- `AndroidManifest.xml` — All permissions declared per `ANDROID_ARCHITECTURE.md §7.12`

Compile target: clean build, zero warnings.

#### D1.4 — Room Database Schema

Implement the local Room database per `ANDROID_ARCHITECTURE.md §6`:

- `SentinelDatabase.kt` — `@Database` class, version 1
- DAOs: `ThreatDao`, `AlertDao`, `ScanHistoryDao`
- Entities: `ThreatEntity`, `AlertEntity`, `ScanHistoryEntity`
- Converters: `RiskLevelConverter`, `DateConverter`
- `UserPreferences.kt` — DataStore-backed typed preferences
- `EncryptedPrefs.kt` — Keystore-backed encrypted prefs for auth tokens (ADR-005)
- Migration stub for version 1 → future migrations

#### D1.5 — FastAPI Backend Scaffold

Initialize the Python backend (`backend/`) per `REPOSITORY_ARCHITECTURE.md §1`:

- `app/main.py` — FastAPI entry point with lifespan context
- `app/config.py` — Pydantic Settings, env-based config
- `app/dependencies.py` — Shared DI (DB session, Redis client, Neo4j driver)
- `app/api/v1/` — Router stubs for `/analyze/sms`, `/analyze/whatsapp`, `/analyze/telegram`, `/analyze/call`, `/analyze/link`, `/analyze/file`, `/copilot/chat`
- `app/schemas/` — Pydantic models mirroring `EVENT_SCHEMA.md` v1.0.0
- `app/db/postgres/` — SQLAlchemy models for threats, alerts, scan history
- `app/db/neo4j/` — Graph driver client stub
- `app/db/redis/` — Intelligence cache client stub
- `alembic.ini` — Migration config; initial schema migration

#### D1.6 — Docker Compose Environment

`docker-compose.yml` with services:

```yaml
services:
  backend:     FastAPI + Uvicorn
  postgres:    PostgreSQL 16
  redis:       Redis 7
  neo4j:       Neo4j 5
  celery:      Celery worker
  celery-beat: Scheduler
```

All services health-checked. `make up` brings the full environment to a ready state.

#### D1.7 — CI/CD Pipeline

GitHub Actions workflows:

- **Android CI:** Gradle build, lint, unit tests on every push
- **Backend CI:** `pytest`, `ruff`, `mypy` on every push
- **Schema Lint:** JSON schema validation on `shared/schemas/` changes
- **Docker Build:** Image build validation on every push to `main`

#### D1.8 — Core Android UI Shell

Jetpack Compose navigation graph (`SentinelNavGraph.kt`) with stub screens:

- `DashboardScreen` — Guardian status (ON/OFF toggle)
- `AlertScreen` — Empty alert list
- `ScannerScreen` — Manual scan input
- `CopilotScreen` — Chat interface stub
- `HistoryScreen` — Empty scan history
- `SettingsScreen` — Permission status list
- Shared components: `RiskBadge`, `ThreatCard`, `AlertBanner`
- Material 3 theme: `Color.kt`, `Typography.kt`, `Theme.kt`

#### D1.9 — Permission Onboarding Flow

`PermissionManager.kt` implementing the full permission request flow:

- `RECEIVE_SMS`, `READ_SMS`
- `READ_CALL_LOG`, `READ_PHONE_STATE`
- `BIND_NOTIFICATION_LISTENER_SERVICE` (deep-link to Settings)
- `SYSTEM_ALERT_WINDOW` (deep-link to Settings)
- `FOREGROUND_SERVICE`
- Per-permission rationale strings in `PermissionRationale.kt`
- Onboarding screen that blocks app use until critical permissions are granted

---

### 1.2 Team Assignments

| Deliverable | Lead | Supporting |
|---|---|---|
| D1.1 Monorepo scaffold | OPS | BE |
| D1.2 Event Schema v1.0.0 | DE | AND, BE |
| D1.3 Android app scaffold | AND | — |
| D1.4 Room database | AND | — |
| D1.5 FastAPI backend scaffold | BE | DE |
| D1.6 Docker Compose | OPS | BE, DE |
| D1.7 CI/CD pipeline | OPS | AND, BE |
| D1.8 Android UI shell | AND | — |
| D1.9 Permission onboarding | AND | — |

---

### 1.3 Dependencies

- **External:** Android Studio Hedgehog+, AGP 8.5.x, Kotlin 2.0.x, Python 3.12+, FastAPI 0.111+
- **Internal:** None. Phase 1 has no predecessor. It is the root of the dependency tree.
- **Blocking for Phase 2:** D1.2 (Event Schema) must be complete and reviewed before any agent implementation begins. D1.3 + D1.4 must be complete before Android agent work starts. D1.5 must be running before backend agent work starts.

---

### 1.4 Risks

| Risk | Severity | Probability | Mitigation |
|---|---|---|---|
| Event Schema ambiguity causing rework in later phases | HIGH | MEDIUM | Schema review gate: all team leads must sign off on D1.2 before Phase 2 starts. No exceptions. |
| Hilt DI misconfiguration causing build failures | MEDIUM | MEDIUM | AND Lead to build and run a smoke test (`./gradlew assembleDebug`) as Phase 1 exit criterion |
| Docker Compose networking issues between services | LOW | LOW | OPS to run `make up` and verify all health checks pass before closing Phase 1 |
| Permission model changes in Android 14/15 | MEDIUM | LOW | Target API 34; test on API 34 emulator. Flag any permission behaviour deviations. |
| Parallelism risk: teams starting Phase 2 before schema is locked | HIGH | MEDIUM | Phase 1 has a hard gate: schema must pass JSON schema lint CI check before Phase 2 tickets open |

---

### 1.5 Estimated Effort

| Deliverable | Effort |
|---|---|
| D1.1 Monorepo scaffold | 1h |
| D1.2 Event Schema v1.0.0 | 3h |
| D1.3 Android app scaffold | 3h |
| D1.4 Room database | 2h |
| D1.5 FastAPI backend scaffold | 3h |
| D1.6 Docker Compose | 1h |
| D1.7 CI/CD pipeline | 2h |
| D1.8 Android UI shell | 3h |
| D1.9 Permission onboarding | 2h |
| **Phase 1 Total** | **~20h** |

---

## Phase 2 — Collection Agents

**Objective:** Build all Android-side OS event listeners and agent coordinators that intercept raw signals from every monitored channel (SMS, Calls, WhatsApp, Telegram, Gmail) and submit normalized, schema-compliant `CommunicationEvent`s to the backend. No intelligence logic lives in this phase — only capture, normalization, deduplication, and dispatch.

**Duration:** Days 3–5 (40 engineer-hours)

---

### 2.1 Deliverables

#### D2.1 — SentinelGuardService (Always-On Foreground Service)

`SentinelGuardService.kt` — the persistent foreground service that keeps all agents alive:

- Starts on device boot via `BootReceiver`
- Starts/stops all agent coordinators
- Emits `GuardActivated` / `GuardDeactivated` events to `ThreatEventBus` (ADR-003)
- Displays persistent notification: "Sentinel AI is protecting you"
- Handles battery optimization exemption prompt
- `WakeLock` management for continuous monitoring
- Bound to `DashboardViewModel` for ON/OFF toggle

#### D2.2 — ThreatEventBus

`ThreatEventBus.kt` — the `SharedFlow`-based in-process event bus (ADR-003):

```kotlin
sealed class ThreatEvent {
    data class SmsThreatDetected(val scanResult: ScanResult) : ThreatEvent()
    data class CallThreatDetected(val scanResult: ScanResult) : ThreatEvent()
    data class WhatsAppThreatDetected(val scanResult: ScanResult) : ThreatEvent()
    data class TelegramThreatDetected(val scanResult: ScanResult) : ThreatEvent()
    data class GmailThreatDetected(val scanResult: ScanResult) : ThreatEvent()
    data class LinkThreatDetected(val scanResult: ScanResult) : ThreatEvent()
    data class FileThreatDetected(val scanResult: ScanResult) : ThreatEvent()
    data class CriticalThreatAlert(val threat: Threat) : ThreatEvent()
    data class AgentError(val channel: String, val error: Throwable) : ThreatEvent()
    object GuardActivated : ThreatEvent()
    object GuardDeactivated : ThreatEvent()
}
```

All agents emit to this bus. `SentinelGuardService`, `OverlayAlertService`, and `AlertViewModel` subscribe.

#### D2.3 — SMS Collection Agent

Components:
- `SmsReceiver.kt` — `BroadcastReceiver` for `android.provider.Telephony.SMS_RECEIVED`
- `SmsAgentCoordinator.kt` — extends `BaseAgent`; handles `AnalyzeSmsUseCase`
- `SmsEventBuilder.kt` — constructs `SmsCommunicationEvent` per `EVENT_SCHEMA.md §6.1`
- `SmsContentHeuristics.kt` — on-device signals: `hasUrgencyLanguage`, `hasAuthorityClaim`, `hasFinancialMention`, OTP pattern detection
- `SmsDeduplicationFilter.kt` — LRU cache, capacity 256, TTL 60s
- `AnalyzeSmsUseCase.kt` — calls `ThreatRepository.analyzeSms()`
- `SmsAnalysisRequest.kt` — Retrofit DTO
- Emits `SmsThreatDetected` to `ThreatEventBus`

Supports event types: `sentinel.sms.received` (§2.1)

#### D2.4 — WhatsApp Collection Agent

Implements the full `WHATSAPP_AGENT_SPEC.md` V1 specification:

- `SentinelNotificationListener.kt` — extended with WhatsApp routing (`com.whatsapp`, `com.whatsapp.w4b` package whitelist, Appendix A)
- `WhatsAppAgentCoordinator.kt` — extends `BaseAgent`
- `WhatsAppNotificationParser.kt` — stateless extractor; parses `Notification.extras` for sender, `EXTRA_BIG_TEXT`, group name, forward indicators
- `WhatsAppEventBuilder.kt` — constructs `WhatsAppCommunicationEvent` per `EVENT_SCHEMA.md §6.3`; enforces all V19 privacy consistency checks (Appendix C)
- `WhatsAppDeduplicationFilter.kt` — LRU cache, capacity 256, TTL 60s; CRC32 fingerprint on `sbn.key` + notification text
- `WhatsAppContentHeuristics.kt` — `hasUrgencyLanguage` (arrest, urgent, turant, abhi), `hasAuthorityClaim` (CBI, ED, RBI, TRAI, Customs, Police, Income Tax), `hasFinancialMention` (₹, lakh, crore, account, payment)
- `UrlPatternDetector.kt` — shared regex URL pre-screening (R6)
- `AccessibilityIntegrationStub.kt` — no-op V1.1 placeholder (R10)
- `AnalyzeWhatsAppUseCase.kt`
- `WhatsAppAnalysisRequest.kt` — Retrofit DTO
- `WhatsAppRetryWorker.kt` — WorkManager worker for offline queuing
- Emits `WhatsAppThreatDetected` to `ThreatEventBus`

Privacy enforcement: `privacy_mode = true` by default; strips `displayName`, `platformHandle`, `groupName`, `rawIdentifier` before any transmission (R4).

Supported event types: `sentinel.whatsapp.message.received` (§2.1)

#### D2.5 — Telegram Collection Agent

Mirrors WhatsApp agent architecture using `NotificationListenerService`:

- Package whitelist: `org.telegram.messenger`, `org.telegram.messenger.web`, `org.thunderdog.challegram`
- `TelegramAgentCoordinator.kt`, `TelegramNotificationParser.kt`, `TelegramEventBuilder.kt`, `TelegramDeduplicationFilter.kt`, `TelegramContentHeuristics.kt`
- `AnalyzeTelegramUseCase.kt`
- Emits `TelegramThreatDetected` to `ThreatEventBus`

Supported event types: `sentinel.telegram.message.received` (§2.1)

#### D2.6 — Call Collection Agent

- `CallReceiver.kt` — `BroadcastReceiver` for `PHONE_STATE` broadcasts
- `CallAgentCoordinator.kt` — handles incoming call detection and ended state
- `CallEventBuilder.kt` — constructs `CallCommunicationEvent` per `EVENT_SCHEMA.md §6.2`
- `AnalyzeCallUseCase.kt`
- Emits `CallThreatDetected` to `ThreatEventBus`

Supported event types: `sentinel.call.incoming`, `sentinel.call.ended` (§2.1)

Note: Call transcript field (`content.call_transcript`) is reserved for V2 ASR integration. V1 captures metadata only.

#### D2.7 — Gmail Collection Agent

- `GmailApiConnector.kt` — OAuth 2.0 integration with Gmail API (`/gmail/v1/users/me/messages`)
- `GmailAgentCoordinator.kt` — polls for new messages on a configurable interval (default: 5 min via WorkManager)
- `GmailEventBuilder.kt` — constructs `GmailCommunicationEvent` per `EVENT_SCHEMA.md §6.5`
- `GmailContentHeuristics.kt` — subject line and body heuristic signals
- `AnalyzeGmailUseCase.kt`
- Emits `GmailThreatDetected` to `ThreatEventBus`

Supported event types: `sentinel.email.received` (§2.1)

#### D2.8 — Backend Event Intake API

FastAPI endpoints to receive normalized events from Android agents:

```
POST /v1/analyze/sms
POST /v1/analyze/whatsapp
POST /v1/analyze/telegram
POST /v1/analyze/call
POST /v1/analyze/link
POST /v1/analyze/file
```

Each endpoint:
- Validates the incoming JSON against the Pydantic schema (mirrors `EVENT_SCHEMA.md`)
- Rejects `schema_version` mismatches with HTTP 422 per `EVENT_SCHEMA.md §11.3`
- Assigns `request_id` (trace ID) to the event
- Transitions event to `QUEUED` state
- Persists the raw event to PostgreSQL
- Enqueues the event for `ThreatOrchestrator` (Phase 3 pickup)
- Returns HTTP 202 Accepted immediately

#### D2.9 — Offline Retry Queue (WorkManager)

`WhatsAppRetryWorker.kt`, `SmsRetryWorker.kt`, and a shared `RetryWorkerBase.kt`:

- When network is unavailable, agents write the event to Room with `status = QUEUED`
- WorkManager picks up `QUEUED` events when connectivity is restored
- Exponential backoff: 1s → 2s → 4s (max 3 retries)
- Events exceeding TTL are marked `EXPIRED` and removed from queue

---

### 2.2 Team Assignments

| Deliverable | Lead | Supporting |
|---|---|---|
| D2.1 SentinelGuardService | AND | — |
| D2.2 ThreatEventBus | AND | — |
| D2.3 SMS Collection Agent | AND | QA |
| D2.4 WhatsApp Collection Agent | AND | QA |
| D2.5 Telegram Collection Agent | AND | — |
| D2.6 Call Collection Agent | AND | — |
| D2.7 Gmail Collection Agent | AND | — |
| D2.8 Backend Event Intake API | BE | DE |
| D2.9 Offline Retry Queue | AND | — |

---

### 2.3 Dependencies

- **Phase 1 complete:** D1.2 (Event Schema), D1.3 (Android scaffold), D1.4 (Room DB), D1.5 (FastAPI scaffold) must all be done.
- **WhatsApp before Telegram:** D2.4 establishes the notification-listener agent pattern. D2.5 should reuse it rather than reinventing.
- **D2.8 (Backend intake) can begin in parallel with Android agents** as long as D1.2 (schema) and D1.5 (FastAPI scaffold) are done. BE team does not need to wait for AND team to finish agents.
- **D2.9 (Retry queue)** depends on D2.3 and D2.4 being functional enough to produce events.

---

### 2.4 Risks

| Risk | Severity | Probability | Mitigation |
|---|---|---|---|
| `NotificationListenerService` permission not granted during demo | HIGH | MEDIUM | Onboarding flow (D1.9) must clearly guide users. Demo device must be pre-configured. |
| WhatsApp notification extras format changes between app versions | MEDIUM | LOW | Test against WhatsApp 2.24.x and WhatsApp Business. Log raw bundle in debug builds. |
| Deduplication LRU eviction causing duplicate backend submissions | MEDIUM | LOW | Instrumentation test WAI-I07 catches this. Raise cache capacity to 512 if needed. |
| Gmail OAuth flow adds significant UX friction | MEDIUM | HIGH | Implement in parallel; if OAuth is not demo-ready, stub with manual message input for demo |
| Call receiver not firing on Android 14+ due to `PHONE_STATE` changes | HIGH | MEDIUM | Test on API 34. Fall back to `READ_CALL_LOG` polling if broadcast is restricted. |
| Offline retry worker consuming battery during demo | LOW | LOW | WorkManager constraints: `requiresNetwork = true`, no periodic retries during demo |

---

### 2.5 Estimated Effort

| Deliverable | Effort |
|---|---|
| D2.1 SentinelGuardService | 3h |
| D2.2 ThreatEventBus | 1h |
| D2.3 SMS Collection Agent | 4h |
| D2.4 WhatsApp Collection Agent | 8h |
| D2.5 Telegram Collection Agent | 4h |
| D2.6 Call Collection Agent | 3h |
| D2.7 Gmail Collection Agent | 5h |
| D2.8 Backend Event Intake API | 4h |
| D2.9 Offline Retry Queue | 2h |
| **Phase 2 Total** | **~34h** |

---

## Phase 3 — Fraud Intelligence

**Objective:** Build the backend AI analysis pipeline — all fraud classifiers, the link intelligence engine, the file intelligence engine, and the digital arrest scam detector. This phase is the intelligence core of Sentinel AI. Every event submitted in Phase 2 gets analyzed here and receives a risk score.

**Duration:** Days 5–8 (48 engineer-hours)

---

### 3.1 Deliverables

#### D3.1 — Scam Text Classifier

`backend/app/agents/sms/sms_classifier.py`

Multi-class NLP classifier for incoming message text:

- **Classes:** `SAFE`, `OTP_THEFT`, `KYC_SCAM`, `BANKING_SCAM`, `REWARD_SCAM`, `LOTTERY_SCAM`, `DIGITAL_ARREST`, `AUTHORITY_IMPERSONATION`, `FINANCIAL_COERCION`
- **Model:** Fine-tuned DistilBERT or MuRIL (multilingual for Hinglish)
- **Input:** `content.body` from any channel event
- **Features:** Content body + client-side heuristic signals (`has_urgency_language`, `has_authority_claim`, `has_financial_mention`) as additional features
- **Output:** `ScamClassifierResult(label, confidence, signals[])`
- **Fallback:** Rule-based keyword classifier when model inference exceeds 500ms latency budget
- **Dataset:** Public phishing datasets (PhishTank, OpenPhish) + synthetic Indian scam generation (`backend/scripts/generate_scam_fixtures.py`)

Fraud regression fixtures from `WHATSAPP_AGENT_SPEC.md §8.4` must pass with confidence > 0.85:
- `SCAM-WA-001` → `DIGITAL_ARREST`
- `SCAM-WA-002` → `LOTTERY_SCAM`
- `SCAM-WA-003` → `KYC_SCAM`
- `SCAM-WA-005` → `DIGITAL_ARREST` + `FINANCIAL_COERCION`
- `SCAM-WA-007` → `SAFE` (negative case)

#### D3.2 — Digital Arrest Scam Detector

`backend/app/agents/fraud/digital_arrest_detector.py`

Specialized agent for the highest-priority fraud pattern in India:

- Detects impersonation of: CBI, ED, Customs, Police, Income Tax, TRAI, RBI, Ministry of Finance, Court
- Detects patterns: arrest threat, money laundering accusation, legal notice delivery, urgency to call back, demand for payment to avoid arrest
- **Scoring model:** Ensemble of regex pattern scores + NLP classifier confidence + heuristic signal weights
- **Output:** `DigitalArrestResult(is_digital_arrest: bool, impersonated_agency: str, threat_patterns: [], confidence: float)`
- Must fire on all five `SCAM-WA-00x` arrest fixtures with confidence > 0.90

#### D3.3 — Link Intelligence Engine

`backend/app/agents/link/link_analyzer.py`

Two-stage URL analysis pipeline:

**Stage 1 — Structural Analysis (< 200ms):**
- Domain age check (WHOIS lookup via `python-whois`)
- Brand impersonation check: Levenshtein distance against a whitelist of 500+ Indian bank/gov domains
- Suspicious TLD detection (`.xyz`, `.tk`, `.ml`, `.ga`, `.cf`)
- Path pattern analysis: `/login`, `/verify`, `/kyc`, `/update`, `/otp`
- Redirect chain detection (max 5 hops)
- HTTP vs HTTPS check

**Stage 2 — Intelligence Cache Lookup (< 50ms):**
- Redis cache lookup: known phishing domains (from PhishTank, OpenPhish, Google Safe Browsing API)
- Neo4j lookup: domain node existence + `riskScore` from fraud graph
- Cache miss → async enqueue for full reputation lookup (Celery task)

**Output:** `LinkAnalysisResult(risk_score: float, risk_level: RiskLevel, threat_indicators: [], domain_age_days: int, is_in_phishing_db: bool, brand_impersonation_target: str | None)`

Referenced from `EVENT_SCHEMA.md §7.1` — populates the `urls[]` enrichment array.

#### D3.4 — File Intelligence Engine

`backend/app/agents/file/file_analyzer.py`

**APK Analysis:**
- Package name extraction (`androguard`)
- Permission audit: flags `READ_SMS`, `SEND_SMS`, `READ_CALL_LOG`, `RECORD_AUDIO`, `ACCESSIBILITY_SERVICE` as HIGH risk
- Certificate check: unsigned or self-signed APK → HIGH risk
- Known malicious hash lookup (SHA-256 against VirusTotal-style blocklist)

**PDF Analysis:**
- JavaScript detection (`pymupdf`)
- Embedded URL extraction → routes to `LinkAnalysisEngine`
- Metadata: author, creator tool, creation date anomalies
- Pattern matching for fake government notice templates (court seal, ministry letterhead OCR check)

**Image Analysis:**
- QR code extraction (`pyzbar`) → extracted URL routed to `LinkAnalysisEngine`
- Basic EXIF metadata check

**Output:** `FileAnalysisResult(risk_level: RiskLevel, file_type: str, threat_indicators: [], extracted_urls: [], malware_probability: float)`

Referenced from `EVENT_SCHEMA.md §7.2` — populates the `attachments[]` enrichment array.

Privacy rule: `on_device_path` field MUST NEVER be transmitted (PRI-002). Android sends only the file bytes or a signed temporary upload reference.

#### D3.5 — Intelligence Feed Manager

`backend/app/feeds/feed_manager.py`

Celery Beat scheduled tasks:

- `FeedUpdateTask` (every 6 hours): polls PhishTank JSON API, OpenPhish CSV, abuse.ch URLhaus
- `ModelUpdateTask` (daily): checks model registry for updated classifier weights
- `FeedNormalizerAgent`: normalizes raw feed entries into `Domain`, `PhoneNumber`, `IPAddress` Neo4j nodes
- Populates Redis intelligence cache with hot domain/phone blocklists (sorted sets by risk score)

`backend/app/db/neo4j/` — Neo4j graph node population:
```cypher
MERGE (d:Domain {fqdn: $fqdn})
SET d.riskScore = $risk, d.lastSeen = $ts
```

#### D3.6 — Risk Scoring Model

`backend/app/agents/risk/risk_scoring_agent.py`

Aggregates signals from all active agents into a single `RiskAssessment`:

- Weighted score formula:
  ```
  final_score = (
    scam_classifier_confidence * 0.35 +
    digital_arrest_confidence  * 0.30 +
    link_risk_score            * 0.20 +
    file_risk_score            * 0.15
  )
  ```
  Weights are configurable in `app/config.py`.

- **Risk level mapping** (per `project_context.md §10`):
  - `0.00–0.30` → GREEN (Safe)
  - `0.31–0.60` → YELLOW (Suspicious)
  - `0.61–0.85` → RED (High Risk)
  - `0.86–1.00` → CRITICAL (Immediate Threat)

- Populates `risk_assessment` enrichment block per `EVENT_SCHEMA.md §7.3`
- Emits `sentinel.risk.assessed` event type (§2.2)

---

### 3.2 Team Assignments

| Deliverable | Lead | Supporting |
|---|---|---|
| D3.1 Scam Text Classifier | AI | BE |
| D3.2 Digital Arrest Scam Detector | AI | BE |
| D3.3 Link Intelligence Engine | BE | AI, DE |
| D3.4 File Intelligence Engine | BE | AI |
| D3.5 Intelligence Feed Manager | DE | BE, OPS |
| D3.6 Risk Scoring Model | AI | BE |

---

### 3.3 Dependencies

- **Phase 2 complete:** Backend event intake API (D2.8) must be running so classifiers receive real events during development.
- **D3.5 (Feed Manager)** can start in parallel with classifier development as soon as Docker/Neo4j/Redis are running (D1.6).
- **D3.6 (Risk Scoring)** depends on D3.1, D3.2, D3.3, D3.4 producing output structs — even stub output is sufficient for integration.
- **D3.3 (Link Engine)** depends on Redis being populated by D3.5 for cache hits to work.

---

### 3.4 Risks

| Risk | Severity | Probability | Mitigation |
|---|---|---|---|
| NLP model inference latency exceeds 3s SLA | HIGH | MEDIUM | Always have rule-based fallback ready (D3.1 fallback path). Profile on target hardware. Cache embeddings in Redis. |
| Scam training data is insufficient or biased | HIGH | HIGH | Use synthetic generation script for Indian-specific scam patterns (₹, Hinglish, agency names). Supplement with public datasets. |
| VirusTotal / PhishTank API rate limits during demo | MEDIUM | HIGH | Pre-populate Redis cache with demo fixture domains before presentation. All demo URLs must be pre-cached. |
| APK analysis library (`androguard`) has heavy dependencies | MEDIUM | LOW | Pin version. If install fails, use lightweight manifest-only parser as fallback. |
| Neo4j graph queries slow on cold start | MEDIUM | LOW | Pre-warm Neo4j with seed data (`backend/scripts/seed_neo4j.py`) before demo. Index `Domain.fqdn` and `PhoneNumber.number`. |
| Model fine-tuning not completed in time | HIGH | MEDIUM | Ship with a prompt-based LLM fallback (Claude/GPT via API) that classifies text using few-shot examples. Replace with fine-tuned model post-hackathon. |

---

### 3.5 Estimated Effort

| Deliverable | Effort |
|---|---|
| D3.1 Scam Text Classifier | 10h |
| D3.2 Digital Arrest Scam Detector | 6h |
| D3.3 Link Intelligence Engine | 8h |
| D3.4 File Intelligence Engine | 6h |
| D3.5 Intelligence Feed Manager | 4h |
| D3.6 Risk Scoring Model | 4h |
| **Phase 3 Total** | **~38h** |

---

## Phase 4 — Master Agent

**Objective:** Build the `ThreatOrchestrator` — the central multi-agent decision engine that receives raw events from the intake API, fans out to all relevant specialist agents in parallel, aggregates their results, runs the risk scoring model, generates the final verdict, and returns a structured `ThreatResponse` to the Android client. This phase also builds the AI Security Copilot.

**Duration:** Days 8–10 (32 engineer-hours)

---

### 4.1 Deliverables

#### D4.1 — ThreatOrchestrator

`backend/app/orchestration/threat_orchestrator.py`

The master coordinator that every event flows through:

```python
class ThreatOrchestrator:
    async def orchestrate(self, event: CommunicationEvent) -> ThreatResponse:
        # 1. Determine which agents are relevant for this event
        agents = self.agent_registry.get_agents_for(event.channel, event.content)
        
        # 2. Fan out to all agents in parallel
        results = await asyncio.gather(
            *[agent.analyze(AgentContext(event)) for agent in agents],
            return_exceptions=True
        )
        
        # 3. Aggregate results and compute risk score
        risk_assessment = self.risk_scoring_agent.score(results)
        
        # 4. Generate investigation report
        report = await self.explanation_agent.explain(event, results, risk_assessment)
        
        # 5. Persist complete event to PostgreSQL + Neo4j
        await self.persistence_service.save(event, risk_assessment, report)
        
        # 6. Trigger alert if threshold exceeded
        if risk_assessment.risk_level in (RiskLevel.RED, RiskLevel.CRITICAL):
            await self.alert_service.trigger(event, risk_assessment)
        
        return ThreatResponse(event_id=event.event_id, risk_assessment=risk_assessment, report=report)
```

- Parallel agent dispatch using `asyncio.gather` — all agents run concurrently within the 3s SLA
- Exception isolation: one agent failure does not block the verdict; `AgentResult.error` is logged
- Emits `sentinel.risk.assessed` and `sentinel.investigation.completed` events

#### D4.2 — AgentRegistry

`backend/app/agents/registry/agent_registry.py`

Auto-discovers agents via capability tags. Channel → agent routing table:

| Channel | Agents Invoked |
|---|---|
| SMS | ScamClassifier, DigitalArrestDetector, LinkAnalyzer (if URL present) |
| WHATSAPP | ScamClassifier, DigitalArrestDetector, LinkAnalyzer (if URL), FileAnalyzer (if attachment) |
| TELEGRAM | ScamClassifier, DigitalArrestDetector, LinkAnalyzer (if URL), FileAnalyzer (if attachment) |
| CALL | DigitalArrestDetector, ScamClassifier (on metadata) |
| GMAIL | ScamClassifier, PhishingDetector, LinkAnalyzer, FileAnalyzer |
| COPILOT | ScamClassifier, LinkAnalyzer, DigitalArrestDetector (intent-driven) |

Adding a new agent requires only: subclass `BaseAgent`, register with capability tags. The orchestrator auto-discovers it (per `REPOSITORY_ARCHITECTURE.md §5.1`).

#### D4.3 — Decision Engine

`backend/app/agents/decision/decision_engine.py`

Post-aggregation logic that applies override rules on top of the weighted risk score:

- **Override Rule R1:** If `is_digital_arrest = true` AND `confidence > 0.80` → force `CRITICAL` regardless of weighted score
- **Override Rule R2:** If `is_in_phishing_db = true` (Redis/PhishTank hit) → force minimum `RED`
- **Override Rule R3:** If `has_authority_claim = true` AND `has_financial_mention = true` AND `has_urgency_language = true` (all three heuristics) → force minimum `RED`
- **Negative Override N1:** If `ScamClassifier.confidence < 0.30` AND no link/file threats → cap at `GREEN`
- Final `processing_status` transition: `ANALYZING → COMPLETED`

#### D4.4 — Alert Service

`backend/app/services/alert_service.py` + Android `AlertViewModel`

Backend:
- Persists `AlertEntity` to PostgreSQL when risk level ≥ RED
- Returns alert payload in `ThreatResponse`

Android:
- `AlertViewModel` subscribes to `ThreatEventBus`
- On receiving `SmsThreatDetected`, `WhatsAppThreatDetected`, etc.: persists to Room via `AlertRepository`, updates `AlertScreen` state
- Emits `UiEvent.ShowOverlayAlert` side-effect channel to trigger Phase 5 overlay

#### D4.5 — AI Security Copilot

`backend/app/services/copilot_service.py` + Android `CopilotScreen`

Backend:
```
POST /v1/copilot/chat
```
- `IntentClassifier` — determines user intent: link check, SMS check, call check, general fraud question, general advice
- `ConversationManager` — maintains multi-turn chat history (stored in PostgreSQL, keyed by device hash)
- `ThreatOrchestrator` integration — if intent = `THREAT_ANALYSIS`, orchestrates a full analysis of the pasted content
- `ResponseGenerator` — LLM call (Claude claude-sonnet-4-6 or GPT-4o) with system prompt: "You are Sentinel AI, an Indian cybersecurity assistant. You help users identify scams, phishing, and digital arrest fraud. Be clear, empathetic, and direct."
- `SafetyGuardrails` — output filter: never advise paying a ransom, never suggest complying with digital arrest

Android `CopilotScreen`:
- Chat bubble UI (`CopilotChatBubble.kt`)
- Supports: text input, paste link, paste message text
- Shows `RiskBadge` inline with AI response when a threat verdict is embedded
- `CopilotViewModel` → `SendCopilotMessageUseCase` → `CopilotRepository` → `CopilotApiService`

Supported event types: `sentinel.copilot.query` (§2.1)

#### D4.6 — ThreatResponse → Android Pipeline

Complete the Android response path:

- `ThreatResponse` from backend → `ThreatRepository` → `ScanResult` domain model → `AlertViewModel`
- `ScanResult` persisted to Room via `ScanHistoryDao` with final `processing_status = COMPLETED`
- `HistoryScreen` renders all past scan results with `RiskBadge` indicators
- `AlertDetailScreen` shows full investigation report from backend

---

### 4.2 Team Assignments

| Deliverable | Lead | Supporting |
|---|---|---|
| D4.1 ThreatOrchestrator | BE | AI |
| D4.2 AgentRegistry | BE | — |
| D4.3 Decision Engine | AI | BE |
| D4.4 Alert Service | BE | AND |
| D4.5 AI Security Copilot | AI | BE, AND |
| D4.6 ThreatResponse → Android pipeline | AND | BE |

---

### 4.3 Dependencies

- **Phase 3 complete:** All intelligence agents (D3.1–D3.6) must be functional (even if not fully tuned) before the orchestrator can fan out to them.
- **D4.5 (Copilot)** requires a working LLM API key provisioned in `app/config.py`. OPS must provide this before AI team begins D4.5.
- **D4.6** depends on the `ThreatResponse` schema being finalized in D4.1 before AND team builds the Android consumption layer.
- **D4.2 (AgentRegistry)** can begin as soon as Phase 3 agent class signatures are defined — actual agent implementations do not need to be complete.

---

### 4.4 Risks

| Risk | Severity | Probability | Mitigation |
|---|---|---|---|
| End-to-end latency exceeds 3s SLA | HIGH | MEDIUM | Profile at D4.1 completion. Profile each agent independently. Redis cache is the primary latency lever. Async gather is non-negotiable. |
| LLM API rate limits or cost overruns during demo | MEDIUM | LOW | Use `claude-haiku-4-5` for copilot (cheaper, faster) with fallback to template responses for common scam patterns |
| Override rules in D4.3 producing too many false positives | MEDIUM | MEDIUM | QA to run full regression suite before Phase 5. Rule thresholds must be calibrated with real test data. |
| Agent exceptions causing total orchestration failure | HIGH | LOW | `asyncio.gather(return_exceptions=True)` prevents cascade. Add circuit breaker per agent. |
| Multi-turn conversation state storage at scale | LOW | LOW | Hackathon MVP: limit conversation history to last 10 turns. Truncate beyond that. |

---

### 4.5 Estimated Effort

| Deliverable | Effort |
|---|---|
| D4.1 ThreatOrchestrator | 6h |
| D4.2 AgentRegistry | 2h |
| D4.3 Decision Engine | 4h |
| D4.4 Alert Service | 4h |
| D4.5 AI Security Copilot | 10h |
| D4.6 ThreatResponse → Android pipeline | 4h |
| **Phase 4 Total** | **~30h** |

---

## Phase 5 — Overlay Protection

**Objective:** Build the real-time on-device intervention layer. When the backend returns a RED or CRITICAL verdict, Sentinel AI must visually interrupt the user before they can open a link, call back a scammer, or share sensitive data. This phase delivers the `OverlayAlertService`, the system alert window UI, the in-app alert screens, and the manual scanner.

**Duration:** Days 10–12 (24 engineer-hours)

---

### 5.1 Deliverables

#### D5.1 — OverlayAlertService

`services/OverlayAlertService.kt`

System overlay that appears on top of any app when a CRITICAL or RED threat is detected:

- Uses `SYSTEM_ALERT_WINDOW` permission + `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`
- Triggered by `ThreatEventBus` subscription — fires within 500ms of `ThreatResponse` receipt
- **Overlay layout:**
  - Full-width banner at top of screen (not full-screen, to preserve user control)
  - Sentinel AI shield icon + "Threat Detected" headline
  - Risk level badge (RED/CRITICAL with colour coding)
  - Shortened threat reason (max 2 lines): "This WhatsApp message appears to be a CBI impersonation scam"
  - Two actions: **"See Details"** → opens `AlertDetailScreen` | **"Dismiss"** → logs dismissal, removes overlay
- CRITICAL threats: auto-expand to full-screen modal; cannot be dismissed with a swipe; requires explicit user action
- Overlay lifetime: auto-dismiss after 30s if user takes no action (RED); CRITICAL never auto-dismisses
- Accessibility: `contentDescription` on all interactive elements; TalkBack compatible

#### D5.2 — In-App Alert Screens

`ui/screens/alert/`

**`AlertScreen.kt`** — Alert inbox:
- Lists all persisted alerts from Room (via `AlertViewModel` → `GetAlertHistoryUseCase`)
- Each row: `ThreatCard` with channel icon, timestamp, risk badge, sender hash, first 50 chars of content body
- Swipe to dismiss (calls `DismissAlertUseCase`)
- Empty state: "You're safe. No threats detected."
- Pull-to-refresh

**`AlertDetailScreen.kt`** — Full threat investigation:
- Full `RiskBadge` with score and level
- "What happened" — plain-language threat summary (from `investigation_report.summary`)
- "Why it's risky" — bullet list of triggered signals (from `investigation_report.signals[]`)
- "What to do" — recommended action (from `investigation_report.recommended_action`)
- Link to AI Copilot: "Ask Sentinel AI about this threat"
- Report as false positive button

#### D5.3 — Risk Badge Component

`ui/components/RiskBadge.kt`

Reusable Compose component used across Dashboard, Alert, and Copilot screens:

```kotlin
@Composable
fun RiskBadge(
    level: RiskLevel,      // GREEN / YELLOW / RED / CRITICAL
    score: Float,          // 0.0–1.0
    showScore: Boolean = false
)
```

| Level | Colour | Icon |
|---|---|---|
| GREEN | `#2E7D32` | Shield check |
| YELLOW | `#F9A825` | Shield warning |
| RED | `#C62828` | Shield alert |
| CRITICAL | `#B71C1C` + pulse animation | Shield X |

#### D5.4 — Manual Scanner (Scan-on-Demand)

`ui/screens/scanner/ScannerScreen.kt`

User-initiated threat check:

- **Text tab:** Paste a message, WhatsApp text, or SMS. Submits to `POST /v1/analyze/sms` (reuses SMS endpoint for manual input)
- **Link tab:** Paste or type a URL. Submits to `POST /v1/analyze/link`
- **File tab:** Pick a file from storage. Submits to `POST /v1/analyze/file`
- Loading state: animated Sentinel shield during analysis
- Result rendered inline: `RiskBadge` + summary from `investigation_report`
- "Send to Copilot" shortcut for follow-up questions

Supported event type: `sentinel.copilot.query` (§2.1) when submitted via scanner.

#### D5.5 — Dashboard Upgrade

`ui/screens/dashboard/DashboardScreen.kt`

Upgrade from Phase 1 shell:

- Guardian status card: ON/OFF toggle → starts/stops `SentinelGuardService`
- Protection coverage summary: SMS ✓, WhatsApp ✓, Telegram ✓, Calls ✓, Gmail ✓ (or permission missing ⚠️)
- Today's stats: threats detected, messages scanned, links checked
- Recent alerts strip: last 3 `ThreatCard`s with "View All" link
- Quick Scan shortcut → `ScannerScreen`
- Copilot shortcut → `CopilotScreen`

#### D5.6 — Demo Scenario Automation

`debug/DemoController.kt` (debug build only):

A hidden debug menu (shake gesture) that allows demo facilitators to:
- Inject a fake scam WhatsApp notification → triggers full pipeline → fires overlay
- Inject a fake CBI arrest SMS → triggers CRITICAL alert
- Inject a known phishing URL → fires RED link alert
- Clear all alerts and reset stats

All demo injections use the `SCAM-WA-00x` fixture data from `WHATSAPP_AGENT_SPEC.md §8.4`.

---

### 5.2 Team Assignments

| Deliverable | Lead | Supporting |
|---|---|---|
| D5.1 OverlayAlertService | AND | QA |
| D5.2 In-App Alert Screens | AND | — |
| D5.3 Risk Badge Component | AND | — |
| D5.4 Manual Scanner | AND | BE |
| D5.5 Dashboard Upgrade | AND | — |
| D5.6 Demo Scenario Automation | AND | QA |

---

### 5.3 Dependencies

- **Phase 4 complete:** `ThreatResponse` structure (D4.1) and `ThreatEventBus` events (D4.4) must be stable before overlay can be built.
- **D5.1 requires `SYSTEM_ALERT_WINDOW` permission** — must be granted in `PermissionManager` flow (D1.9). Verify the permission is included in onboarding.
- **D5.4 (Manual Scanner)** depends on backend `/v1/analyze/link` and `/v1/analyze/sms` endpoints (D2.8) being stable.
- **D5.6 (Demo Controller)** depends on all other Phase 5 deliverables being complete so the full demo path can be validated end-to-end.

---

### 5.4 Risks

| Risk | Severity | Probability | Mitigation |
|---|---|---|---|
| `SYSTEM_ALERT_WINDOW` overlay blocked by device OEM (Samsung One UI, Xiaomi MIUI) | HIGH | HIGH | Test on Samsung and Xiaomi demo devices. Add OEM-specific Settings deep-links in onboarding. |
| Overlay appearing on top of lock screen or secure screens | MEDIUM | LOW | Set `FLAG_NOT_FOCUSABLE`; exclude lock screen via `WindowManager` flags |
| File upload in manual scanner triggers Android storage permission denial | MEDIUM | MEDIUM | Use `ActivityResultContracts.GetContent()` (no storage permission needed); fall back gracefully |
| Demo automation (D5.6) accidentally running in production build | LOW | LOW | Gate strictly with `BuildConfig.DEBUG`. Remove from release ProGuard output. |
| Alert screen showing stale data from Room after a network error | MEDIUM | LOW | Room `Flow<List<AlertEntity>>` is always-live. Stale data is impossible if Room is the single source of truth (ADR-004). |

---

### 5.5 Estimated Effort

| Deliverable | Effort |
|---|---|
| D5.1 OverlayAlertService | 6h |
| D5.2 In-App Alert Screens | 5h |
| D5.3 Risk Badge Component | 2h |
| D5.4 Manual Scanner | 4h |
| D5.5 Dashboard Upgrade | 3h |
| D5.6 Demo Scenario Automation | 2h |
| **Phase 5 Total** | **~22h** |

---

## Phase 6 — Investigation Engine

**Objective:** Build the `ExplanationAgent` — the system that converts raw threat signals and risk scores into human-readable, empathetic, and actionable safety explanations. This phase also delivers the `investigation_report` enrichment block per `EVENT_SCHEMA.md §7.4`, the backend investigation report storage and retrieval, and the full `AlertDetailScreen` investigation view.

**Duration:** Days 11–13 (20 engineer-hours)

---

### 6.1 Deliverables

#### D6.1 — ExplanationAgent

`backend/app/agents/explanation/explanation_agent.py`

Generates a structured `InvestigationReport` from the aggregated agent results:

```python
class InvestigationReport:
    summary: str              # 1–2 sentences. Plain language. No jargon.
    why_risky: List[str]      # Bulleted signals. Max 5. Specific.
    what_to_do: str           # Single clear action.
    confidence_statement: str # Honest about uncertainty.
    scam_type_label: str      # e.g., "Digital Arrest Scam", "KYC Phishing"
    severity_label: str       # "Immediate Threat" / "High Risk" / "Suspicious" / "Safe"
```

**Generation Strategy:**

For high-confidence verdicts (confidence > 0.85): Use templated generation with variable substitution. Fast, consistent, and immune to LLM hallucination.

```
Template (DIGITAL_ARREST):
"This message appears to be a [impersonated_agency] impersonation scam 
known as a Digital Arrest Fraud. Fraudsters claim to be officials to 
threaten victims into paying money."

why_risky:
- "Claims to be from {impersonated_agency}"  
- "Uses urgent arrest language ({matched_phrases})"
- "Demands immediate financial action"
- "Sent from an unknown/unverified number"
```

For medium-confidence verdicts (0.50–0.85): LLM-assisted generation — send the signals to an LLM with the template as a constraint and the raw signals as context. Guardrails prevent contradicting the risk verdict.

For low-confidence (< 0.50): Conservative template — "This message has some unusual patterns. Exercise caution before clicking any links or sharing personal information."

**Scam Type Label Map:**
```python
LABEL_MAP = {
    "DIGITAL_ARREST":          "Digital Arrest Scam",
    "AUTHORITY_IMPERSONATION": "Government Impersonation Scam",
    "KYC_SCAM":                "KYC / Account Update Fraud",
    "BANKING_SCAM":            "Banking Fraud",
    "LOTTERY_SCAM":            "Lottery / Prize Scam",
    "OTP_THEFT":               "OTP Theft Attempt",
    "PHISHING":                "Phishing Attack",
    "FINANCIAL_COERCION":      "Financial Coercion Scam",
    "SAFE":                    "No Threat Detected"
}
```

Emits: `sentinel.investigation.completed` event type (§2.2)
Populates: `investigation_report` enrichment block per `EVENT_SCHEMA.md §7.4`

#### D6.2 — Investigation Report Storage & Retrieval

Backend:
- `InvestigationReportEntity` in PostgreSQL (linked to `ScanHistoryEntity` by `event_id`)
- `GET /v1/scan/{event_id}/report` — returns the full investigation report for a given event

Android:
- `ScanHistoryRepository.getReport(eventId: String): Flow<InvestigationReport>`
- `AlertDetailViewModel` fetches and caches the report
- `ScanHistoryEntity` extended with a `reportJson: String?` column to cache the report locally

#### D6.3 — Alert Detail Screen — Investigation View

`ui/screens/alert/AlertDetailScreen.kt` (completing the stub from Phase 5 D5.2)

Full investigation report rendering:

```
┌────────────────────────────────────┐
│ 🛡 CRITICAL — Immediate Threat     │
│ Risk Score: 0.94                   │
├────────────────────────────────────┤
│ WHAT HAPPENED                      │
│ This WhatsApp message is a Digital │
│ Arrest Scam. A fraudster claimed   │
│ to be a CBI officer.               │
├────────────────────────────────────┤
│ WHY IT'S RISKY                     │
│ • Claims to be from CBI            │
│ • Uses arrest threat language      │
│ • Demands immediate payment        │
│ • Sent from unknown number         │
│ • Pattern matches known scam       │
├────────────────────────────────────┤
│ WHAT TO DO                         │
│ Do not call back. Do not pay.      │
│ Real agencies never contact        │
│ citizens via WhatsApp.             │
├────────────────────────────────────┤
│ [Ask Sentinel AI]  [Report Scam]   │
└────────────────────────────────────┘
```

- Animated CRITICAL badge (pulse effect matching `RiskBadge`)
- "Ask Sentinel AI" → opens `CopilotScreen` pre-seeded with the threat context
- "Report Scam" → deep-links to NCRP (cybercrime.gov.in) — post-MVP, this will auto-submit

#### D6.4 — Explanation Quality Test Suite

`QA/test_explanation_quality.py`

Automated regression tests for explanation quality:

| Test ID | Input Fixture | Assert |
|---|---|---|
| `EQ-001` | SCAM-WA-001 (CBI arrest) | `scam_type_label == "Digital Arrest Scam"` AND `why_risky` contains "CBI" |
| `EQ-002` | SCAM-WA-002 (lottery + phishing URL) | `why_risky` contains "phishing link" |
| `EQ-003` | SCAM-WA-005 (ED + financial coercion) | `severity_label == "Immediate Threat"` |
| `EQ-004` | SCAM-WA-007 (family message, safe) | `severity_label == "Safe"` AND `why_risky` is empty |
| `EQ-005` | Any CRITICAL verdict | `what_to_do` does NOT contain "pay" as advice (anti-complicity guard) |
| `EQ-006` | Any verdict | `summary` length ≤ 200 chars |
| `EQ-007` | Any verdict | `why_risky` list length ≤ 5 items |

#### D6.5 — Copilot Context Injection

When a user opens Copilot from `AlertDetailScreen`, the conversation is pre-seeded with threat context:

```
System: You are Sentinel AI. A threat has been detected.
        Scam type: Digital Arrest Scam
        Risk level: CRITICAL (0.94)
        Signals: CBI impersonation, arrest threat, financial demand

User: [user's question appears here]
```

This ensures the Copilot response is grounded in the specific detected threat rather than providing generic advice.

---

### 6.2 Team Assignments

| Deliverable | Lead | Supporting |
|---|---|---|
| D6.1 ExplanationAgent | AI | BE |
| D6.2 Investigation Report Storage & Retrieval | BE | DE, AND |
| D6.3 Alert Detail Screen — Investigation View | AND | — |
| D6.4 Explanation Quality Test Suite | QA | AI |
| D6.5 Copilot Context Injection | AI | AND |

---

### 6.3 Dependencies

- **Phase 4 D4.1 (ThreatOrchestrator):** The orchestrator calls `ExplanationAgent` as its final step (D6.1). D6.1 can be developed using mock `AgentResult` inputs — it does not need the full Phase 3 pipeline to be complete.
- **Phase 5 D5.2 (AlertDetailScreen stub):** D6.3 completes the stub screen. The stub must exist before D6.3 can be built.
- **D6.4 (Test Suite)** can be developed in parallel with D6.1. Fixture data from `WHATSAPP_AGENT_SPEC.md §8.4` is the input.
- **D6.5 (Copilot Context Injection)** depends on D4.5 (Copilot) being complete and accepting a pre-seeded system context.

---

### 6.4 Risks

| Risk | Severity | Probability | Mitigation |
|---|---|---|---|
| LLM-generated explanations contradict the risk verdict | HIGH | MEDIUM | Guardrail: post-process LLM output. If verdict is CRITICAL but explanation says "safe", override with template. |
| LLM hallucinating specific agency names or legal references | HIGH | MEDIUM | Template-first strategy for high-confidence verdicts. LLM only used for medium-confidence edge cases. |
| Explanation text too long for overlay UI (D5.1) | LOW | LOW | Overlay uses only `summary` (max 2 lines). Full report in `AlertDetailScreen` only. |
| Investigation report latency adding to overall 3s SLA | MEDIUM | MEDIUM | ExplanationAgent must complete in < 500ms. Template generation is < 10ms. LLM path gets 400ms budget. |
| False positive explanation eroding user trust | HIGH | LOW | QA-004 explicitly guards the safe-message case. Any `SAFE` verdict must produce zero `why_risky` items. |

---

### 6.5 Estimated Effort

| Deliverable | Effort |
|---|---|
| D6.1 ExplanationAgent | 6h |
| D6.2 Investigation Report Storage & Retrieval | 4h |
| D6.3 Alert Detail Screen — Investigation View | 5h |
| D6.4 Explanation Quality Test Suite | 2h |
| D6.5 Copilot Context Injection | 2h |
| **Phase 6 Total** | **~19h** |

---

## Master Schedule Summary

| Phase | Title | Duration | Total Effort |
|---|---|---|---|
| Phase 1 | Foundation | Days 1–2 | ~20h |
| Phase 2 | Collection Agents | Days 3–5 | ~34h |
| Phase 3 | Fraud Intelligence | Days 5–8 | ~38h |
| Phase 4 | Master Agent | Days 8–10 | ~30h |
| Phase 5 | Overlay Protection | Days 10–12 | ~22h |
| Phase 6 | Investigation Engine | Days 11–13 | ~19h |
| **Total** | | **13 days** | **~163h** |

---

## Cross-Phase Non-Functional Requirements

### Performance SLA

| Operation | Target | Measured At |
|---|---|---|
| Event capture → backend submission | < 500ms | Android agent coordinator |
| Backend event analysis (end-to-end) | < 3,000ms | `ThreatOrchestrator.orchestrate()` |
| Overlay alert appearance after analysis | < 500ms | `OverlayAlertService.show()` |
| Copilot response | < 5,000ms | `CopilotService.chat()` |
| Link analysis (cold, no cache) | < 1,000ms | `LinkAnalyzer.analyze()` |
| Link analysis (warm, Redis cache) | < 50ms | `LinkAnalyzer.analyze()` |

### Privacy Invariants (Enforced Across All Phases)

Per `EVENT_SCHEMA.md §10.3 (PRI-001 through PRI-006)`:

1. `privacy_mode = true` is the default and must be enforced in all agent builders
2. `source.raw_identifier`, `source.display_name`, `source.e164_number` must never be transmitted
3. `attachments[n].on_device_path` must never be transmitted
4. `channel_payload.call_recording_reference` must never be transmitted
5. Neo4j and PostgreSQL must never persist raw PII — only `identifier_hash`
6. File backend storage keys expire in 24 hours

### Schema Governance

- Any change to `EVENT_SCHEMA.md` requires Principal Architect (CTO) sign-off
- MINOR version bumps (new optional fields) require all team leads to update their Pydantic models and Kotlin DTOs within 24 hours
- MAJOR version bumps trigger a full dual-write migration per `EVENT_SCHEMA.md §11.5`

### Test Coverage Requirements

| Layer | Minimum Coverage |
|---|---|
| Android unit tests | 70% line coverage on agent, use case, and repository classes |
| Backend unit tests | 80% line coverage on all agent classes |
| Fraud regression fixtures | 100% pass rate on all `SCAM-WA-00x` fixtures |
| Explanation quality tests | 100% pass rate on all `EQ-00x` tests |
| Integration tests | End-to-end happy path from WhatsApp notification → CRITICAL overlay |

---

## Demo Readiness Checklist

The following must all be true before the hackathon demo:

- [ ] Demo device has Sentinel AI installed with all permissions granted
- [ ] `SentinelGuardService` starts on device boot
- [ ] `DemoController` (D5.6) can inject all `SCAM-WA-00x` fixtures
- [ ] Full pipeline latency measured at < 3 seconds for demo fixtures
- [ ] Redis cache pre-populated with all demo phishing URLs
- [ ] Neo4j seeded with known scam phone numbers and domains
- [ ] Overlay fires on device for SCAM-WA-001 (CBI arrest) and SCAM-WA-002 (lottery + URL)
- [ ] `AlertDetailScreen` renders full investigation report for SCAM-WA-005 (ED scam)
- [ ] Copilot responds intelligently to: "Is this CBI message real?"
- [ ] SCAM-WA-007 (family dinner message) returns GREEN — zero false positive
- [ ] Battery usage measured: < 5% per hour during continuous monitoring

---

*"Think Before You Click. Sentinel Thinks Before You Do."*

---

**Document Version:** 1.0.0
**Status:** Approved for Hackathon Execution
**Owner:** CTO
**Review Cadence:** End of each phase
**Last Updated:** 2026-06-23
