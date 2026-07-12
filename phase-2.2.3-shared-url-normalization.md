# Phase 2.2.3 Shared URL Normalization

Date: 2026-07-10  
Project: `android-app`  
Scope: behavior-preserving URL parsing consolidation for the click-time local heuristic path

## Summary

Phase 2.2.3 completes the existing `UrlNormalizer` hook and adds an immutable `ParsedUrl`
representation. `LinkHeuristicRiskEngine` now calls `UrlNormalizer.parse()` exactly once for each
analysis, and all 20 registered link rules consume the resulting structured context rather than a
raw string plus `java.net.URI`.

The existing rule order, weights, thresholds, scores, reasons, correlation behavior, score cap,
and verdict mapping were not changed. All 89 existing link-heuristic tests pass with zero
expectation changes. OpenPhish matching and VirusTotal submission behavior also remain unchanged.

## 1. Previous Duplicated Parsing Locations

| Location | Previous behavior | Phase 2.2.3 decision |
| --- | --- | --- |
| `UrlNormalizer` | No-op `normalize(url) = url` placeholder. | Completed as the click-time parser/normalizer. |
| `LinkHeuristicRiskEngine.parseUri()` | `URI(url)`, then `URI("https://$url")` when the first parse had no host. | Migrated into `UrlNormalizer.parse()`. |
| All 20 link rules | Received both the raw string and nullable `URI`; most read URI fields independently. | Migrated to `ParsedUrl`. |
| `UrlEvidenceUtils` | Split query strings, percent-decoded names/values, parsed nested destinations, and matched embedded HTTP(S) strings. | Replaced by bounded, precomputed `ParsedUrl` query/path fields. |
| `TextSelectionProcessActivity` | Android `Uri` validation, two extraction regexes, bare-domain detection, and HTTPS insertion. | Extraction deliberately retained. Its old trim-only handoff was isolated from the now-functional normalizer. |
| `OpenPhishReputationProvider` | Private lowercase/scheme/`www.`/trailing-slash matching normalization. | Deliberately retained exactly. The former no-op `UrlNormalizer` call was removed so the provider contract cannot change. |
| `VirusTotalReputationProvider` | OkHttp `toHttpUrlOrNull()` validation before submission. | Deliberately retained and untouched. |
| `NotificationEventBuilder` | URL/`www.` extraction regexes, punctuation trimming, conditional HTTPS insertion, and `URI` parsing into `UrlAnalysisItem`. | Deliberately retained and untouched. |
| `IntentRouterActivity` and Android payload/UI code | Android `Uri` classification or browser handoff. | Deliberately retained and untouched. |
| `UrlAnalysisItem` | Existing notification schema fields for raw/normalized URL, domain, TLD, scheme, and URL flags. | Schema retained; it has different module ownership and semantics from click-time `ParsedUrl`. |

## 2. Shared Parser and Normalizer Design

`UrlNormalizer` is a stateless Kotlin `object` in the existing
`com.sentinel.ai.protection.intent.link` package. Its public APIs are:

- `parse(url: String): ParsedUrl`
- `normalize(url: String): String`, implemented as `parse(url).normalized`

`ParsedUrl` and `ParsedUrlQueryParameter` are immutable data classes. Their generated equality is
useful for deterministic and stability tests, while their custom `toString()` implementations do
not expose complete URLs, credentials, or query values.

The primary URL is parsed once inside `LinkHeuristicRiskEngine.analyze()`. Query splitting,
bounded decoding, IP/punycode classification, and nested-destination evidence are computed once
as part of that parse. Rules do not receive or expose parser-specific objects.

## 3. Parser Selected and Why

The implementation uses `java.net.URI` because:

- it was already the authoritative parser for the current heuristic behavior;
- it preserves the established missing-scheme fallback and malformed-input characteristics;
- it handles userinfo, raw versus decoded path/query components, IPv6 literals, and explicit ports;
- it is available without a new dependency and works in local JVM unit tests;
- it performs no DNS resolution, redirect following, URL opening, or network access.

OkHttp `HttpUrl` remains provider-specific for VirusTotal validation and OpenPhish feed request URL
construction. Android `Uri` remains Android-entry-point-specific.

## 4. Parsed Model Fields

`ParsedUrl` contains:

