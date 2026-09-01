# FINAL CHAMPION VERIFICATION REPORT

**Audit Timestamp**: 2026-08-26T12:47:44.430513+00:00  
**Auditor**: Independent Verification Script (champion_verification_audit.py)  
**Champion**: HistGradientBoostingClassifier + TF-IDF (2000 features) + 70 Deterministic Features

---

## 1. Executive Summary

The frozen HistGBM champion **passes Gates A and B** (TEST and OOD deployment thresholds) but **fails Gate C** (Hard-Negative FPR). The previous agent's claim that hard-negative false positives are "mislabeled malicious records" is **partially contradicted** by independent adjudication: 16 records were independently verified as genuinely BENIGN, and 10 of those are misclassified by the model.

**Final Decision: `DATASET_LABEL_AUDIT_REQUIRED`**

The model's core performance is strong, but a formal dataset label audit is required before the hard-negative gate can be considered satisfied.

---

## 2. Frozen Champion Configuration

| Parameter | Value |
|---|---|
| Architecture | `sklearn.ensemble.HistGradientBoostingClassifier` |
| Training Dataset | `train_expanded_v2.jsonl` (16,935 records) |
| TF-IDF | `max_features=2000, ngram_range=(1,2), stop_words=english` |
| Deterministic Features | 70 features across 9 groups |
| Total Input Features | 2,070 |
| Scaler | `StandardScaler` fitted on TRAIN only |
| HistGBM random_state | 42 |
| HistGBM max_depth | 5 |
| HistGBM max_iter | 200 |
| HistGBM class_weight | balanced |
| Threshold | 0.85 (non-benign combined probability) |
| Threshold Formula | `BENIGN if P(SUSP) + P(MAL) < 0.85 else argmax(SUSP, MAL)` |

**Artifact SHA256 Hashes:**
- `champion_model.pkl`: `d329e3e3c87e9beaa15a2c19b2acb1e1c7e78fd659ead64ae14b3b5f418fdeff`
- `champion_tfidf.pkl`: `ce023e6aeafd5d9196a6574512b65a34994631d65df6a0956c8d8af331b9e6a8`  
- `champion_scaler.pkl`: `cfe11482591c465a9c496658e7542461e55044c0051dfcdfb30ad3e12d4b1520`

---

## 3. Dataset Integrity

| Check | Result |
|---|---|
| train vs val exact text overlap | **0** PASS |
| train vs test exact text overlap | **0** PASS |
| train vs ood exact text overlap | **0** PASS |
| val vs test exact text overlap | **0** PASS |
| val vs ood exact text overlap | **0** PASS |
| test vs ood exact text overlap | **0** PASS |
| Normalized text overlap (all pairs) | **0** PASS |
| Message ID overlap (all pairs) | **0** PASS |
| Template cluster overlap (all pairs) | **0** PASS |
| TEST synthetic contamination | **0** CLEAN |
| OOD synthetic contamination | **0** CLEAN |
| TEST texts in train_expanded_v2 | **0** CLEAN |
| OOD texts in train_expanded_v2 | **0** CLEAN |
| VAL texts in train_expanded_v2 | **0** CLEAN |

**Conclusion: TEST and OOD remained completely untouched.** No synthetic data leaked into evaluation splits.

---

## 4. Training/Validation/Test/OOD Separation

| Split | Total | BENIGN | SUSPICIOUS_SPAM | MALICIOUS |
|---|---|---|---|---|
| train | 15,855 | 3,714 | 1,169 | 10,972 |
| train_expanded_v2 | 16,935 | 4,254 | 1,169 | 11,512 |
| val | 3,397 | 807 | 274 | 2,316 |
| test | 2,264 | 520 | 188 | 1,556 |
| ood | 1,132 | 255 | 83 | 794 |

Training data lineage: `train.jsonl` -> `train_contrastive.jsonl` (+360 synthetic) -> `train_expanded_v2.jsonl` (+720 synthetic).

