# Future Scope

## Multilingual & Regional Scam Detection
- Expand lexical and TF-IDF vocabulary banks to include regional Indian languages (Hindi, Marathi, Tamil, Telugu, Bengali, Gujarati) and transliterated Hinglish.
- Integrate lightweight on-device character n-gram models for script-mixed phishing attacks.

## Advanced Browser Extensions
- Port the pure-Kotlin tree evaluation engine and feature extractors to WebAssembly / Kotlin Multiplatform for Chrome, Edge, and Firefox browser extensions.
- Provide real-time page DOM and form action analysis before credential submission.

## Cross-Platform Mobile & Desktop
- Expand shared Kotlin Multiplatform (`core`) library to iOS and desktop clients.
- Implement platform-specific intent interception and notification hooks while preserving the exact same `RiskFusionEngine` and ML models.

## Offline Threat Intelligence Sync
- Implement privacy-preserving Bloom filters for known malicious domain and sender hash lists.
- Support scheduled delta updates over metered connections without transmitting telemetry or scan logs.
