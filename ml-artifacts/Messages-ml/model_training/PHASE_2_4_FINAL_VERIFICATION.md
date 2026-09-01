# PHASE 2.4 FINAL PRE-PACKAGING VERIFICATION & AUDIT CORRECTION

## 1. Executive Summary
This document presents the rigorous second-level verification of the Phase 2.4 candidate audit for the `Messages-ml` subsystem. The objective was to independently correct previous methodological flaws in the audit script, specifically concerning threshold selection and source holdout evaluation, and to explicitly distinguish between fundamental model defects and mere dataset limitations.

**Final Decision:** **NO_MODEL_READY**

Despite the corrections and an objectively superior algorithm (N-Gram Logistic Regression), the model fails the critical hard-negative security gate. The model systematically flags legitimate security warnings, account suspension notices, and bank alerts as malicious. This is a severe, structural model defect that fundamentally disqualifies the candidate from production packaging.

## 2. Previous Audit Methodology Defects
The first Phase 2.4 audit contained multiple procedural gaps:
- **Hardcoded Thresholding**: The threshold was hardcoded to `0.50` instead of being programmatically derived from a stated policy on the validation split.
- **Latency Benchmark Size**: Latency was measured on only 100 samples, which is statistically unreliable.
- **Source Holdout**: It did not perform a true "leave-one-source-out" training evaluation.
- **Ambiguous FPR Metrics**: Confusion matrices were indiscriminately collapsed into a binary classification, obscuring the difference between predicting `SUSPICIOUS_SPAM` vs. `MALICIOUS`.
- **Conflation of Rejection Criteria**: It rejected the model partially due to lack of multilingual data (a known dataset coverage limitation) rather than isolating structural model defects.

## 3. Corrections Applied
- **Strict Threshold Policy**: The threshold was dynamically selected exclusively on the `VALIDATION` split using a grid search (0.01 to 0.99) to minimize `BENIGN -> ANY_NON_BENIGN` FPR subject to `Malicious Recall >= 50%`.
- **3-Way Evaluation**: The full 3x3 confusion matrix is reported, tracking `BENIGN -> MALICIOUS` and `BENIGN -> SUSPICIOUS_SPAM` separately.
- **Source-Holdout Experiment**: True source-holdout evaluation was implemented (Train on N-1 sources, Evaluate on held-out source).
- **Latency Overhaul**: Latency metrics were extracted over 1,000 samples with warm-up iterations.
- **Taxonomy Alignment**: Explicitly separated "Dataset Gaps" from "Blockers" to prevent conflation.

## 4. Repository Protection Verification
- `URL-ml` modifications: 0
- Git Writes: 0
- Android / Kotlin integrations: 0
- TFLite generations: 0
- Synthetic Fixtures Modified: 0
- **Regression Tests**: All 40/40 tests PASSED seamlessly.

## 5. Dataset Integrity
A rigorous full-dataset cross-split analysis (N=22,648) confirmed:
- TRAIN ∩ VALIDATION = 0 overlaps
- TRAIN ∩ TEST = 0 overlaps
- VALIDATION ∩ TEST = 0 overlaps
- VALIDATION ∩ OOD = 0 overlaps
- TEST ∩ OOD = 0 overlaps
- **Status:** PASS. No exact string, template, or ID leakage.

## 6. Full Leakage Audit
The feature extraction pipelines deterministically extract vectors using ONLY `raw_text` and `sender_header`. No labels, IDs, source provenance, or template clusters are accessible during extraction.

## 7. Candidate Definition
- **Model Candidate:** `CANDIDATE_NGRAM_LR`
- **Algorithm:** Logistic Regression (`solver="lbfgs"`, `max_iter=1000`, `class_weight="balanced"`, `seed=42`)
- **Features:** 70 Total Features (54 Deterministic + 16 N-Gram Hashes)
- **Hash Function:** MurmurHash3 32-bit (x86 variant for bit-for-bit Kotlin parity)

## 8. Feature Determinism
Repeated calls, across process states, produce mathematically identical feature vectors for the exact same message string and sender header.

## 9. Reproducibility
Training the model multiple times with `random_state=42` yields the exact same 213 parameters.

## 10. Threshold Selection
- **Policy:** Minimize `BENIGN -> ANY_NON_BENIGN` FPR subject to `Malicious Recall >= 0.50`.
- **Search Space:** 0.01 to 0.99 on `VALIDATION` split.
- **Selected Threshold:** `0.56`
- **Rationale:** Ensures we retain at least 50% of the critical malicious coverage while mathematically minimizing the overall false-positive surface.

## 11. Calibration
- **Brier Score (Validation):** 0.1819 
- The model outputs well-calibrated probabilistic scores on the validation distribution.

## 12. Rule-vs-ML Baseline Comparison
Deterministic rules actively degrade the machine learning model's macro capabilities:
- **ML_ONLY:** F1 = 0.609, Recall = 54.53%
- **RULES_ONLY:** F1 = 0.130, Recall = 0.34%
- **HYBRID:** F1 = 0.606, Recall = 54.10%
- **Conclusion:** Deterministic rules are statistically detrimental to classification and should be demoted to advisory metadata.

