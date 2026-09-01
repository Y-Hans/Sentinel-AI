# URL-ML Final Release Audit V2

## Decision

**BOTH_REQUIRED**

Focused final optimization materially improved hard-source FPR, but no challenger preserves the prior security operating point while reaching a low hard-source FPR. The remaining blocker is both data coverage and representation/model separation.

## Finalist

`challenger_hardsource_v3_extra40`, threshold `0.4590`.

- TEST FPR / malicious recall: 0.96% / 89.40%
- Hard-source FPR / malicious recall: 5.67% / 97.03%
- Model size: 4.82 MB
- Independent reproduction: exact thresholded prediction agreement
- OOD: unavailable as a separate artifact

Per-TLD source-holdout, adversarial, resource, hashes, and reproducibility details are recorded in the JSON audit. No model was promoted.
