# Phase 2.3 Final Risk Decision and Explainable Result Integration

Date: 2026-07-11

Project: `android-app`

Scope: backend-only final click-time URL decision and explainable result integration

## Summary

Phase 2.3 adds a deterministic `ALLOW` / `WARN` / `BLOCK` action decision that is separate from
the existing `GREEN` / `YELLOW` / `RED` / `CRITICAL` risk band and from each provider's raw
reputation verdict. Local URL score/reasons, provider verdicts/confidence/reasons, and provider
unknown/unavailable/failure/timeout states now converge in `EvidenceCombiner` and survive as
structured fields in `ScanResult`.

The existing 0..100 score, 30/70/90 risk-level boundaries, complementary positive-evidence
formula, provider confidence values, concurrent provider execution, analyzer orchestration, and
provider implementations were reused. Clean, unknown, and failed lookups no longer reduce risk.
A malicious provider cannot be averaged or discounted below `BLOCK`, and a suspicious provider
cannot produce less than `WARN`.

No UI, browser handoff, provider, network dependency, production Gradle dependency, heuristic
rule, notification behavior, Room schema, or API-key handling was added or changed.

## 1. Previous Decision Behavior

The audited pre-Phase-2.3 flow was:

```text
LinkProtectionAgent
  -> LinkHeuristicRiskEngine.toScanResult()
  -> ScanResult(score, riskLevel, explanation string)
  -> ReputationManagerImpl
  -> EvidenceCombiner
  -> ScanResult(copy with fused score/riskLevel/explanation string)
```

`IntentThreatAnalyzerImpl` already called `LinkScanner`, then `ReputationManager.enrich`, emitted
the combined result through `ThreatEventBus`, and returned it. It did not call providers directly.
That orchestration was already correct and was intentionally left unchanged.

The old final result had no user-action decision. A URL could only be interpreted indirectly from
`RiskLevel`.

## 2. Mandatory Audit Findings and Previous Combiner Limitations

1. **Current combiner inputs:** `heuristicResult: ScanResult` and
   `reputationEvidence: List<ReputationResult>`.
2. **Current combiner output:** a copied `ScanResult` containing a fused score, remapped risk
   level, and concatenated explanation string. Empty evidence returned the original object.
3. **Existing weighting:** local score was treated as a baseline probability. `MALICIOUS` used
   `0.90 * providerConfidence`; `SUSPICIOUS` used `0.60 * providerConfidence`.
4. **Bounded score:** the baseline and fused probability were clamped, so normal combiner output
   was bounded to 0..100. The heuristic engine also clamps its score to 0..100.
5. **`UNKNOWN`:** contributed zero score. If returned as a `ReputationResult`, it appeared only in
   the concatenated provider explanation.
6. **Provider `null`:** `ReputationManagerImpl.filterNotNull()` discarded it, so the final result
   could not distinguish no result from no provider call.
7. **Conflicts:** positive provider probabilities fused, then the maximum `CLEAN` confidence
   discounted the whole score. A clean result could therefore suppress local or malicious
   evidence; there was no explicit malicious-over-clean action precedence.
8. **Local explanations:** `LinkHeuristicRiskEngine.analyze()` retained all `RuleResult` objects,
   but `toScanResult()` discarded them and kept only the first four triggered explanations in one
   string.
9. **Provider attribution:** provider name, verdict, confidence, and reason survived only inside a
   formatted explanation string. They were not structured fields.
10. **Existing action model:** none. `RiskLevel` had four values but there was no allow/warn/block
    type or equivalent user action.

Additional audit findings:

- Provider calls were already concurrent through `coroutineScope`, `async`, and `awaitAll`.
- `runCatching` surrounded provider timeouts and could catch external `CancellationException`.
- OpenPhish maps feed matches to `MALICIOUS` at confidence `0.98`; a completed no-match maps to
  `UNKNOWN` at confidence `0.0`; disabled/offline/error paths return `null`.
- VirusTotal maps any malicious engine count to `MALICIOUS` at `0.95`, otherwise any suspicious
  count to `SUSPICIOUS` at `0.75`, otherwise to `UNKNOWN` at `0.0`; request and parse failures
  return `null`.
