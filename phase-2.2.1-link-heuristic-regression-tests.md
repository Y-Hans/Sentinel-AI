# Phase 2.2.1 Link Heuristic Regression Tests

Date: 2026-07-10  
Project: `android-app`  
Scope: characterization tests for the existing `LinkHeuristicRiskEngine`

## Summary

Phase 2.2.1 adds a deterministic, offline regression safety net for the existing local URL heuristic engine. The final link-heuristic suite contains 75 tests across three test classes. The previous class contained 2 tests, so this phase adds 73 net tests.

All 16 registered local link rules are covered. Tests assert exact scores, risk levels, triggered and non-triggered rule results, score contributions, categories, explanation text, combined explanation order/truncation, duplicate behavior, URL parsing assumptions, and the global score cap.

No production code, configuration, dependencies, URL normalization, scoring behavior, or heuristic behavior was changed.

## Test Files

| File | Tests | Purpose |
|---|---:|---|
| `LinkHeuristicRiskEngineTest.kt` | 30 | Rule inventory, per-rule behavior, combined signals, duplicate handling, score cap, explanation stability, and determinism |
| `LinkHeuristicRiskEngineFalsePositiveTest.kt` | 18 | Baseline safe URLs, legitimate suspicious-looking URLs, brand-domain boundaries, and current false positives |
| `LinkHeuristicRiskEngineBoundaryTest.kt` | 27 | Risk thresholds, rule thresholds, malformed inputs, parsing assumptions, Unicode, IPv6, fragments, queries, and very long URLs |
| `LinkHeuristicRiskEngineTestSupport.kt` | 0 | Shared exact assertions for all 16 rule-result slots and the combined explanation |

The shared assertion helper verifies that every expected rule triggers with the exact contribution, category, and reason, while every unrelated rule remains untriggered with a zero contribution and null reason.

## Existing 16-Rule Inventory and Coverage Matrix

The registration order is asserted because it controls explanation order. `LinkHeuristicRiskEngine` adds every contribution and clamps the total to `0..100`.

| # | Rule ID / class | Current trigger fixture | Contribution | Exact reason | Coverage notes |
|---:|---|---|---:|---|---|
| 1 | `suspicious_tld` / `SuspiciousTldRule` | `https://example.xyz` | 15 | `Uses .xyz domain` | Isolated; suspicious-TLD matching is host suffix based |
| 2 | `ip_address` / `IpAddressRule` | `https://1.2.3.4/` | 25 | `Uses IP address instead of domain` | Isolated using an IPv4 value with fewer than six digits |
| 3 | `excessive_subdomains` / `ExcessiveSubdomainsRule` | `https://a.b.c.example.com` | 10 | `Contains excessive subdomains` | Isolated; implementation counts host dots, not public-suffix-aware subdomains |
| 4 | `random_hostname` / `RandomHostnameRule` | `https://abcdefghijklpq.com` | 10 | `Uses a random-looking hostname` | Isolated; entropy must be greater than 3.8 and clean host length at least 8 |
| 5 | `repeated_hyphens` / `RepeatedHyphensRule` | `https://alpha--beta.com` | 10 | `Contains repeated or multiple hyphens in domain` | Isolated; boundary tests also cover two versus three total hyphens |
| 6 | `excessive_digits` / `ExcessiveDigitsRule` | `https://abc12.com` | 10 | `Contains excessive digits in the domain` | Isolated; exact 0.3 digit/letter ratio remains clean and values above it trigger |
| 7 | `punycode` / `PunycodeRule` | `https://xn--a.com` | 15 | `Punycode domain name detected` | Isolated; a realistic sample with a third hyphen also characterizes repeated-hyphen stacking |
| 8 | `excessive_length` / `LongUrlRule` | 151-character URL with a long fragment | 5 | `URL is unusually long` | Isolated; raw length 150 is clean and 151 triggers |
| 9 | `deep_nesting` / `DeepPathRule` | `https://example.com/a/b/c/d/e` | 5 | `URL path is deeply nested` | Isolated; four segments are clean and five trigger |
| 10 | `long_filename` / `LongFilenameRule` | One 31-character final path segment | 5 | `URL contains an unusually long filename` | Isolated; 30 characters are clean and 31 trigger |
| 11 | `excessive_query` / `ExcessiveQueryParametersRule` | Six ordinary query parameters | 10 | `URL has excessive query parameters` | Isolated; five parameters are clean and six trigger |
| 12 | `encoded_chars` / `EncodedCharactersRule` | `https://example.com/%41%42%43` | 10 | `URL contains many encoded characters` | Isolated; two `%xx` substrings are clean and three trigger |
| 13 | `tracking_parameters` / `TrackingParameterRule` | `?utm_source=newsletter` | 0 | `URL contains tracking parameters` | Isolated; it increments triggered-rule count and explanation evidence despite zero score |
| 14 | `suspicious_redirect` / `RedirectParameterRule` | `?next=https://destination.test` | 15 | `URL contains a redirect parameter placeholder signal` | Isolated with an unencoded target to avoid encoded-character stacking |
| 15 | `brand_impersonation` / `BrandImpersonationRule` | `https://paypol.example` | 25 | `Possible paypal look-alike domain` | Isolated look-alike branch; the 30-point extra-word branch is also asserted exactly |
| 16 | `social_engineering` / `SocialEngineeringRule` | `https://secure-example.com`; `/login` | 20 host / 2 non-host | `Uses social engineering keyword in domain: secure`; `Uses social engineering keyword in path/query: login` | Both current contribution branches are isolated |

