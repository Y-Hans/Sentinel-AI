# PHASE 2.4 CANDIDATE AUDIT & PRE-PACKAGING VERIFICATION

## 1. Executive Summary
This report documents the Phase 2.4 pre-packaging candidate finalization audit for the `Messages-ml` subsystem. The purpose is to evaluate the viability of the `CANDIDATE_NGRAM_LR` model (Logistic Regression + Full Deterministic + N-Gram Hash Features) for Android device packaging.

**Conclusion:** **NO_MODEL_READY**. While the `CANDIDATE_NGRAM_LR` provides a statistically superior baseline compared to the prior hybrid model, critical deployment constraints remain unresolved. Most notably, hard-negative false positive rates (22.7%), source bias, and a lack of multilingual coverage prevent approval for immediate production freezing. 

## 2. Candidate Definition
- **Architecture**: Logistic Regression
- **Features**: `FULL_WITH_NGRAM` (Structural, Urgency, Fear/Threat, Authentication, OTP Intent, Financial, CTA, Sender, N-Gram Hash)
- **Hash Function**: MurmurHash3 32-bit (Pure Python/Kotlin parity)
- **Feature Count**: 54 deterministic + 16 buckets = 70 total features
- **Parameter Count**: 213 (70 features * 3 classes + 3 intercepts)
- **Model Size**: ~3,574 bytes (Raw serialized)

## 3. Repository Integrity
- URL-ml files modified: 0
- Git writes performed: 0
- TFLite artifacts generated: 0
- Android integration: 0
- Synthetic fixtures modified: 0

## 4. Dataset Integrity
A complete cross-split verification was executed across the full dataset (N=22,648).
- TRAIN ∩ VALIDATION = 0 overlaps (exact text, templates, IDs)
- TRAIN ∩ TEST = 0 overlaps
- TRAIN ∩ OOD = 0 overlaps
- VALIDATION ∩ TEST = 0 overlaps
- VALIDATION ∩ OOD = 0 overlaps
- TEST ∩ OOD = 0 overlaps
- **Status:** PASS. No cross-split leakage detected.

## 5. Feature Leakage Audit
The previous `data_audit.py` bug (only testing 100 records) was avoided. A full independent audit confirmed that all 70 features are strictly derived from `raw_text` and `sender_header`. No labels, message IDs, source IDs, or template IDs are implicitly or explicitly accessible to the feature extraction layer.
- **Status:** PASS. Feature extraction is deterministic and leak-free.

## 6. Candidate Reproduction
The candidate was reproduced using the original configurations.
- **Seed**: 42
- **Solver**: LogisticRegression (max_iter=1000, class_weight="balanced")

## 7. Validation Metrics
Evaluated on VALIDATION (N=3397) using an explicit probability threshold of 0.50.
- **Accuracy**: 68.85%
- **Macro F1**: 0.633
- **Malicious Precision**: 95.96%
- **Malicious Recall**: 60.57%
- **Benign FPR**: 2.35% (19/807 FPs)

## 8. Threshold Selection
Thresholds from 0.10 to 0.90 were evaluated using the VALIDATION set. 
- Threshold 0.1: FPR = 27.75%, Recall = 96.54%
- Threshold 0.3: FPR = 8.05%, Recall = 80.69%
- Threshold 0.5: FPR = 2.35%, Recall = 60.57%
- Threshold 0.8: FPR = 1.11%, Recall = 33.80%

**Decision Rule**: Threshold 0.50 was selected to aggressively minimize Benign FPR (<2.5%) while preserving Malicious Recall (>60%).

## 9. Calibration
Calibration was validated via `calibration.py`.
- **Brier Score**: 0.1091
- Calibration logic correctly utilized the VALIDATION split only.

## 10. TEST Metrics
Evaluated on TEST (N=2264) using the frozen 0.50 threshold.
- **Accuracy**: 68.24%
- **Macro F1**: 0.636
- **Malicious Precision**: 96.72%
- **Malicious Recall**: 58.80%
- **Benign FPR**: 1.15% (6/520)

## 11. OOD Metrics
Evaluated on OOD (N=1132) using the frozen 0.50 threshold.
- **Accuracy**: 67.40%
- **Macro F1**: 0.612
- **Malicious Precision**: 95.52%
- **Malicious Recall**: 59.19%
- **Benign FPR**: 1.56% (4/255)

