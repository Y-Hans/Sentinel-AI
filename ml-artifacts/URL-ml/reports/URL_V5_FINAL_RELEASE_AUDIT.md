# URL-ML V5 Final Release Audit

Decision: **DATA_REQUIRED** — no release-ready challenger.

The best V5 candidate is `model/challengers/v5_hgb92_rem160/`. Its threshold (`0.055`) was selected on validation only.

| Metric | Result | Gate | Status |
|---|---:|---:|---|
| TEST FPR | 1.060% | <=1% | FAIL |
| TEST malicious recall | 99.521% | >=98% | PASS |
| Hard-source FPR | 26.901% | <=1% | FAIL |
| Hard-source malicious recall | 96.314% | >=95% | PASS |
| Adversarial recall | 99.692% | >=95% | PASS |
| Model bundle size (model + scaler) | 700,927 bytes | <=10 MB | PASS |
| Model-only latency | ~0.615 ms/URL on development host | documented | PASS |

The 92-feature model class search is not empty: prior campaigns evaluated ExtraTrees, Random Forest, GradientBoosting, logistic/SGD, hashed character representations, and deterministic ablations. V5 HGB did not close the source-shift gap. The remaining blocker is therefore new adjudicated/source-diverse data.

## Integrity and reproducibility

- Frozen champion SHA-256: `bd412cae0749e18ee85ca3cb069ca1de7f67c9ea37f4f59d9ea0adb8bffbac2a`.
- Protected hard dataset SHA-256: `fc0966ed0deaea81496d5a3f384b8cdbdcd2019a2c58cbea153f6f0728ef177f`.
- Clean source SHA-256: `6c71c39dd0291b8ef58b31610ac892cd6fe2186876908d42a7b24dcbf06caf2f`.
- Remediation v3 SHA-256: `0f8da3fba16a33e6bbf9217cb6d24308cf28c2ab8b0988aeacbb39d21fad527e`.
- V5 model SHA-256: `1b8b85ebf7a956047fa4baf1d0e9ca7cdfef286a8afaf49199e210d7ac372ed1`.
- Independent same-process prediction agreement: exact agreement on repeated inference.
- Python compilation passed for URL-ML scripts. The repository has no pytest module installed; the existing TFLite smoke script exited successfully.
- No protected dataset, champion, Messages-ML, or Android integration file was changed by V5.