- `original`: exact caller input;
- `normalized`: trimmed input with normalized scheme/host casing;
- `scheme`: lowercase parsed or inferred scheme;
- `host`: lowercase host;
- `originalHost`: parser host with original casing, retained for exact entropy behavior;
- `port` and `hasExplicitPort`;
- `hasUserInfo` without exposing the credential text;
- decoded `path`, `rawPath`, and one-pass `percentDecodedPath`;
- precomputed `pathContainsEmbeddedHttpUrl`;
- raw `query` and raw `fragment`;
- `isValid`, `isIpv4`, `isIpv6`, and `isPunycode`;
- `subdomainCount`, intentionally preserving the existing host-dot-count convention;
- `rawQueryParameterCount`;
- ordered `queryParameters` and repeated-value-preserving `decodedQueryParameters`;
- `schemeWasInferred`.

Each `ParsedUrlQueryParameter` retains raw name/value, one-pass decoded name/value, a second-pass
decoded value, and the two precomputed booleans used by redirect/embedded-URL rules. Query values
are necessary for current detection but are never logged by the shared layer.

## 5. Missing-Scheme Policy

The existing heuristic policy is preserved:

1. Parse the trimmed input directly.
2. If the direct parse has no host, attempt one parse with `https://` prepended.
3. Mark `schemeWasInferred=true` only when the input did not already contain a `scheme://` prefix
   and the fallback produced a host.

Therefore `example.com` and `www.example.com/path` become valid parsed models with inferred HTTPS.
Incomplete explicit schemes such as `http://` and `https://` remain invalid rather than being
reported as legitimate inferred URLs.

No other scheme is assumed, no redirect is followed, and no URL is opened.

## 6. Normalization Rules

Normalization is intentionally non-destructive:

- trim surrounding whitespace;
- preserve `original` separately;
- lowercase scheme and host only;
- preserve path case;
- preserve raw query and fragment text;
- preserve explicit/default/non-default ports;
- preserve userinfo text in the normalized URL so URL semantics are not silently rewritten;
- preserve percent encoding and repeated parameter order;
- preserve the presence or absence of a trailing slash;
- do not remove `www.`;
- do not remove default ports;
- do not sort, merge, or rewrite query parameters.

`normalize(normalize(url)) == normalize(url)` is covered for valid, bare-domain, IPv6, repeated
query, whitespace, and malformed inputs.

## 7. Decoding-Depth Policy

The hard maximum decode depth is `UrlNormalizer.MAX_DECODE_DEPTH = 2`.

- Path evidence is decoded once, matching the previous embedded-path helper.
- Query names are decoded once.
- Query values retain their raw, once-decoded, and twice-decoded forms.
- Redirect and embedded-query evidence uses at most the second decoded form, preserving the
  effective Phase 2.2.2 behavior without recursive decoding.
- `URLDecoder` failures return the previous layer unchanged.
- The normalized URL always retains the original encoded query; decoding never rewrites it.

This preserves once- and twice-encoded destination detection while ensuring triple-encoded input
does not exceed the two-pass boundary.

## 8. Malformed-Input Behavior

Parsing never intentionally throws to callers. `URI` and decoder failures are contained, and a
deterministic invalid or partial `ParsedUrl` is returned.

| Input category | Result |
| --- | --- |
| Empty, blank, plain text, broken prefix, incomplete scheme | `isValid=false`; safe empty/partial fields. |
| `https://example.com:` | Invalid partial model with host retained, `hasExplicitPort=true`, and `port=null`. |
| `https://example.com:99999` | Invalid partial model with host and parsed port `99999` retained. |
| Multiple authority `@` characters | Invalid model; credentials are not exposed through a userinfo value. |
| Invalid percent escape such as `%ZZ` | Invalid deterministic model; no decoding exception escapes. |

Rules do not treat `isValid` as an automatic safe verdict. They consume available partial fields,
which preserves existing malformed-input scoring instead of introducing a new invalid-URL rule.

Raw Unicode hostnames remain neutral/invalid where `java.net.URI` cannot expose a host; ASCII
punycode is fully parsed and classified. Changing IDN behavior would be a new detection behavior
and is deferred.

## 9. Components Migrated

- `LinkHeuristicRiskEngine`
- `LinkHeuristicRule`
- all 20 registered link-rule classes
- Phase 2.2.2 query/path parsing formerly in `UrlEvidenceUtils`
- `LinkProtectionAgent`, which now forwards the raw URL to the engine so normalization does not
  alter raw-length or raw-keyword scoring before the single authoritative parse

The test support helper did not contain parsing logic, so it required no migration.

## 10. Components Deliberately Not Migrated

- `TextSelectionProcessActivity` extraction and bare-domain acceptance policy
- `NotificationEventBuilder` and the notification protection pipeline
- OpenPhish feed matching normalization
- VirusTotal validation/submission/polling
- `IntentRouterActivity`, `ScanLoadingActivity`, and browser handoff
- `ReputationTarget` and `UrlPayload` public data contracts
- reputation manager, combiner, network infrastructure, notification listener, scam engine,
  event schema, journal, Room, Compose UI, and Gradle dependencies

