# Phase 2.1 Local URL Heuristic Audit

Repository: `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app`

## 1. Current Architecture Map

### Intent-time URL flow
```text
IntentRouterActivity
    ↓
ScanLoadingActivity
    ↓
IntentThreatAnalyzerImpl
    ├── LinkScanner
    │     ↓
    │   LinkProtectionAgent
    │     ↓
    │   UrlNormalizer
    │     ↓
    │   LinkHeuristicRiskEngine
    │     ↓
    │   ScanResult
    ├── ReputationManagerImpl
    │     ↓
    │   EvidenceCombiner
    │     ↓
    │   ScanResult (enriched)
    └── ThreatEventBus
          ↓
      ThreatJournal / warning UI
```

### Text-selection URL flow
```text
TextSelectionProcessActivity
    ↓
UrlNormalizer
    ↓
ScanLoadingActivity
    ↓
IntentThreatAnalyzerImpl
    ↓
LinkProtectionAgent → LinkHeuristicRiskEngine → ReputationManagerImpl → EvidenceCombiner
```

### Notification-side URL extraction and scam flow
```text
SentinelNotificationListener
    ↓
SupportedAppRegistry
    ↓
NotificationParser
    ↓
NotificationEventBuilder
    ├── URL_REGEX / WWW_REGEX extraction
    ├── UrlAnalysisItem creation
    ├── WhatsAppContentHeuristics
    └── ScamRuleEngine
          ↓
      MessageEvent / scamRiskScore / scamRiskLevel
```

The important takeaway is that the intent-time URL pipeline and the notification pipeline are separate, and they already duplicate some URL parsing and matching logic.

## 2. Existing Classes and Responsibilities

