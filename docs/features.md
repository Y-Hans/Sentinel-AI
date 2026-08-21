# Features

## Click-Time Protection

Sentinel AI can register as the Android handler for web links and inspect a destination before browser handoff.

- Accepts `http` and `https` view intents.
- Accepts shared links and selected web text.
- Normalizes the URL without visiting it.
- Applies 20 explainable URL rules and the on-device URL model.
- Shows a result before continuing.
- Removes the browser action for a `BLOCK` decision.

Click protection requires Sentinel AI to be selected as the device's default browser handler. Users can disable this mode independently.

## Notification Scanning

After notification-listener access is granted, Sentinel AI evaluates notifications from its supported-app registry, including major messaging, email, and social communication apps.

The notification pipeline checks:

- Extracted URLs, including shortened and raw-IP links.
- Urgency and account-verification language.
- Financial and credential-related terms.
- Whether the sender can be matched to a known contact when contact permission is available.
- Repeated notifications, which are deduplicated within a short time window.

Elevated results are recorded locally and can produce a warning notification. Message notification analysis uses local rules and does not invoke the URL TFLite model.

## Feature Toggles

The app provides both a master protection switch and focused controls:

| Control | Effect | Requirement |
| --- | --- | --- |
| Real-time protection | Starts or stops guard and monitor services | Standard app access |
| Notification protection | Enables supported notification analysis | Notification-listener access |
| Click protection | Enables inspection before opening web links | Sentinel AI selected as default browser |
| Text selection | Adds analysis for selected URL-like text | No additional privileged permission |

Settings are stored in private Android preferences and persist across app restarts.

## Risk Scoring System

Local URL rules contribute to a score from 0 to 100. The local risk bands are:

| Score | Risk level |
| ---: | --- |
| 0 to less than 30 | `GREEN` |
| 30 to less than 70 | `YELLOW` |
| 70 to less than 90 | `RED` |
| 90 to 100 | `CRITICAL` |

The action decision is intentionally simpler:

| Decision | Trigger | User guidance |
| --- | --- | --- |
| `ALLOW` | No strong local evidence | Continue with normal caution |
| `WARN` | Local score at least 30 | Verify the destination before continuing |
| `BLOCK` | Local score at least 90 | Do not continue |

Reasons are retained so the user can see which signals affected the result.

## Scan History

- Scan history is retained locally, but historical scans DO NOT alter the score of future URLs.
- The final authoritative decision is produced exclusively by local heuristics and on-device ML.

## Manual Scanning and History

The in-app scanner supports link, text, and file scan modes. Completed threat events are written to a Room database through the local threat journal and appear in dashboard, alert, detail, and history views.

History remains on the device and is restored when the app restarts.