`TextSelectionProcessActivity` and `OpenPhishReputationProvider` received only compatibility edits
to preserve their previous use of the no-op normalizer. Neither was migrated to `ParsedUrl`.

## 11. Behavior-Preservation Evidence

- All 89 existing link-heuristic tests pass unchanged.
- Existing test expectations changed: **0**.
- Rule registration order remains unchanged.
- `LinkHeuristicConfig` weights and thresholds were not changed in Phase 2.2.3.
- `RiskLevelMapper` was not changed.
- Reasons, score contributions, global clamping, redirect/embedded correlation, and verdict mapping
  are unchanged.
- All 29 OpenPhish provider tests pass.
- All 17 VirusTotal provider tests pass.
- The full app suite now contains 172 passing tests: the previous 155 plus 17 parser tests.
- The full multi-module debug unit-test task and debug assembly both pass.

## 12. Performance Impact

Before this phase the engine parsed the primary URL once, but rules repeatedly extracted URI
fields, split the raw query, percent-decoded values, and parsed nested redirect targets. After this
phase:

- the primary URL is parsed once per heuristic analysis;
- the query is split once;
- each query value is decoded at most twice and each path at most once;
- repeated parameters are collected in one pass;
- nested HTTP(S) plausibility is computed once per parameter and reused by both relevant rules;
- regexes are compiled once as immutable object/class properties;
- there is no cache, mutable global state, network access, or DNS access.

The added allocation is one immutable `ParsedUrl` plus one small object per named query parameter,
replacing duplicated transient parsing work across rules.

## 13. Security Considerations

- The parser does not execute, open, submit, resolve, or follow URLs.
- No production dependency or network capability was added.
- Decode depth is fixed at two; there is no recursion.
- Malformed attacker-controlled input is exception-contained and deterministic.
- Full URLs and query values are not logged by the parser or heuristic engine.
- Userinfo is represented only by `hasUserInfo`; credentials are not exposed as a model field.
- `ParsedUrl.toString()` and `ParsedUrlQueryParameter.toString()` are redacted.
- Query semantics, encodings, credentials, ports, and fragments are not silently removed.
- Invalid input is not automatically declared safe; existing partial-field heuristic behavior is
  preserved.

## 14. Files Modified

New production model:

- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/link/ParsedUrl.kt`

Completed/migrated production files:

- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/link/UrlNormalizer.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/link/LinkProtectionAgent.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/LinkHeuristicRule.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/LinkHeuristicRiskEngine.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/rules/link/SuspiciousTldRule.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/rules/link/IpAddressRule.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/rules/link/ExcessiveSubdomainsRule.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/rules/link/RandomHostnameRule.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/rules/link/RepeatedHyphensRule.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/rules/link/ExcessiveDigitsRule.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/rules/link/PunycodeRule.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/rules/link/LongUrlRule.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/rules/link/DeepPathRule.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/rules/link/LongFilenameRule.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/rules/link/ExcessiveQueryParametersRule.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/rules/link/EncodedCharactersRule.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/rules/link/TrackingParameterRule.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/rules/link/RedirectParameterRule.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/rules/link/BrandImpersonationRule.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/rules/link/SocialEngineeringRule.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/rules/link/InsecureHttpRule.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/rules/link/NonStandardPortRule.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/rules/link/UserinfoDeceptionRule.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/rules/link/EmbeddedUrlRule.kt`

Removed after consolidation:

- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/rules/link/UrlEvidenceUtils.kt`

Behavior-preserving compatibility edits:

- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/TextSelectionProcessActivity.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/reputation/OpenPhishReputationProvider.kt`

New test and report:

- `android-app/app/src/test/java/com/sentinel/ai/protection/intent/link/UrlNormalizerTest.kt`
- `phase-2.2.3-shared-url-normalization.md`

## 15. Tests Added

`UrlNormalizerTest` adds 17 focused tests covering:

- basic HTTPS parsing;
- scheme/host normalization and path-case preservation;
- explicit ports, query, and fragment;
- userinfo presence and diagnostic redaction;
- IPv4 and bracketed IPv6;
- punycode;
- both existing missing-scheme cases;
- once- and twice-encoded values plus the two-pass ceiling;
- repeated query parameters and ordering;
- whitespace trimming;
- trailing slash and query preservation;
- all required malformed examples;
- invalid-port partial models;
- parsing stability and normalization idempotence.

## 16. Existing Tests Changed

No existing test file or expectation was changed in Phase 2.2.3.