| Class | File | Responsibility | Public methods | Inputs | Outputs | Dependencies | Later action |
|---|---|---|---|---|---|---|---|
| `IntentThreatAnalyzer` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/app/src/main/java/com/sentinel/ai/protection/intent/IntentThreatAnalyzer.kt` | High-level analyzer contract | `analyze(payload)` | `IntentPayload` | `ScanResult` | none | Reuse |
| `IntentThreatAnalyzerImpl` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/app/src/main/java/com/sentinel/ai/protection/intent/IntentThreatAnalyzerImpl.kt` | Orchestrates link/file scan plus reputation enrichment | `analyze(payload)` | `UrlPayload` / `FilePayload` | `ScanResult` | `LinkScanner`, `FileScanner`, `ThreatEventBus`, `ReputationManager` | Extend carefully |
| `IntentRouterActivity` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/app/src/main/java/com/sentinel/ai/protection/intent/IntentRouterActivity.kt` | Entry point that classifies incoming intents | `onCreate()` and private intent helpers | Android `Intent`/`Uri` | Launches `ScanLoadingActivity` | Android framework | Remain mostly unchanged |
| `ScanLoadingActivity` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/app/src/main/java/com/sentinel/ai/protection/intent/ScanLoadingActivity.kt` | UI bridge that runs analysis and renders result | `onCreate()`, `openUrlInBrowser()` | intent extras | UI + browser handoff | `IntentThreatAnalyzer` | Avoid redesign |
| `LinkScanner` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/app/src/main/java/com/sentinel/ai/protection/intent/link/LinkScanner.kt` | Link scan contract | `scan(url)` | raw URL string | `ScanResult` | none | Reuse |
| `LinkProtectionAgent` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/app/src/main/java/com/sentinel/ai/protection/intent/link/LinkProtectionAgent.kt` | Current URL scan entry point | `scan(url)` | raw URL string | `ScanResult` | `LinkHeuristicRiskEngine`, `UrlNormalizer` | Extend, not replace |
| `UrlNormalizer` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/app/src/main/java/com/sentinel/ai/protection/intent/link/UrlNormalizer.kt` | Placeholder normalization hook | `normalize(url)` | raw URL string | same string | none | Extend later |
| `LinkHeuristicRiskEngine` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/LinkHeuristicRiskEngine.kt` | Runs local URL rules, aggregates score, emits explanation, creates `ScanResult` | `analyze(url)`, `toScanResult(url)` | raw URL string | `LinkHeuristicAnalysis` / `ScanResult` | 16 link rules, `LinkHeuristicConfig` | Primary class to extend |
| `LinkHeuristicConfig` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/LinkHeuristicConfig.kt` | Rule thresholds, weights, and keyword lists | data properties only | none | config values | none | Extend carefully |
| `RuleResult` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/RuleResult.kt` | Per-rule local result | data only | rule evaluation | triggered flag, score, explanation, category | none | Reuse |
| `RuleCategory` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/RuleCategory.kt` | Grouping for rules | enum | none | none | none | Reuse |
| `RiskLevelMapper` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/RiskLevelMapper.kt` | Score-to-risk conversion | `Float.toRiskLevel()` | score | `RiskLevel` | none | Reuse |
| `FileHeuristicRiskEngine` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/FileHeuristicRiskEngine.kt` | File analogue to URL engine | `analyze(filename)`, `toScanResult(filename, fileType)` | filename | `FileHeuristicAnalysis` / `ScanResult` | file rules | Leave untouched for URL work |
| `ReputationManager` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/app/src/main/java/com/sentinel/ai/protection/intent/reputation/ReputationManager.kt` | Reputation enrichment contract | `enrich()` | heuristic `ScanResult`, optional `ReputationTarget` | enriched `ScanResult` | none | Reuse |
| `ReputationManagerImpl` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/app/src/main/java/com/sentinel/ai/protection/intent/reputation/ReputationManagerImpl.kt` | Executes providers, times out, filters nulls, combines evidence | `enrich()` | heuristic result + target | enriched `ScanResult` | provider set, `EvidenceCombiner` | Reuse |
| `EvidenceCombiner` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/app/src/main/java/com/sentinel/ai/protection/intent/reputation/EvidenceCombiner.kt` | Fuses heuristic score and provider verdicts into final score and explanation | `combine()` | `ScanResult`, `List<ReputationResult>` | enriched `ScanResult` | `toRiskLevel()` | Avoid changing in Phase 2.1 |
| `ReputationTarget` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/app/src/main/java/com/sentinel/ai/protection/intent/reputation/ReputationTarget.kt` | Provider input target | `Url(url)` | URL string | target object | none | Reuse |
| `ReputationResult` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/app/src/main/java/com/sentinel/ai/protection/intent/reputation/ReputationResult.kt` | Provider verdict payload | data only | provider result | verdict, confidence, reason | none | Reuse |
| `OpenPhishReputationProvider` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/app/src/main/java/com/sentinel/ai/protection/intent/reputation/OpenPhishReputationProvider.kt` | Feed lookup and URL matching | `evaluate()` | URL target | `ReputationResult?` | `HttpClientWrapper`, `JsonParser`, `UrlNormalizer` | Leave unchanged |
| `VirusTotalReputationProvider` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/app/src/main/java/com/sentinel/ai/protection/intent/reputation/VirusTotalReputationProvider.kt` | VirusTotal lookup workflow | `evaluate()` | URL target | `ReputationResult?` | `HttpClientWrapper`, `JsonParser` | Leave unchanged |
| `MockReputationProvider` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/app/src/main/java/com/sentinel/ai/protection/intent/reputation/MockReputationProvider.kt` | Deterministic demo provider | `evaluate()` | URL target | `ReputationResult?` | none | Avoid for production logic |

## 3. Existing URL Parsing and Normalization

### What currently happens

| Location | Behavior |
|---|---|
| `IntentRouterActivity.toIntentPayload()` | Accepts `http`, `https`, `content`, and `file` URIs from `intent.data` or clip data. No normalization beyond type classification. |
| `IntentRouterActivity.getSharedTextPayload()` | Accepts shared text only if it already starts with `http://` or `https://`. Bare domains are rejected here. |
| `TextSelectionProcessActivity.extractUrl()` | Uses `Uri.parse()` if a scheme is present and `host` is non-empty. Otherwise it tries `https?://...` regex extraction, then a domain regex `^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}(/.*)?$` and prepends `https://`. |
| `UrlNormalizer.normalize()` | Currently returns the input unchanged. |
| `LinkHeuristicRiskEngine.parseUri()` | Tries `URI(url)` first, then `URI("https://$url")` if `host` is missing. No host lowercasing, no `www.` removal, no path/query canonicalization. |
| `OpenPhishReputationProvider.normalizeUrl()` | Lowercases, strips `https://`, `http://`, or `//`, strips leading `www.`, strips one trailing slash. Keeps path and query. |
| `OpenPhishReputationProvider.matchesTarget()` | Compares normalized feed entry and target string with exact or safe prefix logic. |
| `NotificationEventBuilder.normalizeUrl()` | Adds `https://` only for `www.`-prefixed strings. |
| `NotificationEventBuilder.extractUrls()` | Uses regexes to find URLs, trims punctuation, then passes the string to `URI(normalizedUrl)`. If parsing fails there is no catch around that call. |
| `VirusTotalReputationProvider.evaluate()` | Validates target URL with `toHttpUrlOrNull()` only. No normalization beyond validation. |

