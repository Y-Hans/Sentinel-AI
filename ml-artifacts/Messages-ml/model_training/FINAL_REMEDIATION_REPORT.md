# Messages-ML Final Remediation Report

## Decision

**`NO_MODEL_READY`**  
**`DATASET_REMEDIATION_REQUIRED`**

The deployment blocker is not cleared. The measured credential recall remains below the required 80% (`1,823/2,302 = 79.19%`) under the frozen F_exact operating point. The audit also shows that the metric population is materially ambiguous, so the result cannot be interpreted as a clean measure of genuine credential-theft recall. This is not permission to relax the gate.

## Reasoning chain

The prior campaign explored the documented model families, feature sets, weighting strategies, data expansions, thresholds, seeds, hard negatives, and holdouts. F_exact was the best stable HN-safe baseline: TEST benign-any-nonbenign FPR 0.00%, malicious recall 86.96%; OOD FPR 0.00%, malicious recall 88.69%; hard-negative FPR 0.00%; credential recall 79.19%; size about 1.87 MB. The later seed-22 challenger passed the recorded validation gates but failed untouched TEST FPR, so it was not promoted.

The record-level credential audit found 2,302 records, all originally labelled MALICIOUS: 27 `VERIFIED_MALICIOUS`, 602 `SUSPICIOUS`, 17 `ANTI_PHISHING`, and 1,656 `AMBIGUOUS`. F_exact predicted MALICIOUS for 1,823 and missed 479 original-label records, but missed 0 of the 27 conservatively verified malicious records. This demonstrates a substantial evaluation-definition/data-quality problem alongside the nominal model shortfall.

Because no definitive malicious failure pattern was established, no synthetic contrastive data and no new challenger were justified. This preserves scientific scope and avoids training to questionable labels. The metric definition remains unchanged pending independent annotation review.

## Gate record

| Gate | Result | Status |
|---|---:|---|
| TEST benign-any-nonbenign FPR | 0.00% | PASS |
| TEST malicious recall | 86.96% | PASS |
| OOD benign-any-nonbenign FPR | 0.00% | PASS |
| OOD malicious recall | 88.69% | PASS |
| Verified hard-negative FPR | 0.00% | PASS |
| Credential-request recall | 79.19% | FAIL |
| Model size | ~1.87 MB | PASS |
| Required test suite | 40/40 historically reported | PASS (historical) |

The untouched TEST/OOD and hard-negative detailed matrices remain in the prior verification artifacts. They were not used to select a new threshold or challenger.

## Artifacts

- `CREDENTIAL_REQUEST_AUDIT.md` and `CREDENTIAL_REQUEST_AUDIT.json`: complete record-level audit.
- `FINAL_REMEDIATION_REPORT.md` and `FINAL_REMEDIATION_REPORT.json`: this decision and evidence chain.
- `autonomous_optimization_results.json`: historical JSONL registry preserved and appended with the audit event.
- `audit_credential_request.py`: reproducible audit procedure.
- `champion_v2_*_s22_precontinuation.pkl` and `CHAMPION_V2_CONFIG_s22_precontinuation.json`: recoverable backup of the pre-continuation active artifacts.

`URL-ml/` was not modified. No TFLite/Android packaging, commit, or push was performed.