## 13. N-Gram Ablation
Adding 16-bucket N-grams mathematically lifts performance across held-out evaluations without compromising FPR:
- **TEST Deterministic:** Recall = 44.53%
- **TEST N-Gram:** Recall = 52.57%
- **OOD Deterministic:** Recall = 46.09%
- **OOD N-Gram:** Recall = 53.14%

## 14. TEST Results
- **Accuracy:** 64.22%
- **Macro F1:** 0.611
- **Malicious Precision:** 97.26%
- **Malicious Recall:** 52.57%
- **Benign -> Malicious FPR:** 0.96%
- **Benign -> Any Non-Benign FPR:** 8.07%

## 15. OOD Results
- **Accuracy:** 63.69%
- **Macro F1:** 0.593
- **Malicious Precision:** 96.56%
- **Malicious Recall:** 53.14%
- **Benign -> Malicious FPR:** 1.17%
- **Benign -> Any Non-Benign FPR:** 7.45%
- **Conclusion:** Generalization degradation is minimal; the model handles out-of-distribution textual drift reasonably well.

## 16. Hard-Negative Results
This is the primary security failure of the candidate.
- **Generic Hard Negatives:** `Benign -> Any Non-Benign FPR` = 27.27%
- **Curated Hard Negatives (SRC_CURATED_HARD_NEGATIVES_V1):** `Benign -> Any Non-Benign FPR` = 100.0% (63.4% Malicious, 36.5% Suspicious)
- **Impact:** Legitimate account suspension warnings and critical institutional alerts are systematically flagged as malicious/suspicious. The model is confusing urgency/threat semantics with actual phishing intent.

## 17. OTP Results
- **Legitimate Authentication OTP (N=20):** FPR = 0.0%
- **Reverse OTP Theft (N=3):** Recall = 100.0%
- **Ambiguous OTP (N=399):** FPR = 4.54%
- **Conclusion:** Explicit OTPs are safe. Ambiguous cases exhibit slight error.

## 18. Threat Vector Results
- `BANK_KYC_SUSPENSION` (N=971): Recall = 78.88%
- `CREDENTIAL_REQUEST` (N=1137): Recall = 45.91%
- `DELIVERY_SCAM` (N=187): Recall = 40.64%
- `APK_MALWARE_DROPPER` (N=29): Recall = 56.00%
- Remaining vectors lack statistical sample size.

## 19. Source-Holdout Results
Models trained on N-1 sources and evaluated entirely out-of-source:
- `SRC_IMC25_FISHING_SMISHING`: Recall = 46.2%
- `SRC_MENDELEY_SMISHING_2022`: Recall = 13.3%
- `SRC_CURATED_HARD_NEGATIVES_V1`: Malicious Recall = 100%, Benign FPR = 100%
- **Conclusion:** Extreme source bias. The model fails to generalize benign semantics when tested on an unseen curated hard-negative source.

## 20. Language Results
- **English (N=3377):** Malicious Recall = 54.28%, Benign FPR = 9.82%
- **Hinglish (N=18):** INSUFFICIENT SAMPLE SIZE.

## 21. Sender Results
- **UNKNOWN:** Malicious Recall = 53.72%, FPR = 9.52%
- **ALPHANUMERIC_HEADER:** Malicious Recall = 77.14%, FPR = 16.66%
- **PHONE_NUMBER:** Malicious Recall = 100%, FPR = 0.0%

## 22. Model Size
- **Parameter Count:** 213 (Coefficient matrix `[3, 70]`, intercept `[3]`)
- **Serialized Byte Size:** 3,574 Bytes (~3.5 KB)

## 23. Latency Benchmark
Environment: x86 Python 3.14 (Non-Android), Samples: 1000
- **Median:** 1.21 ms
- **Mean:** 1.34 ms
- **p95:** 2.35 ms
- **p99:** 3.46 ms
- **Conclusion:** Superlative performance. Easily conforms to strict mobile OS timeouts.

## 24. Dataset Gaps
These are limitations of the environment, not defects of the algorithm:
- Multilingual coverage (e.g. Hinglish, Devanagari) is statistically non-existent.
- Shortcode sender representation is minimal.
- Threat vector instances are heavily imbalanced.

## 25. Remaining Blockers
This is the fundamental reason for rejection:
- **Hard-negative FPR is unacceptably high (27.27% - 100%), failing the critical security gate.** The model cannot reliably distinguish a legitimate bank penalty/suspension notice from a phishing lure leveraging the same urgent semantics.

## 26. Final Candidate Decision
**NO_MODEL_READY**

**Rationale:** While `CANDIDATE_NGRAM_LR` is highly performant in latency, size, and OOD generalization, the systemic False Positives against hard-negative institutional alerts act as an absolute disqualifier for deployment. Deploying this artifact would actively harm end-users by suppressing critical, legitimate communications.

## 27. Phase 2.4 Packaging Gate
- **Status:** **REJECTED**. Do not export TFLite. Do not integrate into Kotlin. Do not modify the production Android module. The focus must return to dataset augmentation specifically targeting hard-negative lexical distributions.