### Feature-by-feature state

| Capability | Current state |
|---|---|
| Missing scheme | Partially handled. Text selection adds `https://` for some bare domains; link engine fallback tries `https://` during parsing. |
| Lowercase hosts | Only OpenPhish matching lowercases. Link engine does not canonicalize host. |
| Remove `www.` | Only OpenPhish matching removes it. |
| Trailing slash removal | Only OpenPhish matching removes one trailing slash. |
| Parse hostname | Yes, via `java.net.URI` in link engine and notification builder, and Android `Uri.parse()` in intent routing. |
| Parse port | Not explicitly used in heuristics. |
| Parse path | Yes, in several link rules. |
| Parse query parameters | Yes, via `rawQuery` in link rules and OpenPhish matching. |
| Parse fragments | Not used. |
| Handle malformed URLs | Partially. Link engine falls back and may return green; VirusTotal rejects invalid URLs; notification builder may throw. |
| Handle raw IPv4 | Yes, in `IpAddressRule` and notification-side `ScamRuleEngine`. |
| Handle IPv6 | Yes in `IpAddressRule`, but not in notification side. |
| Handle Unicode domains | Not explicitly handled. |
| Handle punycode | Yes, `PunycodeRule` and a `xn--` exclusion in `RepeatedHyphensRule`. |
| Handle URL encoding | Partially, only by counting `%xx` sequences and by OpenPhish feed matching with string comparisons. |
| Embedded URLs | Not explicitly handled in intent analyzer. |
| Use `java.net.URI` | Yes, in the link engine, OpenPhish, and notification builder. |
| Use Android `Uri` | Yes, in intent routing and loading UI. |
| Use `HttpUrl` | Yes, only for VirusTotal validation and OpenPhish feed URL building. |
| Regex only | Used for text selection and notification extraction, but not as the only parser. |

### Contradictions

* `UrlNormalizer` is a no-op, but `OpenPhishReputationProvider` still contains a private normalization function that performs real work.
* `IntentRouterActivity` rejects bare domains in shared text, while `TextSelectionProcessActivity` accepts some bare domains and prepends `https://`.
* `LinkHeuristicRiskEngine` parses with `java.net.URI` but does not canonicalize the host; `OpenPhishReputationProvider` lowercases and strips prefixes.
* `NotificationEventBuilder` derives `UrlAnalysisItem` fields from its own regex and `URI` parsing, not from the intent pipeline.

## 4. Existing Heuristic Signals

The current local URL analyzer is `LinkHeuristicRiskEngine`, which registers 16 rules.