Rules that could not be isolated: none. Some additional fixtures deliberately characterize correlated stacking even when an isolated fixture exists.

## Score and Risk Boundaries

Current mapping from `RiskLevelMapper.kt`:

| Score | Risk level |
|---:|---|
| `< 30` | `GREEN` |
| `30..<70` | `YELLOW` |
| `70..<90` | `RED` |
| `>= 90` | `CRITICAL` |

The suite asserts `Math.nextDown(30f)`/`30f`, `Math.nextDown(70f)`/`70f`, `Math.nextDown(90f)`/`90f`, and the `0f`/`100f` endpoints directly. Reachable engine combinations additionally assert scores 30 (`YELLOW`), 35 (`YELLOW`), 50 (`YELLOW`), 80 (`RED`), and capped 100 (`CRITICAL`).

Rule-specific exclusive boundaries are also covered:

| Behavior | Clean boundary | Trigger boundary |
|---|---:|---:|
| Raw URL length | 150 characters | 151 characters |
| Path segments | 4 | 5 |
| Last path-segment length | 30 characters | 31 characters |
| Query parameters | 5 | 6 |
| Host dot count | 3 | 4 |
| Digit/letter ratio | 0.3 | greater than 0.3 |
| Total host hyphens without `--` | 2 | 3 |
| `%xx` substrings | 2 | 3 |

## Baseline Safe URL Cases

“Safe” here means the current engine remains `GREEN`; it does not necessarily mean zero score.

| URL | Score | Risk | Current evidence |
|---|---:|---|---|
| `https://example.com` | 0 | `GREEN` | None |
| `https://www.example.com` | 0 | `GREEN` | None |
| `https://github.com` | 0 | `GREEN` | None |
| `https://github.com/login` | 2 | `GREEN` | Path/query social keyword `login` |
| `https://accounts.google.com` | 20 | `GREEN` | Host social keyword substring `account` |
| `https://support.microsoft.com` | 0 | `GREEN` | None |
| `https://developer.android.com` | 0 | `GREEN` | None |

Each case asserts the exact combined safe/risk explanation and the absence of every unrelated rule.

## False-Positive and Domain-Boundary Cases

