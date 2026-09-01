# URL-ML Remediation Dataset Report

V1 introduced 980 source-diverse benign records across 49 registrable domains absent from the original dataset. V2 added 1,470 targeted security/account/payment/query variants over the same providers (2,450 total). No TEST, OOD, or hard-source row was copied or transformed.

The expansion targets the observed distribution shift: legitimate provider URLs with security semantics and non-empty paths/queries. It is intentionally source-diverse but still synthetic/template-generated; real-world adjudicated acquisition remains a residual risk.

Dataset hashes and counts are recorded in `data/remediation_hard_negatives_v1.json` and `data/remediation_hard_negatives_v2.json`.
