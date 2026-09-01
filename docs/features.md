# Features

## Click-Time Protection

Sentinel AI registers as an Android default browser candidate to intercept, inspect, and adjudicate links in real time before hand-off:

- **Intent Interception:** Accepts `http` and `https` view intents, shared links, and selected web text.
- **Normalization:** Cleans and normalizes URLs without network connections.
- **Hybrid Threat Analysis:**
  - Evaluates 20 explainable lexical and structural heuristic rules.
  - Runs **URL-ML Champion V7** (67 continuous features, 350 gradient boosted trees, safe-brand clamping).
- **Risk Fusion:** Fuses heuristic and ML evidence into a single authoritative `ScanResult`.
- **Enforcement & Fast Path:**
  - `GREEN + ALLOW`: Seamlessly opens the destination in the user's preferred browser (Safe-Link Fast Path).
  - `WARN`: Presents a detailed threat breakdown with an optional "Continue Anyway" override.
  - `BLOCK`: Hard blocks the destination; completely disables browser navigation.

---

## Notification Scanning

With notification-listener access granted, Sentinel AI monitors incoming alerts from supported communication applications (e.g. WhatsApp, SMS, Telegram, banking/email apps):

- **Sender Intelligence:** Analyzes DLT header formats, entity names, short codes, and known-contact status.
- **Text Normalization:** Applies NFKD Unicode normalization, zero-width stripping, and homoglyph mapping.
- **Multimodal Message ML:**
  - Runs **Messages-ML Champion V2** (70 deterministic tabular features + 1,500 word TF-IDF + 500 char_wb TF-IDF, standard scaling, 309 HistGradientBoosting trees).
- **Explainable Scam Rules:** Flags urgency pressure, account suspension threats, fake legal notices, power disconnection coercion, and OTP/credential harvesting.
- **Deduplication:** Suppresses duplicate notifications within a sliding time window.
- **Warning Dispatch:** For elevated threats (`WARN` / `BLOCK`), dispatches immediate system warning alerts.

---

## Manual In-App Scanner

The manual scanner interface provides instant on-demand analysis:
- **Link Scan:** Full URL analysis with detailed feature breakdown.
- **Text Scan:** Inspects raw SMS, chat snippets, or emails for social engineering signals.
- **File Scan:** Validates document extensions, hash indicators, and APK threats.

---

## Protection Controls & Preferences

Sentinel AI provides granular toggles for all protection features:

| Control | Functionality | Prerequisite |
| --- | --- | --- |
| **Real-Time Protection** | Master switch for background protection services | Standard app installation |
| **Notification Protection** | Enables active monitoring of incoming notifications | Android Notification Listener Access |
| **Click-Time Protection** | Intercepts link clicks prior to browser loading | Default Browser set to Sentinel AI |
| **Text Selection Scan** | Adds "Scan with Sentinel" option to text selection menu | Standard Android context menu |

All settings persist locally across device reboots in private preferences.

---

## Risk Scoring System

The `RiskFusionEngine` evaluates all emitted `ThreatEvidence` items to compute a normalized compound score ($0\text{--}100$):

| Score Band | Risk Level | Meaning |
| ---: | :--- | :--- |
| **$0\text{--}39$** | `GREEN` | Clean or verified safe destination / message |
| **$40\text{--}69$** | `YELLOW` | Low-to-moderate suspicious indicators detected |
| **$70\text{--}89$** | `RED` | High-confidence scam or phishing signals detected |
| **$90\text{--}100$** | `CRITICAL` | Confirmed malicious payload, credential theft attempt, or ML-unavailable failure |

Authoritative Action Decisions:
- **`ALLOW` (Score $< 40$):** Safe to proceed.
- **`WARN` (Score $40\text{--}89$):** User alerted with explainable evidence; requires user confirmation.
- **`BLOCK` (Score $\ge 90$):** High severity; navigation disabled.

---

## Local Threat Journal & History

- All scan results and threat events are committed directly to a local **Room SQLite** database via `ThreatJournal`.
- Scans and threats persist across app lifecycles.
- **Privacy Guarantee:** History is stored strictly on-device; historical records never alter future scan scores.