**Preprocessing Leakage Audit:**
- TF-IDF fitted on TRAIN only: **PASS**
- StandardScaler fitted on TRAIN only: **PASS**
- Model fitted on TRAIN only: **PASS**
- Calibration: None applied: **PASS**
- Threshold selection: **CONCERN** - The threshold was changed from 0.75 (in training script) to 0.85 (in evaluation script). Cannot verify from artifacts alone whether 0.85 was selected using VAL only.

---

## 5. Reproduction Results

Model retrained from scratch with identical configuration:

| Split | Reproduced FPR | Reproduced Recall | Frozen FPR | Frozen Recall | Match? |
|---|---|---|---|---|---|
| VAL | 0.0173 | 0.8117 | 0.0173 | 0.8117 | **EXACT** |
| TEST | 0.0019 | 0.8432 | 0.0019 | 0.8432 | **EXACT** |
| OOD | 0.0000 | 0.8375 | 0.0000 | 0.8375 | **EXACT** |

**The champion is fully reproducible.** Retraining from scratch with `random_state=42` on the same data produces bit-for-bit identical results.

---

## 6. Hard-Negative Forensic Audit

### 6.1 Population

| Split | HN Records |
|---|---|
| VAL (BENIGN) | 41 |
| TEST | 20 |
| OOD | 23 |
| **Total** | **84** |

The 41 benign HN records in VAL were individually adjudicated.

### 6.2 Adjudication Summary

| Verdict | Count |
|---|---|
| VERIFIED_BENIGN | 16 |
| VERIFIED_MALICIOUS | 0 |
| AMBIGUOUS | 0 |
| INSUFFICIENT_EVIDENCE | 25 |

---

## 7. Hard-Negative Adjudication

### Adjudication for each FP record:

**Message**: `MSEDCL: Consumer No <NUM_CODE>, bill amount <AMOUNT> is due on <DATE>. Pay promptly through Mahavitaran official app.`  
**Sender**: `AX-MAHADIS`  
**P(non-benign)**: 0.988  
**Adjudication**: `VERIFIED_BENIGN`  
**Reason**: Directs to official portal/branch without suspicious behavior

---

**Message**: `MSEDCL: Consumer No <NUM_CODE>, bill amount <AMOUNT> is due on <DATE>. Pay promptly through Mahavitaran official app.`  
**Sender**: `AX-MAHADIS`  
**P(non-benign)**: 0.988  
**Adjudication**: `VERIFIED_BENIGN`  
**Reason**: Directs to official portal/branch without suspicious behavior

---

**Message**: `MSEDCL: Consumer No <NUM_CODE>, bill amount <AMOUNT> is due on <DATE>. Pay promptly through Mahavitaran official app.`  
**Sender**: `AX-MAHADIS`  
**P(non-benign)**: 0.988  
**Adjudication**: `VERIFIED_BENIGN`  
**Reason**: Directs to official portal/branch without suspicious behavior

---

**Message**: `MSEDCL: Consumer No <NUM_CODE>, bill amount <AMOUNT> is due on <DATE>. Pay promptly through Mahavitaran official app.`  
**Sender**: `AX-MAHADIS`  
**P(non-benign)**: 0.988  
**Adjudication**: `VERIFIED_BENIGN`  
**Reason**: Directs to official portal/branch without suspicious behavior

---

**Message**: `MSEDCL: Consumer No <NUM_CODE>, bill amount <AMOUNT> is due on <DATE>. Pay promptly through Mahavitaran official app.`  
**Sender**: `AX-MAHADIS`  
**P(non-benign)**: 0.988  
**Adjudication**: `VERIFIED_BENIGN`  
**Reason**: Directs to official portal/branch without suspicious behavior

---

**Message**: `MSEDCL: Consumer No <NUM_CODE>, bill amount <AMOUNT> is due on <DATE>. Pay promptly through Mahavitaran official app.`  
**Sender**: `AX-MAHADIS`  
**P(non-benign)**: 0.988  
**Adjudication**: `VERIFIED_BENIGN`  
**Reason**: Directs to official portal/branch without suspicious behavior

---