- Neither production provider currently emits `CLEAN`, although the shared verdict enum supports
  it and conflict behavior still requires a safe policy.

## 3. Final Decision Model

The new core result model uses:

```kotlin
enum class ProtectionDecision {
    ALLOW,
    WARN,
    BLOCK
}

enum class ProtectionAction {
    CONTINUE,
    PROCEED_WITH_CAUTION,
    DO_NOT_CONTINUE
}
```

`ProtectionDecision` is deliberately separate from `ReputationVerdict`. For example,
`ReputationVerdict.MALICIOUS` is provider evidence; the resulting user action is
`ProtectionDecision.BLOCK`.

Legacy `ScanResult` constructors remain source-compatible through default values. Their default
decision mapping is `GREEN -> ALLOW`, `YELLOW/RED -> WARN`, and `CRITICAL -> BLOCK`.

## 4. Allow, Warn, and Block Rules

Decision evaluation is precedence-based:

```text
Any deduplicated provider MALICIOUS -> BLOCK
else local score >= 90             -> BLOCK
else any provider SUSPICIOUS       -> WARN
else local score >= 30             -> WARN
else                               -> ALLOW
```

This means:

- `ALLOW`: local score `0..<30`, no malicious provider, and no suspicious provider.
- `WARN`: local score `30..<90` or at least one suspicious provider, unless stronger blocking
  evidence exists.
- `BLOCK`: local critical score `>=90` or at least one malicious provider.

The established risk bands remain separate:

| Final score | Risk level |
| ---: | --- |
| `<30` | `GREEN` |
| `30..<70` | `YELLOW` |
| `70..<90` | `RED` |
| `>=90` | `CRITICAL` |

A combination of suspicious signals can produce a `RED` risk band while the user action remains
`WARN`; only malicious or critical local evidence produces `BLOCK`.

## 5. Provider Precedence

Provider precedence is:

```text
MALICIOUS > SUSPICIOUS > CLEAN > UNKNOWN > unavailable/failure/timeout
```

- Any `MALICIOUS` provider forces `BLOCK` and a final score of at least 90.
- Otherwise any `SUSPICIOUS` provider forces at least `WARN` and a final score of at least 30.
- `CLEAN` can support confidence in an `ALLOW` decision but cannot lower score or cancel positive
  evidence.
- `UNKNOWN` is not clean and has no risk-reducing effect.
- Unavailable, failed, and timed-out providers do not change risk or force a warning by themselves.

## 6. Conflict Handling

Conflicts are resolved by precedence, never averaging:

| Evidence | Result |
| --- | --- |
| OpenPhish `MALICIOUS` + VirusTotal `UNKNOWN` | `BLOCK` |
| Provider `MALICIOUS` + provider `CLEAN` | `BLOCK`; clean does not discount score |
| VirusTotal `SUSPICIOUS` + OpenPhish `UNKNOWN` | `WARN` |
| Local critical + provider failures | `BLOCK` |
| Local low + all provider failures | `ALLOW`, with incomplete-coverage summary and zero allow confidence |

Duplicate observations from the same case-insensitive provider name are collapsed before fusion.
The strongest verdict wins; ties use bounded confidence and stable reason ordering. Identical
provider evidence therefore cannot be counted repeatedly.

## 7. `UNKNOWN` Handling

An explicit provider `UNKNOWN` becomes a structured provider finding with:

- status `UNKNOWN`;
- verdict `"UNKNOWN"`;
- the provider name, bounded confidence, and original provider reason.

It contributes no score, does not reduce local risk, and does not force a warning on a low-risk
URL. Allow summaries use "no conclusive verdict" wording and never call the URL guaranteed safe.
All-unknown allow confidence is zero because no provider produced positive clean support.

## 8. Failure, Timeout, and Null Handling

`ReputationManagerImpl` now converts each provider attempt into `ReputationEvidence`:

| Provider outcome | Status | Risk effect |
| --- | --- | --- |
| Non-unknown result | `COMPLETED` | Verdict policy applies |
| Explicit `UNKNOWN` result | `UNKNOWN` | None |
| Provider returns `null` | `UNAVAILABLE` | None |
| Provider throws | `FAILED` | None |
| Per-provider timeout | `TIMED_OUT` | None |

