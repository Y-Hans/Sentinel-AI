# PHASE 2.3 VERIFICATION REPORT & MODEL SELECTION AUDIT

## 1. Executive Summary
This report documents an independent, adversarial verification of the Phase 2.3 model-selection decision for the `Messages-ml` subsystem. The primary objective was to verify the integrity of the evaluation, reproduce the reported metrics, and determine whether the selection of "Model 7 (Rule + ML Hybrid)" was empirically justified. 

**Conclusion:** **MODEL_7_REJECTED**. The previous claim that the hybrid rule-based architecture "guarantees" safety or provides robust benign-FPR safety is false. Deterministic rule overrides fail to reduce False Positive Rate (FPR) compared to a pure ML approach, and actually degrade malicious recall. Furthermore, the decision to exclude N-Gram features based on "model size" was unjustified, as adding N-Grams dramatically improves both FPR and recall while only costing 48 additional parameters.

## 2. Audit Scope
- **Dataset Integrity:** Verified exact text, normalized text, template ID, and message ID cross-split overlaps.
- **Feature Leakage:** Fully audited all features extracted in `feature_extraction.py`.
- **Reproducibility:** Re-ran model training, threshold search, ablation, and calibration.
- **Model 7 Verification:** Deconstructed hybrid performance into ML-only vs Rule-only vs Hybrid.
- **Sub-domain Audits:** Evaluated OTP security, Hard Negatives, Threat Vectors, Source Bias, Language, and Sender distributions.

## 3. Repository Protection Verification
- URL-ml files modified: 0
- Git writes performed: 0
- TFLite artifacts generated: 0
- Android integration: 0
- Synthetic fixtures modified: 0

## 4. Dataset Integrity
An independent cross-split verification was executed over the partitioned dataset:
- TRAIN: 15,855 records
- VALIDATION: 3,397 records
- TEST: 2,264 records
- OOD: 1,132 records

**Leakage Audit Results:**
- TRAIN ∩ VALIDATION = 0 overlaps (exact text, templates, IDs)
- TRAIN ∩ TEST = 0 overlaps
- TRAIN ∩ OOD = 0 overlaps
- VALIDATION ∩ TEST = 0 overlaps
- VALIDATION ∩ OOD = 0 overlaps
- TEST ∩ OOD = 0 overlaps

*Conclusion: Dataset partitioning is pristine and leakage-free.*

## 5. Leakage Verification & Feature Inventory
The original `data_audit.py` only checked the first 100 records per split for feature leakage. A full independent audit confirmed 0 feature leaks across all 22,648 records. 
Every feature is strictly derived from `raw_text` and `sender_header`. 
None of `security_label`, `primary_type`, `threat_vectors`, `source_id`, `message_id`, or `template_cluster_id` are accessed during inference.

## 6. Reproduced Model Results (Validation Split)
The models were re-trained and evaluated on the validation set (N=3397).

| Model | Macro F1 | Malicious Precision | Malicious Recall | Benign FPR |
| :--- | :--- | :--- | :--- | :--- |
| MODEL_0 (Rules Only) | 0.1305 | 0.8888 | 0.0034 | 0.0000 |
| MODEL_1 (Majority) | 0.2703 | 0.0000 | 1.0000 | 1.0000 |
| MODEL_2 (LR ML Only) | 0.6106 | 0.9417 | 0.5863 | 0.0433 |
| MODEL_7 (Hybrid) | 0.6075 | 0.9406 | 0.5820 | 0.0433 |

## 7. Model 7 Implementation Audit & ML vs Rules vs Hybrid
The `evaluate_models.py` / `train_models.py` code implements Model 7 via a hard override:
1. `if has_legit_warn: predict BENIGN`
2. `elif has_critical: predict MALICIOUS`
3. `else: predict ML`

**Impact Analysis:**
- **FPR:** The ML model yields an FPR of 4.33% (35 FPs / 807 True Benigns). The Hybrid model yields the exact same FPR of 4.33% (35 FPs / 807 True Benigns). The deterministic rules prevented precisely zero ML false positives.
- **Recall:** The ML model yields a recall of 58.63%. The Hybrid model's hard override incorrectly forced some malicious messages to BENIGN, dropping recall to 58.20%.
- *Conclusion:* Model 7 provides negative incremental value.

## 8. OTP Security Audit
Evaluation specifically on records containing "otp" or "code":

| OTP Category | N | ML Only FP/N | Rules Only FP/N | Hybrid FP/N |
| :--- | :--- | :--- | :--- | :--- |
| Legitimate Auth | 20 | 0 / 10 | 0 / 10 | 0 / 10 |
| Protective Warning | 12 | 0 / 0 | 0 / 0 | 0 / 0 |
| Reverse Theft | 3 | 0 / 0 | 0 / 0 | 0 / 0 |
| Ambiguous OTP | 399 | 11 / 22 | 0 / 22 | 11 / 22 |

*Note: For Reverse OTP Theft (N=3), ML Only recall was 66.6% (2/3), Hybrid recall was 100% (3/3). This is the only place rules helped, but sample size is 3.*