| Rule | File | Trigger condition | Weight | Explanation | Category | Current limitations |
|---|---|---|---|---|---|---|
| `SuspiciousTldRule` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/rules/link/SuspiciousTldRule.kt` | Host ends with one of the configured suspicious TLDs | 15 | `Uses .<tld> domain` | `DOMAIN` | Host-only check; no public-suffix awareness |
| `IpAddressRule` | same directory | Host matches IPv4 regex or IPv6 regex | 25 | `Uses IP address instead of domain` | `DOMAIN` | IPv6 regex is broad; no bracket normalization |
| `ExcessiveSubdomainsRule` | same | Host dot count `> subdomainThreshold` | 10 | `Contains excessive subdomains` | `DOMAIN` | Counts all dots, including host dots in non-subdomain cases |
| `RandomHostnameRule` | same | Host entropy `> 3.8` and length `>= 8` | 10 | `Uses a random-looking hostname` | `DOMAIN` | Entropy can flag benign random-looking CDN hosts |
| `RepeatedHyphensRule` | same | Host contains `--` and is not punycode, or hyphen count `> 2` | 10 | `Contains repeated or multiple hyphens in domain` | `DOMAIN` | Still flags some legitimate hyphen-heavy domains |
| `ExcessiveDigitsRule` | same | Host digit count `> 5` or digit/letter ratio `> 0.3` | 10 | `Contains excessive digits in the domain` | `DOMAIN` | Ratio may be noisy on short hosts |
| `PunycodeRule` | same | Host starts with `xn--` or contains `.xn--` | 15 | `Punycode domain name detected` | `DOMAIN` | Does not decode or inspect Unicode spoofing depth |
| `LongUrlRule` | same | URL length `> 150` | 5 | `URL is unusually long` | `URL_STRUCTURE` | Uses raw string length only |
| `DeepPathRule` | same | Path segment count `> 4` | 5 | `URL path is deeply nested` | `URL_STRUCTURE` | No path-length metric |
| `LongFilenameRule` | same | Last path segment length `> 30` | 5 | `URL contains an unusually long filename` | `URL_STRUCTURE` | No extension-specific logic |
| `ExcessiveQueryParametersRule` | same | Query parameter count `> 5` | 10 | `URL has excessive query parameters` | `URL_STRUCTURE` | Uses raw query split only |
| `EncodedCharactersRule` | same | At least 3 `%xx` substrings in the raw URL | 10 | `URL contains many encoded characters` | `URL_STRUCTURE` | Does not distinguish benign encoding from obfuscation |
| `TrackingParameterRule` | same | Parameter name is one of the tracking names or starts with `utm_` | 0 | `URL contains tracking parameters` | `URL_STRUCTURE` | Triggered signal has no score contribution |
| `RedirectParameterRule` | same | Query parameter name is one of the redirect names and value contains `http` / encoded `http` | 15 | `URL contains a redirect parameter placeholder signal` | `URL_STRUCTURE` | Only detects obvious redirect strings |
| `BrandImpersonationRule` | same | Host contains a protected brand, or a close look-alike to a protected brand, while not on an official domain | 30 or 25 | `Possible <brand> brand impersonation` or `Possible <brand> look-alike domain` | `BRAND_IMPERSONATION` | Brand list is finite and manual |
| `SocialEngineeringRule` | same | Raw URL contains a configured keyword; if keyword is in host score is 20, otherwise score is 2 | 20 or 2 | `Uses social engineering keyword in domain/path/query` | `SOCIAL_ENGINEERING` | Path/query matches are heavily discounted and keyword list is partial |

### Keyword/config inventory

* Suspicious TLDs: `live`, `click`, `top`, `xyz`, `online`, `info`, `vip`, `fit`, `gq`, `cf`, `tk`, `ml`, `ga`, `work`, `club`, `buzz`, `support`, `security`, `update`, `verify`, `download`, `bid`, `loan`, `men`, `win`, `stream`
* Social engineering keywords: `banking`, `payment`, `verification`, `verify`, `login`, `password`, `otp`, `aadhaar`, `pan`, `wallet`, `upi`, `reward`, `lottery`, `ipl-ticket`, `ipl`, `ticket`, `gift`, `urgent`, `account`, `secure`, `signin`, `free-gift`, `cashback`, `refund`, `claim`, `win-money`
* Brand list: google, paypal, amazon, apple, instagram, facebook, whatsapp, telegram, microsoft, netflix

## 5. Existing Score and Verdict Model

### Local URL scoring

* The link engine is additive.
* Each rule contributes a non-negative float, then the sum is clamped to `0..100`.
* Risk thresholds are:
  * `>= 90` -> `CRITICAL`
  * `>= 70` -> `RED`
  * `>= 30` -> `YELLOW`
  * otherwise -> `GREEN`
* Threshold source: `RiskLevelMapper.kt`.

### Important behaviors

* Duplicate signals do not double-count within a single rule because each rule can trigger only once per scan.
* Correlated rules can stack freely. There is no per-category cap, no host-level cap, and no correlation-aware dampening.
* A single weak rule cannot usually produce `YELLOW` by itself, because the lightest scores are `2`, `5`, or `10`, but multiple weak rules can still accumulate.
* Scoring is deterministic for the same input string and config.
* `LinkHeuristicRiskEngine.toScanResult()` adds a random UUID and current timestamp, but those fields do not affect scoring.

### How local scores affect final result

* `LinkProtectionAgent.scan()` returns a `ScanResult` built from the link analysis score and explanation.
* `IntentThreatAnalyzerImpl.analyze()` passes that `ScanResult` into `ReputationManager.enrich()`.
* `EvidenceCombiner` treats the heuristic score as the baseline probability for further fusion.

## 6. Evidence and Explainability

### Current evidence model

| Type | Exists? | Notes |
|---|---|---|
| Raw integer score | Yes | `ScanResult.riskScore` is a float score in `0..100`. |
| List of reasons | Yes | `ScanResult.explanation` is a single formatted string, not a list. |
| Structured evidence | Partially | `LinkHeuristicAnalysis.ruleResults` and `RuleResult` are structured internally, but they are not propagated into `ScanResult`. |
| Rule IDs | Partially | Rule IDs exist in `RuleResult`-adjacent metadata by rule class, but they are not surfaced in the final result. |
| Severity | Yes | `RiskLevel` and `ReputationVerdict` provide coarse severity. |
| Confidence | Yes, only for providers | `ReputationResult.confidence`; local heuristics do not have confidence. |
| Metadata | Yes, partly | `triggeredRuleCount` exists in `LinkHeuristicAnalysis`, but it is not exported in the final `ScanResult`. |
| Boolean flags | Yes, elsewhere | `UrlAnalysisItem` stores booleans such as `isShortened` and `isIpAddressUrl`. |

### Explainability loss points

* `LinkHeuristicRiskEngine.analyze()` computes `ruleResults`, but `toScanResult()` throws away the list.
* `buildExplanation()` truncates to the first 4 triggered rule explanations.
* `ScanResult` has one explanation string, so the UI cannot directly render per-rule evidence.
* `ScanLoadingActivity` shows only the formatted explanation and percent score.
* `ThreatJournal` and `WarningUiModel` also consume only the final explanation text.

## 7. EvidenceCombiner Integration

### Inputs

* `heuristicResult: ScanResult`
* `reputationEvidence: List<ReputationResult>`

### Behavior

* If the evidence list is empty, it returns the heuristic result unchanged.
* It converts the baseline heuristic score to `pHeuristic = riskScore / 100`.
* For each provider result:
  * `MALICIOUS` contributes `0.90 * confidence`
  * `SUSPICIOUS` contributes `0.60 * confidence`
  * `CLEAN` and `UNKNOWN` contribute `0`
* It combines evidence with a complementary probability model.
* If a `CLEAN` verdict is present, it discounts the fused score:
  * if fused score `>= 70`, max discount is `20%`
  * if fused score `>= 30`, max discount is `50%`
  * otherwise, max discount is `100%`
* It then maps the combined score back to `RiskLevel`.
* `UNKNOWN` does not affect the score, but it can still appear in provider-specific metadata if a provider returns it.
* Provider failures never reach the combiner because `ReputationManagerImpl` drops `null` results.

### Limitations

* The combiner is score-only; it cannot reason over rule-level evidence.
* Local heuristics and online providers are not weighted differently beyond the hardcoded probability mapping and clean-discount behavior.
* The combiner returns a copied `ScanResult`, so any preexisting explanation is merged into a string, not structured data.

## 8. Existing Unit Tests

### App module

| Test class | File | Test count | Coverage | Missing behavior | Internet? | Deterministic? |
|---|---|---:|---|---|---|---|
| `LinkHeuristicRiskEngineTest` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/app/src/test/java/com/sentinel/ai/protection/intent/heuristic/LinkHeuristicRiskEngineTest.kt` | 2 | Clean URL -> green; suspicious brand URL -> multiple signals | No malformed URL cases, no per-rule tests | No | Yes |
| `FileHeuristicRiskEngineTest` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/app/src/test/java/com/sentinel/ai/protection/intent/heuristic/FileHeuristicRiskEngineTest.kt` | 2 | Clean filename; fake executable document | No URL-related coverage | No | Yes |
| `EvidenceCombinerTest` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/app/src/test/java/com/sentinel/ai/protection/intent/reputation/EvidenceCombinerTest.kt` | 6 | Empty evidence, malicious, suspicious, multiple suspicious, clean discount, clean cap | No mixed clean+malicious scenario; no UNKNOWN-specific assertions | No | Yes |
| `ReputationManagerImplTest` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/app/src/test/java/com/sentinel/ai/protection/intent/reputation/ReputationManagerImplTest.kt` | 5 | Null target, no providers, successful provider, exception recovery, timeout | No target-type filtering beyond URL path | No | Yes |
| `OpenPhishReputationProviderTest` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/app/src/test/java/com/sentinel/ai/protection/intent/reputation/OpenPhishReputationProviderTest.kt` | 29 | Feed disabled, offline, HTTP errors, empty/malformed body, exact and prefix matches, false-positive boundaries, comments, blank lines, API key handling, metadata | No unicode/punycode cases; no fragment handling; no encoded path edge cases | No external internet; local MockWebServer only | Yes |
| `VirusTotalReputationProviderTest` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/app/src/test/java/com/sentinel/ai/protection/intent/reputation/VirusTotalReputationProviderTest.kt` | 17 | API key gating, success, suspicious, unknown, malformed JSON, offline, timeout, HTTP error, invalid URL, exception safety | No punycode or normalization cases | No external internet; local MockWebServer only | Yes |
| `MockReputationProviderTest` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/app/src/test/java/com/sentinel/ai/protection/intent/reputation/MockReputationProviderTest.kt` | 5 | Deterministic verdict mapping for synthetic URLs | Not representative of production behavior | No | Yes |