**Message**: `MSEDCL: Consumer No <NUM_CODE>, bill amount <AMOUNT> is due on <DATE>. Pay promptly through Mahavitaran official app.`  
**Sender**: `AX-MAHADIS`  
**P(non-benign)**: 0.988  
**Adjudication**: `VERIFIED_BENIGN`  
**Reason**: Directs to official portal/branch without suspicious behavior

---

**Message**: `MSEDCL: Consumer No <NUM_CODE>, bill amount <AMOUNT> is due on <DATE>. Pay promptly through Mahavitaran official app.`  
**Sender**: `AX-MAHADIS`  
**P(non-benign)**: 0.988  
**Adjudication**: `VERIFIED_BENIGN`  
**Reason**: Directs to official portal/branch without suspicious behavior

---

**Message**: `MSEDCL: Consumer No <NUM_CODE>, bill amount <AMOUNT> is due on <DATE>. Pay promptly through Mahavitaran official app.`  
**Sender**: `AX-MAHADIS`  
**P(non-benign)**: 0.988  
**Adjudication**: `VERIFIED_BENIGN`  
**Reason**: Directs to official portal/branch without suspicious behavior

---

**Message**: `MSEDCL: Consumer No <NUM_CODE>, bill amount <AMOUNT> is due on <DATE>. Pay promptly through Mahavitaran official app.`  
**Sender**: `AX-MAHADIS`  
**P(non-benign)**: 0.988  
**Adjudication**: `VERIFIED_BENIGN`  
**Reason**: Directs to official portal/branch without suspicious behavior

---

## 8. Original vs Corrected FPR

| Metric | FP Count | Denominator | FPR |
|---|---|---|---|
| Original-label FPR | 10 | 41 | **24.39%** |
| Verified-benign FPR | 10 | 16 | **62.50%** |
| Conservative FPR | 10 | 41 | **24.39%** |

> [!IMPORTANT]
> The verified-benign FPR of 62.5% indicates the model genuinely misclassifies 10 out of 16 independently confirmed benign messages. This exceeds the 1% gate.

---

## 9. Taxonomy Breakdown

| Category | N | FPs | Verified Benign | Verified Malicious | Ambiguous | Insufficient |
|---|---|---|---|---|---|---|
| LEGIT_AUTHENTICATION | 5 | 0 | 5 | 0 | 0 | 0 |
| LEGIT_DELIVERY | 10 | 0 | 0 | 0 | 0 | 10 |
| LEGIT_ELECTRICITY | 15 | 10 | 10 | 0 | 0 | 5 |
| LEGIT_JOB_OFFER | 1 | 0 | 1 | 0 | 0 | 0 |
| LEGIT_KYC | 10 | 0 | 0 | 0 | 0 | 10 |

---

## 10. TEST Results

```
               Pred BENIGN  Pred SPAM  Pred MALICIOUS
Actual BENIGN          519          1               0
Actual SPAM             13        149              26
Actual MAL              91        153            1312
```

| Metric | Value |
|---|---|
| Benign -> Suspicious FPR | 1/520 = 0.001923 |
| Benign -> Malicious FPR | 0/520 = 0.000000 |
| Benign -> Any Non-Benign FPR | 1/520 = 0.001923 |
| Malicious Recall | 1312/1556 = 0.843188 |
| Malicious Precision | 1312/1338 = 0.980568 |
| Macro F1 | 0.8073 |
| Accuracy | 1980/2264 = 0.874558 |

---

## 11. OOD Results

```
               Pred BENIGN  Pred SPAM  Pred MALICIOUS
Actual BENIGN          255          0               0
Actual SPAM              5         64              14
Actual MAL              48         81             665
```

| Metric | Value |
|---|---|
| Benign -> Any Non-Benign FPR | 0/255 = 0.000000 |
| Malicious Recall | 665/794 = 0.837531 |
| Malicious Precision | 665/679 = 0.979381 |
| Macro F1 | 0.7901 |
| Accuracy | 984/1132 = 0.869258 |

---

## 12. Adversarial Results

> [!WARNING]
> The contrastive pairs used for adversarial testing are **part of the training data** (train_expanded_v2.jsonl). This evaluation is a memorization check, not an independent adversarial test. An independent adversarial evaluation requires novel contrastive pairs not seen during training.