| URL | Score | Risk | Preserved current behavior |
|---|---:|---|---|
| `https://accounts.google.com/ServiceLogin` | 2 | `GREEN` | `login` is the first configured match and is outside the host, so it shadows the host substring `account` |
| `https://support.example.com/account-recovery` | 2 | `GREEN` | `account` in path |
| `https://bank.example.com/account` | 2 | `GREEN` | `account` in path |
| `https://developer.android.com/studio` | 0 | `GREEN` | No trigger |
| `https://example.com/download/app.zip` | 0 | `GREEN` | Link engine has no download-extension rule |
| `https://paypal.com.example.org` | 25 | `GREEN` | Brand substring is treated as PayPal impersonation despite registrable-domain boundary |
| `https://notpaypal.com` | 45 | `YELLOW` | PayPal substring contributes 25 and cross-character substring `otp` contributes 20 |
| `https://google.com.example.net` | 25 | `GREEN` | Google substring is treated as impersonation despite registrable-domain boundary |
| `https://secure-example.com` | 20 | `GREEN` | `secure` in host |
| `https://paypal.com` | 0 | `GREEN` | Official brand domain is exempt |
| `https://login.paypal.com` | 20 | `GREEN` | Official-domain exemption avoids the brand rule, but host keyword `login` still scores |

These are characterization tests. No current false positive was fixed in this phase.

## Combined-Signal Cases

| Scenario | Fixture summary | Contributions | Final result |
|---|---|---|---|
| Two weak signals | 151-character URL with five path segments | 5 long URL + 5 deep path | 10, `GREEN` |
| One strong plus one weak | PayPal look-alike plus five path segments | 25 brand look-alike + 5 deep path | 30, `YELLOW` |
| Several medium signals | Suspicious TLD, excessive host dots, double hyphen | 15 + 10 + 10 | 35, `YELLOW` |
| Several strong signals | `paypal-secure.xyz` plus redirect parameter | 15 TLD + 15 redirect + 30 brand + 20 host keyword | 80, `RED` |
| Maximum realistic combination | Punycode-like PayPal host, digits, subdomains, URL-structure signals, tracking, redirect, brand, keyword | Raw contribution sum 170 across 15 triggered rules | Clamped to 100, `CRITICAL` |

The maximum-combination test verifies that reasons are not duplicated, the triggered-rule count remains 15, the score is capped at 100, and the combined explanation contains the first four reasons in registered rule order.

## Duplicate Detection and Determinism

| Fixture | Preserved behavior |
|---|---|
| `https://example.com/login/login/login` | Social-engineering rule contributes 2 once |
| `https://bit.ly/bit.ly/bit.ly` | Score 0; no local shortener rule exists in this engine |
| `https://verify-verify-verify.example.com` | Social-engineering rule contributes 20 once; two hyphens do not trigger repeated-hyphen rule |
| Two repeated `next=https://...` parameters | Redirect rule contributes 15 once |

Safe, malformed, combined, and capped inputs are each evaluated repeatedly and compared as complete `LinkHeuristicAnalysis` values. Score, risk level, rule results, reason order, explanation, and triggered count are stable. No network provider or reputation manager is constructed or called.

## Malformed Inputs and Parsing Assumptions

The API accepts a non-null `String`; null input is therefore not representable without changing the production signature and was not tested.

| Input/category | Current result |
|---|---|
| `""`, `" "`, `"not a url"` | No throw; score 0, `GREEN`, no reasons |
| `example.com`, `www.example.com` | Parsed using the engine's `https://` fallback; score 0 |
| `http://`, `https://`, `://broken` | No throw; score 0 |
| `https://example.com/`, `https://example.com////` | Score 0; slash-only path segments are discarded |
| Uppercase scheme/host | Parsed; rule-specific host lowercasing allows `HTTPS://EXAMPLE.XYZ` to score 15 |
| Fragment `#login` | Raw-string social rule contributes 2 and labels it as path/query evidence |
| Query `?action=login` | Social rule contributes 2 |
| Unicode host `https://bücher.example` | Current fallback behavior is neutral: score 0 |
| IPv6 literal `https://[2001:db8::1]/` | IP rule 25 plus excessive-digits rule 10 = 35, `YELLOW` |
| 1,021-character URL with long fragment | Only raw-length rule triggers; score 5 |
| Bare `example.xyz` | HTTPS fallback supplies a host and suspicious-TLD rule contributes 15 |
| Unparseable `login not a url` | URI-based rules remain clean, but raw-string social rule contributes 2 |