### Agents module

| Test class | File | Test count | Coverage | Missing behavior | Internet? | Deterministic? |
|---|---|---:|---|---|---|---|
| `ScamRuleEngineTest` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/agents/src/test/java/com/sentinel/ai/agents/whatsapp/ScamRuleEngineTest.kt` | 2 | Shortener/IP detection plus message-term scoring | No IPv6, no punycode, no scheme/port tests | No | Yes |
| `WhatsAppEventBuilderTest` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/agents/src/test/java/com/sentinel/ai/agents/whatsapp/WhatsAppEventBuilderTest.kt` | 6 | URL extraction, truncation, privacy-related payload shaping | No malformed URL regression tests, no internationalized URLs | No | Yes |
| `WhatsAppNotificationParserTest` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/agents/src/test/java/com/sentinel/ai/agents/whatsapp/WhatsAppNotificationParserTest.kt` | 2 | Field extraction, group/forwarded detection | No URL parsing edge cases | No | Yes |
| `SupportedAppRegistryTest` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/agents/src/test/java/com/sentinel/ai/agents/registry/SupportedAppRegistryTest.kt` | 1 | Supported package list | Not URL-specific | No | Yes |

### Core module

| Test class | File | Test count | Coverage | Missing behavior | Internet? | Deterministic? |
|---|---|---:|---|---|---|---|
| `LinkEventTest` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/core/src/test/java/com/sentinel/ai/core/event/schema/LinkEventTest.kt` | 4 | Link event creation/validation and URL score range validation | No analyzer behavior | No | Yes |
| `MessageEventTest` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/core/src/test/java/com/sentinel/ai/core/event/schema/MessageEventTest.kt` | 4 | Message event validity and URL event exclusion | No analyzer behavior | No | Yes |
| `EventValidatorTest` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/core/src/test/java/com/sentinel/ai/core/event/schema/EventValidatorTest.kt` | 4 | Privacy mode and risk assessment validation | No URL heuristics | No | Yes |
| `CommunicationEventTest` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/core/src/test/java/com/sentinel/ai/core/event/schema/CommunicationEventTest.kt` | 3 | Event wrappers and TTL validation | No analyzer behavior | No | Yes |
| `AttachmentEventTest` | `/C:/Users/Harshita/OneDrive/Desktop/sentinel-ai/android-app/core/src/test/java/com/sentinel/ai/core/event/schema/AttachmentEventTest.kt` | 4 | Attachment event creation and hash validation | No URL heuristics | No | Yes |