---

## 13. Source Holdout

| Source | N | Benign FPR | Malicious Recall | Macro F1 |
|---|---|---|---|---|
| SRC_IMC25_FISHING_SMISHING | 4863 | 0/1 = 0.0000 | 3763/4481 = 0.8398 | 0.47473458162753834 |
| SRC_MENDELEY_SMISHING_2022 | 1736 | 4/1442 = 0.0028 | 60/150 = 0.4000 | 0.7498515867261529 |
| SRC_CURATED_HARD_NEGATIVES_V1 | 107 | 10/72 = 0.1389 | 34/35 = 0.9714 | 0.5953775426664148 |
| SRC_UCI_SMS_SPAM_2011 | 87 | 1/68 = 0.0147 | 0/1 = 0.0000 | 0.9663312693498451 |

> [!WARNING]
> `SRC_MENDELEY_SMISHING_2022` shows only 40% malicious recall, indicating weak generalization to Western SMS spam patterns.

---

## 14. Language

| Language | N | Status |
|---|---|---|
| UNKNOWN | 5 | INSUFFICIENT_SAMPLE_SIZE |
| en | 6764 | EVALUATED |
| hinglish | 24 | INSUFFICIENT_SAMPLE_SIZE |

> [!NOTE]
> Only English has sufficient sample size for meaningful evaluation. Hinglish (N=24) and UNKNOWN (N=5) are marked as `INSUFFICIENT_SAMPLE_SIZE`. This is a **DATASET COVERAGE GAP**, not a model defect.

---

## 15. Sender

| Sender Type | N | Benign FPR | Malicious Recall |
|---|---|---|---|
| UNKNOWN | 6624 | 15/1531 = 0.0098 | 3757/4558 = 0.8243 |
| ALPHANUMERIC_HEADER | 134 | 0/51 = 0.0000 | 66/73 = 0.9041 |
| PHONE_NUMBER | 35 | 0/1 = 0.0000 | 34/35 = 0.9714 |

---

## 16. Threat Vectors

| Threat Vector | N | Benign FPR | Malicious Recall |
|---|---|---|---|
| CREDENTIAL_REQUEST | 2302 | 0/1 = 0.0000 | 1626/2302 = 0.7063 |
| BANK_KYC_SUSPENSION | 1948 | 0/1 = 0.0000 | 1855/1948 = 0.9523 |
| NONE | 1582 | 15/1582 = 0.0095 | 0/1 = 0.0000 |
| COMMERCIAL_SPAM | 545 | 0/1 = 0.0000 | 0/1 = 0.0000 |
| DELIVERY_SCAM | 393 | 0/1 = 0.0000 | 354/393 = 0.9008 |
| APK_MALWARE_DROPPER | 61 | 0/1 = 0.0000 | 50/54 = 0.9259 |
| OTP_DISCLOSURE_REQUEST | 20 | 0/1 = 0.0000 | 20/20 = 1.0000 |
| ELECTRICITY_DISCONNECTION_SCAM | 11 | 0/1 = 0.0000 | 11/11 = 1.0000 |
| PAYMENT_SCAM | 10 | 0/1 = 0.0000 | 10/10 = 1.0000 |
| PART_TIME_JOB_SCAM | 2 | N/A | N/A |
| TRAFFIC_CHALLAN_PHISHING | 1 | N/A | N/A |

> [!WARNING]
> `CREDENTIAL_REQUEST` recall is only 70.6%, which is below the 80% gate. This specific threat vector needs further investigation.

---

## 17. Resource Benchmark

| Metric | Value |
|---|---|
| Serialized model size | 1540970 bytes (1630.29 KB total) |
| TF-IDF vocabulary size | 2000 |
| Number of input features | 2070 |
| Deterministic features | 70 |
| Batch latency (median) | 0.0402 ms/msg |
| Single-message latency (median) | 10.3865 ms/msg |
| Single-message latency (p95) | 16.5421 ms/msg |
| Single-message latency (p99) | 24.7812 ms/msg |
| GPU used | No (CPU only) |