The existing 89-test heuristic suite was used unchanged as the regression oracle.

## 17. Commands Run

All final commands used Android Studio's bundled JBR:

`JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`

They also used `--console=plain --no-watch-fs` for deterministic, non-watching CI-style output.

1. `.\gradlew.bat :app:compileDebugKotlin --console=plain --no-watch-fs`
2. `.\gradlew.bat :app:testDebugUnitTest --tests '*UrlNormalizer*' --tests '*ParsedUrl*' --tests '*UrlParser*' --console=plain --no-watch-fs`
3. `.\gradlew.bat :app:testDebugUnitTest --tests '*LinkHeuristicRiskEngine*' --console=plain --no-watch-fs`
4. `.\gradlew.bat :app:testDebugUnitTest --tests '*OpenPhishReputationProviderTest' --console=plain --no-watch-fs`
5. `.\gradlew.bat :app:testDebugUnitTest --tests '*VirusTotalReputationProviderTest' --console=plain --no-watch-fs`
6. `.\gradlew.bat :app:testDebugUnitTest --console=plain --no-watch-fs`
7. `.\gradlew.bat testDebugUnitTest --console=plain --no-watch-fs`
8. `.\gradlew.bat assembleDebug --console=plain --no-watch-fs`
9. `git diff --check`

The first preliminary sandboxed Gradle invocation could not download the pinned Gradle 8.7
wrapper because network access was restricted. The approved retry downloaded that repository-
pinned wrapper. This was build-tool setup only; URL parsing and all unit tests remained offline.

Notification parsing code was not changed, so the conditional standalone
`:agents:testDebugUnitTest` command was not required. The agents debug tests were still included
by the full `testDebugUnitTest` run.

## 18. Exact Results

| Command | Exact final result |
| --- | --- |
| `:app:compileDebugKotlin` | `BUILD SUCCESSFUL in 19s`; 79 actionable tasks: 2 executed, 77 up-to-date. |
| Shared parser selection | `BUILD SUCCESSFUL in 29s`; 17 tests, 0 failures/errors/skips; 100 actionable tasks: 7 executed, 93 up-to-date. |
| `*LinkHeuristicRiskEngine*` | `BUILD SUCCESSFUL in 15s`; 89 tests, 0 failures/errors/skips; 100 actionable tasks: 1 executed, 99 up-to-date. |
| `*OpenPhishReputationProviderTest` | `BUILD SUCCESSFUL in 36s`; 29 tests, 0 failures/errors/skips; 100 actionable tasks: 1 executed, 99 up-to-date. |
| `*VirusTotalReputationProviderTest` | `BUILD SUCCESSFUL in 28s`; 17 tests, 0 failures/errors/skips; 100 actionable tasks: 1 executed, 99 up-to-date. |
| `:app:testDebugUnitTest` | `BUILD SUCCESSFUL in 49s`; 172 tests in 12 suites, 0 failures/errors/skips; 100 actionable tasks: 1 executed, 99 up-to-date. |
| `testDebugUnitTest` | `BUILD SUCCESSFUL in 9s`; all requested module debug test tasks passed; 128 actionable tasks, all up-to-date. |
| `assembleDebug` | `BUILD SUCCESSFUL in 42s`; 171 actionable tasks: 4 executed, 167 up-to-date. |
| `git diff --check` | Exit code 0; no whitespace errors. |

## 19. Remaining Duplication

The following duplication remains deliberately because its behavior or module contract differs:

- Text-selection URL extraction and its narrow bare-domain regex;
- notification URL extraction, `www.` handling, and `UrlAnalysisItem` construction in the
  separate `agents` module;
- OpenPhish's feed-matching normalization (`www.` removal, scheme removal, lowercase-all, and one
  trailing-slash removal);
- VirusTotal's strict OkHttp validation;
- Android `Uri` use for intent classification, file payloads, and browser handoff.

These are not all interchangeable normalization problems. In particular, replacing OpenPhish's
matching canonicalization with click-time normalization would change feed boundaries, while the
notification module cannot depend on the app module without an architectural dependency change.

## 20. Recommended Next Phase

Recommended Phase 2.2.4: characterize notification/text-selection malformed and internationalized
URL behavior more thoroughly, then decide whether a parser-neutral subset belongs in a lower-level
shared module. Migrate one consumer at a time only after exact contract tests exist. Keep OpenPhish
matching canonicalization as an explicit provider policy even if it later consumes common parsed
components.

Public-suffix-aware registrable domains and Unicode IDN conversion should remain separate future
detection/calibration work because either can change heuristic outputs and false-positive behavior.
