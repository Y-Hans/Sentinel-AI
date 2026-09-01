# Autonomous Optimization Report

## 1. Initial baseline
- Model: MODEL_7_HYBRID and Logistic Regression
- Hard Negative FPR: ~39%
- Benign FPR: ~4%

## 2. Champion History
- EXP_001: Added `LEGIT_INTENT` feature group for institutional context. (FPR still high due to missing variations in training).
- EXP_002: Added `NGRAM_HASH` (bins=128) to capture lexical sequences deterministically.
- DATA_EXPANSION: Added 320 synthetic legitimate institutional records resembling threats.

## 3. Final Model
- **Algorithm**: Logistic Regression (class_weight='balanced')
- **Features**: ['STRUCTURAL', 'URGENCY', 'FEAR_THREAT', 'AUTH', 'OTP_INTENT', 'FINANCIAL', 'CTA', 'SENDER', 'NGRAM_HASH', 'LEGIT_INTENT']
- **Threshold**: 0.75
- **TEST Macro F1**: 0.6403
- **TEST Malicious Recall**: 0.5482
- **TEST Benign FPR**: 0.0019
- **OOD Macro F1**: 0.6313
- **OOD Benign FPR**: 0.0039
- **Hard Negative FPR**: 0.1803

## 4. Final decision
**DATA_EXPANSION_REQUIRED**

The loop terminated because the hard-negative FPR remains > 1% (18.03%) and malicious recall dropped to < 80% (54.82%). The current features and linear model are insufficient to separate sophisticated benign institution communications (e.g. UIDAI, Income Tax) from malicious threats sharing the exact same lexical overlap without a more robust semantic representation or much larger dataset.
