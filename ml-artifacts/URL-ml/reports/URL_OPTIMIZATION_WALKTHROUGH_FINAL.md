# URL-ML Optimization Walkthrough — Focused Final Phase

## Previously established

The prior campaign reproduced the frozen `extratrees_full` baseline: TEST FPR 0.96%, TEST malicious recall 98.53%, and hard-source FPR 99.39%. Source-invariant feature ablations did not solve the collapse. Source-diverse v1/v2 remediation reduced hard-source FPR to 16.69% with 96.91% hard recall, but TEST recall fell to 92.44%.

## Focused phase

Residual forensic analysis of the v2 finalist found 327 hard-source benign false positives across 67 registrable domains. The largest cluster was ordinary/deep provider paths (220/327); residuals were disproportionately non-HTTPS and non-recognized-provider URLs. A targeted v3 dataset added 980 legitimate HTTP/ordinary/deep path records over 49 source-diverse providers, with no TEST/OOD/hard row copied or transformed.

Fresh v3 ExtraTrees training reduced hard-source FPR to 5.67% at validation-safe threshold 0.459, but TEST malicious recall declined to 89.40%. A lower remediation weight recovered only 90.50% TEST recall while hard-source FPR rose to 16.13%. The validation-only threshold sweep showed no point preserving the prior TEST recall/FPR profile and the hard-source improvement simultaneously.

## Final decision

**BOTH_REQUIRED**: more adjudicated, source-diverse legitimate URL coverage is required, and the current 15-feature representation/model cannot reliably separate non-recognized legitimate providers from malicious structural patterns. The frozen champion, historical reports, TEST/OOD/hard data, Messages-ML, and Android integration were preserved.
