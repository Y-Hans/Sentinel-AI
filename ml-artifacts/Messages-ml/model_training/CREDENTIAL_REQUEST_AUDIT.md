# CREDENTIAL_REQUEST Audit

Audit date: 2026-08-28  
Population: untouched `val.jsonl` + `test.jsonl` + `ood.jsonl` records whose existing `threat_vectors` contains `CREDENTIAL_REQUEST`.

## Scope and method

The audit reconstructed the recorded `F_exact` recipe: `train_expanded_v5.jsonl`, word TF-IDF (1,500 features), character TF-IDF (500 features), 70 deterministic features, `HistGradientBoostingClassifier(max_depth=7, max_iter=300, random_state=42, class_weight=balanced)`. The HN-safe operating threshold was `0.823`, selected from the prior VAL/HN sweep. TEST and OOD were used only for reporting.

Every record is retained in `CREDENTIAL_REQUEST_AUDIT.json` with its original label, split, model prediction, probability, audit verdict, and rationale. No labels or evaluation files were changed.

## Population result

| Item | Count |
|---|---:|
| Total records | 2,302 |
| Original `MALICIOUS` labels | 2,302 |
| Original `BENIGN` / `SUSPICIOUS_SPAM` labels | 0 / 0 |
| Model `MALICIOUS` predictions | 1,823 |
| Model non-malicious predictions | 479 |
| Measured recall | 1,823 / 2,302 = **79.19%** |
| Unique texts | 2,239 |
| Duplicate text groups | 41 |

Audit verdicts:

| Verdict | Count |
|---|---:|
| `VERIFIED_MALICIOUS` | 27 |
| `SUSPICIOUS` | 602 |
| `ANTI_PHISHING` | 17 |
| `BENIGN` | 0 |
| `AMBIGUOUS` | 1,656 |

The record-level rules are conservative: `VERIFIED_MALICIOUS` requires explicit credential/OTP/PIN/password/CVV theft or induced credential entry; explicit warnings not to disclose credentials are `ANTI_PHISHING`; phishing-like but non-explicit content is `SUSPICIOUS`; remaining taxonomy assignments lacking enough evidence are `AMBIGUOUS`. These are audit verdicts, not replacement labels.

## Diagnosis

The measured 79.19% is not a clean estimate of credential-theft recall. All 2,302 records are labelled MALICIOUS, while only 27 provide definitive textual evidence under the conservative audit and 17 explicitly warn users not to disclose credentials. The large ambiguous population includes generic conversational/social-engineering-like and non-credential messages. The model missed 479 original-label records, but missed **0/27** `VERIFIED_MALICIOUS` records in this audit.

Therefore the gate is a combination of model performance and evaluation-definition/data-quality problems. The metric must **remain unchanged** for deployment accountability; it must not be silently recomputed using the audit verdicts. The dataset requires an independent annotation review before the gate can be interpreted as genuine credential-theft recall.

## Decision

No targeted training set or challenger was created: the audit did not demonstrate a missed, definitively malicious credential pattern requiring remediation. The existing F_exact baseline remains frozen. Existing TEST/OOD labels and all historical experiments remain intact.