## 9. Duplicate or Conflicting Logic

### URL parsing / normalization duplication

| Location | What it duplicates |
|---|---|
| `TextSelectionProcessActivity` | URL detection and bare-domain handling |
| `LinkHeuristicRiskEngine.parseUri()` | URL parsing and fallback scheme insertion |
| `OpenPhishReputationProvider.normalizeUrl()` | Lowercasing, scheme stripping, `www.` stripping, trailing slash stripping |
| `NotificationEventBuilder.extractUrls()` | URL regex extraction, punctuation trimming, URI parsing |
| `NotificationEventBuilder.normalizeUrl()` | Adds `https://` for `www.` |

### Signal duplication

| Signal | Duplicate location |
|---|---|
| Shortened URL | `NotificationEventBuilder` builds `UrlAnalysisItem.isShortened`; `ScamRuleEngine` consumes it |
| Raw IP URL | `NotificationEventBuilder` sets `isIpAddressUrl`; `ScamRuleEngine` consumes it; intent analyzer has its own `IpAddressRule` |
| Social-engineering keywords | `LinkHeuristicConfig.socialEngineeringKeywords`, `ScamRuleEngine` urgency/financial/credential terms, `WhatsAppContentHeuristics` terms |
| Brand impersonation | `LinkHeuristicConfig.brandOfficialDomains`/lookalikes and schema fields like `brandImpersonationDetected`/`impersonatedBrand` |

