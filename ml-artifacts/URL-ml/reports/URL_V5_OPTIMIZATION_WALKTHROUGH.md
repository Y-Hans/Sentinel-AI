# URL-ML V5 Optimization Walkthrough

## Decision

**DATA_REQUIRED**. No challenger satisfies the complete release gate. The strongest new result is `v5_hgb92_rem160`; it improves hard-source benign FPR versus most earlier challengers but remains 26.90%, far above the 1% gate.

## Starting point and protocol

V4 established that the 92-feature deterministic representation improved the fixed domain-disjoint TEST set, while source-shifted hard-source benign FPR remained 12.40% at the best safe remediation-weight point. The frozen V1 champion remains at `model/champion/` and was not modified.

V5 used the established clean split from `cleaned_dataset.csv` only: 141,178 train rows, 44,106 validation rows, and 50,511 TEST rows, grouped by registrable domain. Remediation v3 added 3,430 benign rows. Thresholds were selected on validation only. `hard_dataset.csv` and the adversarial suite were evaluated only after fitting.

## Controlled experiment

The only new model-class experiment was HistGradientBoosting on the existing 92 deterministic host/path/query/interaction features, at remediation weights 40x and 160x. This was selected because prior V4 work had already covered ExtraTrees, Random Forest, GradientBoosting, logistic/SGD hashed character models, and representation ablations.

At 160x, validation FPR/recall was 0.980%/98.82%, TEST was 1.060%/99.52%, hard-source was 26.90%/96.31%, and adversarial recall was 99.69%. The 40x point had hard-source FPR 31.14% and TEST FPR 1.06%.

## Diagnosis and stop rationale

The residual failure is predominantly legitimate hard-source false positives under provider/infrastructure, deep-path, HTTP, query-heavy, and authentication-like morphology. V4 source-diverse remediation reduced the failure but did not approach the gate; changing model class did not solve it. The evidence across V3, V4, and V5 supports a missing real-world/adjudicated source-diverse benign coverage problem, not a threshold-selection or artifact-size problem.

Additional synthetic/template remediation without new adjudicated source coverage is not scientifically justified. A future campaign should acquire and adjudicate new provider, CDN, localized, multilingual, redirect/tracking, account-workflow, and long legitimate URL sources, with domain separation from all protected evaluations.

## Preservation

No promotion, packaging, commit, or push was performed. Messages-ML and Android integration were untouched. Historical V1–V4 reports and artifacts remain recoverable.
