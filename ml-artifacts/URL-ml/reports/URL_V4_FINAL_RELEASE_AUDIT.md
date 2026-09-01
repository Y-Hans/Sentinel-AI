# URL-ML V4 Final Release Audit

Decision: **NOT READY — SCIENTIFICALLY CONTINUING**.

- Frozen champion preserved and hash recorded in `URL_V4_EXPERIMENT_REGISTRY.json`.
- No TEST, OOD, or hard-source row was used for fitting, feature selection, model selection, or threshold selection.
- V4 deterministic and hashed character families were implemented and independently compared.
- Best clean TEST result: 0.713% FPR / 99.53% recall (1,592-feature hashed candidate).
- Best safe hard-source result: 12.40% FPR / 96.79% recall (160× remediation-weight deterministic candidate).
- Resource target passed for the 160× artifact: 4.88 MB model and approximately 0.015 ms model inference per URL on the development host.
- Release gates failed because hard-source benign FPR is far above 1%; no challenger was promoted.

The evidence supports the V3 conclusion that additional adjudicated, source-diverse training coverage is required in addition to representation work. Historical reports and the frozen champion remain recoverable.