The current provider contract deliberately returns `null` for several internally different
conditions, including disabled, offline, HTTP, and parse failures. The manager cannot safely infer
which occurred, so it reports the honest aggregate status `UNAVAILABLE` instead of claiming a
successful check. Exceptions and timeouts remain separately identifiable.

External coroutine cancellation is rethrown. The implementation uses a wrapper value inside
`withTimeoutOrNull` to distinguish a provider's real `null` result from a timeout without catching
or swallowing parent cancellation.

## 9. Risk-Score Formula

The existing positive-evidence formula is retained and made precedence-safe.

Let:

```text
L = clamp(localScore, 0, 100) / 100
P(malicious provider) = 0.90 * clamp(providerConfidence, 0, 1)
P(suspicious provider) = 0.60 * clamp(providerConfidence, 0, 1)
P(clean/unknown/unavailable/failed/timed-out provider) = 0

fused = 100 * (1 - (1 - L) * product(1 - P(provider)))
```

Then apply verdict floors:

```text
if any MALICIOUS: finalScore = max(fused, 90)
else if any SUSPICIOUS: finalScore = max(fused, 30)
else: finalScore = localScore

finalScore = clamp(finalScore, 0, 100)
```

The `0.90` malicious and `0.60` suspicious weights are the existing combiner weights. The 30 and
90 floors are the existing warning and critical risk thresholds. No clean discount remains.

## 10. Confidence Formula

Final confidence is distinct from risk score and is bounded to 0..1. It describes support for the
selected decision, not another threat score.

Independent support values combine as:

```text
combine(c1..cn) = 1 - product(1 - clamp(ci, 0, 1))
```

- `BLOCK`: combine all malicious-provider confidences and `localScore / 100` when local score is
  at least 90.
- `WARN`: combine all suspicious-provider confidences and `localScore / 100` when local score is
  30..<90.
- `ALLOW`: local margin is `(30 - localScore) / 30`, clamped to 0..1. With no provider
  observations, confidence is that local margin. With observations, it is
  `localMargin * combinedCleanConfidence * cleanProviderCount / providerObservationCount`.

Thus multiple independent malicious providers increase confidence without exceeding 1. Unknown,
null, failure, and timeout never lower risk. They provide no positive allow confidence, so
incomplete or inconclusive online coverage cannot masquerade as certainty.

## 11. Reason Aggregation and Stable Ordering

Structured `ScanReason` values use this order:

1. malicious provider evidence;
2. suspicious provider evidence;
3. local findings in the heuristic engine's registered rule order;
4. clean/unknown provider information;
5. unavailable/failure/timeout provider status.

Provider observations are normalized and sorted deterministically. Local rule order is preserved
by `LinkProtectionAgent`, which associates each `RuleResult` with the corresponding existing rule
ID/name without changing `LinkHeuristicRiskEngine`.

## 12. Deduplication Behavior

- Duplicate provider observations are collapsed case-insensitively by provider name before score
  and confidence calculation.
- Display reasons are deduplicated by normalized reason text after severity ordering, so the
  highest-priority attribution survives.
- `LocalEvidence.findings` still retains every contributing local rule, even if two rules happen
  to share display text, so rule attribution is not lost.
- `providerFindings` still retains every distinct provider and its status, even if providers share
  a generic failure reason.

## 13. Result Model Changes

`ScanResult` now exposes:

- `decision`;
- bounded `riskScore` and `confidence`;
- `headline` and short `summary`;
- structured, attributed `reasons`;
- structured `providerFindings` with name/status/verdict/confidence/reason;
- structured `localEvidence` with score/risk level/trigger count and each rule ID/name/category/
  contribution/reason;
- `recommendedAction`;
- the existing `riskLevel` and backward-compatible `explanation` string.

No query values, URL credentials, API keys, tokens, or full URLs are introduced into these
findings. Existing provider and local reasons contain only diagnostic labels or aggregate counts.

## 14. `IntentThreatAnalyzer` Integration

The audited `IntentThreatAnalyzerImpl` already performs the required sequence:

1. `LinkScanner.scan(payload.url)` obtains local evidence.
2. `ReputationManager.enrich(localResult, ReputationTarget.Url(payload.url))` performs provider
   lookup.
