# Final Release-Gate Audit

Audit timestamp: `2026-08-28T12:34:29.558282+00:00`  
Model: `CHAMPION_F_EXACT_RECONSTRUCTED`; train-derived `train_expanded_v5.jsonl`; threshold `0.823`.

## Decision

**CONDITION B — DATASET_REMEDIATION_REQUIRED.** The model passes the main untouched TEST/OOD gates under this reconstruction, but the credential gate is not scientifically interpretable as an actual-credential-theft recall measure until the over-broad `CREDENTIAL_REQUEST` population is independently annotated. The current reconstruction satisfies the hard-negative slice; the frozen V1 artifact independently reproduces the historical MSEDCL false-positive defect, and both versioned results are retained.

## Gate metrics

| Split | N | benign FPR | malicious recall |
|---|---:|---:|---:|
| VALIDATION | 3397 | 0.0037 | 0.8718 |
| TEST | 2264 | 0.0000 | 0.8914 |
| OOD | 1132 | 0.0000 | 0.8955 |

## Credential population

The untouched evaluation population is **2302** records, all originally `MALICIOUS`. Original-label recall is **79.19%** (1823/2302), with **479** misses. Derived categories are: `VERIFIED_MALICIOUS=15, PROBABLE_MALICIOUS=17, SUSPICIOUS=624, AMBIGUOUS=1629, ANTI_PHISHING=17`.

| Definition | Denominator | Recall |
|---|---:|---:|
| Original labels | 2302 | 79.19% |
| Verified malicious | 15 | 100.00% |
| Verified + probable | 32 | 71.88% |
| Conservative verified + probable + suspicious | 656 | 92.23% |

The 479 apparent misses in the historical audit are not 479 verified attacks: this run records misses by derived category in the JSON. Every one of the historical 27 `VERIFIED_MALICIOUS` examples is retained and checked; the verified-malicious stratum has **15/15** recall.

## Hard negatives

The curated source contains 107 records (72 benign and 35 malicious). Benign record-level FPR is **0.00%** across 72 records and **0.00%** across 10 unique messages. The frozen V1 artifact produces 10 MSEDCL false positives (13.89% record-level FPR on the current benign slice), while the current reconstruction produces zero. The JSON includes all predictions; duplicated parameterized templates are not treated as independent evidence.

## Integrity, limitations, and reproducibility

TEST/OOD are read-only evaluation inputs; no training, synthetic generation, threshold tuning, or label edits use them. The full file hashes, overlap checks, source/language/sender distributions, category rules, examples, predictions, and probabilities are in `FINAL_RELEASE_GATE_AUDIT.json`. The reconstruction is independent of the frozen champion artifact and therefore does not overwrite it; its relationship to the frozen champion and historical reports must be resolved before packaging. Language coverage is materially English-dominated, so multilingual deployment claims remain limited.

Historical artifacts preserved: `FINAL_CHAMPION_VERIFICATION.*`, `HARD_NEGATIVE_ADJUDICATION.*`, `CREDENTIAL_REQUEST_AUDIT.*`, and `FINAL_REMEDIATION_REPORT.*`.
