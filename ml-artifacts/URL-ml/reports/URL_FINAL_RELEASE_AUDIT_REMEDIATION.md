# URL-ML Final Release Audit — Remediation Campaign

## Decision

**MODEL_REMEDIATION_REQUIRED**

The strongest finalist materially reduces hard-source FPR but does not satisfy the full release framework: TEST recall falls below the frozen champion and hard-source FPR remains above the required low-FPR target. No new champion was promoted.

## Finalist

`challenger_hardsource_v2_extra40` at validation-only threshold `0.4525`.

- TEST FPR/recall: 0.0108 / 0.9244
- Hard-source FPR/recall: 0.1669 / 0.9691
- OOD: unavailable as a separate repository artifact
- Model size: 4824825 bytes
- Independent reproduction: True

Adversarial, source-holdout, resource, hashes, and detailed provenance are in the JSON audit. Historical champion and reports were preserved.