3. `ReputationManagerImpl` runs supported providers concurrently and passes every structured
   outcome to `EvidenceCombiner`.
4. `EvidenceCombiner` returns the complete final `ScanResult`.
5. The analyzer emits and returns that final object.

No provider is called directly by the analyzer, and scoring policy is not duplicated there.
File payload behavior remains unchanged: it passes `target = null` and returns the existing
file-analysis result.

## 15. Files Modified

Production:

- `android-app/core/src/main/java/com/sentinel/ai/core/model/ScanResult.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/link/LinkProtectionAgent.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/reputation/EvidenceCombiner.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/reputation/ReputationManagerImpl.kt`
- `android-app/app/src/main/java/com/sentinel/ai/protection/intent/reputation/ReputationEvidence.kt` (new)

Tests:

- `android-app/app/src/test/java/com/sentinel/ai/protection/intent/reputation/EvidenceCombinerTest.kt`
- `android-app/app/src/test/java/com/sentinel/ai/protection/intent/reputation/ReputationManagerImplTest.kt`
- `android-app/app/src/test/java/com/sentinel/ai/protection/intent/IntentThreatAnalyzerImplTest.kt` (new)
- `android-app/app/src/test/java/com/sentinel/ai/core/model/ScanResultTest.kt` (new)

Documentation:

- `phase-2.3-final-risk-decision.md` (new)

`IntentThreatAnalyzerImpl` was audited and required no production change.

## 16. Tests Added

- `EvidenceCombinerTest`: 34 tests total, replacing the previous 6-test score-only suite with 28
  net new cases. Coverage includes local-only, provider-only, combined, conflict, status,
  deduplication, ordering, finite/non-finite boundaries, formula, confidence, wording, and
  repeated-input determinism.
- `IntentThreatAnalyzerImplTest`: 8 new tests for local/target handoff, final result return,
  malicious integration, failure fallback, local-only fallback, invalid URL control, real local
  rule attribution, and cancellation during both local and reputation stages.
- `ScanResultTest`: 3 new tests for legacy constructor compatibility, default decision/action
  mapping, and structured field retention.
- `ReputationManagerImplTest`: 8 tests total, adding 3 net cases and expanding existing assertions
  for unavailable, explicit unknown, failed, timed-out, concurrent successful, and externally
  cancelled provider outcomes.

Net app tests added: 42. App suite total: 214.

## 17. Existing Tests Changed

- `EvidenceCombinerTest` assertions were intentionally updated from the old clean-discounted,
  score-only behavior to explicit precedence and structured results.
- `ReputationManagerImplTest` assertions were intentionally updated because failed and timed-out
  providers are now retained as non-malicious status findings instead of silently discarded.
- Existing link heuristic, URL normalizer, OpenPhish, VirusTotal, warning-model, file-analysis, and
  other app expectations were not weakened or changed.

## 18. Commands Run

All final Gradle commands used Android Studio's bundled JBR:

`JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`

They also used `--console=plain --no-watch-fs`.

1. `.\gradlew.bat :app:compileDebugKotlin`
2. `.\gradlew.bat :app:testDebugUnitTest --tests '*EvidenceCombiner*'`
3. `.\gradlew.bat :app:testDebugUnitTest --tests '*IntentThreatAnalyzer*'`
4. `.\gradlew.bat :app:testDebugUnitTest --tests '*ScanResult*'`
5. `.\gradlew.bat :app:testDebugUnitTest --tests '*ReputationManagerImpl*'` (additional focused check)
6. `.\gradlew.bat :app:testDebugUnitTest --tests '*LinkHeuristicRiskEngine*'`
7. `.\gradlew.bat :app:testDebugUnitTest --tests '*OpenPhishReputationProviderTest'`
8. `.\gradlew.bat :app:testDebugUnitTest --tests '*VirusTotalReputationProviderTest'`
9. `.\gradlew.bat :app:testDebugUnitTest`
10. `.\gradlew.bat testDebugUnitTest`
11. `.\gradlew.bat assembleDebug`
12. `git diff --check`

The first sandboxed Gradle attempt could not download the pinned Gradle 8.7 wrapper because
network access was restricted. The approved retry downloaded only that repository-pinned build
tool. All unit tests themselves remained deterministic and offline.

