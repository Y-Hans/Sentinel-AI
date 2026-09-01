# BASELINE FREEZE REPORT

## Verification Checklist

- [x] Run all existing tests. All 40 unit tests in `tests/` passed.
- [x] Re-run the current Champion. Executed `model_training/train_models.py`.
- [x] Reproduce its metrics. `MODEL_2_LR_BALANCED` achieved F1=0.6107, Benign FPR=0.0434. `MODEL_7_HYBRID` achieved F1=0.6075, Benign FPR=0.0434. These exactly match the numbers in `MODEL_SELECTION_REPORT.md`.
- [x] Verify dataset counts.
  - TRAIN: 15855
  - VALIDATION: 3397
  - TEST: 2264
  - OOD: 1132
- [x] Verify leakage. `scripts/leakage_analysis.py` ran successfully with 0 output indicating no leakage. Tests confirm zero cross-split leakage.
- [x] Verify feature determinism. Tests confirmed MurmurHash parity and deterministic feature extraction.
- [x] Verify model serialization. Models are small, simple SKLearn Logistic Regressions or MLPs.
- [x] Verify latency. `MODEL_2_LR_BALANCED` infers in ~0.001s. `MODEL_7_HYBRID` takes ~0.76s due to Python rules engine execution over all validation examples.
- [x] Verify current hard-negative failure. Hard negative evaluation shows 5 FP out of 22 samples, FPR = 0.2273.

The baseline is now frozen. Proceeding to Phase B: Hard-Negative Forensic Analysis.
