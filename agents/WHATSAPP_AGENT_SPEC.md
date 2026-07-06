# WHATSAPP_AGENT_SPEC.md
# Sentinel AI — WhatsApp Agent V1 Design Specification

**Version:** 1.0.0
**Author Role:** Senior Android Security Engineer
**Hackathon:** ET AI Hackathon 2026 — Problem Statement 6
**References:**
- `project_context.md` — Product Vision, F3 WhatsApp Fraud Shield
- `ANDROID_ARCHITECTURE.md` v1.0.0 — Package Structure, MVVM, DI, ThreatEventBus, ADR-003, ADR-006
- `EVENT_SCHEMA.md` v1.0.0 — §1.3 Lifecycle, §2.1 Event Types, §3 Base Envelope, §4 Source Block, §5 Content Block, §6.3 WhatsApp Payload
**Status:** Draft — Hackathon MVP
**Last Updated:** 2026-06-23

---

## Table of Contents

1. [Responsibilities](#1-responsibilities)
2. [Components](#2-components)
3. [Classes](#3-classes)
4. [Interfaces](#4-interfaces)
5. [Data Flow](#5-data-flow)
6. [Sequence Diagram](#6-sequence-diagram)
7. [Error Handling](#7-error-handling)
8. [Testing Strategy](#8-testing-strategy)

---

## 1. Responsibilities

The WhatsApp Agent V1 is the **event capture and normalization layer** for the WhatsApp channel in Sentinel AI. It sits at the boundary between the Android OS notification subsystem and the Sentinel AI Threat Intelligence Layer, translating raw OS signals into schema-compliant `CommunicationEvent`s ready for backend analysis.

### 1.1 Primary Responsibilities

**R1 — Notification Interception**
Receive all `StatusBarNotification` objects delivered to `SentinelNotificationListener` (the system `NotificationListenerService`) and filter by the WhatsApp package whitelist (`com.whatsapp`, `com.whatsapp.w4b`). Ignore all other packages.

**R2 — Raw Signal Extraction**
Parse the `Notification.extras` bundle from the `StatusBarNotification` to extract: sender display name, message text (preferring `EXTRA_BIG_TEXT` over `EXTRA_TEXT` when available), group name, notification action labels, and forwarding label indicators. Produce a `WhatsAppRawNotificationData` value object.

**R3 — CommunicationEvent Construction**
Map extracted raw signals to a fully schema-compliant `WhatsAppCommunicationEvent` conforming to `EVENT_SCHEMA.md` v1.0.0. The constructed event must include:
- Base envelope: `event_id` (UUID v4), `event_type`, `channel`, `processing_status`, `captured_at`, `submitted_at`, `device_id`, `app_version`, `schema_version`, `ttl_seconds`
- Source block: `identifier_hash`, `identifier_type = WHATSAPP_JID`, `is_known_contact`, `contact_type`
- Content block: `body`, `body_truncated`, `character_count`, `contains_urls`, `url_count`, `contains_attachments`, `has_urgency_language`, `has_authority_claim`, `has_financial_mention`, `media_type`
- WhatsApp channel_payload: `chat_id_hash`, `sender_wa_id_hash`, `is_group_chat`, `group_name`, `message_type`, `is_forwarded`, `forward_chain_length`, `is_broadcast`, `capture_method`, `has_call_button`

**R4 — Privacy Enforcement**
Apply the privacy mode rules defined in `EVENT_SCHEMA.md` §4.3 before any data leaves the device. When `privacy_mode = true` (the default and safe fallback), strip `source.raw_identifier`, `source.display_name`, `source.platform_handle`, and `source.e164_number`. Retain only `identifier_hash` and `chat_id_hash` as SHA-256 digests. Privacy mode must be enforced unconditionally inside `WhatsAppEventBuilder`, independent of any upstream caller behaviour.

**R5 — Deduplication**
Prevent duplicate `CommunicationEvent` submissions caused by WhatsApp re-posting the same notification for badge count updates, message reactions, or notification coalescing. Use an in-memory LRU cache (capacity 256, TTL 60 seconds) keyed on a composite fingerprint of `StatusBarNotification.key` and a CRC32 of the notification text.

**R6 — URL Pre-Screening**
Detect URL-like patterns in the notification body using a lightweight on-device regex. Set `content.contains_urls = true` and `content.url_count` when URLs are found. This flag routes the backend event to the `LinkAgent` without performing any on-device URL analysis in V1.

**R7 — Client-Side Content Heuristics**
Compute three lightweight boolean signals on-device before transmission to enable fast backend routing and preliminary risk estimation:
- `has_urgency_language` — keyword match against a curated list: arrest, urgent, deadline, block, suspend, action required, legal notice, and Hinglish equivalents (turant, abhi)
- `has_authority_claim` — entity match: CBI, ED, RBI, TRAI, Customs, Police, Income Tax, Ministry, Court, Tribunal
- `has_financial_mention` — currency or financial keyword: ₹, lakh, crore, account, payment, refund, transfer, fine, penalty

**R8 — Event Dispatch**
Submit the validated `WhatsAppCommunicationEvent` to `WhatsAppAgentCoordinator`, which calls `AnalyzeWhatsAppUseCase`, handles the `Result<ScanResult>`, and emits a `WhatsAppThreatDetected` event to `ThreatEventBus` (as per ADR-003, `SharedFlow`-based event bus).

**R9 — Event Lifecycle Management**
Transition the event through the lifecycle states defined in `EVENT_SCHEMA.md` §1.3: `CAPTURED → QUEUED → ANALYZING → COMPLETED` (or `FAILED`). Persist lifecycle state to Room via `ScanHistoryDao` so that pending and completed scans are visible in the History screen.

**R10 — Future Accessibility Integration Readiness**
V1 operates exclusively via `NotificationListenerService`. The architecture must accommodate a future `SentinelAccessibilityService` integration path (V1.1) without structural changes to downstream consumers. All extraction logic is abstracted behind interfaces so the Accessibility-sourced path can share the same normalization and dispatch pipeline. `AccessibilityIntegrationStub` documents the future contract explicitly.

---

### 1.2 Out of Scope for V1

| Capability | Reason | Target Version |
|---|---|---|
| `sentinel.whatsapp.file.shared` event type | File path extraction requires `AccessibilityService` | V1.1 |
| Full message thread context | Notification API exposes only the latest message per conversation | V1.1 |
| Real-time in-app overlay triggered from WhatsApp screen | Requires `SYSTEM_ALERT_WINDOW` + `AccessibilityService` | V1.1 |
| Media/file content analysis | Attachment path not exposed via Notification API | V1.1 |
| Voice note transcription | ASR pipeline not in MVP scope | V2 |
| Group member count or member list | Not exposed via Notification API | Future |
| `is_broadcast` detection | Cannot be reliably inferred from notification extras alone | V1.1 |

---

## 2. Components

The WhatsApp Agent V1 is composed of the following logical components, each mapping to a package location within the Sentinel AI Android application as defined in `ANDROID_ARCHITECTURE.md` §1.1.

```
com.sentinel.ai
├── listeners/
│   └── SentinelNotificationListener          [EXISTING — extended with WhatsApp routing]
│
├── agents/
│   └── whatsapp/
│       ├── WhatsAppAgentCoordinator           [NEW — extends BaseAgent]
│       ├── WhatsAppNotificationParser         [NEW — stateless extractor]
│       ├── WhatsAppEventBuilder               [NEW — schema-compliant builder]
│       ├── WhatsAppDeduplicationFilter        [NEW — in-memory LRU cache]
│       ├── WhatsAppContentHeuristics          [NEW — keyword signal detector]
│       └── AccessibilityIntegrationStub       [NEW — no-op V1.1 placeholder]
│
├── data/
│   └── remote/
│       └── dto/
│           └── request/
│               └── WhatsAppAnalysisRequest    [NEW — Retrofit DTO]
│
├── domain/
│   ├── model/
│   │   └── WhatsAppCommunicationEvent         [NEW — domain model]
│   └── usecase/
│       └── AnalyzeWhatsAppUseCase             [NEW — single-responsibility use case]
│
└── core/
    └── utils/
        └── UrlPatternDetector                 [SHARED — also used by SmsAgent]
```

### 2.1 Component Descriptions

**`SentinelNotificationListener`** *(listeners)*
The existing `NotificationListenerService` and single OS entry point for all notification-based events. For WhatsApp Agent V1, it gains one routing rule in `onNotificationPosted()`: when `sbn.packageName` belongs to the WhatsApp package whitelist, it delegates to `IWhatsAppAgent.onWhatsAppNotification(sbn)`. All other packages are routed to their respective agents. This service is always-on when `SentinelGuardService` is active.

**`WhatsAppNotificationParser`** *(agents/whatsapp)*
A stateless, pure-Kotlin class responsible solely for extracting fields from a `StatusBarNotification`. It produces a `WhatsAppRawNotificationData` value object. Performs no hashing, no privacy enforcement, no schema mapping, and no I/O. Its isolation from Android framework types (via constructor injection of an `ExtrasBundle` abstraction) enables fast, instrumentation-free unit tests.

**`WhatsAppEventBuilder`** *(agents/whatsapp)*
A stateless class that converts a `WhatsAppRawNotificationData` into a `WhatsAppCommunicationEvent`. Owns all SHA-256 hashing, privacy mode enforcement (read from `UserPreferences`), `event_id` generation (UUID v4), `device_id` derivation (SHA-256 of salted Android ID), timestamp stamping, content heuristics (delegated to `WhatsAppContentHeuristics`), URL pre-screening (delegated to `UrlPatternDetector`), and schema field population. Also provides a `validate()` method that returns a `ValidationResult` sealed class.

**`WhatsAppDeduplicationFilter`** *(agents/whatsapp)*
A `@Singleton` in-memory LRU cache (capacity 256, TTL 60 seconds). Accepts a composite fingerprint string derived from `StatusBarNotification.key` and a CRC32 of the notification body text. Returns a boolean `isDuplicate` and provides a `record()` method to register accepted fingerprints. Prevents duplicate event submission caused by notification re-posting, animated WhatsApp typing indicators, and OS-level notification coalescing.

**`WhatsAppContentHeuristics`** *(agents/whatsapp)*
A lightweight, stateless keyword and regex matcher. Takes the notification body string and returns a `ContentSignals` data object with boolean flags: `hasUrgencyLanguage`, `hasAuthorityClaim`, `hasFinancialMention`, and `hasOtpPattern`. Backed by curated keyword lists covering Indian fraud and scam contexts in both English and Hinglish transliterations. Pure string matching — no ML model — targeting sub-1 ms latency.

**`AccessibilityIntegrationStub`** *(agents/whatsapp)*
A no-op implementation of `IAccessibilityEventSource`. In V1, `accessibilityEvents()` returns an empty `Flow` and `isSupported()` returns `false`. When `SentinelAccessibilityService` gains the capability to read WhatsApp `AccessibilityNodeInfo` content (V1.1), this stub is replaced with a live implementation without changing any downstream consumer. It explicitly documents the contract expected by V1.1 implementers.

**`WhatsAppAgentCoordinator`** *(agents/whatsapp)*
The orchestration class that extends `BaseAgent`. Receives events from `SentinelNotificationListener` (V1 path) and the future `IAccessibilityEventSource` (V1.1). Coordinates the full pipeline: deduplication check → parsing → event building → validation → use case invocation → `ThreatEventBus` emission. Manages the `agentStatus: StateFlow<AgentStatus>` observable and event lifecycle state updates in Room.

**`WhatsAppAnalysisRequest`** *(data/remote/dto/request)*
The Retrofit DTO that serializes a `WhatsAppCommunicationEvent` domain model to JSON for the `/v1/analyze/whatsapp` backend endpoint. All field names are annotated with `@SerializedName` matching the exact field names defined in `EVENT_SCHEMA.md`. PII fields are typed as `String?` so that null values (set by the privacy filter) are omitted from the JSON body.

**`AnalyzeWhatsAppUseCase`** *(domain/usecase)*
A single-responsibility `suspend` function returning `Result<ScanResult>`. Delegates to `IThreatRepository.analyzeWhatsApp(WhatsAppAnalysisRequest)`. Follows the identical pattern of existing use cases (`AnalyzeSmsUseCase`, `AnalyzeLinkUseCase`) per `ANDROID_ARCHITECTURE.md` §3.3.

---

## 3. Classes

### 3.1 Class Catalog

| Class | Package | Type | Lifecycle | Responsibility |
|---|---|---|---|---|
| `SentinelNotificationListener` | `listeners` | `NotificationListenerService` | App-singleton (OS Service) | OS entry point; routes WhatsApp notifications to `IWhatsAppAgent` |
| `WhatsAppNotificationParser` | `agents.whatsapp` | Plain Kotlin class | Stateless, `@Inject` | Extracts raw fields from `StatusBarNotification` extras |
| `WhatsAppEventBuilder` | `agents.whatsapp` | Plain Kotlin class | Stateless, `@Inject` | Constructs and validates schema-compliant `WhatsAppCommunicationEvent` |
| `WhatsAppDeduplicationFilter` | `agents.whatsapp` | Plain Kotlin class | `@Singleton` | In-memory LRU deduplication cache (capacity 256, TTL 60 s) |
| `WhatsAppContentHeuristics` | `agents.whatsapp` | Plain Kotlin class | Stateless, `@Inject` | On-device keyword signal extraction |
| `AccessibilityIntegrationStub` | `agents.whatsapp` | Plain Kotlin class | `@Singleton` | No-op `IAccessibilityEventSource` for V1; replaced in V1.1 |
| `WhatsAppAgentCoordinator` | `agents.whatsapp` | Extends `BaseAgent` | `@Singleton` | Full pipeline orchestration and `ThreatEventBus` dispatch |
| `WhatsAppRawNotificationData` | `agents.whatsapp` (internal) | Kotlin `data class` | Value object | Intermediate, unvalidated extraction from notification extras |
| `ContentSignals` | `agents.whatsapp` (internal) | Kotlin `data class` | Value object | On-device heuristic signals from message body |
| `WhatsAppCommunicationEvent` | `domain.model` | Kotlin `data class` | Value object | Schema-compliant domain model; maps to `EVENT_SCHEMA.md` §6.3 |
| `WhatsAppAnalysisRequest` | `data.remote.dto.request` | Kotlin `data class` | Value object | JSON-serializable Retrofit DTO for `/v1/analyze/whatsapp` |
| `AnalyzeWhatsAppUseCase` | `domain.usecase` | Plain Kotlin class | `@Inject` | Single-responsibility suspend use case |

---

### 3.2 `WhatsAppRawNotificationData` — Field Definitions

Intermediate, unvalidated extraction result from `StatusBarNotification`. Internal to `agents.whatsapp`. Never transmitted or persisted.

| Field | Type | Notification Source | Derivation Rule |
|---|---|---|---|
| `notificationKey` | `String` | `StatusBarNotification.key` | Direct read; used as deduplication cache key component |
| `packageName` | `String` | `StatusBarNotification.packageName` | `com.whatsapp` or `com.whatsapp.w4b` |
| `senderDisplayName` | `String?` | `extras[EXTRA_TITLE]` | For 1:1 chats: sender name. For groups: group name may appear here |
| `messageText` | `String?` | `extras[EXTRA_BIG_TEXT]` preferred, `extras[EXTRA_TEXT]` fallback | `EXTRA_BIG_TEXT` is set when the notification is expanded; prefer it for full message body |
| `subText` | `String?` | `extras[EXTRA_SUB_TEXT]` | Sometimes contains group sender attribution in format "SenderName @ GroupName" |
| `conversationTitle` | `String?` | `extras[EXTRA_CONVERSATION_TITLE]` | Set by WhatsApp for group chats; non-null is a reliable group indicator |
| `isGroupChat` | `Boolean` | Derived | `true` if `conversationTitle != null` OR `subText` contains `"@"` |
| `groupName` | `String?` | Derived from `conversationTitle` | Present only when `isGroupChat = true` |
| `isForwarded` | `Boolean` | Derived from message text prefix | `true` if `messageText` starts with "Forwarded" (case-insensitive, trimmed) |
| `forwardChainLength` | `Int?` | Derived from forwarding label | `"Forwarded many times"` → `5`; `"Forwarded"` (single) → `1`; absent → `null` |
| `actionLabels` | `List<String>` | `notification.actions[n].title.toString()` | Labels of all action buttons on the notification |
| `hasCallButton` | `Boolean` | Derived from `actionLabels` | `true` if any label contains "call" or "video" (case-insensitive) |
| `capturedAtMs` | `Long` | `System.currentTimeMillis()` at receipt | Wall-clock UTC milliseconds at the moment of `onNotificationPosted()` |
| `notificationId` | `Int` | `StatusBarNotification.id` | Used in deduplication fingerprint as secondary discriminator |

**Forwarding Detection Rules:**

| Notification Text Prefix (trimmed, lowercase) | `isForwarded` | `forwardChainLength` |
|---|---|---|
| Starts with `"forwarded many times"` | `true` | `5` |
| Starts with `"forwarded"` | `true` | `1` |
| Any other prefix | `false` | `null` |

---

### 3.3 `WhatsAppCommunicationEvent` — Schema Field Mapping

Maps directly to `EVENT_SCHEMA.md` v1.0.0, §3–§6.3. This is the authoritative V1 field binding.

| Schema Block | Schema Field | Domain Field | V1 Value / Source |
|---|---|---|---|
| **Envelope** | `schema_version` | `schemaVersion` | `"1.0.0"` (constant) |
| | `event_id` | `eventId` | UUID v4 generated by `WhatsAppEventBuilder` |
| | `event_type` | `eventType` | `"sentinel.whatsapp.message.received"` |
| | `channel` | `channel` | `"WHATSAPP"` |
| | `processing_status` | `processingStatus` | `"CAPTURED"` at construction |
| | `captured_at` | `capturedAt` | ISO 8601 UTC from `capturedAtMs` |
| | `submitted_at` | `submittedAt` | ISO 8601 UTC stamped just before `ThreatApiService` call |
| | `device_id` | `deviceId` | SHA-256 of `(ANDROID_ID + DEVICE_ID_SALT)` |
| | `app_version` | `appVersion` | `BuildConfig.VERSION_NAME` |
| | `ttl_seconds` | `ttlSeconds` | `30` (default per schema) |
| **Source** | `source.identifier_hash` | `source.identifierHash` | SHA-256 of WhatsApp sender JID |
| | `source.identifier_type` | `source.identifierType` | `"WHATSAPP_JID"` |
| | `source.display_name` | `source.displayName` | Sender name from `senderDisplayName`; `null` in privacy mode |
| | `source.platform_handle` | `source.platformHandle` | Raw JID; `null` in privacy mode |
| | `source.is_known_contact` | `source.isKnownContact` | Android Contacts API lookup by display name |
| | `source.contact_type` | `source.contactType` | `"PERSONAL"` if known; `"UNKNOWN"` otherwise |
| **Content** | `content.body` | `content.body` | `messageText`; truncated to 50,000 chars if needed |
| | `content.body_truncated` | `content.bodyTruncated` | `true` if truncation was applied |
| | `content.character_count` | `content.characterCount` | `body.length` after truncation |
| | `content.contains_urls` | `content.containsUrls` | From `UrlPatternDetector` |
| | `content.url_count` | `content.urlCount` | Count from `UrlPatternDetector` |
| | `content.contains_attachments` | `content.containsAttachments` | `false` in V1 (Notification path cannot extract attachments) |
| | `content.has_urgency_language` | `content.hasUrgencyLanguage` | From `WhatsAppContentHeuristics` |
| | `content.has_authority_claim` | `content.hasAuthorityClaim` | From `WhatsAppContentHeuristics` |
| | `content.has_financial_mention` | `content.hasFinancialMention` | From `WhatsAppContentHeuristics` |
| | `content.has_otp_pattern` | `content.hasOtpPattern` | From `WhatsAppContentHeuristics` |
| | `content.media_type` | `content.mediaType` | `"TEXT"` in V1 (media type not determinable from notification) |
| **Channel Payload** | `channel_payload.chat_id_hash` | `channelPayload.chatIdHash` | SHA-256 of WhatsApp chat JID |
| | `channel_payload.sender_wa_id_hash` | `channelPayload.senderWaIdHash` | SHA-256 of sender WhatsApp JID |
| | `channel_payload.is_group_chat` | `channelPayload.isGroupChat` | From `WhatsAppRawNotificationData.isGroupChat` |
| | `channel_payload.group_name` | `channelPayload.groupName` | `groupName`; `null` in privacy mode |
| | `channel_payload.message_type` | `channelPayload.messageType` | `"TEXT"` in V1; `"UNKNOWN"` if body is empty |
| | `channel_payload.is_forwarded` | `channelPayload.isForwarded` | From forwarding label detection |
| | `channel_payload.forward_chain_length` | `channelPayload.forwardChainLength` | `1` or `5` per detection rule; `null` if not forwarded |
| | `channel_payload.is_broadcast` | `channelPayload.isBroadcast` | `false` in V1 (not reliably detectable) |
| | `channel_payload.capture_method` | `channelPayload.captureMethod` | `"NOTIFICATION_LISTENER"` |
| | `channel_payload.has_call_button` | `channelPayload.hasCallButton` | From `WhatsAppRawNotificationData.hasCallButton` |

---

### 3.4 `ContentSignals` — Field Definitions

| Field | Type | Populated By | Description |
|---|---|---|---|
| `hasUrgencyLanguage` | `Boolean` | `WhatsAppContentHeuristics` | Matched urgency keyword in message body |
| `hasAuthorityClaim` | `Boolean` | `WhatsAppContentHeuristics` | Matched authority entity name in message body |
| `hasFinancialMention` | `Boolean` | `WhatsAppContentHeuristics` | Matched currency or financial keyword in message body |
| `hasOtpPattern` | `Boolean` | `WhatsAppContentHeuristics` | Regex-detected OTP pattern (6-digit or 8-digit with label) |

---

## 4. Interfaces

### 4.1 `IWhatsAppNotificationParser`

**Package:** `com.sentinel.ai.agents.whatsapp`
**Purpose:** Abstracts extraction of raw notification data, enabling test doubles without Android instrumentation.

| Method | Signature | Contract |
|---|---|---|
| `isWhatsAppNotification` | `(sbn: StatusBarNotification): Boolean` | Fast-path check. Returns `true` iff `sbn.packageName` is in the WhatsApp package whitelist. Called before `parse()`. |
| `parse` | `(sbn: StatusBarNotification): WhatsAppRawNotificationData?` | Returns `null` if the notification does not contain a parseable WhatsApp message (e.g., badge-only updates, call notifications without text). Never throws. |

---

### 4.2 `IWhatsAppEventBuilder`

**Package:** `com.sentinel.ai.agents.whatsapp`
**Purpose:** Abstracts event construction, enabling injection of a controlled clock, device ID provider, and privacy mode source in tests.

| Method | Signature | Contract |
|---|---|---|
| `build` | `(raw: WhatsAppRawNotificationData, privacyMode: Boolean): WhatsAppCommunicationEvent` | Produces a complete, schema-valid domain event. Privacy mode is applied unconditionally inside this method regardless of field nullability at the call site. |
| `validate` | `(event: WhatsAppCommunicationEvent): ValidationResult` | Checks all required schema fields. Returns `ValidationResult.Valid` or `ValidationResult.Invalid(errors: List<String>)`. Called after `build()` by the coordinator before dispatching. |

**`ValidationResult` sealed class:**

```
ValidationResult
├── Valid
└── Invalid(errors: List<String>)   // Human-readable list of failed checks
```

---

### 4.3 `IDeduplicationFilter`

**Package:** `com.sentinel.ai.agents.whatsapp`
**Purpose:** Abstracts the deduplication cache, allowing test implementations to be pre-seeded with specific fingerprints.

| Method | Signature | Contract |
|---|---|---|
| `computeFingerprint` | `(sbn: StatusBarNotification): String` | Returns a composite fingerprint string: `CRC32(notificationKey + messageText)`. Stable for identical notification content. |
| `isDuplicate` | `(fingerprint: String): Boolean` | Returns `true` if this fingerprint was already recorded within the TTL window. Read-only — does not record. |
| `record` | `(fingerprint: String): Unit` | Registers a fingerprint as processed. Called immediately after a non-duplicate is accepted into the pipeline. |

---

### 4.4 `IAccessibilityEventSource`

**Package:** `com.sentinel.ai.agents.whatsapp`
**Purpose:** Contract for the future Accessibility-sourced event path. `AccessibilityIntegrationStub` implements this in V1 with empty output. V1.1 replaces the stub with a live `SentinelAccessibilityService` adapter without touching any downstream consumer.

| Method | Signature | V1 Stub Behaviour | V1.1 Live Behaviour |
|---|---|---|---|
| `accessibilityEvents` | `(): Flow<WhatsAppRawAccessibilityData>` | Returns `emptyFlow()` | Returns a hot `SharedFlow` of events sourced from `AccessibilityEvent` callbacks |
| `isSupported` | `(): Boolean` | Returns `false` | Returns `true` when `SentinelAccessibilityService` is active and WhatsApp is in the foreground |

**Note:** `WhatsAppRawAccessibilityData` is a reserved type for V1.1. In V1, `AccessibilityIntegrationStub` is declared but `WhatsAppRawAccessibilityData` remains a forward-declared placeholder type with no fields. The `WhatsAppAgentCoordinator` merges both flows using `Flow.merge()` — an empty flow has zero impact on V1 behaviour.

---

### 4.5 `IWhatsAppAgent`

**Package:** `com.sentinel.ai.agents.whatsapp`
**Purpose:** The public contract of `WhatsAppAgentCoordinator` as consumed by `SentinelNotificationListener`. Decouples the listener from the coordinator implementation.

| Method | Signature | Contract |
|---|---|---|
| `onWhatsAppNotification` | `(sbn: StatusBarNotification): Unit` | Non-blocking entry point. All processing dispatched to `DispatcherProvider.io` via a `coroutineScope`. Returns immediately. |
| `agentStatus` | `(): StateFlow<AgentStatus>` | Observable lifecycle state of the agent coordinator. |

**`AgentStatus` enum:** `IDLE` | `PROCESSING` | `ERROR`

---

## 5. Data Flow

### 5.1 End-to-End Data Flow — V1 Notification Path

```
┌────────────────────────────────────────────────────────────────────────────────┐
│  ANDROID OS                                                                    │
│                                                                                │
│  WhatsApp App                                                                  │
│      │ notification.post()                                                     │
│      ▼                                                                         │
│  NotificationManager → delivers StatusBarNotification to registered            │
│                         NotificationListenerService components                 │
└───────────────────────────────┬────────────────────────────────────────────────┘
                                │ onNotificationPosted(sbn)
                                ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│  LISTENER LAYER  com.sentinel.ai.listeners                                     │
│                                                                                │
│  SentinelNotificationListener                                                  │
│      │                                                                         │
│      ├─ [Guard Active?]  SentinelGuardService.isActive()                      │
│      │      └─ No  ──► DROP                                                   │
│      │                                                                         │
│      ├─ [Package Filter]  sbn.packageName ∈ WhatsApp whitelist?               │
│      │      └─ No  ──► route to other agent (Telegram, etc.)                  │
│      │                                                                         │
│      └─ Yes ──► IWhatsAppAgent.onWhatsAppNotification(sbn)                    │
└───────────────────────────────┬────────────────────────────────────────────────┘
                                │
                                ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│  AGENT LAYER  com.sentinel.ai.agents.whatsapp                                  │
│                                                                                │
│  WhatsAppAgentCoordinator  [DispatcherProvider.io]                             │
│      │                                                                         │
│      ├─ Step 1:  IDeduplicationFilter.computeFingerprint(sbn)                 │
│      │           IDeduplicationFilter.isDuplicate(fingerprint)?               │
│      │               └─ Yes ──► DROP silently                                 │
│      │                                                                         │
│      ├─ Step 2:  IWhatsAppNotificationParser.isWhatsAppNotification(sbn)?     │
│      │               └─ No  ──► DROP (not a message notification)             │
│      │                                                                         │
│      ├─ Step 3:  IWhatsAppNotificationParser.parse(sbn)                       │
│      │               └─ null ──► DROP (parse failure, log WARN)               │
│      │               └─ WhatsAppRawNotificationData ──►                       │
│      │                                                                         │
│      ├─ Step 4:  IDeduplicationFilter.record(fingerprint)                     │
│      │                                                                         │
│      ├─ Step 5:  UserPreferences.privacyMode (DataStore read)                 │
│      │                                                                         │
│      ├─ Step 6:  IWhatsAppEventBuilder.build(raw, privacyMode)                │
│      │               └─ WhatsAppCommunicationEvent                            │
│      │                                                                         │
│      ├─ Step 7:  IWhatsAppEventBuilder.validate(event)                        │
│      │               └─ Invalid ──► DROP (log ERROR with field list)          │
│      │                                                                         │
│      └─ Step 8:  AnalyzeWhatsAppUseCase.invoke(event)  [suspend]              │
│                      │                                                         │
│                      └─ dispatched on DispatcherProvider.io                   │
└───────────────────────────────┬────────────────────────────────────────────────┘
                                │
                                ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│  DOMAIN / DATA LAYER  com.sentinel.ai.domain + com.sentinel.ai.data            │
│                                                                                │
│  AnalyzeWhatsAppUseCase                                                        │
│      │ IThreatRepository.analyzeWhatsApp(WhatsAppAnalysisRequest)              │
│      │                                                                         │
│      ├─ [Network available?]                                                   │
│      │      └─ No ──► ScanHistoryDao.insert(status = QUEUED)                  │
│      │                WorkManager enqueues WhatsAppRetryWorker                 │
│      │                                                                         │
│      ├─ ThreatApiService.POST /v1/analyze/whatsapp (JSON body)                │
│      │                                                                         │
│      ├─ HTTP 200 ──► parse ThreatResponse ──► ScanResult                     │
│      │               ScanHistoryDao.update(status = COMPLETED)                │
│      │                                                                         │
│      └─ HTTP error / timeout ──► ScanHistoryDao.update(status = FAILED)       │
│                                  emit failure signal                           │
└───────────────────────────────┬────────────────────────────────────────────────┘
                                │ Result<ScanResult>
                                ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│  EVENT BUS + ALERT LAYER                                                       │
│                                                                                │
│  WhatsAppAgentCoordinator receives Result<ScanResult>                          │
│      │                                                                         │
│      ├─ Success ──► ThreatEventBus.emit(WhatsAppThreatDetected(scanResult))   │
│      │                  │                                                      │
│      │                  ├─ SentinelGuardService ──► persist Alert to Room      │
│      │                  ├─ OverlayAlertService  ──► show overlay (RED/CRITICAL)│
│      │                  └─ AlertViewModel       ──► update UI if foreground    │
│      │                                                                         │
│      └─ Failure ──► ThreatEventBus.emit(AgentError(channel = WHATSAPP))       │
└────────────────────────────────────────────────────────────────────────────────┘
```

---

### 5.2 Privacy Data Flow

```
WhatsAppRawNotificationData
    │
    │  WhatsAppEventBuilder.build(raw, privacyMode = true)
    ▼
    senderDisplayName  ─────────────────────────────────────── STRIPPED (null)
    senderDisplayName  ─► SHA-256("wa_jid:" + rawJid) ──────── source.identifier_hash  ✔
    rawJid (platform_handle)  ──────────────────────────────── STRIPPED (null)
    groupName  ─────────────────────────────────────────────── STRIPPED (null)
    chatJid  ──► SHA-256("wa_chat:" + chatJid) ─────────────── channel_payload.chat_id_hash  ✔
    │
    ▼
WhatsAppCommunicationEvent  (zero PII transmitted)
    │
    ▼
WhatsAppAnalysisRequest DTO  (null PII fields omitted from JSON by Gson)
    │
    ▼
POST /v1/analyze/whatsapp  ──► Backend receives hashes only
```

---

### 5.3 Future Accessibility Integration Path (V1.1 — Documented for Design Continuity)

When V1.1 implements the Accessibility path, the coordinator's coroutine scope will merge both event sources transparently. No structural change to the coordinator is required beyond replacing `AccessibilityIntegrationStub` with a live implementation.

```
SentinelAccessibilityService (V1.1)
    │  AccessibilityEvent (TYPE_WINDOW_CONTENT_CHANGED)
    ▼
IAccessibilityEventSource.accessibilityEvents()  [live in V1.1]
    │  Flow<WhatsAppRawAccessibilityData>
    │
    │   merge with ──►
    │
SentinelNotificationListener path
    │  Flow<StatusBarNotification>
    │
    ▼
WhatsAppAgentCoordinator  [Flow.merge() combining both sources]
    │  captureMethod = "ACCESSIBILITY_SERVICE" for accessibility events
    ▼
[Same build → validate → dispatch pipeline as V1]
```

---

## 6. Sequence Diagram

```
WhatsApp    Android OS         SentinelNotification   WhatsAppAgent        AnalyzeWhatsApp   ThreatRepository    ThreatEventBus  SentinelGuardService
  App       NotifManager       Listener               Coordinator          UseCase            (Room + API)
   │             │                  │                      │                    │                   │                  │               │
   │  post()     │                  │                      │                    │                   │                  │               │
   │────────────►│                  │                      │                    │                   │                  │               │
   │             │                  │                      │                    │                   │                  │               │
   │             │ onNotification   │                      │                    │                   │                  │               │
   │             │ Posted(sbn)      │                      │                    │                   │                  │               │
   │             │─────────────────►│                      │                    │                   │                  │               │
   │             │                  │                      │                    │                   │                  │               │
   │             │                  │ [Guard active?]      │                    │                   │                  │               │
   │             │                  │ [Package filter?]    │                    │                   │                  │               │
   │             │                  │                      │                    │                   │                  │               │
   │             │                  │ onWhatsApp           │                    │                   │                  │               │
   │             │                  │ Notification(sbn)    │                    │                   │                  │               │
   │             │                  │─────────────────────►│                    │                   │                  │               │
   │             │                  │                      │                    │                   │                  │               │
   │             │                  │                      │ computeFingerprint │                   │                  │               │
   │             │                  │                      │ isDuplicate?       │                   │                  │               │
   │             │                  │                      │ ──► No             │                   │                  │               │
   │             │                  │                      │                    │                   │                  │               │
   │             │                  │                      │ isWhatsApp         │                   │                  │               │
   │             │                  │                      │ Notification?      │                   │                  │               │
   │             │                  │                      │ ──► Yes            │                   │                  │               │
   │             │                  │                      │                    │                   │                  │               │
   │             │                  │                      │ parse(sbn)         │                   │                  │               │
   │             │                  │                      │ ──► RawData        │                   │                  │               │
   │             │                  │                      │                    │                   │                  │               │
   │             │                  │                      │ record(fingerprint)│                   │                  │               │
   │             │                  │                      │                    │                   │                  │               │
   │             │                  │                      │ read privacyMode   │                   │                  │               │
   │             │                  │                      │ (DataStore)        │                   │                  │               │
   │             │                  │                      │                    │                   │                  │               │
   │             │                  │                      │ build(raw, mode)   │                   │                  │               │
   │             │                  │                      │ ──► CommEvent      │                   │                  │               │
   │             │                  │                      │                    │                   │                  │               │
   │             │                  │                      │ validate(event)    │                   │                  │               │
   │             │                  │                      │ ──► Valid          │                   │                  │               │
   │             │                  │                      │                    │                   │                  │               │
   │             │                  │                      │   invoke(event)    │                   │                  │               │
   │             │                  │                      │───────────────────►│                   │                  │               │
   │             │                  │                      │                    │                   │                  │               │
   │             │                  │                      │                    │ analyzeWhatsApp() │                  │               │
   │             │                  │                      │                    │──────────────────►│                  │               │
   │             │                  │                      │                    │                   │ insert QUEUED    │               │
   │             │                  │                      │                    │                   │ POST /v1/analyze │               │
   │             │                  │                      │                    │                   │ update COMPLETED │               │
   │             │                  │                      │                    │                   │──► ScanResult    │               │
   │             │                  │                      │                    │◄──────────────────│                  │               │
   │             │                  │                      │◄───────────────────│                   │                  │               │
   │             │                  │                      │                    │                   │                  │               │
   │             │                  │                      │ emit(WhatsApp       │                  │                  │               │
   │             │                  │                      │  ThreatDetected)   │                   │                  │               │
   │             │                  │                      │───────────────────────────────────────────────────────►  │               │
   │             │                  │                      │                    │                   │                  │               │
   │             │                  │                      │                    │                   │                  │ [subscribers] │
   │             │                  │                      │                    │                   │                  │──────────────►│
   │             │                  │                      │                    │                   │                  │               │ persist Alert
   │             │                  │                      │                    │                   │                  │               │ show overlay?
```

---

### 6.1 Latency Budget

End-to-end target is the 3-second SLA defined in `project_context.md` §9.

| Step | Owner | P95 Target |
|---|---|---|
| OS notification delivery to `onNotificationPosted()` | Android OS | < 50 ms |
| Guard active check + package filter | `SentinelNotificationListener` | < 1 ms |
| Deduplication fingerprint + cache lookup | `WhatsAppDeduplicationFilter` | < 1 ms |
| Notification parsing (`extras` bundle read) | `WhatsAppNotificationParser` | < 2 ms |
| Event building: hashing + heuristics + URL scan | `WhatsAppEventBuilder` + `WhatsAppContentHeuristics` | < 5 ms |
| Schema validation | `WhatsAppEventBuilder.validate()` | < 1 ms |
| API call (network + backend processing) | `ThreatRepository` → FastAPI backend | < 2,500 ms |
| `ThreatEventBus` emit + overlay render | `ThreatEventBus` → `OverlayAlertService` | < 200 ms |
| **Total P95** | | **< 2,760 ms** |

---

## 7. Error Handling

### 7.1 Error Category Register

| Error ID | Category | Trigger Condition | Handling Action | User Impact |
|---|---|---|---|---|
| `WA-E01` | `NOTIFICATION_NULL_EXTRAS` | `sbn.notification.extras == null` | Timber.w(); DROP event | None |
| `WA-E02` | `NOTIFICATION_EMPTY_BODY` | `EXTRA_TEXT` and `EXTRA_BIG_TEXT` both null or blank after trim | Timber.d(); DROP event | None |
| `WA-E03` | `PARSE_EXCEPTION` | Unexpected `extras` structure throws unchecked exception | Timber.e(throwable, notifKey); DROP event | None |
| `WA-E04` | `DEDUP_HIT` | `isDuplicate() == true` | Timber.d(); DROP silently | None |
| `WA-E05` | `DEDUP_EVICTION` | LRU evicts early under heavy notification load | Proceed as non-duplicate; potential benign re-scan | Duplicate backend call (rare, benign) |
| `WA-E06` | `BUILD_VALIDATION_FAILED` | `validate()` returns `Invalid` | Timber.e(errors); DROP event | None |
| `WA-E07` | `PRIVACY_DATASTORE_FAILURE` | `UserPreferences` DataStore throws | Default to `privacyMode = true` (safe fallback); Timber.e() | None — privacy preserved |
| `WA-E08` | `NETWORK_UNAVAILABLE` | No internet at submission time | Insert `QUEUED` to Room; schedule `WorkManager` retry | Delayed analysis; no data loss |
| `WA-E09` | `API_4XX_ERROR` | Backend returns HTTP 4xx | Timber.e(statusCode, body); mark `FAILED` in Room; **do NOT retry** | Event logged as failed in History |
| `WA-E10` | `API_5XX_ERROR` | Backend returns HTTP 5xx | Schedule `WorkManager` retry (exponential backoff) | Delayed analysis |
| `WA-E11` | `API_TIMEOUT` | OkHttp read timeout (30 s) exceeded | Retry once in-coroutine; then `WorkManager` retry | Delayed analysis |
| `WA-E12` | `SCHEMA_VERSION_TOO_NEW` | Backend returns HTTP 422 `SCHEMA_VERSION_TOO_NEW` | Timber.wtf(); disable WhatsApp agent; notify user via dashboard `AgentDegradedBanner` | Agent suspended; user notified |
| `WA-E13` | `THREAT_BUS_OVERFLOW` | `SharedFlow` buffer (64) overflows | `DROP_OLDEST` per ADR-003; Timber.w() | Oldest threat event result discarded (rare) |

---

### 7.2 Retry Policy

```
Submission attempt fails (WA-E08, WA-E10, WA-E11)
    │
    ├─ In-coroutine immediate retry (once)
    │       └─ Success ──► COMPLETED
    │       └─ Fail    ──►
    │
    └─ WorkManager: WhatsAppRetryWorker
            │
            ├─ Retry 1: backoff =   5 seconds  ──► COMPLETED | Fail
            ├─ Retry 2: backoff =  30 seconds  ──► COMPLETED | Fail
            ├─ Retry 3: backoff = 120 seconds  ──► COMPLETED | Fail
            └─ Exhausted ──► ScanHistoryEntity.status = FAILED
                             (visible in History screen with error reason)

Conditions that prevent retry:
    - HTTP 4xx   ──► client is at fault; fix required before retry
    - WA-E12     ──► schema mismatch; agent disabled until app update
```

---

### 7.3 Privacy Safety Net

Privacy mode enforcement operates at three independent layers to prevent any single point of failure from leaking PII:

**Layer 1 — Builder Enforcement (Primary)**
`IWhatsAppEventBuilder.build()` unconditionally calls the privacy filter as a mandatory step, not an optional conditional. PII fields are set to `null` before any other operation on the event object.

**Layer 2 — DTO Null Exclusion (Secondary)**
`WhatsAppAnalysisRequest` has PII fields typed as `String?`. Gson's default behaviour omits null fields from serialized JSON. A PII field nulled at Layer 1 cannot reach the wire even if the coordinator bypasses validation.

**Layer 3 — `PrivacySafetyNetInterceptor` (Tertiary Audit)**
An OkHttp `Interceptor` inspects the outgoing request body for phone number patterns (regex: `\+91\d{10}`) and email patterns before transmission. On detection, logs a `CRITICAL` warning with a sanitized stacktrace. Does not block the request but produces an audit trail for post-release analysis.

---

### 7.4 Deduplication Edge Cases

| Edge Case | Handling |
|---|---|
| WhatsApp updates notification with message reaction emoji | `EXTRA_TEXT` changes → new CRC32 → different fingerprint → treated as a new event (acceptable; reaction update may carry new text) |
| Two rapid messages from the same sender | Different `EXTRA_TEXT` values → different fingerprints → two independent events (correct) |
| In-memory cache cleared on device reboot | First message after reboot may be re-submitted if already processed in the prior session. TTL is 60 s, so only messages received within 60 s before reboot are affected (acceptable, rare, benign re-scan) |
| Both WhatsApp and WhatsApp Business installed | `packageName` differs → independent fingerprint namespaces → no cross-app deduplication conflicts |
| WhatsApp notification updated with "Delivered" tick state change | Notification key unchanged but no text change → same CRC32 fingerprint → correctly identified as duplicate |

---

## 8. Testing Strategy

### 8.1 Test Pyramid

```
                      ┌───────────────────────────┐
                      │  Manual End-to-End Demo   │  < 5% effort
                      │  Live WhatsApp scam        │
                      │  scenario walkthrough       │
                      └───────────────────────────┘
                   ┌─────────────────────────────────┐
                   │  Instrumentation Tests           │  ~20% effort
                   │  NotificationListenerService     │
                   │  lifecycle; Room persistence;    │
                   │  WorkManager retry; Hilt DI      │
                   └─────────────────────────────────┘
              ┌────────────────────────────────────────────┐
              │  Unit Tests                                │  ~75% effort
              │  Parser · EventBuilder · Heuristics        │
              │  DeduplicationFilter · Coordinator (fakes) │
              │  Privacy mode · Schema validation          │
              └────────────────────────────────────────────┘
```

---

### 8.2 Unit Tests

**Location:** `test/java/com/sentinel/ai/agents/whatsapp/`
**Framework:** JUnit 4 + MockK + `kotlinx-coroutines-test` (`runTest`, `TestDispatcher`)

#### `WhatsAppNotificationParserTest`

Uses a pure-Kotlin `FakeStatusBarNotification` builder (wraps `Bundle` — no instrumentation required).

| Test ID | Input Scenario | Expected Result |
|---|---|---|
| `WAP-U01` | `EXTRA_TITLE = "John"`, `EXTRA_TEXT = "Hello"` | `isGroupChat = false`, `messageText = "Hello"` |
| `WAP-U02` | `EXTRA_CONVERSATION_TITLE = "Family"`, `EXTRA_TEXT = "Alice: Hi"` | `isGroupChat = true`, `groupName = "Family"` |
| `WAP-U03` | `EXTRA_TEXT = "Forwarded\nHello"` | `isForwarded = true`, `forwardChainLength = 1` |
| `WAP-U04` | `EXTRA_TEXT = "Forwarded many times\nClick: http://…"` | `isForwarded = true`, `forwardChainLength = 5` |
| `WAP-U05` | `extras = null` | Returns `null` |
| `WAP-U06` | `EXTRA_TEXT = "short"`, `EXTRA_BIG_TEXT = "full long message"` | `messageText = "full long message"` |
| `WAP-U07` | Actions: `["Reply", "Video Call"]` | `hasCallButton = true` |
| `WAP-U08` | Actions: `["Reply", "Mark as Read"]` | `hasCallButton = false` |
| `WAP-U09` | `EXTRA_SUB_TEXT = "John @ Group Name"` | `isGroupChat = true` |
| `WAP-U10` | `packageName = "com.whatsapp.w4b"` | `isWhatsAppNotification() = true` |
| `WAP-U11` | `packageName = "com.example.fake"` | `isWhatsAppNotification() = false` |

---

#### `WhatsAppEventBuilderTest`

Uses MockK stubs for `UrlPatternDetector`, `WhatsAppContentHeuristics`, a controlled `Clock`, and a fixed `deviceId` provider.

| Test ID | Scenario | Assertion |
|---|---|---|
| `WEB-U01` | `privacyMode = true` | `event.source.displayName == null` |
| `WEB-U02` | `privacyMode = true` | `event.source.platformHandle == null` |
| `WEB-U03` | `privacyMode = true` | `event.source.identifierHash.length == 64` (valid SHA-256 hex) |
| `WEB-U04` | `privacyMode = true` | `event.channelPayload.groupName == null` |
| `WEB-U05` | `privacyMode = false` | `event.source.displayName == "John Doe"` |
| `WEB-U06` | `UrlPatternDetector` returns 2 URLs | `event.content.containsUrls == true && event.content.urlCount == 2` |
| `WEB-U07` | Any valid input | `event.channelPayload.captureMethod == "NOTIFICATION_LISTENER"` |
| `WEB-U08` | Any valid input | `event.eventType == "sentinel.whatsapp.message.received"` |
| `WEB-U09` | Any valid input | `event.channel == "WHATSAPP"` |
| `WEB-U10` | Any valid input | `event.processingStatus == "CAPTURED"` |
| `WEB-U11` | Any valid input | `event.schemaVersion == "1.0.0"` |
| `WEB-U12` | Message body of 50,001 characters | `event.content.bodyTruncated == true`, `body.length == 50000` |
| `WEB-U13` | Required field missing (empty JID) | `validate()` returns `ValidationResult.Invalid` |
| `WEB-U14` | Fully populated valid event | `validate()` returns `ValidationResult.Valid` |

---

#### `WhatsAppContentHeuristicsTest`

| Test ID | Input Text | Expected Signals |
|---|---|---|
| `WCH-U01` | `"You will be arrested immediately"` | `hasUrgencyLanguage = true` |
| `WCH-U02` | `"CBI has issued a warrant against you"` | `hasAuthorityClaim = true` |
| `WCH-U03` | `"Enforcement Directorate official notice"` | `hasAuthorityClaim = true` |
| `WCH-U04` | `"Transfer ₹50,000 to avoid arrest"` | `hasFinancialMention = true`, `hasUrgencyLanguage = true` |
| `WCH-U05` | `"Your OTP is 482910. Do not share."` | `hasOtpPattern = true` |
| `WCH-U06` | `"Mom: Are you coming for dinner tonight?"` | All signals `false` |
| `WCH-U07` | `"Turant action lo nahi toh account band hoga"` | `hasUrgencyLanguage = true` |
| `WCH-U08` | `"RBI ne aapka account freeze kiya hai"` | `hasAuthorityClaim = true` |
| `WCH-U09` | Empty string `""` | All signals `false`; no exception |
| `WCH-U10` | `"Congratulations! 50 lakh prize claim karo"` | `hasFinancialMention = true` |

---

#### `WhatsAppDeduplicationFilterTest`

Uses `advanceTimeBy()` from `kotlinx-coroutines-test` to simulate TTL expiry.

| Test ID | Scenario | Assertion |
|---|---|---|
| `WDF-U01` | First occurrence of fingerprint A | `isDuplicate(A) == false` |
| `WDF-U02` | After `record(A)`, call `isDuplicate(A)` | `isDuplicate(A) == true` |
| `WDF-U03` | After `record(A)`, call `isDuplicate(B)` | `isDuplicate(B) == false` |
| `WDF-U04` | After `record(A)`, advance time by 61 s | `isDuplicate(A) == false` (expired) |
| `WDF-U05` | Insert 257 unique fingerprints (LRU at capacity 256) | `isDuplicate(fingerprint_1) == false` (evicted) |
| `WDF-U06` | Fingerprint computed from identical `sbn` twice | Both calls return identical string |

---

#### `WhatsAppAgentCoordinatorTest`

Uses MockK for all `IWhatsApp*` interface dependencies, `runTest` for coroutine testing, `TestDispatcher` for `DispatcherProvider`.

| Test ID | Setup | Assertion |
|---|---|---|
| `WAC-U01` | All steps succeed; UseCase returns `Result.success(scanResult)` | `ThreatEventBus.emit()` called once with `WhatsAppThreatDetected` |
| `WAC-U02` | `isDuplicate()` returns `true` | `parse()` is never called |
| `WAC-U03` | `parse()` returns `null` | `build()` is never called; no bus emission |
| `WAC-U04` | `validate()` returns `Invalid` | `useCase.invoke()` is never called |
| `WAC-U05` | UseCase returns `Result.failure(IOException)` | `ThreatEventBus.emit(AgentError(channel=WHATSAPP))` called |
| `WAC-U06` | Network unavailable at repository level | Room `ScanHistoryDao.insert(QUEUED)` called; WorkManager task enqueued |
| `WAC-U07` | Backend returns HTTP 422 `SCHEMA_VERSION_TOO_NEW` | Agent emits `AgentDegraded` event; `agentStatus == ERROR` |
| `WAC-U08` | Two simultaneous notifications | Both processed independently on IO dispatcher; no race condition on dedup cache |

---

### 8.3 Instrumentation Tests

**Location:** `androidTest/java/com/sentinel/ai/services/`
**Runner:** `HiltAndroidTestRunner` (as per `ANDROID_ARCHITECTURE.md` §7.10)

| Test ID | Description | Pass Criteria |
|---|---|---|
| `WAI-I01` | Post a fake `StatusBarNotification` with `packageName = com.whatsapp`; confirm agent invocation | `WhatsAppAgentCoordinator.onWhatsAppNotification()` called within 500 ms |
| `WAI-I02` | Disable guard service; post WhatsApp notification | No `ThreatEventBus` emission |
| `WAI-I03` | Full happy-path with in-memory Room and `MockWebServer` returning HTTP 200 | `ScanHistoryEntity.status == COMPLETED` within 3 s |
| `WAI-I04` | Simulate `NETWORK_UNAVAILABLE`; post notification | `ScanHistoryEntity.status == QUEUED` persisted to Room |
| `WAI-I05` | Restore network after `QUEUED` state; confirm WorkManager runs `WhatsAppRetryWorker` | `ScanHistoryEntity.status == COMPLETED` after WorkManager execution |
| `WAI-I06` | Revoke `BIND_NOTIFICATION_LISTENER_SERVICE` permission | `SentinelNotificationListener.onNotificationPosted()` never invoked |
| `WAI-I07` | Post same WhatsApp notification twice within 60 s | Only one `ScanHistoryEntity` row inserted (dedup working) |

---

### 8.4 Fraud Scenario Regression Fixtures

A curated set of scam scenario test fixtures for CI regression and live demo validation. Each fixture represents a real-world WhatsApp fraud pattern sourced from Indian cybercrime reports.

| Fixture ID | Notification Text | Expected Signals | Expected Backend Route |
|---|---|---|---|
| `SCAM-WA-001` | `"CBI officer here. You are under digital arrest. Call immediately."` | `hasAuthorityClaim=true`, `hasUrgencyLanguage=true` | Digital Arrest Detector |
| `SCAM-WA-002` | `"Congratulations! You won ₹5,00,000 lottery. Click: http://prize-win.xyz"` | `hasFinancialMention=true`, `containsUrls=true` | LinkAgent + Scam Classifier |
| `SCAM-WA-003` | `"Forwarded many times\nKYC required: http://sbi-kyc.net/update"` | `isForwarded=true`, `forwardChainLength=5`, `containsUrls=true` | LinkAgent + Phishing Detector |
| `SCAM-WA-004` | `"Your OTP is 847291. Do not share with anyone."` | `hasOtpPattern=true` | OTP Theft Classifier |
| `SCAM-WA-005` | `"ED notice for money laundering. Turant ₹2 lakh deposit karo."` | `hasAuthorityClaim=true`, `hasFinancialMention=true`, `hasUrgencyLanguage=true` | Digital Arrest Detector + Financial Fraud |
| `SCAM-WA-006` | `"TRAI: Your number will be disconnected in 2 hours. Dial 1800..."` | `hasAuthorityClaim=true`, `hasUrgencyLanguage=true` | Authority Impersonation Classifier |
| `SCAM-WA-007` | `"Mom: Are you coming for dinner tonight?"` | All signals `false` | Expected risk: GREEN (negative case) |

---

### 8.5 Test Tooling Reference

| Tool | Version | Role in WhatsApp Agent Tests |
|---|---|---|
| `junit:junit` | 4.13.x | Unit test runner |
| `io.mockk:mockk` | 1.13.x | Mocking all `IWhatsApp*` interfaces in coordinator tests |
| `kotlinx-coroutines-test` | 1.8.x | `runTest`, `advanceTimeBy()` for TTL tests, `TestCoroutineDispatcher` |
| `com.google.dagger:hilt-android-testing` | 2.51.x | `@HiltAndroidTest` for instrumentation tests |
| `androidx.room:room-testing` | 2.6.x | In-memory `SentinelDatabase` for persistence tests |
| `androidx.work:work-testing` | 2.9.x | `TestListenableWorkerBuilder` for `WhatsAppRetryWorker` tests |
| `okhttp3:mockwebserver` | 4.12.x | Simulating backend HTTP 200, 4xx, 5xx, and timeout responses |

---

## Appendix A — WhatsApp Package Whitelist

| Package Name | App Variant |
|---|---|
| `com.whatsapp` | WhatsApp (standard consumer app) |
| `com.whatsapp.w4b` | WhatsApp Business |

Any `StatusBarNotification` bearing a package name not in this whitelist MUST NOT be processed by the WhatsApp Agent, even if the notification content references WhatsApp. A third-party app impersonating WhatsApp via package name spoofing is not possible at the OS level.

---

## Appendix B — Manifest Additions

No new permissions are required for V1 beyond those already declared in `ANDROID_ARCHITECTURE.md` §7.12. The `BIND_NOTIFICATION_LISTENER_SERVICE` permission is granted through Android Settings → Special App Access → Notification Access, not via a runtime permission dialog.

The `res/xml/notification_listener_config.xml` file (already referenced in the architecture) should include the `<service>` filter to accept events from all packages. The WhatsApp package filter is applied in-process by `SentinelNotificationListener`, not at the OS config level, to keep the XML config generic and avoid requiring manifest changes when adding new channels.

---

## Appendix C — Schema Compliance Checklist

`IWhatsAppEventBuilder.validate()` enforces the following checks before any event is dispatched. Failure on any check produces a `ValidationResult.Invalid` with the failing field name in the error list.

| Check ID | Field | Validation Rule |
|---|---|---|
| `V01` | `eventId` | Valid UUID v4 format |
| `V02` | `eventType` | Must equal `"sentinel.whatsapp.message.received"` |
| `V03` | `channel` | Must equal `"WHATSAPP"` |
| `V04` | `processingStatus` | Must equal `"CAPTURED"` at construction |
| `V05` | `capturedAt` | Valid ISO 8601 UTC string; not in the future |
| `V06` | `submittedAt` | Valid ISO 8601 UTC string; `>= capturedAt` |
| `V07` | `deviceId` | Exactly 64 lowercase hex characters (SHA-256 output) |
| `V08` | `schemaVersion` | Must equal `"1.0.0"` |
| `V09` | `source.identifierHash` | Exactly 64 lowercase hex characters |
| `V10` | `source.identifierType` | Must equal `"WHATSAPP_JID"` |
| `V11` | `source.isKnownContact` | Not null |
| `V12` | `content.body` | Not null; `length <= 50,000` |
| `V13` | `content.characterCount` | Equals `content.body.length` |
| `V14` | `content.containsUrls` | Not null |
| `V15` | `content.containsAttachments` | Not null; must be `false` in V1 |
| `V16` | `channelPayload.chatIdHash` | Exactly 64 lowercase hex characters |
| `V17` | `channelPayload.senderWaIdHash` | Exactly 64 lowercase hex characters |
| `V18` | `channelPayload.captureMethod` | Must equal `"NOTIFICATION_LISTENER"` in V1 |
| `V19` | Privacy consistency | If `privacyMode = true`: `displayName`, `platformHandle`, `groupName` must all be null |

---

## Appendix D — `ThreatEventBus` Extension for WhatsApp

`WhatsAppAgentCoordinator` adds one new sealed subtype to `ThreatEvent` (defined in `ANDROID_ARCHITECTURE.md` §4.2):

```
ThreatEvent
├── SmsThreatDetected(val scanResult: ScanResult)      [existing]
├── CallThreatDetected(val scanResult: ScanResult)     [existing]
├── LinkThreatDetected(val scanResult: ScanResult)     [existing]
├── FileThreatDetected(val scanResult: ScanResult)     [existing]
├── CriticalThreatAlert(val threat: Threat)            [existing]
├── GuardActivated                                     [existing]
├── GuardDeactivated                                   [existing]
├── WhatsAppThreatDetected(val scanResult: ScanResult) [NEW — V1]
└── AgentError(val channel: String, val error: Throwable) [NEW — V1, shared with future agents]
```

`SentinelGuardService`, `OverlayAlertService`, and `AlertViewModel` subscribe to `ThreatEventBus.events` and handle `WhatsAppThreatDetected` identically to `SmsThreatDetected` for alert persistence and overlay triggering.

---

*"Think Before You Click. Sentinel Thinks Before You Do."*

---
**Document Version:** 1.0.0
**Status:** Draft — Hackathon MVP
**Agent:** WhatsApp Agent V1
**Schema Compatibility:** `EVENT_SCHEMA.md` v1.0.0
**Architecture Compatibility:** `ANDROID_ARCHITECTURE.md` v1.0.0
**Last Updated:** 2026-06-23