## 19. Exact Results

| Command | Exact final result |
| --- | --- |
| `:app:compileDebugKotlin` | `BUILD SUCCESSFUL in 22s`; 79 actionable tasks: 2 executed, 77 up-to-date. |
| `*EvidenceCombiner*` | `BUILD SUCCESSFUL in 30s`; 34 tests, 0 failures/errors/skips; 100 actionable tasks: 9 executed, 91 up-to-date. |
| `*IntentThreatAnalyzer*` | `BUILD SUCCESSFUL in 10s`; 8 tests, 0 failures/errors/skips; 100 actionable tasks: 1 executed, 99 up-to-date. |
| `*ScanResult*` | `BUILD SUCCESSFUL in 1m 51s`; 3 tests, 0 failures/errors/skips; 100 actionable tasks, all up-to-date. |
| `*ReputationManagerImpl*` | `BUILD SUCCESSFUL in 33s`; 8 tests, 0 failures/errors/skips; 100 actionable tasks: 1 executed, 99 up-to-date. |
| `*LinkHeuristicRiskEngine*` | `BUILD SUCCESSFUL in 22s`; 89 tests in 4 suites, 0 failures/errors/skips; 100 actionable tasks: 1 executed, 99 up-to-date. |
| `*OpenPhishReputationProviderTest` | `BUILD SUCCESSFUL in 44s`; 29 tests, 0 failures/errors/skips; 100 actionable tasks: 1 executed, 99 up-to-date. |
| `*VirusTotalReputationProviderTest` | `BUILD SUCCESSFUL in 25s`; 17 tests, 0 failures/errors/skips; 100 actionable tasks: 1 executed, 99 up-to-date. |
| `:app:testDebugUnitTest` | `BUILD SUCCESSFUL in 48s`; 214 tests in 14 suites, 0 failures/errors/skips; 100 actionable tasks: 1 executed, 99 up-to-date. |
| `testDebugUnitTest` | `BUILD SUCCESSFUL in 26s`; 269 tests in 28 suites, 0 failures/errors/skips; 128 actionable tasks, all up-to-date. |
| `assembleDebug` | `BUILD SUCCESSFUL in 1m 42s`; 171 actionable tasks: 3 executed, 168 up-to-date. |
| `git diff --check` | Exit code 0; no whitespace errors. |

## 20. Known Limitations

- The provider `null` contract does not distinguish disabled, offline, HTTP failure, parse failure,
  and intentionally unavailable results. Phase 2.3 reports `UNAVAILABLE` rather than guessing.
- Structured result fields are available in the live `ScanResult`, but the existing Room entity
  still persists only legacy score/risk/explanation fields. Room was explicitly out of scope.
- Existing UI models continue to consume `riskLevel` and `explanation`; they do not render the new
  structured fields yet.
- Provider verdict is stored in core `ProviderFinding` as its stable enum name string to avoid
  reversing the existing core-to-app module dependency.
- The invalid-URL policy remains the existing local heuristic behavior. Phase 2.3 makes its result
  controlled and conservatively worded but does not add a new invalid-URL rule.
- No browser block/continue action is executed; `recommendedAction` is data only.

## 21. Recommended Next Phase

Recommended Phase 2.4: implement the warning/result UI as a pure consumer of `decision`,
`headline`, `summary`, structured reasons, provider findings, local findings, confidence, and
`recommendedAction`. Keep browser handoff/block enforcement as a separate follow-up so UI
presentation and navigation policy can be tested independently. A later persistence phase can
decide how to serialize structured findings without coupling Room migrations to click-time UI
work.

## Completion Status

- Local and reputation evidence combine in one place: complete.
- Deterministic final action exists: complete.
- Malicious evidence cannot be diluted: complete.
- Suspicious evidence forces at least warning: complete.
- Unknown/failure do not imply safety: complete.
- Local-only fallback: complete.
- Score and confidence bounded: complete.
- Structured source attribution and deduplication: complete.
- Analyzer integration and cancellation: complete.
- Existing heuristic, OpenPhish, and VirusTotal behavior: preserved and verified.
- Full app and multi-module unit tests: passed.
- Debug APK assembly: passed.
- New production dependency: none.
- UI/browser handoff: intentionally not implemented.