The tests preserve the current `java.net.URI` first-pass behavior, the second-pass `https://` prefix fallback, and the no-op `UrlNormalizer`. No new normalization is introduced.

## Existing Behavior That Appears Questionable

The following observations are intentionally locked down, not fixed:

- Social-engineering keywords are raw substrings without token or label boundaries. `accounts.google.com` matches `account`, and `notpaypal.com` contains the cross-character substring `otp`.
- Only the first configured social keyword match is used. In `accounts.google.com/ServiceLogin`, path `login` is found before host `account`, reducing the result from the otherwise observed 20-point host match to 2 points.
- Brand checks use `host.contains(brand)` without registrable-domain boundaries, so `paypal.com.example.org`, `notpaypal.com`, and `google.com.example.net` trigger.
- A punycode host with a third hyphen triggers both punycode and repeated-hyphen rules even though the leading `xn--` double hyphen has a special exclusion.
- IPv6 literals can stack raw-IP and excessive-digit signals.
- Tracking parameters create evidence and increment `triggeredRuleCount` while contributing zero points.
- Fragments are not separately modeled; a keyword in a fragment is reported as path/query evidence.
- Malformed URLs default to a green result, although raw-string rules can still trigger on malformed text.
- Unicode hostnames currently receive neutral fallback behavior rather than explicit IDN handling.
- Combined explanations expose only the first four rule reasons even when more rules trigger.
- Correlated contributions have no category cap; only the final global score is clamped to 100.
- URL shortener detection exists in the notification pipeline, not in `LinkHeuristicRiskEngine`; `bit.ly` remains zero in the local intent-time engine.
- APK, executable, and archive extension rules exist in file analysis, not URL analysis; `/download/app.zip` remains zero.

## Validation

`JAVA_HOME` was set for the commands to the bundled Android Studio JBR because the configured user JDK path was unavailable. The Gradle 8.7 wrapper was downloaded once, after which the tests and build ran locally. Test behavior itself is offline and uses no reputation providers.

Final verification runs:

| Command | Exact result |
|---|---|
| `.\gradlew.bat :app:compileDebugUnitTestKotlin --console=plain --no-watch-fs` | `BUILD SUCCESSFUL in 15s`; 88 actionable tasks: 2 executed, 86 up-to-date |
| `.\gradlew.bat :app:testDebugUnitTest --tests "*LinkHeuristicRiskEngine*" --console=plain --no-watch-fs` | `BUILD SUCCESSFUL in 12s`; 75 tests, 0 failures, 0 errors, 0 skipped; 100 actionable tasks: 2 executed, 98 up-to-date |
| `.\gradlew.bat :app:testDebugUnitTest --console=plain --no-watch-fs` | `BUILD SUCCESSFUL in 41s`; 141 tests in 10 suites, 0 failures, 0 errors, 0 skipped; 100 actionable tasks: 1 executed, 99 up-to-date |
| `.\gradlew.bat assembleDebug --console=plain --no-watch-fs` | `BUILD SUCCESSFUL in 6s`; 171 actionable tasks: 171 up-to-date |

An earlier `assembleDebug` invocation without `--no-watch-fs` did not return a Gradle result before the execution timeout and was not counted as validation. The required assembly was rerun with file-system watching disabled and completed successfully as shown above.

## Completion Status

- Existing local link rules covered: 16 of 16
- Final link-heuristic tests: 75
- Net tests added: 73
- Full app unit tests: 141 passing
- New heuristic rules: none
- Production files modified: none
- Production dependencies modified: none
- URL normalization modified: no
- Network calls in new tests: none
- `:app:testDebugUnitTest`: passed
- `assembleDebug`: passed