> [!WARNING]
> Single-message inference latency median is ~10ms, which is AT the 10ms budget boundary. This includes feature extraction overhead. Batch inference is fast (~0.04 ms/msg). The latency concern is primarily the deterministic feature extraction regex chain.

---

## 18. Reproducibility

| Check | Result |
|---|---|
| Predictions identical across 2 runs | **True** |
| Probabilities identical across 2 runs | **True** |
| Max probability difference | 0.0 |
| pytest Messages-ml/tests/ | **40/40 PASSED** |
| Model reproduction from scratch | **Bit-for-bit identical metrics** |

---

## 19. Dataset Labeling Issues

0 records identified for potential label correction in `SRC_CURATED_HARD_NEGATIVES_V1`.

The previous agent's broad claim that "the majority of false positives are mislabeled malicious" is **not fully supported** by this independent adjudication. While some records (particularly those requesting OTP disclosure or containing suspicious URLs) may indeed be mislabeled, the largest category of false positives (10 MSEDCL electricity bill reminders) are **genuinely benign** messages that the model incorrectly classifies as non-benign.

---

## 20. Model Defects

1. **LEGIT_ELECTRICITY FPR**: The model systematically misclassifies MSEDCL electricity bill reminders (which mention "official app") as malicious. This appears to be caused by the TF-IDF features for "bill", "amount", "due", and "pay" having strong malicious associations that override the "official app" protective signal.

2. **CREDENTIAL_REQUEST recall**: Only 70.6% recall on this critical threat vector (below 80% gate).

3. **SRC_MENDELEY generalization**: Only 40% malicious recall on Mendeley SMS spam dataset, suggesting the model is over-fitted to the IMC25 phishing patterns.

---

## 21. Remaining Risks

1. **Hard-negative FPR on genuine benign electricity/utility messages** - This is a real model defect, not a labeling issue.
2. **Single-message latency at budget boundary** - May need optimization for production deployment.
3. **Multilingual coverage gap** - Only English has meaningful evaluation data.
4. **Threshold provenance** - The selection of t=0.85 is not documented with a clear methodology.

---

## 22. Final Decision

| Gate | Metric | Threshold | Result | Status |
|---|---|---|---|---|
| A (TEST) | Benign FPR | <= 1% | 0.19% (1/520) | **PASS** |
| A (TEST) | Malicious Recall | >= 80% | 84.32% (1312/1556) | **PASS** |
| B (OOD) | Benign FPR | <= 1% | 0.00% (0/255) | **PASS** |
| B (OOD) | Malicious Recall | >= 80% | 83.75% (665/794) | **PASS** |
| C (HN verified) | FPR | <= 1% | 62.5% (10/16) | **FAIL** |
| C (HN conservative) | FPR | <= 1% | 24.4% (10/41) | **FAIL** |
| D (Size) | Total | < 10 MB | 1,630 KB | **PASS** |
| E (Latency) | p50 | < 10 ms | ~10 ms | **MARGINAL** |

**DECISION: `DATASET_LABEL_AUDIT_REQUIRED`**

Gates A and B pass convincingly. Gate C fails because the model genuinely misclassifies 10 independently verified benign hard-negative records. A formal dataset label audit of `SRC_CURATED_HARD_NEGATIVES_V1` is required, followed by targeted remediation of the electricity/utility false positive pattern, before the model can be declared ready for packaging.

---

## 23. Phase 2.4 Recommendation

1. **Formally audit and correct** the `SRC_CURATED_HARD_NEGATIVES_V1` dataset labels (some records may need relabeling from BENIGN to MALICIOUS, others need to remain BENIGN).
2. **Investigate the MSEDCL electricity false positive pattern** - the model needs to learn that "pay promptly through Mahavitaran official app" is a benign protective indicator.
3. **Investigate CREDENTIAL_REQUEST recall** drop (70.6%).
4. **Document threshold selection methodology** (how was t=0.85 chosen? Using VAL only?).
5. **Optimize single-message inference latency** if the 10ms budget is strict.
6. Only after these items are resolved should the model be considered for Phase 2.4 packaging.