### Which implementation looks authoritative

* For intent-time local URL analysis, `LinkHeuristicRiskEngine` plus `LinkProtectionAgent` is the authoritative path because it is what `IntentThreatAnalyzerImpl` uses.
* For notification URL extraction, `NotificationEventBuilder` is the authoritative path because it creates `UrlAnalysisItem` and message events.
* For OpenPhish feed matching, `OpenPhishReputationProvider.normalizeUrl()` is the authoritative matching routine, not `UrlNormalizer`.
* `BrowserLauncher` and `LinkAgentCoordinator` are present but currently inert; they should not become the source of truth.

## 10. Gap Analysis

| Capability | Status | Notes |
|---|---|---|
| Raw IPv4/IPv6 | Implemented | `IpAddressRule` handles both, though IPv6 handling is regex-based. |
| Punycode | Implemented | `PunycodeRule` plus `RepeatedHyphensRule` excludes `xn--`. |
| Excessive subdomains | Implemented | `ExcessiveSubdomainsRule`. |
| Suspicious TLD | Implemented | `SuspiciousTldRule`. |
| Long hostname | Missing | No dedicated host-length rule. |
| Repeated hyphens | Implemented | `RepeatedHyphensRule`. |
| Numeric-heavy host | Partially implemented | `ExcessiveDigitsRule` approximates the idea via digit count and ratio. |
| Domain impersonation | Implemented | `BrandImpersonationRule`. |
| HTTP | Missing | No scheme-based risk rule. |
| Non-standard port | Missing | No port rule. |
| Long URL | Implemented | `LongUrlRule`. |
| Long path/query | Partially implemented | `DeepPathRule` and `ExcessiveQueryParametersRule` exist, but there is no path-length/query-length rule. |
| Excessive query parameters | Implemented | `ExcessiveQueryParametersRule`. |
| Embedded URL | Missing | No dedicated nested-URL/URL-in-URL rule. |
| Redirect parameters | Implemented | `RedirectParameterRule`. |
| `@` character | Missing | No dedicated authority-userinfo rule. |
| Suspicious encoding | Partially implemented | `EncodedCharactersRule` counts `%xx` sequences only. |
| Social-engineering terms | Partially implemented | Good starting set, but not complete against the requested list. |
| URL shorteners | Implemented elsewhere but not reused | Notification-side `SHORTENER_DOMAINS` and `UrlAnalysisItem.isShortened`, but no intent-time link rule. |
| APK/executable/archive downloads | Partially implemented elsewhere | File heuristics cover attachments; intent URL analysis does not inspect downloads. |
| Invalid URL handling | Partially implemented | Some entry points reject invalid input, but there is no unified invalid-url verdict path. |
| False-positive protection | Partially implemented | OpenPhish and brand rules have careful checks, but there is no unified FP policy. |
| Structured evidence | Missing | Final `ScanResult` still carries one explanation string only. |
| Score caps | Implemented only at global score level | Overall score is capped at `100`, but not by category or correlation. |
| Correlated-signal caps | Missing | No dampening for stacked related rules. |