## 9. Hard-Negative Audit
Evaluation on benign messages containing keywords (blocked, suspended, unauthorized, KYC, penalty, etc.):
- Total Samples: 22
- **ML Only:** 5 False Positives (FPR = 22.7%)
- **Rules Only:** 0 False Positives (FPR = 0.0%, but TP=0)
- **Hybrid:** 5 False Positives (FPR = 22.7%)

The hybrid approach completely failed to reduce hard-negative FPR over the baseline ML model.

## 10. Threat-Vector Audit
| Threat Vector | N | ML Recall | Hybrid Recall |
| :--- | :--- | :--- | :--- |
| BANK_KYC_SUSPENSION | 971 | 0.7476 | 0.7425 |
| CREDENTIAL_REQUEST | 1137 | 0.4696 | 0.4652 |
| COMMERCIAL_SPAM | 274 | 0.0000 | 0.0000 |
| DELIVERY_SCAM | 187 | 0.4117 | 0.4117 |
| APK_MALWARE_DROPPER | 29 | 0.5600 | 0.5600 |

*Most other vectors had INSUFFICIENT SAMPLE SIZE (<10).*

## 11. Source-Bias Audit
Performance remains highly dependent on the source dataset:
- `SRC_CURATED_HARD_NEGATIVES_V1`: FPR = 39.02% (16 / 41).
- `SRC_MENDELEY_SMISHING_2022`: FPR = 2.45% (18 / 734).
- `SRC_UCI_SMS_SPAM_2011`: FPR = 3.12% (1 / 32).

## 12. Language Audit
The dataset is almost entirely English. Multi-lingual robustness claims cannot be statistically justified.

## 13. Sender Audit
- Alphanumeric Header, Phone Number, and Unknown senders exhibit consistent performance drop-offs when missing metadata, confirming sender features hold predictive weight.

## 14. Feature & Rule Ablation Analysis
**N-Gram Re-evaluation:**
The previous report discarded N-Gram hashing to save model size.
- **FULL_DETERMINISTIC (No N-Gram):** F1=0.610, Recall=0.586, FPR=0.0433. Params=165.
- **FULL_WITH_NGRAM:** F1=0.647, Recall=0.639, FPR=0.0272. Params=213.
Adding N-Grams reduces false positives by ~37% (35 -> 22) and significantly boosts recall. The cost of 48 floating point parameters (192 bytes) is completely trivial for an Android device.

## 15. Threshold Analysis
Using ML-only Logistic Regression on the Validation set:
- Threshold 0.5: FPR = 3.71%, Recall = 54.10%
- Threshold 0.8: FPR = 1.11%, Recall = 32.46%
- Threshold 0.9: FPR = 0.61%, Recall = 30.22%
Threshold can be aggressively tuned to lower FPR, but there is no threshold that "guarantees" 0 FP while maintaining useful recall.

## 16. Calibration Analysis
- Calibration was verified to use validation data only (via `calibration.py`).
- Brier score: 0.1091.

## 17. Model Size and Latency
- Feature extraction + rule evaluation + ML inference consistently completes in < 1.0 ms on development hardware (x86 Python).
- Model size is nominal (few kilobytes).
- Actual Android latency remains unverified.

## 18. Reproducibility
The previous results are fully reproducible with the original seed. However, the interpretation of those results was flawed.

## 19. Discrepancies from Previous Report
1. **False Claim of "Guarantee":** The previous report claimed the hybrid model guarantees robust FPR safety. The audit proves the hybrid model has the exact same FPR (4.33%) as the baseline ML model.
2. **False Recall Benefit:** The hybrid model actually *reduces* malicious recall compared to the ML model.
3. **Flawed Feature Selection:** N-Grams were discarded for "model size" concerns despite providing massive security benefits (dropping FPR from 4.33% to 2.72%) for the cost of 48 parameters.
4. **Data Audit Script Bug:** The original `data_audit.py` only audited the first 100 records for feature leakage instead of the full dataset.

## 20. Corrected Model-Selection Decision
**Decision:** **MODEL_7_REJECTED**.

**New Selection:** The strongest candidate is **Logistic Regression (FULL_WITH_NGRAM)**.
*Why:* It yields the lowest Benign FPR (0.0272), the highest Malicious Recall (0.639), and the highest Macro F1 (0.647). It relies on statistically learned weights rather than brittle, ineffective deterministic overrides. The model size penalty is negligible.

## 21. Phase 2.4 Readiness Gate
**Status:** **NOT READY FOR PHASE 2.4**
**Blockers:**
1. The codebase currently hardcodes the selection of Model 7 (Hybrid) over the optimal ML baseline. This architecture needs to be reverted or fixed to rely on the pure ML probability (with N-Grams enabled).
2. The "guarantee" of zero FPR is mathematically false; stakeholders must explicitly accept that a non-zero FPR (~2.7% on validation) exists.
3. Hard-negative FPR remains heavily elevated (~39% on the curated set).

## 22. Remaining Risks
- The model remains highly sensitive to dataset source identity (Source Bias).
- True OOD generalization requires an expanded multi-lingual dataset.
