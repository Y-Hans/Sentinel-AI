# URL-ML Hard-Source Forensic Report

The frozen champion labels 1,947 of 1,959 hard-source benign URLs as malicious (99.39% FPR). The failures are concentrated in legitimate provider URLs with non-empty paths/queries, security/account/payment vocabulary, and provider domains that are underrepresented in original benign training data.

A controlled source-invariance ablation showed that removing brand/TLD/identity-like features did not materially reduce hard-source FPR (still 99.39%), falsifying a simple identity-feature leakage explanation. Distribution evidence and complete rows are in `URL_HARD_SOURCE_FORENSIC_ROWS.csv`; false positives are in `URL_HARD_SOURCE_FALSE_POSITIVES.csv`.

The source-diverse v1/v2 remediation set reduced finalist hard-source FPR to 16.69% while preserving 96.91% hard-source malicious recall, but the remaining gap and TEST recall tradeoff prevent release.