### Key technical debts surfaced by the audit

* `UrlNormalizer` is still a placeholder.
* `LinkHeuristicAnalysis.ruleResults` is computed but discarded.
* `NotificationEventBuilder` can throw on malformed URL parsing because `URI(normalizedUrl)` is not protected.
* `MockReputationProvider` is included in the production `ReputationModule` provider set, which is risky for real scoring behavior.

## 11. Recommended Phase 2.2 Scope

The safest next step is to extend the existing link heuristic path rather than introduce a new analyzer.

### Extend these existing classes

* `LinkHeuristicRiskEngine`
* `LinkHeuristicConfig`
* `LinkProtectionAgent`

### Reuse these utilities

* `RuleResult`
* `RuleCategory`
* `RiskLevelMapper`
* `LinkHeuristicAnalysis`
* `ScanResult`

### Do not touch yet

* `EvidenceCombiner`
* `ReputationManagerImpl`
* `OpenPhishReputationProvider`
* `VirusTotalReputationProvider`
* `IntentRouterActivity`
* `ScanLoadingActivity`
* Notification protection flow

### Smallest safe Phase 2.2 shape

* First, add any missing signal detection only inside the current `LinkHeuristicRiskEngine` rule list.
* Preserve the additive score model and current thresholds for now.
* If a shared URL normalization helper is needed, make it a tiny utility that does not change the public flow yet.
* Add tests before any scoring changes:
  * invalid URL handling
  * bare domain / scheme handling
  * each new rule’s positive and false-positive case
  * a regression test proving existing scores do not change for current fixtures

### Architectural changes to avoid in Phase 2.2

* Do not create a second local URL analyzer.
* Do not move notification rules into the intent pipeline yet.
* Do not change the final `ScanResult` shape in this phase.
* Do not alter reputation fusion or provider weighting yet.

## 12. Commands Run and Validation Results

### Validation commands

1. `./gradlew.bat :app:testDebugUnitTest --tests com.sentinel.ai.protection.intent.heuristic.LinkHeuristicRiskEngineTest --tests com.sentinel.ai.protection.intent.heuristic.FileHeuristicRiskEngineTest --tests com.sentinel.ai.protection.intent.reputation.EvidenceCombinerTest --tests com.sentinel.ai.protection.intent.reputation.ReputationManagerImplTest --tests com.sentinel.ai.protection.intent.reputation.OpenPhishReputationProviderTest --tests com.sentinel.ai.protection.intent.reputation.VirusTotalReputationProviderTest --tests com.sentinel.ai.protection.intent.reputation.MockReputationProviderTest`
   * Result: initially blocked by invalid `JAVA_HOME`, then rerun successfully with `JAVA_HOME` pointed at Android Studio JBR and Gradle wrapper download allowed.
   * Final result: `BUILD SUCCESSFUL in 37s`
2. `./gradlew.bat :agents:testDebugUnitTest --tests com.sentinel.ai.agents.whatsapp.ScamRuleEngineTest --tests com.sentinel.ai.agents.whatsapp.WhatsAppEventBuilderTest --tests com.sentinel.ai.agents.whatsapp.WhatsAppNotificationParserTest --tests com.sentinel.ai.agents.registry.SupportedAppRegistryTest`
   * Result: `BUILD SUCCESSFUL in 1m 19s`
3. `./gradlew.bat :core:testDebugUnitTest --tests com.sentinel.ai.core.event.schema.LinkEventTest --tests com.sentinel.ai.core.event.schema.MessageEventTest --tests com.sentinel.ai.core.event.schema.EventValidatorTest --tests com.sentinel.ai.core.event.schema.CommunicationEventTest --tests com.sentinel.ai.core.event.schema.AttachmentEventTest`
   * Result: `BUILD SUCCESSFUL in 1m 22s`
4. `./gradlew.bat :app:compileDebugKotlin`
   * Result: `BUILD SUCCESSFUL in 36s`

### Notes on validation

* The app test command implicitly compiled the app module before executing tests.
* OpenPhish and VirusTotal tests use `MockWebServer` and local stubs only; no external internet was used.
* No production code was modified during this audit.
