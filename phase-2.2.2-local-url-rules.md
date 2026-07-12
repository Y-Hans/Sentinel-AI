# Phase 2.2.2 Local URL Rules

Date: 2026-07-10  
Project: `android-app`  
Scope: offline, click-time additions to `LinkHeuristicRiskEngine`

## Existing Rules Confirmed Before Implementation

The engine registered 16 rules, in this preserved order: `suspicious_tld`, `ip_address`, `excessive_subdomains`, `random_hostname`, `repeated_hyphens`, `excessive_digits`, `punycode`, `excessive_length`, `deep_nesting`, `long_filename`, `excessive_query`, `encoded_chars`, `tracking_parameters`, `suspicious_redirect`, `brand_impersonation`, and `social_engineering`.

Their existing score weights and the `GREEN` (<30), `YELLOW` (>=30), `RED` (>=70), and `CRITICAL` (>=90) thresholds were not changed. The engine continues to sum non-negative rule contributions and clamp the final score to `0..100`.

## Rules Added and Updated

| Rule ID | Score | Explanation | Behavior |
| --- | ---: | --- | --- |
| `insecure_http` | 5 | `The URL uses unencrypted HTTP` | Fires only for a parsed `http` scheme. HTTPS and missing schemes do not fire it. |
| `non_standard_port` | 10 | `The URL uses a non-standard network port` | Fires for an explicit HTTP/HTTPS port other than 80/443. `URI.port` safely distinguishes IPv6 literal colons from ports. |
| `userinfo_deception` | 30 | `The URL contains deceptive user information before the actual host` | Uses parsed URI userinfo, so `@` in a query or fragment is ignored. Other rules receive the parsed destination host. |
| `embedded_url` | 15 by default | `The URL embeds another destination URL` | Detects plain or once-percent-decoded HTTP(S) URLs in a path or query value, not in the primary URL itself. |
| `suspicious_redirect` (existing) | unchanged at 15 | `URL uses a redirect parameter pointing to another destination` | Now parses parameter values safely, matches names case-insensitively, accepts the expanded configured redirect list, and requires a plausible HTTP(S) destination. |

The redirect list is now: `redirect`, `redirect_url`, `redirect_uri`, `return`, `return_url`, `next`, `continue`, `target`, `destination`, `dest`, `goto`, `out`, `link`, `url`, and the pre-existing `to`.

Weights follow the existing scale: HTTP is deliberately weak; port is low; embedded and redirect destinations are medium; and authority userinfo deception is high, matching the existing 30-point brand-impersonation signal. HTTP alone remains 5 (`GREEN`).

## Parsing and Correlation Handling

The existing `java.net.URI` parser and bare-domain HTTPS fallback remain in use; no dependency or shared-normalizer refactor was added. A local rule helper exposes URI raw path/query values, decodes a component at most once with an exception-safe decoder, and verifies candidate destinations with `URI` plus an HTTP(S) scheme/host check.

The engine's existing final `0..100` clamp and one-result-per-registered-rule behavior already prevent duplicate rule identifiers and score overflow. For correlated redirect evidence, `suspicious_redirect` keeps its established 15-point contribution. If every URL embedded in the query is the same configured redirect-parameter evidence, `embedded_url` still reports its stable explanation but contributes 0. This retains both explainable signals without charging the same destination twice. An independently embedded URL (for example, in a `ref` parameter or path) receives the configured 15 points.

## Domain-Boundary False-Positive Fix

`BrandImpersonationRule` now examines the registrable-style label (the label immediately before the final hostname label) for a brand token/lookalike, while retaining the existing official-domain exemption. This is the smallest local correction possible without adding a public-suffix-list dependency.

Changed Phase 2.2.1 characterizations:

| URL | Old result | New result | Reason |
| --- | --- | --- | --- |
| `https://paypal.com.example.org` | 25, `GREEN` | 0, `GREEN` | `example.org`, not the PayPal substring in a subdomain, is evaluated. |
| `https://notpaypal.com` | 45, `YELLOW` | 20, `GREEN` | The false PayPal substring signal is removed; the existing unrelated `otp` social-keyword characterization remains. |
| `https://google.com.example.net` | 25, `GREEN` | 0, `GREEN` | `example.net`, not the Google substring in a subdomain, is evaluated. |

Official domains such as `paypal.com` and `accounts.google.com` remain exempt from the brand rule. Existing social-keyword behavior, including `secure-example.com`, was intentionally not changed.

## Tests

The prior suite had 75 heuristic tests across three classes. The final focused suite has 89 tests across four classes: 14 focused Phase 2.2.2 tests were added in `LinkHeuristicRiskEngineLocalUrlRulesTest`, existing rule-order/score assertions were extended to 20 rules, and the three domain-boundary characterizations above were intentionally updated after the production correction.

Coverage includes HTTP case handling, default/non-default ports and IPv6, malformed ports, authority userinfo versus query/fragment `@`, actual-host analysis, plain and encoded embedded URLs, redirect parameter names/values, correlation scoring, deterministic malformed inputs, domain boundaries, score cap, and the specified combined scenarios. Tests construct only the local heuristic engine; they do not call OpenPhish, VirusTotal, or any other network provider.

## Files Modified

Production:

- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/LinkHeuristicRiskEngine.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/LinkHeuristicConfig.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/rules/link/BrandImpersonationRule.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/rules/link/RedirectParameterRule.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/rules/link/UrlEvidenceUtils.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/rules/link/InsecureHttpRule.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/rules/link/NonStandardPortRule.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/rules/link/UserinfoDeceptionRule.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/heuristic/rules/link/EmbeddedUrlRule.kt`

Tests:

- `android-app/app/src/test/java/com/sentinel/ai/protection/intent/heuristic/LinkHeuristicRiskEngineTest.kt`
- `android-app/app/src/test/java/com/sentinel/ai/protection/intent/heuristic/LinkHeuristicRiskEngineFalsePositiveTest.kt`
- `android-app/app/src/test/java/com/sentinel/ai/protection/intent/heuristic/LinkHeuristicRiskEngineTestSupport.kt`
- `android-app/app/src/test/java/com/sentinel/ai/protection/intent/heuristic/LinkHeuristicRiskEngineLocalUrlRulesTest.kt`

OpenPhish, VirusTotal, reputation management/combination, intent routing, and networking code were not modified.

## Verification

All commands used the Android Studio JBR `JAVA_HOME` and `--console=plain --no-watch-fs`.

| Command | Exact result |
| --- | --- |
| `./gradlew.bat :app:compileDebugKotlin` | `BUILD SUCCESSFUL in 14s` |
| `./gradlew.bat :app:testDebugUnitTest --tests "*LinkHeuristicRiskEngine*"` | `BUILD SUCCESSFUL in 32s`; 89 tests, 0 failures/errors/skips |
| `./gradlew.bat :app:testDebugUnitTest` | `BUILD SUCCESSFUL in 43s`; 155 app tests, 0 failures/errors/skips |
| `./gradlew.bat testDebugUnitTest` | `BUILD SUCCESSFUL in 17s`; 262 module tests, 0 failures/errors/skips |
| `./gradlew.bat assembleDebug` | `BUILD SUCCESSFUL in 49s` |

`git diff --check` also completed without whitespace errors.

## Limitations Deferred to Phase 2.2.3

- No shared URL-normalizer refactor or public-suffix-list dependency was introduced.
- The brand boundary fix is intentionally registrable-style rather than PSL-aware, so multi-label public suffixes remain a known limitation.
- Embedded destinations are decoded once only; recursive decoding and redirect following are intentionally absent.
- Relative and non-HTTP(S) redirect destinations are not treated as external redirect evidence.
- Correlation handling is intentionally limited to the new embedded/redirect overlap; the existing additive scoring framework and unrelated legacy rule scores remain unchanged.
