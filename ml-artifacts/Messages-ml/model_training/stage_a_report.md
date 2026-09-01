# Stage A: Hard-Negative Root-Cause Analysis

## LEGIT_TAX
- N: 1
- False Positives: 1
- FPR: 1.0000
- Mean Malicious Prob: 0.9874
- Median Malicious Prob: 0.9874

### Representative Errors:
- **Text**: Income Tax Department: Non-filing of ITR for AY <NUM_CODE>-<NUM> may attract late fee under Section 234F. File return on official portal incometax.gov.in.
  - **Sender**: AD-INCOMET
  - **Confidence**: 0.9874
  - **Top Features**: legit_context_score, legit_institution_keyword_count, legit_notice_keyword_count
  - **Root Cause**: The model fails to contextualize protective language with the institutional sender. This is primarily a FEATURE and MODEL CAPACITY problem (linear model cannot XOR the urgency vs sender/protective context properly without explicit non-linear interaction features).

## LEGIT_ELECTRICITY
- N: 15
- False Positives: 10
- FPR: 0.6667
- Mean Malicious Prob: 0.6667
- Median Malicious Prob: 0.9997

### Representative Errors:
- **Text**: MSEDCL: Consumer No <NUM_CODE>, bill amount <AMOUNT> is due on <DATE>. Pay promptly through Mahavitaran official app.
  - **Sender**: AX-MAHADIS
  - **Confidence**: 0.9997
  - **Top Features**: legit_context_score, legit_notice_keyword_count, legit_institution_keyword_count
  - **Root Cause**: The model fails to contextualize protective language with the institutional sender. This is primarily a FEATURE and MODEL CAPACITY problem (linear model cannot XOR the urgency vs sender/protective context properly without explicit non-linear interaction features).

- **Text**: MSEDCL: Consumer No <NUM_CODE>, bill amount <AMOUNT> is due on <DATE>. Pay promptly through Mahavitaran official app.
  - **Sender**: AX-MAHADIS
  - **Confidence**: 0.9997
  - **Top Features**: legit_context_score, legit_notice_keyword_count, legit_institution_keyword_count
  - **Root Cause**: The model fails to contextualize protective language with the institutional sender. This is primarily a FEATURE and MODEL CAPACITY problem (linear model cannot XOR the urgency vs sender/protective context properly without explicit non-linear interaction features).

- **Text**: MSEDCL: Consumer No <NUM_CODE>, bill amount <AMOUNT> is due on <DATE>. Pay promptly through Mahavitaran official app.
  - **Sender**: AX-MAHADIS
  - **Confidence**: 0.9997
  - **Top Features**: legit_context_score, legit_notice_keyword_count, legit_institution_keyword_count
  - **Root Cause**: The model fails to contextualize protective language with the institutional sender. This is primarily a FEATURE and MODEL CAPACITY problem (linear model cannot XOR the urgency vs sender/protective context properly without explicit non-linear interaction features).

## LEGIT_OTP
- N: 20
- False Positives: 0
- FPR: 0.0000
- Mean Malicious Prob: 0.0002
- Median Malicious Prob: 0.0002

## LEGIT_GOVERNMENT
- N: 5
- False Positives: 0
- FPR: 0.0000
- Mean Malicious Prob: 0.1930
- Median Malicious Prob: 0.1930

