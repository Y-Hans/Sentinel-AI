# Security Architecture

## Threat Analysis & Evidence Generation

Sentinel AI employs a multi-layered approach to threat detection, gathering evidence from multiple independent vectors without modifying source payloads. The evidence generation process is isolated, deterministic, and read-only.

1. **URL Heuristics**: Analyzes link structures for suspicious patterns (e.g. typosquatting, credential harvesting keywords, obscured IP addresses, excessive subdomains).
2. **On-Device URL-ML Champion V7**: A pure-Kotlin 67-feature HistGradientBoosting tree ensemble running locally on the device with safe-brand domain clamping ($\tau = 0.22588723$).
3. **Message Heuristics & Content Analysis**: Evaluates incoming notification text for urgency language, financial demands, fake legal threats, utility disconnection coercion, and credential harvesting patterns.
4. **On-Device Messages-ML Champion V2**: A pure-Kotlin 2,070-dimensional multimodal pipeline combining 70 deterministic tabular features with dual sublinear TF-IDF vectorization (1,500 word + 500 char_wb n-grams), standardized scaling, and a 3-class HistGradientBoosting tree classifier ($\tau = 0.704$).
5. **Sender Intelligence**: Evaluates DLT header metadata, entity identity, transactional context, and known-contact status.

---

## Risk Fusion Engine

The `RiskFusionEngine` is the core component that aggregates evidence and determines the final risk decision.

- **Invariant**: The `RiskFusionEngine` is the *sole final risk authority*. Intermediate heuristic or ML scores are strictly contributing evidence and never constitute final security decisions.
- **Scoring**: Compounded base scoring from payload threat severity, contextual multipliers for multi-vector attacks, and calibrated dampening for verified transactional contexts.
- **Decisions**:
  - `ALLOW` (Score $< 40$): No critical threat evidence found (`GREEN`).
  - `WARN` (Score $40\text{--}89$): Suspicious or elevated risk signals detected (`YELLOW` / `RED`).
  - `BLOCK` (Score $\ge 90$): Critical phishing, scam, credential-theft threat, or ML-unavailable failure detected (`CRITICAL`).

---

## Browser Routing & Safe-Link Fast Path

When a user interacts with a web link, Sentinel AI intercepts the intent and executes real-time analysis:

- **Browser Selection**: Defers to the user's preferred browser (stored via `BrowserPreferenceRepository`). If no preference is configured, an "Ask Every Time" chooser is presented, filtering out Sentinel AI to prevent intent loops.
- **Safe-Link Fast Path**: `GREEN + ALLOW` is the **ONLY** automatic browser fast path. If a link evaluates to `GREEN + ALLOW`, it is seamlessly handed off to the browser.
- **Non-GREEN / Elevated Risk Handling**: For `WARN` results, a warning screen is presented with full evidence explainability and an explicit "Continue Anyway" override option. For `BLOCK` results, continuation actions are completely disabled, enforcing a hard block.

---

## Local & Offline Operation

Sentinel AI is designed for complete offline resilience:
- **Zero Cloud / Network Dependency**: All feature extraction, tokenization, TF-IDF vectorization, tree evaluation, and risk fusion occur entirely on-device.
- **Zero Remote Data Exfiltration**: URLs and message notification bodies never leave the device.
- **Fail-Safe Invariant**: If model evaluation or asset loading encounters an unexpected error (missing asset, corrupted JSON, runtime exception), the analyzer emits explicit `CRITICAL` ML-unavailable evidence (`EvidenceSeverity.CRITICAL`, confidence `1.0f`). The `RiskFusionEngine` triggers critical indicator escalation ($\ge 90$), producing an immediate `BLOCK` decision. **ML failure is explicit security evidence, not silent absence of evidence.** ML failure can never silently degrade to `ALLOW`.

---

## Security Tips

During scan loading phases, the app surfaces general security awareness tips. These tips are decoupled from payload scoring and serve educational purposes.
