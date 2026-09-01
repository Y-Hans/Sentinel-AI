# URL-ML V6 Optimization Walkthrough

## Decision

**DATA_REQUIRED.** No V6 candidate satisfies the release gates. V6 continued the campaign with two new hypotheses and complete protected evaluation; neither closed the source-shift gap.

## Initialization and protection

V5 was read before experimentation. The frozen champion remains `model/champion/` and its SHA-256 is `bd412cae0749e18ee85ca3cb069ca1de7f67c9ea37f4f59d9ea0adb8bffbac2a`. The clean source split is 141,178 train, 44,106 validation, and 50,511 TEST rows, grouped by registrable domain. `hard_dataset.csv` and the adversarial suite were loaded only after fitting and validation-only threshold selection.

V6 verified the clean hash (`6c71c39d...caf2f`), protected hard hash (`fc0966ed...177f`), and remediation v3 hash (`0f8da3fb...527e`). Historical V1–V5 work already covered tree ensembles, linear models, hashed character representations, 92 deterministic features, weighting, source-diverse remediation, threshold frontiers, adversarial tests, and independent reproduction.

## Experiments

1. **Domain-disjoint synthetic structural coverage.** 400 explicitly synthetic benign rows across 20 `.example` registrable domains and account/security/payment/deep/query/HTTP/localization structures were added to clean + remediation v3. ExtraTrees and HGB variants preserved TEST performance but produced hard-source benign FPRs of 36.96%, 36.65%, and 46.91%. The hypothesis failed; the synthetic rows and artifacts are retained with provenance.
2. **Normalized character morphology.** A compact char 2–5-gram TF-IDF logistic model trained on clean + remediation v3 achieved 0.786% TEST FPR and 99.663% TEST recall, but hard-source benign FPR was 69.83% (AUC 0.883). The hypothesis failed despite excellent ordinary TEST results.

## Stop rationale

The unresolved errors are source-shifted legitimate provider/infrastructure, deep-path, HTTP, query-heavy, account/security/payment, redirect/tracking, localized/multilingual, encoded, and long URL families. Historical source-diverse remediation reduced the gap substantially, but V6’s independent data and representation attempts moved the protected frontier in the wrong direction. Multiple materially different model classes and representations have failed, while ordinary TEST and adversarial performance remain strong. The evidence supports a requirement for new genuinely sourced and adjudicated benign URLs, not another blind threshold or lightweight architecture sweep.

No protected row was copied, paraphrased, mutated, augmented, or used for threshold/feature selection. No champion, Messages-ML, Android integration, or historical V1–V5 report was overwritten.
