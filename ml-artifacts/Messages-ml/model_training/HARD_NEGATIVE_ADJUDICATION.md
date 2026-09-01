# HARD-NEGATIVE ADJUDICATION REPORT

**Total benign HN records in VAL**: 41  
**False positives (by original label)**: 10/41 = 24.39%

## Adjudication Summary

| Verdict | Count |
|---|---|
| VERIFIED_BENIGN | 16 |
| VERIFIED_MALICIOUS | 0 |
| AMBIGUOUS | 0 |
| INSUFFICIENT_EVIDENCE | 25 |

## Three FPR Metrics

| Metric | FP | Total | FPR |
|---|---|---|---|
| Original-label | 10 | 41 | 24.39% |
| Verified-benign only | 10 | 16 | 62.50% |
| Conservative | 10 | 41 | 24.39% |

## Taxonomy Breakdown

| Category | N | FPs | VB | VM | AMB | IE |
|---|---|---|---|---|---|---|
| LEGIT_AUTHENTICATION | 5 | 0 | 5 | 0 | 0 | 0 |
| LEGIT_DELIVERY | 10 | 0 | 0 | 0 | 0 | 10 |
| LEGIT_ELECTRICITY | 15 | 10 | 10 | 0 | 0 | 5 |
| LEGIT_JOB_OFFER | 1 | 0 | 1 | 0 | 0 | 0 |
| LEGIT_KYC | 10 | 0 | 0 | 0 | 0 | 10 |

## Individual False Positive Adjudications

### Record: `MSG_SRC_CURATED_HARD...`

- **Text**: `MSEDCL: Consumer No <NUM_CODE>, bill amount <AMOUNT> is due on <DATE>. Pay promptly through Mahavitaran official app.`
- **Sender**: `AX-MAHADIS`
- **P(non-benign)**: 0.9880
- **Predicted**: MALICIOUS
- **Verdict**: `VERIFIED_BENIGN`
- **Reason**: Directs to official portal/branch without suspicious behavior
- **Category**: LEGIT_ELECTRICITY

---

### Record: `MSG_SRC_CURATED_HARD...`

- **Text**: `MSEDCL: Consumer No <NUM_CODE>, bill amount <AMOUNT> is due on <DATE>. Pay promptly through Mahavitaran official app.`
- **Sender**: `AX-MAHADIS`
- **P(non-benign)**: 0.9880
- **Predicted**: MALICIOUS
- **Verdict**: `VERIFIED_BENIGN`
- **Reason**: Directs to official portal/branch without suspicious behavior
- **Category**: LEGIT_ELECTRICITY

---

### Record: `MSG_SRC_CURATED_HARD...`

- **Text**: `MSEDCL: Consumer No <NUM_CODE>, bill amount <AMOUNT> is due on <DATE>. Pay promptly through Mahavitaran official app.`
- **Sender**: `AX-MAHADIS`
- **P(non-benign)**: 0.9880
- **Predicted**: MALICIOUS
- **Verdict**: `VERIFIED_BENIGN`
- **Reason**: Directs to official portal/branch without suspicious behavior
- **Category**: LEGIT_ELECTRICITY

---

### Record: `MSG_SRC_CURATED_HARD...`

- **Text**: `MSEDCL: Consumer No <NUM_CODE>, bill amount <AMOUNT> is due on <DATE>. Pay promptly through Mahavitaran official app.`
- **Sender**: `AX-MAHADIS`
- **P(non-benign)**: 0.9880
- **Predicted**: MALICIOUS
- **Verdict**: `VERIFIED_BENIGN`
- **Reason**: Directs to official portal/branch without suspicious behavior
- **Category**: LEGIT_ELECTRICITY

---

### Record: `MSG_SRC_CURATED_HARD...`

- **Text**: `MSEDCL: Consumer No <NUM_CODE>, bill amount <AMOUNT> is due on <DATE>. Pay promptly through Mahavitaran official app.`
- **Sender**: `AX-MAHADIS`
- **P(non-benign)**: 0.9880
- **Predicted**: MALICIOUS
- **Verdict**: `VERIFIED_BENIGN`
- **Reason**: Directs to official portal/branch without suspicious behavior
- **Category**: LEGIT_ELECTRICITY

---

### Record: `MSG_SRC_CURATED_HARD...`

- **Text**: `MSEDCL: Consumer No <NUM_CODE>, bill amount <AMOUNT> is due on <DATE>. Pay promptly through Mahavitaran official app.`
- **Sender**: `AX-MAHADIS`
- **P(non-benign)**: 0.9880
- **Predicted**: MALICIOUS
- **Verdict**: `VERIFIED_BENIGN`
- **Reason**: Directs to official portal/branch without suspicious behavior
- **Category**: LEGIT_ELECTRICITY

---

### Record: `MSG_SRC_CURATED_HARD...`

- **Text**: `MSEDCL: Consumer No <NUM_CODE>, bill amount <AMOUNT> is due on <DATE>. Pay promptly through Mahavitaran official app.`
- **Sender**: `AX-MAHADIS`
- **P(non-benign)**: 0.9880
- **Predicted**: MALICIOUS
- **Verdict**: `VERIFIED_BENIGN`
- **Reason**: Directs to official portal/branch without suspicious behavior
- **Category**: LEGIT_ELECTRICITY

---

### Record: `MSG_SRC_CURATED_HARD...`

- **Text**: `MSEDCL: Consumer No <NUM_CODE>, bill amount <AMOUNT> is due on <DATE>. Pay promptly through Mahavitaran official app.`
- **Sender**: `AX-MAHADIS`
- **P(non-benign)**: 0.9880
- **Predicted**: MALICIOUS
- **Verdict**: `VERIFIED_BENIGN`
- **Reason**: Directs to official portal/branch without suspicious behavior
- **Category**: LEGIT_ELECTRICITY

---

### Record: `MSG_SRC_CURATED_HARD...`

- **Text**: `MSEDCL: Consumer No <NUM_CODE>, bill amount <AMOUNT> is due on <DATE>. Pay promptly through Mahavitaran official app.`
- **Sender**: `AX-MAHADIS`
- **P(non-benign)**: 0.9880
- **Predicted**: MALICIOUS
- **Verdict**: `VERIFIED_BENIGN`
- **Reason**: Directs to official portal/branch without suspicious behavior
- **Category**: LEGIT_ELECTRICITY

---

### Record: `MSG_SRC_CURATED_HARD...`

- **Text**: `MSEDCL: Consumer No <NUM_CODE>, bill amount <AMOUNT> is due on <DATE>. Pay promptly through Mahavitaran official app.`
- **Sender**: `AX-MAHADIS`
- **P(non-benign)**: 0.9880
- **Predicted**: MALICIOUS
- **Verdict**: `VERIFIED_BENIGN`
- **Reason**: Directs to official portal/branch without suspicious behavior
- **Category**: LEGIT_ELECTRICITY

---