## 12. Hard-Negative Audit
Evaluation on benign security alerts and warnings (N=22):
- **False Positives**: 5
- **FPR**: 22.7%
- **Conclusion**: The model struggles significantly to distinguish legitimate security notices from malicious attacks. This is a deployment blocker.

## 13. OTP Audit
- **Legitimate Authentication OTP** (N=20): FPR = 0%
- **Protective OTP Warning** (N=12): FPR = 0%
- **Reverse OTP Theft** (N=3): Recall = 66.6% (2/3)
- **Ambiguous OTP** (N=399): FPR = 4.5% (1/22)
- **Conclusion**: Performance on OTP messages is acceptable, but the dataset lacks diverse Edge cases.

## 14. Threat Vector Audit
- `BANK_KYC_SUSPENSION` (N=971): Recall = 78.88%
- `CREDENTIAL_REQUEST` (N=1137): Recall = 45.91%
- `DELIVERY_SCAM` (N=187): Recall = 50.26%
- `APK_MALWARE_DROPPER` (N=29): Recall = 60.00%
- Other vectors flagged for INSUFFICIENT SAMPLE SIZE.

## 15. Source Bias
Performance remains dependent on the source dataset.
- `SRC_MENDELEY_SMISHING_2022`: FPR = 1.63%
- `SRC_UCI_SMS_SPAM_2011`: FPR = 3.12%
- `SRC_CURATED_HARD_NEGATIVES_V1`: FPR = 14.63% (6/41 FPs)
- **Conclusion**: Source bias has reduced but remains apparent.

## 16. Language Generalization
- **English**: N=3377, FPR = 2.36%, Recall = 60.36%
- **Hinglish**: N=18 (INSUFFICIENT SAMPLE SIZE)
- **Conclusion**: Multilingual coverage is entirely inadequate for Indian-language generalization claims.

## 17. Sender Generalization
- **UNKNOWN**: FPR = 1.80%, Recall = 59.78%
- **ALPHANUMERIC_HEADER**: FPR = 16.66% (5/30), Recall = 85.71%
- **PHONE_NUMBER**: FPR = 0.0%, Recall = 100.0%

## 18. N-Gram Ablation
Adding 16-bucket MurmurHash3 N-Grams proved empirically justified across unseen data:
- **TEST Deterministic (No N-Gram)**: Recall = 51.4%, FPR = 1.15%
- **TEST With N-Gram**: Recall = 58.8%, FPR = 1.15%
- **OOD Deterministic (No N-Gram)**: Recall = 52.8%, FPR = 0.78%
- **OOD With N-Gram**: Recall = 59.1%, FPR = 1.56%
- **Conclusion**: N-Grams provide a massive +7% boost in Malicious Recall on TEST with zero FPR penalty.

## 19. Rule Ablation
Deterministic rules are completely removed from inference overrides. They remain useful strictly for logging/advisory metadata.

## 20. Model Size
- Model payload (weights + scaler) is exceptionally lightweight at 3,574 bytes.
- Parameter count: 213.

## 21. Latency
Measured on x86 Python for text normalization + feature extraction + inference:
- Median: 1.27 ms
- p95: 2.83 ms
- p99: 3.25 ms
- **Conclusion**: Well within real-time Android latency constraints.

## 22. Reproducibility
- Foundation Tests: 40/40 PASSED.
- Execution environment relies exclusively on deterministic random seeds.

## 23. Known Limitations
- The dataset is 99.6% English.
- Threat vector sample sizes are highly imbalanced.
- "Zero False Positive" safety claims are impossible to uphold probabilistically.

## 24. Security Risks
- The high FPR (22.7%) on Hard-Negatives risks suppressing critical legitimate security alerts from institutions.
- Alphanumeric sender structures can trick the model (16.6% FPR).

## 25. Final Candidate Decision
**NO_MODEL_READY**

## 26. Phase 2.4 Packaging Gate
Blockers:
- **Hard-Negative FPR (22.7%)**: The model struggles to differentiate legitimate account suspension alerts from phishing lures.
- **Multilingual Generalization**: Hinglish/Hindi sample sizes are statistically insignificant (N=18).
- **Zero-FP Guarantee**: A Non-zero baseline FPR remains a statistical reality that stakeholders must structurally accept before production.

Non-blocking Risks:
- High dependence on lexical vocabulary limits robustness against adversarial typos.
- Missing shortcode sender coverage.
