# EVENT_SCHEMA.md
# Sentinel AI — Universal Event Schema

**Version:** 1.0.0
**Author Role:** Distributed Systems Architect
**Hackathon:** ET AI Hackathon 2026 — Problem Statement 6
**Classification:** Shared Contract — Principal Architect Review Required on Every Change
**Applies To:** `shared/schemas/` — Android ↔ Backend ↔ Agent ↔ Database boundary
**Last Updated:** 2026-06-23

---

## Table of Contents

1. [Schema Overview and Philosophy](#1-schema-overview-and-philosophy)
2. [Event Type Registry](#2-event-type-registry)
3. [Base Event Envelope](#3-base-event-envelope)
4. [Source Block](#4-source-block)
5. [Content Block](#5-content-block)
6. [Channel Payload Schemas](#6-channel-payload-schemas)
   - 6.1 [SMS Payload](#61-sms-payload)
   - 6.2 [Call Payload](#62-call-payload)
   - 6.3 [WhatsApp Payload](#63-whatsapp-payload)
   - 6.4 [Telegram Payload](#64-telegram-payload)
   - 6.5 [Gmail Payload](#65-gmail-payload)
7. [Enrichment Schemas](#7-enrichment-schemas)
   - 7.1 [URL Analysis Block](#71-url-analysis-block)
   - 7.2 [Attachment Analysis Block](#72-attachment-analysis-block)
   - 7.3 [Risk Assessment Block](#73-risk-assessment-block)
   - 7.4 [Investigation Report Block](#74-investigation-report-block)
8. [Complete Composite JSON Schemas](#8-complete-composite-json-schemas)
9. [Required and Optional Fields Reference](#9-required-and-optional-fields-reference)
10. [Validation Rules](#10-validation-rules)
11. [Versioning Strategy](#11-versioning-strategy)
12. [Extension Points and Future Channels](#12-extension-points-and-future-channels)

---

## 1. Schema Overview and Philosophy

### 1.1 Purpose

The Sentinel AI Universal Event Schema is the **single shared contract** between every system boundary in the platform:

| Producer | Consumer |
|---|---|
| Android OS listeners (`SmsReceiver`, `CallReceiver`, `NotificationListener`) | FastAPI backend endpoints (`/v1/analyze/*`) |
| FastAPI backend agents | PostgreSQL (scan history, alerts) |
| FastAPI backend agents | Neo4j fraud graph (node/relationship population) |
| FastAPI backend agents | Redis intelligence cache |
| ThreatOrchestrator | Android ViewModel (alert rendering) |
| Celery background tasks | Feed managers and model loaders |

This schema lives at `shared/schemas/` and is consumed verbatim by both Kotlin (Android data layer DTOs) and Python (FastAPI Pydantic models). Any change to this document requires a Principal Architect review and a version bump per the [Versioning Strategy](#11-versioning-strategy).

### 1.2 Design Principles

**Envelope + Discriminated Payload.** Every event carries a fixed base envelope. The `channel` field discriminates which `channel_payload` block is present. This allows all consumers to parse the envelope before touching channel-specific data.

**Progressive Enrichment.** Enrichment blocks (`urls`, `attachments`, `risk_assessment`, `investigation_report`) are absent when an event is first emitted by Android and populated progressively as backend agents process the event. This makes the schema suitable as both a transport contract and a storage document.

**Privacy by Schema.** PII fields are isolated into the `source` block. The `source` block is the only block eligible for on-device anonymization before transmission. Agents MUST NOT reference raw PII from other blocks.

**Immutable Core, Extensible Enrichments.** Required base fields are frozen once set. Only enrichment blocks grow. This ensures historical events remain valid against newer schema versions.

**Channel Agnosticism for Agents.** All AI agents receive a normalized `content.body` and `content.language` regardless of channel. Agents MUST NOT switch logic based on `channel`; they MUST rely only on content signals and enrichment blocks.

**Latency Budget Alignment.** The schema is designed so the Android client can emit a minimal valid event (base + source + content + channel_payload) in under 50 ms. Enrichment blocks are appended by the backend within the 3-second latency SLA.

### 1.3 Lifecycle State Machine

```
CAPTURED ──▶ QUEUED ──▶ ANALYZING ──▶ COMPLETED
                │                          │
                └──▶ FAILED ◀─────────────┘
                          │
                          ▼
                       EXPIRED
```

| State | Set By | Meaning |
|---|---|---|
| `CAPTURED` | Android Listener | Event intercepted by OS hook, not yet sent to backend |
| `QUEUED` | Android Agent Coordinator | Sent to backend, awaiting orchestrator pickup |
| `ANALYZING` | ThreatOrchestrator | At least one agent has started processing |
| `COMPLETED` | Decision Engine | All agents finished, risk score assigned |
| `FAILED` | Any layer | Unrecoverable error during processing |
| `EXPIRED` | Celery cleanup task | Event exceeded TTL without completing (default: 30 s) |

---

## 2. Event Type Registry

Event types follow a reverse-domain dotted namespace: `sentinel.{channel}.{stage}`.

### 2.1 Inbound Channel Events (Android → Backend)

| Event Type | Channel | Trigger |
|---|---|---|
| `sentinel.sms.received` | SMS | OS delivers SMS via `SmsReceiver` broadcast |
| `sentinel.call.incoming` | CALL | Incoming call detected by `CallReceiver` |
| `sentinel.call.ended` | CALL | Call disconnected; transcript may be attached |
| `sentinel.whatsapp.message.received` | WHATSAPP | Notification intercepted from WhatsApp via `NotificationListener` |
| `sentinel.whatsapp.file.shared` | WHATSAPP | File/media shared in WhatsApp (via Accessibility API) |
| `sentinel.telegram.message.received` | TELEGRAM | Notification intercepted from Telegram |
| `sentinel.telegram.file.shared` | TELEGRAM | File/media shared in Telegram |
| `sentinel.email.received` | GMAIL | New email detected via Gmail API connector |
| `sentinel.copilot.query` | COPILOT | User manually submits text/link to AI Copilot |

### 2.2 Analysis Events (Backend Internal / Backend → Android)

| Event Type | Stage | Set By |
|---|---|---|
| `sentinel.url.scan.completed` | Enrichment | LinkAgent |
| `sentinel.file.scan.completed` | Enrichment | FileAgent |
| `sentinel.risk.assessed` | Decision | RiskScoringAgent + DecisionEngine |
| `sentinel.alert.triggered` | Alert | AlertService |
| `sentinel.investigation.completed` | Report | ExplanationAgent |

### 2.3 Future Event Types (Reserved)

| Event Type | Planned Version |
|---|---|
| `sentinel.voice.transcript.ready` | V2 |
| `sentinel.deepfake.voice.detected` | V2 |
| `sentinel.campaign.detected` | V3 |
| `sentinel.geospatial.hotspot.updated` | V3 |
| `sentinel.counterfeit.image.scanned` | V4 |

---

## 3. Base Event Envelope

The base envelope is present in **every** Sentinel AI event, regardless of channel or stage. All fields in this block are immutable once set by the producer.

### 3.1 Field Definitions

| Field | Type | Required | Description |
|---|---|---|---|
| `schema_version` | string | YES | Semantic version of this schema (e.g. `"1.0.0"`) |
| `event_id` | string (UUID v4) | YES | Globally unique event identifier. Never reused. |
| `event_type` | string (enum) | YES | Dotted event type from [Event Type Registry](#2-event-type-registry) |
| `channel` | string (enum) | YES | Top-level channel discriminator. Determines which `channel_payload` block is valid. |
| `processing_status` | string (enum) | YES | Lifecycle state from [State Machine](#13-lifecycle-state-machine) |
| `captured_at` | string (ISO 8601) | YES | Timestamp when the OS event was intercepted by the Android listener. UTC. |
| `submitted_at` | string (ISO 8601) | YES | Timestamp when the Android client submitted the event to the backend. UTC. |
| `processed_at` | string (ISO 8601) | NO | Timestamp when the backend completed analysis. Set by Decision Engine. UTC. |
| `device_id` | string | YES | Anonymized, stable device identifier. SHA-256 of Android ID, salted. Never raw hardware ID. |
| `app_version` | string | YES | Sentinel AI Android app version (semver) that produced this event. |
| `schema_version` | string | YES | Version of this schema document the producer used. |
| `request_id` | string | NO | Backend-assigned request trace ID. Set on first receipt by FastAPI middleware. |
| `ttl_seconds` | integer | NO | Maximum processing window before the event is marked EXPIRED. Default: 30. |
| `source` | object | YES | See [Source Block](#4-source-block) |
| `content` | object | YES | See [Content Block](#5-content-block) |
| `channel_payload` | object | YES | Channel-specific fields. Schema determined by `channel` value. |
| `urls` | array | NO | URL analysis enrichment. Populated by LinkAgent. See [URL Analysis Block](#71-url-analysis-block) |
| `attachments` | array | NO | Attachment analysis enrichment. Populated by FileAgent. See [Attachment Analysis Block](#72-attachment-analysis-block) |
| `risk_assessment` | object | NO | Risk scoring result. Populated by RiskScoringAgent + DecisionEngine. |
| `investigation_report` | object | NO | Human-readable investigation output. Populated by ExplanationAgent. |

### 3.2 Channel Enum Values

```
"SMS" | "CALL" | "WHATSAPP" | "TELEGRAM" | "GMAIL" | "COPILOT"
```

Future: `"VOICE_NOTE"` | `"RCS"` | `"INSTAGRAM_DM"` | `"SIGNAL"`

### 3.3 Minimal Valid Event (Android Emission)

This is the smallest document a producer MUST emit. Enrichment blocks are absent at this stage.

```json
{
  "schema_version": "1.0.0",
  "event_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "event_type": "sentinel.sms.received",
  "channel": "SMS",
  "processing_status": "CAPTURED",
  "captured_at": "2026-06-23T10:15:30.123Z",
  "submitted_at": "2026-06-23T10:15:30.891Z",
  "device_id": "a3f8c2d1e4b57690f1a2b3c4d5e6f708",
  "app_version": "1.0.0",
  "ttl_seconds": 30,
  "source": { ... },
  "content": { ... },
  "channel_payload": { ... }
}
```

---

## 4. Source Block

The `source` block captures **who sent** the communication. This is the sole location for PII. Android clients MUST apply the anonymization rules below before transmission when user privacy mode is enabled.

### 4.1 Field Definitions

| Field | Type | Required | Description |
|---|---|---|---|
| `source.raw_identifier` | string | CONDITIONAL | Raw phone number, email address, or username. MUST be omitted when `privacy_mode = true`. Only transmitted when user has explicitly consented to cloud analysis. |
| `source.identifier_hash` | string | YES | SHA-256 of the raw identifier, lowercase, no whitespace. Always present. Used for Neo4j entity matching without exposing PII. |
| `source.identifier_type` | string (enum) | YES | Type of identifier. Determines format validation. |
| `source.display_name` | string | NO | Contact name from device address book. Omitted in privacy mode. |
| `source.country_code` | string | NO | ISO 3166-1 alpha-2 country code inferred from identifier (e.g., `"IN"`, `"US"`). |
| `source.e164_number` | string | CONDITIONAL | E.164-formatted phone number (e.g., `"+919876543210"`). Present only when `identifier_type` is `PHONE_NUMBER`. Omitted in privacy mode. |
| `source.is_known_contact` | boolean | YES | Whether the sender exists in the device address book. |
| `source.contact_type` | string (enum) | NO | `PERSONAL` / `BUSINESS` / `UNKNOWN`. Derived from Android Contacts API. |
| `source.platform_handle` | string | NO | Platform-specific handle (e.g., WhatsApp JID, Telegram username). Omitted in privacy mode. |
| `source.alpha_sender_id` | string | NO | Alphanumeric SMS sender ID (e.g., `"HDFCBK"`, `"CBISEC"`). Only for SMS channel. |
| `source.reported_scam_count` | integer | NO | Number of scam reports against this identifier in local cache. Populated by intelligence enrichment. |
| `source.intelligence_match` | object | NO | Neo4j/Redis intelligence lookup result for this source. |
| `source.intelligence_match.is_known_fraudster` | boolean | NO | Found in Neo4j `KnownFraudster` node. |
| `source.intelligence_match.associated_campaigns` | array of string | NO | List of `ScamCampaign.id` values associated with this source. |
| `source.intelligence_match.risk_score_from_graph` | number | NO | Neo4j-derived risk score for this entity (0.0–1.0). |

### 4.2 Identifier Type Enum

```
"PHONE_NUMBER" | "EMAIL_ADDRESS" | "WHATSAPP_JID" | "TELEGRAM_USER_ID"
| "TELEGRAM_CHANNEL_ID" | "ALPHA_SENDER_ID" | "UNKNOWN"
```

### 4.3 Privacy Mode Rules

When `privacy_mode = true` (default):
- `raw_identifier` MUST be absent
- `e164_number` MUST be absent
- `display_name` MUST be absent
- `platform_handle` MUST be absent
- `identifier_hash` MUST be present and valid

When `privacy_mode = false` (user-consented cloud analysis):
- All fields may be present
- Backend MUST still store only `identifier_hash` in Neo4j nodes

---

## 5. Content Block

The `content` block captures **what was communicated**. It provides a normalized text surface to all agents regardless of channel.

### 5.1 Field Definitions

| Field | Type | Required | Description |
|---|---|---|---|
| `content.body` | string | YES | Primary text content of the communication. For emails: plain-text body. For calls: empty string until transcript is available. Maximum 50,000 characters. |
| `content.body_truncated` | boolean | YES | `true` if `content.body` was truncated from a longer original. |
| `content.original_length` | integer | NO | Character count of the full original body before truncation. |
| `content.subject` | string | NO | Subject line. Only meaningful for `GMAIL` channel. |
| `content.language` | string | NO | BCP 47 language tag detected on the client (e.g., `"en"`, `"hi"`, `"ta"`). |
| `content.language_confidence` | number | NO | Confidence score of language detection (0.0–1.0). |
| `content.script` | string | NO | Unicode script of the content (e.g., `"Latn"`, `"Deva"`, `"Beng"`). |
| `content.character_count` | integer | YES | Character count of `content.body` as transmitted. |
| `content.word_count` | integer | NO | Word count of `content.body`. |
| `content.contains_urls` | boolean | YES | Precomputed flag: at least one URL-like pattern was detected in `content.body`. Used for fast routing to LinkAgent. |
| `content.contains_attachments` | boolean | YES | Precomputed flag: at least one attachment is associated with this event. Used for fast routing to FileAgent. |
| `content.url_count` | integer | NO | Number of URL-like patterns detected. |
| `content.attachment_count` | integer | NO | Number of attachments associated with this event. |
| `content.has_otp_pattern` | boolean | NO | Regex-detected OTP pattern in body (6-digit, 8-digit, or labeled "OTP"/"PIN"). |
| `content.has_urgency_language` | boolean | NO | Client-side heuristic: urgency words detected (`"immediate"`, `"arrest"`, `"deadline"`, `"action required"`). |
| `content.has_authority_claim` | boolean | NO | Client-side heuristic: authority entity name detected (`"CBI"`, `"ED"`, `"RBI"`, `"TRAI"`, `"police"`). |
| `content.has_financial_mention` | boolean | NO | Client-side heuristic: currency or financial term detected. |
| `content.media_type` | string (enum) | NO | Primary media type for non-text messages. |
| `content.call_transcript` | string | NO | ASR-generated transcript. Only for `CALL` channel, populated after call ends. |
| `content.call_transcript_confidence` | number | NO | ASR model confidence score (0.0–1.0). |

### 5.2 Media Type Enum

```
"TEXT" | "IMAGE" | "AUDIO" | "VIDEO" | "DOCUMENT" | "STICKER"
| "CONTACT_CARD" | "LOCATION" | "APK" | "VOICE_NOTE" | "UNKNOWN"
```

---

## 6. Channel Payload Schemas

The `channel_payload` object contains fields that are meaningful only for a specific channel. The `channel` field in the base envelope determines which payload schema applies. Agents MUST NOT read `channel_payload` fields directly; the Orchestrator extracts and normalizes these before routing.

---

### 6.1 SMS Payload

**Applies when:** `channel = "SMS"`

| Field | Type | Required | Description |
|---|---|---|---|
| `channel_payload.sender_number_raw` | string | CONDITIONAL | Raw sender number string as received by Android. Omitted in privacy mode. |
| `channel_payload.sender_number_e164` | string | CONDITIONAL | E.164 normalized form. Omitted in privacy mode. |
| `channel_payload.alpha_sender_id` | string | NO | Alphanumeric sender ID (e.g., `"HDFCBK"`). Mutually exclusive with numeric number fields. |
| `channel_payload.sms_type` | string (enum) | YES | `"TRANSACTIONAL"` / `"PROMOTIONAL"` / `"PERSONAL"` — inferred from sender ID type and content signals. |
| `channel_payload.sim_slot_index` | integer | NO | SIM slot that received the SMS (0 = SIM 1, 1 = SIM 2). |
| `channel_payload.carrier` | string | NO | Network carrier inferred from number prefix (e.g., `"Airtel"`, `"Jio"`, `"BSNL"`). |
| `channel_payload.message_parts` | integer | YES | Number of SMS parts (multi-part SMS). |
| `channel_payload.has_dlt_header` | boolean | NO | Whether a DLT (Distributed Ledger Technology) header prefix is present (Indian regulatory requirement for transactional SMS). |
| `channel_payload.dlt_principal_entity_id` | string | NO | TRAI DLT Principal Entity ID extracted from SMS header, if present. |
| `channel_payload.dlt_template_id` | string | NO | TRAI DLT Template ID extracted from SMS header, if present. |

**Example:**

```json
{
  "channel_payload": {
    "alpha_sender_id": "CBISEC",
    "sms_type": "TRANSACTIONAL",
    "sim_slot_index": 0,
    "carrier": "Airtel",
    "message_parts": 1,
    "has_dlt_header": false,
    "dlt_principal_entity_id": null,
    "dlt_template_id": null
  }
}
```

---

### 6.2 Call Payload

**Applies when:** `channel = "CALL"` and event type is `sentinel.call.incoming` or `sentinel.call.ended`

| Field | Type | Required | Description |
|---|---|---|---|
| `channel_payload.caller_number_raw` | string | CONDITIONAL | Raw caller number. Omitted in privacy mode. |
| `channel_payload.caller_number_e164` | string | CONDITIONAL | E.164 normalized. Omitted in privacy mode. |
| `channel_payload.call_direction` | string (enum) | YES | `"INBOUND"` / `"OUTBOUND"`. Scam calls are almost always `INBOUND`. |
| `channel_payload.call_state` | string (enum) | YES | Current/final call state. |
| `channel_payload.duration_seconds` | integer | NO | Total call duration. Present only when `call_state = "ENDED"`. |
| `channel_payload.is_number_unknown` | boolean | YES | `true` if the caller is not in the device address book. |
| `channel_payload.voip_detected` | boolean | NO | Whether Android heuristics suggest a VoIP call (e.g., Google Voice, SIP). |
| `channel_payload.call_type` | string (enum) | NO | `"VOICE"` / `"VIDEO"`. |
| `channel_payload.carrier` | string | NO | Carrier of the calling number. |
| `channel_payload.transcript_available` | boolean | YES | Whether `content.call_transcript` has been populated. |
| `channel_payload.call_recording_reference` | string | NO | Ephemeral on-device reference to call recording. MUST NOT be transmitted to backend unless user explicitly shares. Retained on device only. |

**Call State Enum:**

```
"RINGING" | "ACTIVE" | "ENDED" | "MISSED" | "REJECTED" | "BUSY"
```

**Example:**

```json
{
  "channel_payload": {
    "call_direction": "INBOUND",
    "call_state": "ENDED",
    "duration_seconds": 342,
    "is_number_unknown": true,
    "voip_detected": false,
    "call_type": "VOICE",
    "transcript_available": true
  }
}
```

---

### 6.3 WhatsApp Payload

**Applies when:** `channel = "WHATSAPP"`

WhatsApp payloads are captured via the Android `NotificationListener` API and `AccessibilityService`. Fields are limited to what is observable without root access or app modification.

| Field | Type | Required | Description |
|---|---|---|---|
| `channel_payload.chat_id_hash` | string | YES | SHA-256 of the WhatsApp chat JID. Enables Neo4j entity matching without exposing the JID. |
| `channel_payload.sender_wa_id_hash` | string | YES | SHA-256 of the sender's WhatsApp JID. |
| `channel_payload.is_group_chat` | boolean | YES | `true` if the message originated in a group chat. |
| `channel_payload.group_name` | string | NO | Group name. Present only when `is_group_chat = true`. May be omitted in privacy mode. |
| `channel_payload.group_member_count` | integer | NO | Group member count if available. |
| `channel_payload.message_type` | string (enum) | YES | Type of WhatsApp message. |
| `channel_payload.is_forwarded` | boolean | NO | Detected from WhatsApp forwarding indicator label in notification. |
| `channel_payload.forward_chain_length` | integer | NO | If forwarded, the `"Forwarded many times"` label maps to `5`, single forward maps to `1`. |
| `channel_payload.is_broadcast` | boolean | NO | Detected as a WhatsApp broadcast message. |
| `channel_payload.capture_method` | string (enum) | YES | How the event was captured by Sentinel AI. |
| `channel_payload.has_call_button` | boolean | NO | Notification contained a WhatsApp call-back action (sign of vishing setup). |

**WhatsApp Message Type Enum:**

```
"TEXT" | "IMAGE" | "VIDEO" | "AUDIO" | "DOCUMENT" | "STICKER"
| "CONTACT_CARD" | "LOCATION" | "VOICE_NOTE" | "UNKNOWN"
```

**Capture Method Enum:**

```
"NOTIFICATION_LISTENER" | "ACCESSIBILITY_SERVICE" | "USER_PASTE" | "SHARE_INTENT"
```

**Example:**

```json
{
  "channel_payload": {
    "chat_id_hash": "b94d27b9934d3e08a52e52d7da7dabfac484efe04294e576",
    "sender_wa_id_hash": "3c8d4e6f2a1b5c7d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4",
    "is_group_chat": false,
    "message_type": "TEXT",
    "is_forwarded": true,
    "forward_chain_length": 5,
    "is_broadcast": false,
    "capture_method": "NOTIFICATION_LISTENER",
    "has_call_button": false
  }
}
```

---

### 6.4 Telegram Payload

**Applies when:** `channel = "TELEGRAM"`

| Field | Type | Required | Description |
|---|---|---|---|
| `channel_payload.chat_id_hash` | string | YES | SHA-256 of the Telegram chat ID. |
| `channel_payload.sender_user_id_hash` | string | CONDITIONAL | SHA-256 of the sender's Telegram user ID. Absent for channels (no individual sender). |
| `channel_payload.chat_type` | string (enum) | YES | Type of Telegram chat. |
| `channel_payload.channel_name` | string | NO | Channel or group name as shown in notification. May be omitted in privacy mode. |
| `channel_payload.is_verified_channel` | boolean | NO | Telegram blue-check verification detected in notification. |
| `channel_payload.message_type` | string (enum) | YES | Type of Telegram message. |
| `channel_payload.has_inline_keyboard` | boolean | NO | Message contains inline keyboard buttons (e.g., phishing "Click Here" buttons). |
| `channel_payload.bot_interaction` | boolean | NO | Message originated from or targets a Telegram bot. |
| `channel_payload.capture_method` | string (enum) | YES | How the event was captured. Same enum as WhatsApp capture method. |
| `channel_payload.has_payment_button` | boolean | NO | Message contains a Telegram payment button (financial fraud signal). |

**Telegram Chat Type Enum:**

```
"PRIVATE" | "GROUP" | "SUPERGROUP" | "CHANNEL" | "UNKNOWN"
```

**Example:**

```json
{
  "channel_payload": {
    "chat_id_hash": "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4",
    "chat_type": "CHANNEL",
    "channel_name": "Investment Tips Official",
    "is_verified_channel": false,
    "message_type": "TEXT",
    "has_inline_keyboard": true,
    "bot_interaction": false,
    "capture_method": "NOTIFICATION_LISTENER",
    "has_payment_button": false
  }
}
```

---

### 6.5 Gmail Payload

**Applies when:** `channel = "GMAIL"`

Gmail events are captured via the Gmail API connector after explicit user authorization (OAuth 2.0). All header fields are available.

| Field | Type | Required | Description |
|---|---|---|---|
| `channel_payload.message_id` | string | YES | Gmail Message-ID header value (RFC 2822). Used for deduplication. |
| `channel_payload.thread_id` | string | NO | Gmail thread ID for conversation grouping. |
| `channel_payload.from_address_hash` | string | YES | SHA-256 of `from` email address. |
| `channel_payload.from_address_raw` | string | CONDITIONAL | Raw `from` email address. Omitted in privacy mode. |
| `channel_payload.from_display_name` | string | NO | Sender display name from `From:` header. |
| `channel_payload.reply_to_address_hash` | string | NO | SHA-256 of `Reply-To` address if different from `from`. Mismatch is a phishing signal. |
| `channel_payload.from_domain` | string | YES | Domain extracted from `from` address. Used by LinkAgent for reputation check. |
| `channel_payload.return_path_domain` | string | NO | Domain from `Return-Path` header. Mismatch with `from_domain` is a phishing signal. |
| `channel_payload.subject` | string | NO | Email subject line. |
| `channel_payload.has_html_body` | boolean | YES | Whether the email has an HTML part (obfuscated phishing links often use HTML). |
| `channel_payload.label_ids` | array of string | NO | Gmail label IDs (e.g., `["INBOX", "UNREAD", "CATEGORY_UPDATES"]`). |
| `channel_payload.spam_label_present` | boolean | NO | Gmail's own spam label was applied. |
| `channel_payload.dkim_result` | string (enum) | NO | DKIM authentication result from email headers. |
| `channel_payload.spf_result` | string (enum) | NO | SPF authentication result. |
| `channel_payload.dmarc_result` | string (enum) | NO | DMARC policy result. |
| `channel_payload.to_address_count` | integer | NO | Number of `To:` recipients. |
| `channel_payload.cc_address_count` | integer | NO | Number of `CC:` recipients. |
| `channel_payload.email_size_bytes` | integer | NO | Total email size in bytes including attachments. |
| `channel_payload.received_at_server` | string (ISO 8601) | NO | Timestamp from email `Received:` header (server-side receipt). |

**Email Auth Result Enum:**

```
"PASS" | "FAIL" | "SOFTFAIL" | "NEUTRAL" | "NONE" | "UNKNOWN"
```

**Example:**

```json
{
  "channel_payload": {
    "message_id": "<CA+XrJ1pB8kLm3nO9qR2sT4uV5wX6yZ@mail.gmail.com>",
    "thread_id": "18a2b3c4d5e6f7a8",
    "from_address_hash": "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
    "from_display_name": "CBI Official Notice",
    "from_domain": "cbi-india-gov.co.in",
    "reply_to_address_hash": "c0535e4be2b79ffd93291305436bf889314e4a3faec05ecffcbb7df31ad9e51b",
    "return_path_domain": "bounce.spamservice.ru",
    "subject": "Urgent: Legal Notice from Central Bureau of Investigation",
    "has_html_body": true,
    "spam_label_present": false,
    "dkim_result": "FAIL",
    "spf_result": "FAIL",
    "dmarc_result": "FAIL",
    "to_address_count": 1,
    "cc_address_count": 0,
    "email_size_bytes": 48392,
    "received_at_server": "2026-06-23T09:45:10.000Z"
  }
}
```

---

## 7. Enrichment Schemas

Enrichment blocks are **absent on emission** and **populated by backend agents** during the `ANALYZING` phase. They are appended to the event document and persisted to PostgreSQL. Relevant fields are also written to Neo4j nodes and Redis cache.

---

### 7.1 URL Analysis Block

**Field:** `urls` (array)
**Populated by:** `LinkAgent` (`backend/app/agents/link/link_agent.py`)
**Routing trigger:** `content.contains_urls = true`

Each element in the `urls` array represents one URL detected in the event content.

| Field | Type | Required | Description |
|---|---|---|---|
| `urls[n].url_id` | string (UUID v4) | YES | Unique ID for this URL analysis record. |
| `urls[n].raw_url` | string | YES | URL exactly as it appeared in the content body. |
| `urls[n].normalized_url` | string | YES | Normalized URL (lowercase scheme+host, decoded percent-encoding, trailing slash stripped). |
| `urls[n].domain` | string | YES | Registered domain (e.g., `"cbi-india-gov.co.in"`). |
| `urls[n].subdomain` | string | NO | Subdomain portion (e.g., `"secure.login"`). |
| `urls[n].tld` | string | YES | Top-level domain (e.g., `"co.in"`, `"xyz"`). |
| `urls[n].url_scheme` | string | YES | `"https"` / `"http"` / `"ftp"` / other. `http` without `s` is a risk signal. |
| `urls[n].is_shortened` | boolean | YES | URL resolves through a known shortening service (bit.ly, tinyurl, etc.). |
| `urls[n].final_url` | string | NO | URL after following all redirects. Populated if redirect chain was followed. |
| `urls[n].redirect_chain` | array of string | NO | Ordered list of intermediate redirect URLs. |
| `urls[n].redirect_depth` | integer | NO | Number of redirects in chain. Deep chains are suspicious. |
| `urls[n].domain_age_days` | integer | NO | Age of the domain in days at time of analysis. Freshly registered domains are high-risk. |
| `urls[n].registrar` | string | NO | Domain registrar from WHOIS. |
| `urls[n].registration_country` | string | NO | ISO 3166-1 alpha-2 country of registrar. |
| `urls[n].is_ip_address_url` | boolean | YES | URL uses a raw IP address instead of a domain name (strong phishing signal). |
| `urls[n].ip_address` | string | CONDITIONAL | IP address if `is_ip_address_url = true`. |
| `urls[n].ssl_valid` | boolean | NO | Whether a valid, non-expired SSL certificate is present. |
| `urls[n].ssl_organization` | string | NO | Organization name from SSL certificate. |
| `urls[n].brand_impersonation_detected` | boolean | YES | `true` if the URL appears to impersonate a known legitimate brand. |
| `urls[n].impersonated_brand` | string | NO | The brand being impersonated (e.g., `"SBI"`, `"HDFC"`, `"TRAI"`, `"Amazon"`). |
| `urls[n].phishing_feed_match` | boolean | YES | URL or domain found in OpenPhish / PhishTank / intelligence feeds. |
| `urls[n].phishing_feed_sources` | array of string | NO | Names of feeds that matched (e.g., `["OpenPhish", "PhishTank"]`). |
| `urls[n].neo4j_domain_node_id` | string | NO | Neo4j node ID of the `(:Domain)` node created/matched for this URL. |
| `urls[n].url_risk_score` | number | YES | Per-URL risk score from LinkAgent (0.0–1.0). |
| `urls[n].url_risk_signals` | array of string | NO | Human-readable risk signals (e.g., `["domain_age_3_days", "brand_impersonation_HDFC", "ssl_invalid"]`). |
| `urls[n].analyzed_at` | string (ISO 8601) | YES | When the LinkAgent completed this URL analysis. |

**Example:**

```json
{
  "urls": [
    {
      "url_id": "a1b2c3d4-e5f6-7890-ab12-cd34ef567890",
      "raw_url": "http://bit.ly/sbi-kyc-update",
      "normalized_url": "http://bit.ly/sbi-kyc-update",
      "domain": "bit.ly",
      "tld": "ly",
      "url_scheme": "http",
      "is_shortened": true,
      "final_url": "http://sbi-secure-kyc.xyz/login",
      "redirect_chain": ["http://bit.ly/sbi-kyc-update", "http://sbi-secure-kyc.xyz/login"],
      "redirect_depth": 1,
      "domain_age_days": 2,
      "is_ip_address_url": false,
      "ssl_valid": false,
      "brand_impersonation_detected": true,
      "impersonated_brand": "SBI",
      "phishing_feed_match": false,
      "url_risk_score": 0.94,
      "url_risk_signals": [
        "http_no_ssl",
        "shortened_url",
        "final_domain_age_2_days",
        "brand_impersonation_SBI",
        "ssl_absent"
      ],
      "analyzed_at": "2026-06-23T10:15:33.201Z"
    }
  ]
}
```

---

### 7.2 Attachment Analysis Block

**Field:** `attachments` (array)
**Populated by:** `FileAgent` (`backend/app/agents/file/file_agent.py`)
**Routing trigger:** `content.contains_attachments = true`

Each element represents one file attachment associated with the event.

| Field | Type | Required | Description |
|---|---|---|---|
| `attachments[n].attachment_id` | string (UUID v4) | YES | Unique ID for this attachment analysis record. |
| `attachments[n].filename` | string | YES | Original filename as provided by sender. |
| `attachments[n].file_extension` | string | YES | Lowercase extension without dot (e.g., `"pdf"`, `"apk"`, `"docx"`). |
| `attachments[n].mime_type` | string | YES | MIME type detected by backend (not trusted from sender). |
| `attachments[n].file_size_bytes` | integer | YES | File size in bytes. |
| `attachments[n].sha256_hash` | string | YES | SHA-256 of file content. Used for deduplication and intelligence matching. |
| `attachments[n].md5_hash` | string | NO | MD5 hash. Provided for compatibility with older threat intelligence feeds. |
| `attachments[n].on_device_path` | string | NO | Ephemeral Android file path. NEVER transmitted to backend — used only by on-device FileAgent coordinator. |
| `attachments[n].backend_storage_key` | string | CONDITIONAL | Backend ephemeral object storage key. Present only if file was uploaded for server-side analysis. TTL 24 hours. |
| `attachments[n].file_category` | string (enum) | YES | High-level file category. |
| `attachments[n].is_executable` | boolean | YES | File is an executable or installable package (APK, EXE, JS). Always HIGH risk. |
| `attachments[n].malware_hash_match` | boolean | YES | SHA-256 found in known malware hash database. |
| `attachments[n].malware_hash_sources` | array of string | NO | Names of databases where hash matched. |
| `attachments[n].embedded_urls` | array of string | NO | URLs found embedded within the file (e.g., in PDF, DOCX, APK manifest). |
| `attachments[n].embedded_url_count` | integer | NO | Count of embedded URLs. |
| `attachments[n].has_macro` | boolean | NO | Macro code detected in document. Only applicable to `DOCUMENT` category. |
| `attachments[n].has_javascript` | boolean | NO | Embedded JavaScript detected. Applicable to PDF and HTML files. |
| `attachments[n].pdf_analysis` | object | NO | Present only when `file_category = "PDF"`. |
| `attachments[n].pdf_analysis.page_count` | integer | NO | Number of pages. |
| `attachments[n].pdf_analysis.has_form_fields` | boolean | NO | Credential-harvesting form fields detected. |
| `attachments[n].pdf_analysis.government_seal_detected` | boolean | NO | Known government seal/logo detected via image analysis (fake notice indicator). |
| `attachments[n].pdf_analysis.fake_notice_probability` | number | NO | ML model probability that this PDF is a fake legal notice (0.0–1.0). |
| `attachments[n].apk_analysis` | object | NO | Present only when `file_category = "APK"`. |
| `attachments[n].apk_analysis.package_name` | string | NO | Android package name from APK manifest. |
| `attachments[n].apk_analysis.declared_permissions` | array of string | NO | Android permissions requested by the APK. |
| `attachments[n].apk_analysis.is_signed` | boolean | NO | APK has a valid digital signature. |
| `attachments[n].apk_analysis.signing_certificate_hash` | string | NO | Hash of the signing certificate. |
| `attachments[n].apk_analysis.requests_sms_permission` | boolean | NO | APK requests `READ_SMS` or `RECEIVE_SMS` — critical signal. |
| `attachments[n].apk_analysis.requests_call_log_permission` | boolean | NO | APK requests call log access. |
| `attachments[n].apk_analysis.requests_overlay_permission` | boolean | NO | APK requests `SYSTEM_ALERT_WINDOW` (screen overlay attacks). |
| `attachments[n].attachment_risk_score` | number | YES | Per-attachment risk score from FileAgent (0.0–1.0). |
| `attachments[n].attachment_risk_signals` | array of string | NO | Human-readable risk signals. |
| `attachments[n].analyzed_at` | string (ISO 8601) | YES | When the FileAgent completed this attachment analysis. |

**File Category Enum:**

```
"PDF" | "DOCUMENT" | "SPREADSHEET" | "IMAGE" | "AUDIO" | "VIDEO"
| "APK" | "ARCHIVE" | "EXECUTABLE" | "UNKNOWN"
```

---

### 7.3 Risk Assessment Block

**Field:** `risk_assessment`
**Populated by:** `RiskScoringAgent` + `DecisionEngine` (`backend/app/agents/risk/`)
**Routing trigger:** All `ANALYZING` state events; populated at end of agent fan-out.

| Field | Type | Required | Description |
|---|---|---|---|
| `risk_assessment.risk_level` | string (enum) | YES | Final verdict risk level. |
| `risk_assessment.overall_score` | number | YES | Aggregated risk score (0.0–1.0). Weighted composite of all agent scores. |
| `risk_assessment.confidence` | number | YES | Confidence in the overall score (0.0–1.0). Low confidence triggers `YELLOW` floor. |
| `risk_assessment.threat_categories` | array of string | YES | Detected threat categories from the Threat Category Registry. |
| `risk_assessment.primary_threat_category` | string | NO | The highest-weight threat category if multiple are present. |
| `risk_assessment.is_digital_arrest_scam` | boolean | YES | Explicit flag: DigitalArrestAgent fired and confirmed. Exposed separately for fast UI rendering. |
| `risk_assessment.is_authority_impersonation` | boolean | YES | Authority impersonation pattern confirmed. |
| `risk_assessment.agent_scores` | array of object | YES | Per-agent scoring breakdown. |
| `risk_assessment.agent_scores[n].agent_id` | string | YES | Agent identifier (e.g., `"sms_agent"`, `"link_agent"`, `"digital_arrest_agent"`). |
| `risk_assessment.agent_scores[n].agent_version` | string | YES | Version of the agent model/classifier that produced this score. |
| `risk_assessment.agent_scores[n].score` | number | YES | Agent's individual risk score (0.0–1.0). |
| `risk_assessment.agent_scores[n].confidence` | number | YES | Agent's confidence in its score (0.0–1.0). |
| `risk_assessment.agent_scores[n].signals` | array of string | NO | Signals that contributed to this agent's score. |
| `risk_assessment.agent_scores[n].threat_categories` | array of string | NO | Threat categories flagged by this specific agent. |
| `risk_assessment.agent_scores[n].latency_ms` | integer | NO | Time this agent took to produce a result in milliseconds. |
| `risk_assessment.aggregation_method` | string (enum) | NO | How agent scores were combined. |
| `risk_assessment.neo4j_context_score` | number | NO | Risk score contribution from Neo4j fraud graph context. |
| `risk_assessment.intelligence_feed_match` | boolean | YES | Any intelligence feed matched this event's source, content, or URLs. |
| `risk_assessment.false_positive_probability` | number | NO | Model-estimated probability that this is a false positive (0.0–1.0). |
| `risk_assessment.assessed_at` | string (ISO 8601) | YES | Timestamp of assessment. |
| `risk_assessment.model_versions` | object | NO | Map of `agent_id → model_version` for reproducibility. |

**Risk Level Enum and Score Ranges:**

| Risk Level | Score Range | UI Color | User Action |
|---|---|---|---|
| `GREEN` | 0.00 – 0.24 | Green | Safe to proceed |
| `YELLOW` | 0.25 – 0.59 | Yellow | Exercise caution |
| `RED` | 0.60 – 0.84 | Red | High risk — avoid action |
| `CRITICAL` | 0.85 – 1.00 | Red (flashing) | Immediate threat — stop all interaction |

**Aggregation Method Enum:**

```
"WEIGHTED_AVERAGE" | "MAX_SCORE" | "BAYESIAN_COMBINATION" | "ENSEMBLE_VOTE"
```

**Threat Category Registry:**

```
"OTP_THEFT" | "KYC_SCAM" | "BANKING_FRAUD" | "REWARD_SCAM" | "LOTTERY_SCAM"
| "DIGITAL_ARREST" | "AUTHORITY_IMPERSONATION" | "CBI_IMPERSONATION"
| "ED_IMPERSONATION" | "CUSTOMS_IMPERSONATION" | "POLICE_IMPERSONATION"
| "TRAI_IMPERSONATION" | "RBI_IMPERSONATION" | "UIDAI_IMPERSONATION"
| "PHISHING" | "CREDENTIAL_THEFT" | "INVESTMENT_FRAUD" | "LOAN_FRAUD"
| "GOVERNMENT_IMPERSONATION" | "CRYPTO_SCAM" | "FINANCIAL_COERCION"
| "SOCIAL_ENGINEERING" | "MALWARE_DISTRIBUTION" | "APK_TROJAN"
| "INVOICE_FRAUD" | "VENDOR_SCAM" | "SCHOLARSHIP_SCAM" | "JOB_SCAM"
| "INTERNSHIP_FRAUD" | "DEEPFAKE_ASSISTED" | "SIM_SWAP_FRAUD"
| "ACCOUNT_TAKEOVER" | "BRAND_IMPERSONATION" | "UNKNOWN"
```

**Example:**

```json
{
  "risk_assessment": {
    "risk_level": "CRITICAL",
    "overall_score": 0.96,
    "confidence": 0.91,
    "threat_categories": ["DIGITAL_ARREST", "CBI_IMPERSONATION", "FINANCIAL_COERCION"],
    "primary_threat_category": "DIGITAL_ARREST",
    "is_digital_arrest_scam": true,
    "is_authority_impersonation": true,
    "agent_scores": [
      {
        "agent_id": "sms_agent",
        "agent_version": "1.2.0",
        "score": 0.88,
        "confidence": 0.92,
        "signals": ["authority_keyword_CBI", "urgency_language", "financial_threat"],
        "threat_categories": ["DIGITAL_ARREST", "CBI_IMPERSONATION"],
        "latency_ms": 142
      },
      {
        "agent_id": "digital_arrest_agent",
        "agent_version": "1.1.0",
        "score": 0.97,
        "confidence": 0.94,
        "signals": ["arrest_threat_language", "coercion_pattern", "fake_case_number"],
        "threat_categories": ["DIGITAL_ARREST", "FINANCIAL_COERCION"],
        "latency_ms": 214
      },
      {
        "agent_id": "link_agent",
        "agent_version": "1.0.3",
        "score": 0.94,
        "confidence": 0.89,
        "signals": ["brand_impersonation_SBI", "domain_age_2_days", "http_no_ssl"],
        "threat_categories": ["PHISHING", "CREDENTIAL_THEFT"],
        "latency_ms": 387
      }
    ],
    "aggregation_method": "WEIGHTED_AVERAGE",
    "neo4j_context_score": 0.82,
    "intelligence_feed_match": false,
    "false_positive_probability": 0.04,
    "assessed_at": "2026-06-23T10:15:34.005Z",
    "model_versions": {
      "sms_agent": "scam_classifier_v1.2.0_IndicBERT",
      "digital_arrest_agent": "da_classifier_v1.1.0_MuRIL",
      "link_agent": "url_classifier_v1.0.3_domain_embedding"
    }
  }
}
```

---

### 7.4 Investigation Report Block

**Field:** `investigation_report`
**Populated by:** `ExplanationAgent` + `RecommendationEngine` (`backend/app/agents/explanation/`)
**Routing trigger:** `risk_assessment.risk_level` is `YELLOW`, `RED`, or `CRITICAL`

| Field | Type | Required | Description |
|---|---|---|---|
| `investigation_report.report_id` | string (UUID v4) | YES | Unique ID for this report. Referenced by the AlertService. |
| `investigation_report.summary` | string | YES | One-sentence plain-language summary for the alert banner. Maximum 150 characters. |
| `investigation_report.detailed_explanation` | string | YES | Multi-sentence explanation of why this is a threat. Plain language, no jargon. Maximum 1,000 characters. |
| `investigation_report.what_happened` | string | YES | Description of the threat event from the user's perspective. |
| `investigation_report.why_its_risky` | string | YES | Explanation of the specific risk indicators found. |
| `investigation_report.what_to_do` | string | YES | Primary recommended user action. Single, direct instruction. |
| `investigation_report.recommended_actions` | array of object | YES | Ordered list of recommended actions, from most to least urgent. |
| `investigation_report.recommended_actions[n].action_id` | string | YES | Unique action ID. |
| `investigation_report.recommended_actions[n].action_type` | string (enum) | YES | Action type for UI rendering. |
| `investigation_report.recommended_actions[n].label` | string | YES | Short button label (e.g., `"Block Sender"`, `"Report to NCRP"`). |
| `investigation_report.recommended_actions[n].description` | string | YES | One-sentence explanation of why to take this action. |
| `investigation_report.recommended_actions[n].deep_link` | string | NO | Android deep link or URI to execute the action directly. |
| `investigation_report.recommended_actions[n].is_primary` | boolean | YES | Only one action per report should be `true`. |
| `investigation_report.evidence` | array of object | NO | Evidence items that support the verdict. Displayed in Copilot details view. |
| `investigation_report.evidence[n].evidence_type` | string (enum) | YES | Type of evidence. |
| `investigation_report.evidence[n].description` | string | YES | Human-readable description of this evidence item. |
| `investigation_report.evidence[n].severity` | string (enum) | YES | `"INFO"` / `"WARNING"` / `"CRITICAL"`. |
| `investigation_report.evidence[n].source_agent` | string | NO | Agent ID that produced this evidence. |
| `investigation_report.neo4j_relationships` | array of object | NO | Neo4j entities discovered during analysis. Used for fraud graph visualization in V4 dashboard. |
| `investigation_report.neo4j_relationships[n].node_type` | string | YES | Neo4j node label (e.g., `"PhoneNumber"`, `"Domain"`, `"ScamCampaign"`). |
| `investigation_report.neo4j_relationships[n].node_id` | string | YES | Neo4j node ID. |
| `investigation_report.neo4j_relationships[n].relationship_type` | string | YES | Neo4j relationship type (e.g., `"USED_IN"`, `"PART_OF"`, `"HOSTS"`). |
| `investigation_report.neo4j_relationships[n].related_entity` | string | NO | Display name of the related entity. |
| `investigation_report.language` | string | NO | BCP 47 language tag of the report. Used for multilingual output in V2. Default: `"en"`. |
| `investigation_report.generated_at` | string (ISO 8601) | YES | When the ExplanationAgent generated this report. |

**Action Type Enum:**

```
"BLOCK_SENDER" | "DELETE_MESSAGE" | "REPORT_NCCRP" | "REPORT_TRAI"
| "CALL_HELPLINE_1930" | "DO_NOT_SHARE_OTP" | "DO_NOT_CLICK_LINK"
| "DO_NOT_DOWNLOAD_FILE" | "DO_NOT_MAKE_PAYMENT" | "DISCONNECT_CALL"
| "CONTACT_BANK" | "CONTACT_POLICE" | "MARK_SPAM" | "FORWARD_TO_SENTINEL"
| "CUSTOM"
```

**Evidence Type Enum:**

```
"KEYWORD_MATCH" | "URL_RISK" | "DOMAIN_AGE" | "BRAND_IMPERSONATION"
| "FEED_MATCH" | "GRAPH_MATCH" | "PERMISSION_RISK" | "MALWARE_HASH"
| "AUTHORITY_CLAIM" | "COERCION_LANGUAGE" | "URGENCY_LANGUAGE"
| "FAKE_NOTICE" | "DLT_ABSENT" | "AUTH_FAILURE" | "SENDER_MISMATCH"
| "FORWARD_CHAIN" | "EXECUTABLE_ATTACHMENT"
```

---

## 8. Complete Composite JSON Schemas

The following schemas are authoritative JSON Schema (Draft-07) definitions. These are the canonical files for `shared/schemas/`.

### 8.1 Base Envelope Schema (`shared/schemas/base_event.json`)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "https://sentinel-ai.internal/schemas/v1/base_event.json",
  "title": "SentinelBaseEvent",
  "description": "Universal base envelope for all Sentinel AI events. All channel events extend this schema.",
  "type": "object",
  "required": [
    "schema_version",
    "event_id",
    "event_type",
    "channel",
    "processing_status",
    "captured_at",
    "submitted_at",
    "device_id",
    "app_version",
    "source",
    "content",
    "channel_payload"
  ],
  "additionalProperties": false,
  "properties": {
    "schema_version": {
      "type": "string",
      "pattern": "^\\d+\\.\\d+\\.\\d+$",
      "description": "Semantic version of the schema used to produce this event.",
      "examples": ["1.0.0"]
    },
    "event_id": {
      "type": "string",
      "format": "uuid",
      "description": "Globally unique event identifier. UUID v4. Never reused or reissued."
    },
    "event_type": {
      "type": "string",
      "enum": [
        "sentinel.sms.received",
        "sentinel.call.incoming",
        "sentinel.call.ended",
        "sentinel.whatsapp.message.received",
        "sentinel.whatsapp.file.shared",
        "sentinel.telegram.message.received",
        "sentinel.telegram.file.shared",
        "sentinel.email.received",
        "sentinel.copilot.query",
        "sentinel.url.scan.completed",
        "sentinel.file.scan.completed",
        "sentinel.risk.assessed",
        "sentinel.alert.triggered",
        "sentinel.investigation.completed"
      ]
    },
    "channel": {
      "type": "string",
      "enum": ["SMS", "CALL", "WHATSAPP", "TELEGRAM", "GMAIL", "COPILOT"],
      "description": "Discriminator field. Determines which channel_payload schema applies."
    },
    "processing_status": {
      "type": "string",
      "enum": ["CAPTURED", "QUEUED", "ANALYZING", "COMPLETED", "FAILED", "EXPIRED"]
    },
    "captured_at": {
      "type": "string",
      "format": "date-time",
      "description": "ISO 8601 UTC timestamp when OS event was intercepted."
    },
    "submitted_at": {
      "type": "string",
      "format": "date-time",
      "description": "ISO 8601 UTC timestamp when Android client submitted to backend."
    },
    "processed_at": {
      "type": ["string", "null"],
      "format": "date-time",
      "description": "ISO 8601 UTC timestamp when backend completed analysis. Null until COMPLETED."
    },
    "device_id": {
      "type": "string",
      "pattern": "^[a-f0-9]{32,64}$",
      "description": "Anonymized stable device ID. SHA-256 hex of salted Android ID."
    },
    "app_version": {
      "type": "string",
      "pattern": "^\\d+\\.\\d+\\.\\d+$"
    },
    "request_id": {
      "type": ["string", "null"],
      "description": "Backend-assigned request trace ID. Null on Android emission."
    },
    "ttl_seconds": {
      "type": "integer",
      "minimum": 5,
      "maximum": 300,
      "default": 30
    },
    "source": {
      "$ref": "#/definitions/SourceBlock"
    },
    "content": {
      "$ref": "#/definitions/ContentBlock"
    },
    "channel_payload": {
      "type": "object",
      "description": "Channel-specific payload. Validated against the channel-specific schema referenced by the 'channel' field."
    },
    "urls": {
      "type": ["array", "null"],
      "items": { "$ref": "#/definitions/UrlAnalysisItem" },
      "description": "URL enrichment array. Absent on Android emission. Populated by LinkAgent."
    },
    "attachments": {
      "type": ["array", "null"],
      "items": { "$ref": "#/definitions/AttachmentAnalysisItem" },
      "description": "Attachment enrichment array. Absent on Android emission. Populated by FileAgent."
    },
    "risk_assessment": {
      "oneOf": [
        { "$ref": "#/definitions/RiskAssessmentBlock" },
        { "type": "null" }
      ],
      "description": "Risk scoring result. Absent until DecisionEngine runs."
    },
    "investigation_report": {
      "oneOf": [
        { "$ref": "#/definitions/InvestigationReportBlock" },
        { "type": "null" }
      ],
      "description": "Human-readable investigation report. Absent until ExplanationAgent runs."
    }
  },
  "definitions": {
    "SourceBlock": {
      "type": "object",
      "required": ["identifier_hash", "identifier_type", "is_known_contact"],
      "additionalProperties": false,
      "properties": {
        "raw_identifier":           { "type": ["string", "null"] },
        "identifier_hash":          { "type": "string", "pattern": "^[a-f0-9]{64}$" },
        "identifier_type": {
          "type": "string",
          "enum": [
            "PHONE_NUMBER", "EMAIL_ADDRESS", "WHATSAPP_JID",
            "TELEGRAM_USER_ID", "TELEGRAM_CHANNEL_ID",
            "ALPHA_SENDER_ID", "UNKNOWN"
          ]
        },
        "display_name":             { "type": ["string", "null"], "maxLength": 200 },
        "country_code":             { "type": ["string", "null"], "pattern": "^[A-Z]{2}$" },
        "e164_number":              { "type": ["string", "null"], "pattern": "^\\+[1-9]\\d{6,14}$" },
        "is_known_contact":         { "type": "boolean" },
        "contact_type": {
          "type": ["string", "null"],
          "enum": ["PERSONAL", "BUSINESS", "UNKNOWN", null]
        },
        "platform_handle":          { "type": ["string", "null"], "maxLength": 200 },
        "alpha_sender_id":          { "type": ["string", "null"], "maxLength": 20 },
        "reported_scam_count":      { "type": ["integer", "null"], "minimum": 0 },
        "intelligence_match": {
          "type": ["object", "null"],
          "properties": {
            "is_known_fraudster":        { "type": "boolean" },
            "associated_campaigns":      { "type": "array", "items": { "type": "string" } },
            "risk_score_from_graph":     { "type": "number", "minimum": 0, "maximum": 1 }
          }
        }
      }
    },
    "ContentBlock": {
      "type": "object",
      "required": ["body", "body_truncated", "character_count", "contains_urls", "contains_attachments"],
      "additionalProperties": false,
      "properties": {
        "body":                        { "type": "string", "maxLength": 50000 },
        "body_truncated":              { "type": "boolean" },
        "original_length":             { "type": ["integer", "null"], "minimum": 0 },
        "subject":                     { "type": ["string", "null"], "maxLength": 500 },
        "language":                    { "type": ["string", "null"], "pattern": "^[a-z]{2,3}(-[A-Z]{2})?$" },
        "language_confidence":         { "type": ["number", "null"], "minimum": 0, "maximum": 1 },
        "script":                      { "type": ["string", "null"] },
        "character_count":             { "type": "integer", "minimum": 0 },
        "word_count":                  { "type": ["integer", "null"], "minimum": 0 },
        "contains_urls":               { "type": "boolean" },
        "contains_attachments":        { "type": "boolean" },
        "url_count":                   { "type": ["integer", "null"], "minimum": 0 },
        "attachment_count":            { "type": ["integer", "null"], "minimum": 0 },
        "has_otp_pattern":             { "type": ["boolean", "null"] },
        "has_urgency_language":        { "type": ["boolean", "null"] },
        "has_authority_claim":         { "type": ["boolean", "null"] },
        "has_financial_mention":       { "type": ["boolean", "null"] },
        "media_type": {
          "type": ["string", "null"],
          "enum": [
            "TEXT", "IMAGE", "AUDIO", "VIDEO", "DOCUMENT",
            "STICKER", "CONTACT_CARD", "LOCATION", "APK",
            "VOICE_NOTE", "UNKNOWN", null
          ]
        },
        "call_transcript":             { "type": ["string", "null"], "maxLength": 100000 },
        "call_transcript_confidence":  { "type": ["number", "null"], "minimum": 0, "maximum": 1 }
      }
    },
    "UrlAnalysisItem": {
      "type": "object",
      "required": [
        "url_id", "raw_url", "normalized_url", "domain", "tld",
        "url_scheme", "is_shortened", "is_ip_address_url",
        "brand_impersonation_detected", "phishing_feed_match",
        "url_risk_score", "analyzed_at"
      ],
      "properties": {
        "url_id":                       { "type": "string", "format": "uuid" },
        "raw_url":                      { "type": "string", "format": "uri", "maxLength": 2048 },
        "normalized_url":               { "type": "string", "maxLength": 2048 },
        "domain":                       { "type": "string", "maxLength": 253 },
        "subdomain":                    { "type": ["string", "null"] },
        "tld":                          { "type": "string" },
        "url_scheme":                   { "type": "string", "enum": ["https", "http", "ftp", "other"] },
        "is_shortened":                 { "type": "boolean" },
        "final_url":                    { "type": ["string", "null"], "maxLength": 2048 },
        "redirect_chain":               { "type": ["array", "null"], "items": { "type": "string" } },
        "redirect_depth":               { "type": ["integer", "null"], "minimum": 0 },
        "domain_age_days":              { "type": ["integer", "null"], "minimum": 0 },
        "registrar":                    { "type": ["string", "null"] },
        "registration_country":         { "type": ["string", "null"], "pattern": "^[A-Z]{2}$" },
        "is_ip_address_url":            { "type": "boolean" },
        "ip_address":                   { "type": ["string", "null"] },
        "ssl_valid":                    { "type": ["boolean", "null"] },
        "ssl_organization":             { "type": ["string", "null"] },
        "brand_impersonation_detected": { "type": "boolean" },
        "impersonated_brand":           { "type": ["string", "null"] },
        "phishing_feed_match":          { "type": "boolean" },
        "phishing_feed_sources":        { "type": ["array", "null"], "items": { "type": "string" } },
        "neo4j_domain_node_id":         { "type": ["string", "null"] },
        "url_risk_score":               { "type": "number", "minimum": 0, "maximum": 1 },
        "url_risk_signals":             { "type": ["array", "null"], "items": { "type": "string" } },
        "analyzed_at":                  { "type": "string", "format": "date-time" }
      }
    },
    "AttachmentAnalysisItem": {
      "type": "object",
      "required": [
        "attachment_id", "filename", "file_extension", "mime_type",
        "file_size_bytes", "sha256_hash", "file_category",
        "is_executable", "malware_hash_match",
        "attachment_risk_score", "analyzed_at"
      ],
      "properties": {
        "attachment_id":             { "type": "string", "format": "uuid" },
        "filename":                  { "type": "string", "maxLength": 255 },
        "file_extension":            { "type": "string", "maxLength": 20 },
        "mime_type":                 { "type": "string", "maxLength": 100 },
        "file_size_bytes":           { "type": "integer", "minimum": 0 },
        "sha256_hash":               { "type": "string", "pattern": "^[a-f0-9]{64}$" },
        "md5_hash":                  { "type": ["string", "null"], "pattern": "^[a-f0-9]{32}$" },
        "backend_storage_key":       { "type": ["string", "null"] },
        "file_category": {
          "type": "string",
          "enum": [
            "PDF", "DOCUMENT", "SPREADSHEET", "IMAGE",
            "AUDIO", "VIDEO", "APK", "ARCHIVE", "EXECUTABLE", "UNKNOWN"
          ]
        },
        "is_executable":             { "type": "boolean" },
        "malware_hash_match":        { "type": "boolean" },
        "malware_hash_sources":      { "type": ["array", "null"], "items": { "type": "string" } },
        "embedded_urls":             { "type": ["array", "null"], "items": { "type": "string" } },
        "embedded_url_count":        { "type": ["integer", "null"], "minimum": 0 },
        "has_macro":                 { "type": ["boolean", "null"] },
        "has_javascript":            { "type": ["boolean", "null"] },
        "pdf_analysis": {
          "type": ["object", "null"],
          "properties": {
            "page_count":                 { "type": "integer", "minimum": 1 },
            "has_form_fields":            { "type": "boolean" },
            "government_seal_detected":   { "type": "boolean" },
            "fake_notice_probability":    { "type": "number", "minimum": 0, "maximum": 1 }
          }
        },
        "apk_analysis": {
          "type": ["object", "null"],
          "properties": {
            "package_name":                    { "type": "string" },
            "declared_permissions":            { "type": "array", "items": { "type": "string" } },
            "is_signed":                       { "type": "boolean" },
            "signing_certificate_hash":        { "type": ["string", "null"] },
            "requests_sms_permission":         { "type": "boolean" },
            "requests_call_log_permission":    { "type": "boolean" },
            "requests_overlay_permission":     { "type": "boolean" }
          }
        },
        "attachment_risk_score":     { "type": "number", "minimum": 0, "maximum": 1 },
        "attachment_risk_signals":   { "type": ["array", "null"], "items": { "type": "string" } },
        "analyzed_at":               { "type": "string", "format": "date-time" }
      }
    },
    "RiskAssessmentBlock": {
      "type": "object",
      "required": [
        "risk_level", "overall_score", "confidence", "threat_categories",
        "is_digital_arrest_scam", "is_authority_impersonation",
        "agent_scores", "intelligence_feed_match", "assessed_at"
      ],
      "properties": {
        "risk_level": {
          "type": "string",
          "enum": ["GREEN", "YELLOW", "RED", "CRITICAL"]
        },
        "overall_score":               { "type": "number", "minimum": 0, "maximum": 1 },
        "confidence":                  { "type": "number", "minimum": 0, "maximum": 1 },
        "threat_categories":           { "type": "array", "items": { "type": "string" }, "minItems": 0 },
        "primary_threat_category":     { "type": ["string", "null"] },
        "is_digital_arrest_scam":      { "type": "boolean" },
        "is_authority_impersonation":  { "type": "boolean" },
        "agent_scores": {
          "type": "array",
          "minItems": 1,
          "items": {
            "type": "object",
            "required": ["agent_id", "agent_version", "score", "confidence"],
            "properties": {
              "agent_id":          { "type": "string" },
              "agent_version":     { "type": "string" },
              "score":             { "type": "number", "minimum": 0, "maximum": 1 },
              "confidence":        { "type": "number", "minimum": 0, "maximum": 1 },
              "signals":           { "type": ["array", "null"], "items": { "type": "string" } },
              "threat_categories": { "type": ["array", "null"], "items": { "type": "string" } },
              "latency_ms":        { "type": ["integer", "null"], "minimum": 0 }
            }
          }
        },
        "aggregation_method": {
          "type": ["string", "null"],
          "enum": ["WEIGHTED_AVERAGE", "MAX_SCORE", "BAYESIAN_COMBINATION", "ENSEMBLE_VOTE", null]
        },
        "neo4j_context_score":         { "type": ["number", "null"], "minimum": 0, "maximum": 1 },
        "intelligence_feed_match":     { "type": "boolean" },
        "false_positive_probability":  { "type": ["number", "null"], "minimum": 0, "maximum": 1 },
        "assessed_at":                 { "type": "string", "format": "date-time" },
        "model_versions":              { "type": ["object", "null"] }
      }
    },
    "InvestigationReportBlock": {
      "type": "object",
      "required": [
        "report_id", "summary", "detailed_explanation",
        "what_happened", "why_its_risky", "what_to_do",
        "recommended_actions", "generated_at"
      ],
      "properties": {
        "report_id":             { "type": "string", "format": "uuid" },
        "summary":               { "type": "string", "maxLength": 150 },
        "detailed_explanation":  { "type": "string", "maxLength": 1000 },
        "what_happened":         { "type": "string", "maxLength": 500 },
        "why_its_risky":         { "type": "string", "maxLength": 500 },
        "what_to_do":            { "type": "string", "maxLength": 200 },
        "recommended_actions": {
          "type": "array",
          "minItems": 1,
          "items": {
            "type": "object",
            "required": ["action_id", "action_type", "label", "description", "is_primary"],
            "properties": {
              "action_id":    { "type": "string" },
              "action_type":  { "type": "string" },
              "label":        { "type": "string", "maxLength": 50 },
              "description":  { "type": "string", "maxLength": 200 },
              "deep_link":    { "type": ["string", "null"] },
              "is_primary":   { "type": "boolean" }
            }
          }
        },
        "evidence": {
          "type": ["array", "null"],
          "items": {
            "type": "object",
            "required": ["evidence_type", "description", "severity"],
            "properties": {
              "evidence_type":  { "type": "string" },
              "description":    { "type": "string", "maxLength": 300 },
              "severity":       { "type": "string", "enum": ["INFO", "WARNING", "CRITICAL"] },
              "source_agent":   { "type": ["string", "null"] }
            }
          }
        },
        "neo4j_relationships": {
          "type": ["array", "null"],
          "items": {
            "type": "object",
            "required": ["node_type", "node_id", "relationship_type"],
            "properties": {
              "node_type":         { "type": "string" },
              "node_id":           { "type": "string" },
              "relationship_type": { "type": "string" },
              "related_entity":    { "type": ["string", "null"] }
            }
          }
        },
        "language":        { "type": ["string", "null"], "default": "en" },
        "generated_at":    { "type": "string", "format": "date-time" }
      }
    }
  }
}
```

---

## 9. Required and Optional Fields Reference

### 9.1 Base Envelope — Required Fields (ALL channels)

| Field | Notes |
|---|---|
| `schema_version` | Must match a published schema version |
| `event_id` | UUID v4. Generated by Android client. |
| `event_type` | From the Event Type Registry. |
| `channel` | Discriminator. Must align with `event_type`. |
| `processing_status` | Initial value always `CAPTURED`. |
| `captured_at` | Set by Android listener at OS event time. |
| `submitted_at` | Set by Android client just before HTTP POST. |
| `device_id` | Anonymized SHA-256 hex, 32–64 chars. |
| `app_version` | Semver of the Sentinel AI app. |
| `source.identifier_hash` | SHA-256 hex, always present. |
| `source.identifier_type` | Identifier type enum. |
| `source.is_known_contact` | Boolean from Android Contacts lookup. |
| `content.body` | Text content. Empty string for calls before transcript. |
| `content.body_truncated` | Boolean. |
| `content.character_count` | Integer. |
| `content.contains_urls` | Boolean. Used for agent routing. |
| `content.contains_attachments` | Boolean. Used for agent routing. |
| `channel_payload` | Non-null object. Schema varies by `channel`. |

### 9.2 Channel Payload — Required Fields by Channel

| Channel | Required Payload Fields |
|---|---|
| SMS | `sms_type`, `message_parts` |
| CALL | `call_direction`, `call_state`, `is_number_unknown`, `transcript_available` |
| WHATSAPP | `chat_id_hash`, `sender_wa_id_hash`, `is_group_chat`, `message_type`, `capture_method` |
| TELEGRAM | `chat_id_hash`, `chat_type`, `message_type`, `capture_method` |
| GMAIL | `message_id`, `from_address_hash`, `from_domain`, `has_html_body` |

### 9.3 Enrichment Blocks — Required Fields When Block is Present

| Block | Required Fields When Present |
|---|---|
| `urls[n]` | `url_id`, `raw_url`, `normalized_url`, `domain`, `tld`, `url_scheme`, `is_shortened`, `is_ip_address_url`, `brand_impersonation_detected`, `phishing_feed_match`, `url_risk_score`, `analyzed_at` |
| `attachments[n]` | `attachment_id`, `filename`, `file_extension`, `mime_type`, `file_size_bytes`, `sha256_hash`, `file_category`, `is_executable`, `malware_hash_match`, `attachment_risk_score`, `analyzed_at` |
| `risk_assessment` | `risk_level`, `overall_score`, `confidence`, `threat_categories`, `is_digital_arrest_scam`, `is_authority_impersonation`, `agent_scores`, `intelligence_feed_match`, `assessed_at` |
| `investigation_report` | `report_id`, `summary`, `detailed_explanation`, `what_happened`, `why_its_risky`, `what_to_do`, `recommended_actions`, `generated_at` |

### 9.4 Fields That Must Never Be Present in Privacy Mode

The following fields MUST be absent (not just null) when `privacy_mode = true`:

- `source.raw_identifier`
- `source.e164_number`
- `source.display_name`
- `source.platform_handle`
- `channel_payload.sender_number_raw`
- `channel_payload.caller_number_raw`
- `channel_payload.from_address_raw`
- `channel_payload.group_name`
- `channel_payload.channel_name`
- `attachments[n].on_device_path`

---

## 10. Validation Rules

### 10.1 Structural Rules (Schema-Level, Enforced by FastAPI Pydantic)

| Rule ID | Rule | Enforcement |
|---|---|---|
| `VAL-001` | `event_id` MUST be a valid UUID v4. | Regex + uuid.UUID parse |
| `VAL-002` | `captured_at` MUST be before or equal to `submitted_at`. | Timestamp comparison |
| `VAL-003` | `submitted_at` MUST be before or equal to `processed_at` (when set). | Timestamp comparison |
| `VAL-004` | `schema_version` MUST be a recognized published version. | Version registry lookup |
| `VAL-005` | `channel` value MUST be consistent with `event_type`. (e.g., `channel = "SMS"` requires `event_type = "sentinel.sms.received"`). | Cross-field validation |
| `VAL-006` | `content.character_count` MUST equal `len(content.body)` as received. | Character count check |
| `VAL-007` | If `content.contains_urls = true`, then `content.url_count` MUST be `>= 1` when provided. | Logical consistency |
| `VAL-008` | If `content.contains_attachments = true`, then `content.attachment_count` MUST be `>= 1` when provided. | Logical consistency |
| `VAL-009` | `source.identifier_hash` MUST be exactly 64 lowercase hex characters. | Regex `^[a-f0-9]{64}$` |
| `VAL-010` | If `source.e164_number` is present, it MUST match `^\\+[1-9]\\d{6,14}$`. | Regex |
| `VAL-011` | `source.country_code`, when present, MUST be a valid ISO 3166-1 alpha-2 code. | Allowlist check |
| `VAL-012` | `ttl_seconds` MUST be between 5 and 300 inclusive. | Range check |
| `VAL-013` | `risk_assessment.overall_score` MUST be consistent with `risk_assessment.risk_level` per the score range table. | Score-level consistency |
| `VAL-014` | Exactly one `recommended_actions[n].is_primary` MUST be `true` per investigation report. | Array uniqueness check |
| `VAL-015` | `attachments[n].sha256_hash` MUST be exactly 64 lowercase hex characters. | Regex |
| `VAL-016` | `attachments[n].file_size_bytes` MUST be `<= 104857600` (100 MB). Backend enforces max upload size. | Range check |
| `VAL-017` | If `attachments[n].file_category = "APK"`, then `attachments[n].apk_analysis` SHOULD be present. | Soft rule (logged if absent) |
| `VAL-018` | If `attachments[n].file_category = "PDF"`, then `attachments[n].pdf_analysis` SHOULD be present. | Soft rule |
| `VAL-019` | `urls[n].url_risk_score` MUST be `<= risk_assessment.overall_score` only when `urls` is the sole risk signal. | Soft consistency check |
| `VAL-020` | `risk_assessment.agent_scores` MUST contain at least one entry when `risk_assessment` is present. | minItems: 1 |

### 10.2 Business Rules (Enforced by ThreatOrchestrator / DecisionEngine)

| Rule ID | Rule |
|---|---|
| `BIZ-001` | If `is_digital_arrest_scam = true`, `risk_level` MUST be `RED` or `CRITICAL`. Never `GREEN` or `YELLOW`. |
| `BIZ-002` | If `malware_hash_match = true` for any attachment, `risk_level` MUST be `CRITICAL`. |
| `BIZ-003` | If `is_executable = true` for any attachment and `malware_hash_match = false`, `risk_level` MUST be at least `RED`. |
| `BIZ-004` | If any URL has `phishing_feed_match = true`, `risk_level` MUST be at least `RED`. |
| `BIZ-005` | If `confidence < 0.5`, `risk_level` MUST be floored to `YELLOW` (never `RED` or `CRITICAL` on low confidence). |
| `BIZ-006` | If `apk_analysis.requests_sms_permission = true` AND `is_executable = true`, trigger `CRITICAL` regardless of other scores. |
| `BIZ-007` | If `dkim_result = "FAIL"` AND `spf_result = "FAIL"` AND `dmarc_result = "FAIL"` for a Gmail event, the email `source` MUST be treated as unverified by all agents. |
| `BIZ-008` | `investigation_report` MUST be generated for all events where `risk_level` is `YELLOW`, `RED`, or `CRITICAL`. |
| `BIZ-009` | `investigation_report` MUST include at least one `recommended_actions` entry. |
| `BIZ-010` | If `call_direction = "INBOUND"` AND `is_number_unknown = true` AND `content.has_authority_claim = true`, DigitalArrestAgent MUST be included in the agent fan-out. |
| `BIZ-011` | Events with `processing_status = "EXPIRED"` MUST NOT have a non-null `risk_assessment`. If an expired event receives late agent results, they are discarded. |
| `BIZ-012` | `event_id` collision MUST result in HTTP 409 Conflict. The duplicate event MUST be rejected; the original is not modified. |
| `BIZ-013` | `alpha_sender_id` values matching known Indian government agency names (CBI, ED, RBI, TRAI, CBISEC, etc.) from a non-DLT source MUST trigger `BIZ-014`. |
| `BIZ-014` | When a sender ID matches a government agency but `has_dlt_header = false`, `is_authority_impersonation` MUST be set to `true` in the risk assessment. |

### 10.3 Privacy Rules (Enforced by Android Client and Backend Ingestion Layer)

| Rule ID | Rule |
|---|---|
| `PRI-001` | Raw PII fields (see [Section 9.4](#94-fields-that-must-never-be-present-in-privacy-mode)) MUST be stripped before transmission when `privacy_mode = true`. |
| `PRI-002` | `attachments[n].on_device_path` MUST NEVER be transmitted to the backend under any circumstance. |
| `PRI-003` | `channel_payload.call_recording_reference` MUST NEVER be transmitted to the backend under any circumstance. |
| `PRI-004` | Backend MUST NOT persist `source.raw_identifier` or `source.e164_number` in PostgreSQL or Neo4j. Only `source.identifier_hash` is stored in graph nodes. |
| `PRI-005` | `backend_storage_key` for attachments MUST expire within 24 hours. FileAgent MUST delete the backing object after analysis. |
| `PRI-006` | Events where `channel = "CALL"` and `content.call_transcript` is non-null MUST NOT be cached in Redis. Transcripts are written directly to PostgreSQL with column-level encryption. |

---

## 11. Versioning Strategy

### 11.1 Principles

The schema follows **Semantic Versioning (SemVer)**: `MAJOR.MINOR.PATCH`

| Change Type | Version Bump | Migration Required | Backwards Compatible |
|---|---|---|---|
| Adding a new optional field | PATCH | No | Yes |
| Adding a new enum value to an existing enum | MINOR | No | Yes (old consumers ignore unknown values) |
| Adding a new required field with a default | MINOR | Soft (backfill old records) | Yes |
| Adding a new optional enrichment block | MINOR | No | Yes |
| Adding a new channel (new enum value + new payload) | MINOR | No | Yes |
| Renaming an existing field | MAJOR | Yes — full migration | No |
| Removing a field | MAJOR | Yes — full migration | No |
| Changing a field type | MAJOR | Yes — full migration | No |
| Adding a new required field without a default | MAJOR | Yes — producer changes required | No |
| Changing enum values (removal or rename) | MAJOR | Yes — full migration | No |

### 11.2 Schema Registry

All published versions of the schema are stored at:

```
shared/schemas/versions/
  v1.0.0/
    base_event.json
    sms_payload.json
    call_payload.json
    whatsapp_payload.json
    telegram_payload.json
    gmail_payload.json
    url_analysis.json
    attachment_analysis.json
    risk_assessment.json
    investigation_report.json
  v1.1.0/
    ...
```

The current active version symlink:

```
shared/schemas/current/ → shared/schemas/versions/v1.0.0/
```

### 11.3 Multi-Version Support Policy

| Scenario | Policy |
|---|---|
| Android app sends `schema_version: "1.0.0"` and backend is on `1.1.0` | Backend MUST accept and process. Old fields remain valid. New optional fields absent. |
| Android app sends `schema_version: "1.1.0"` and backend is on `1.0.0` | Backend MUST reject with HTTP 422 and error code `SCHEMA_VERSION_TOO_NEW`. Client falls back to `1.0.0`. |
| Android app sends unknown `schema_version` | Backend MUST reject with HTTP 422 and error code `SCHEMA_VERSION_UNKNOWN`. |
| MAJOR version mismatch | Both directions MUST reject. Requires coordinated deployment. |

### 11.4 Field Deprecation Process

Fields are never removed immediately. The lifecycle is:

```
ACTIVE → DEPRECATED (announced, still accepted) → REMOVED (MAJOR bump)
```

Deprecated fields are annotated in the schema with `"x-deprecated": true` and a `"x-deprecated-since"` version string. Backend emits a WARNING log for every event that uses a deprecated field. Deprecated fields are supported for a minimum of two MINOR versions before a MAJOR bump removes them.

### 11.5 Migration Strategy

For MAJOR version bumps:

1. New schema is published as `v{N+1}.0.0` alongside `v{N}`.
2. Backend activates a **dual-write period**: accepts both versions, writes to separate tables/collections tagged by version.
3. Android app ships with the new schema but keeps the old version as a fallback for devices on older app versions.
4. After 95% of active devices are on the new app version (measured by `app_version` field in incoming events), the old version is set to DEPRECATED.
5. Old version support is removed after the following MINOR release cycle.

### 11.6 Changelog

| Version | Date | Changes |
|---|---|---|
| `1.0.0` | 2026-06-23 | Initial schema. Supports SMS, CALL, WHATSAPP, TELEGRAM, GMAIL. URL, Attachment, Risk, Investigation enrichment blocks defined. |

---

## 12. Extension Points and Future Channels

### 12.1 Adding a New Channel (Zero Architecture Change)

To add a new channel (e.g., `"SIGNAL"`, `"RCS"`, `"INSTAGRAM_DM"`):

1. Add the new channel name to the `channel` enum in `base_event.json`. (MINOR version bump.)
2. Create a new payload schema file: `shared/schemas/versions/vX.Y.Z/{channel}_payload.json`.
3. Add the channel-specific event types to the `event_type` enum. (MINOR version bump.)
4. Implement a new Agent class extending `BaseAgent` in `backend/app/agents/message/`.
5. Register the agent in `AgentRegistry` with the new channel capability tag.
6. Add the new Android listener/connector in `android/listeners/`.

The ThreatOrchestrator, RiskScoringAgent, DecisionEngine, and ExplanationAgent require zero changes.

### 12.2 Reserved Extension Fields

The following fields are reserved in the base schema for future use and MUST NOT be used for any other purpose:

| Reserved Field | Planned Use | Target Version |
|---|---|---|
| `voice_analysis` | Voice scam analysis block (ASR score, synthetic voice probability) | V2 |
| `deepfake_assessment` | Deepfake voice detection results | V2 |
| `campaign_context` | Cross-user scam campaign metadata from Neo4j V3 graph | V3 |
| `geospatial_signals` | Fraud hotspot proximity data | V3 |
| `visual_analysis` | Computer vision results for image/video analysis | V4 |
| `law_enforcement_flags` | Fields for V4 law enforcement dashboard (read-only, aggregate only) | V4 |

### 12.3 Protobuf Compatibility

The `shared/proto/threat.proto` Protobuf definition is a mechanical translation of this schema. Field numbers in the Protobuf definition are permanently assigned and correspond to fields in this document as follows:

| JSON Field | Proto Field Number |
|---|---|
| `event_id` | 1 |
| `event_type` | 2 |
| `channel` | 3 |
| `processing_status` | 4 |
| `captured_at` | 5 |
| `submitted_at` | 6 |
| `device_id` | 7 |
| `source` | 10 |
| `content` | 11 |
| `channel_payload` | 12 |
| `urls` | 20 |
| `attachments` | 21 |
| `risk_assessment` | 22 |
| `investigation_report` | 23 |
| Fields 30–49 | Reserved for V2 voice/deepfake blocks |
| Fields 50–69 | Reserved for V3 campaign/geospatial blocks |
| Fields 70–89 | Reserved for V4 visual/law enforcement blocks |

When the system migrates from REST/JSON to gRPC/Protobuf (see `REPOSITORY_ARCHITECTURE.md` §5.4), this mapping ensures no field number conflicts and maintains wire-format backward compatibility.

---

*"Think Before You Click. Sentinel Thinks Before You Do."*

---

**Document Version:** 1.0.0
**Schema Status:** Active — Hackathon MVP
**Review Cadence:** Every MINOR version bump requires Architect sign-off.
**Breaking Change Gate:** MAJOR version bumps require Architect + Team Lead sign-off and a dual-write migration plan.
**Last Updated:** 2026-06-23
