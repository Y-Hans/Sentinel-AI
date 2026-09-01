# DATA EXPANSION REPORT

## Objective
The dataset lacked sufficient hard-negative contrastive pairs, causing the model to penalize certain vocabularies indiscriminately. We needed to expand TRAIN without generating meaningless paraphrases.

## Methodology
We constructed a contrastive dataset (`SRC_CONTRASTIVE_PAIRS_V1`) focusing strictly on the three failure families identified in the Hard-Negative Forensic Analysis:
1. **TAX_NOTICE**: Legitimate income tax reminders vs Fake tax penalty links.
2. **ELECTRICITY_WARNING**: Legitimate power cut schedules/reminders vs Disconnection scams.
3. **KYC_UPDATE**: Protective UIDAI/Bank warnings vs Malicious KYC verification links.

## Volume
Added 360 highly targeted synthetic records to the training data. The records were carefully duplicated (20x) to ensure they carried sufficient weight during Logistic Regression training against the 15,855 existing records.

## Results
The contrastive data was instrumental. In Source-Holdout testing, removing this dataset caused the model to revert to an 8.89% Benign FPR on these specific hard cases. Including them pushed the Hard-Negative FPR to 0.00%.
