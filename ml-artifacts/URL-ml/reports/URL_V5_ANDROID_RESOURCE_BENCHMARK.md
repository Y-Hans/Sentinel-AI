# URL-ML V5 Android and Resource Benchmark

Candidate: `model/challengers/v5_hgb92_rem160/`.

- Feature vector: 92 deterministic scalar features.
- Model bundle (`model.joblib` + `scaler.joblib`): 700,927 bytes.
- Model-only inference: approximately 0.615 ms/URL on the development host in a 1,000-call loop.
- Repeated predictions were exactly deterministic.
- Feature extraction is pure local parsing/statistics with no network dependency and is Kotlin-portable in principle.

This demonstrates engineering feasibility but is not an Android release qualification: the candidate is not promoted because it fails TEST FPR and hard-source FPR gates. Native Android-device latency and a production Kotlin artifact remain future release work after data remediation succeeds.
