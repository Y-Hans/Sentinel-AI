# Privacy

## Privacy Position

Sentinel AI is designed so its primary detection paths run on the Android device. In the repository's default configuration, message content, file content, URL feature vectors, and scan history are not sent to a Sentinel AI backend.

## No User Content Leaves the Device by Default

The following processing is local:

- URL parsing and normalization.
- The 20-rule URL heuristic engine.
- The 15-feature TensorFlow Lite inference pipeline.
- Notification parsing and scam-rule evaluation.
- Feature-toggle and protection preferences.
- Scan, threat, and alert history stored in Room.

The app does not require an account or a Sentinel cloud service for these functions.

## No Tracking

The current dependency set does not include an analytics, advertising, attribution, or user-tracking SDK. The app does not define a tracking identifier or analytics event pipeline.

Debug and operational logs may contain diagnostic information during development. Production builds should keep logging minimal and must not log full message bodies, credentials, or private URL query values.

## Local ML Inference

The TensorFlow Lite model and scaler are packaged in the application assets. The runtime transforms the 15 URL features and executes the model locally. No cloud inference endpoint receives the model input.

This design provides:

- Offline model availability.
- Low inference latency.
- A fixed model version per app build.
- Reduced exposure of browsing and message data.

## Local Storage

Threat and scan records are stored in the app's private Room database. Protection settings are stored in private Android preferences. Other apps cannot access these files through normal Android storage APIs.

Records can include the scanned target, source or sender details available to the app, risk score, explanation, and timestamp. Anyone with access to an unlocked device and the app may be able to view its history.

## Permissions

Sentinel AI requests permissions only for the protection mode that uses them:

| Permission or system access | Purpose |
| --- | --- |
| Notification listener | Read supported notification content for local scam analysis |
| Post notifications | Display risk warnings |
| Contacts | Determine whether a notification sender is a known contact |
| Default browser role | Intercept links for click-time analysis |
| Display over other apps | Present urgent alerts |
| File/media read access | Inspect user-shared supported files where Android requires it |
| Internet | Retrieve optional reputation data or call a configured reputation provider |

Users can disable real-time protection or individual notification, click, and text-selection modes.

## Network Boundaries

The app contains optional reputation integrations, so privacy claims must distinguish local inference from network reputation checks.

### OpenPhish

The default build can download the OpenPhish feed. Matching occurs on the device: the scanned URL is compared with the downloaded feed and is not included in the feed request. As with any network request, the feed service can observe connection metadata such as the device's public IP address and request time.

### VirusTotal

The repository default leaves the VirusTotal API key blank, which disables VirusTotal submission. If a developer supplies a key, the provider posts the scanned URL to VirusTotal for analysis. That configuration is not a strict on-device-only mode and must be disclosed to users.

### Strict Offline Configuration

For a strictly offline build, keep the VirusTotal API key blank and disable the OpenPhish feed URL at build time. Local heuristics, notification rules, file heuristics, and TensorFlow Lite inference continue to operate without those providers.

## Data Minimization

- Grant only the permissions needed for enabled protection modes.
- Keep scan records only as long as they are useful to the user.
- Avoid enabling external URL submission without clear consent and disclosure.
- Do not reuse notification or contact data for advertising, profiling, or unrelated analytics.

