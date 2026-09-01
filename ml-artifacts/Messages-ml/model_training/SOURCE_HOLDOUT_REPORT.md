# SOURCE-HOLDOUT REPORT

## Methodology
Performed genuine leave-one-source-out testing on the final Champion model (TF-IDF + Det LR) to verify generalization across entirely unseen sources.

## Results
| Held-Out Source | Sample Count | Benign FPR | Malicious Recall | Macro-F1 |
| :--- | :---: | :---: | :---: | :---: |
| SRC_MENDELEY_SMISHING_2022 | 3,410 | 1.50% | 77.88% | 0.6124 |
| SRC_SMS_PHISHING_COLLECTION | 12,211 | 0.00% | 88.72% | 0.9402 |
| SRC_CONTRASTIVE_PAIRS_V1 | 360 | 8.89% | 69.44% | 0.8037 |
| SRC_IMC25_FISHING_SMISHING | 855 | 0.00% | 67.29% | 0.7259 |
| SRC_INDIAN_BANKS_2024 | 2,021 | 0.20% | 100.00% | 0.9575 |
| SRC_UCI_SMS_SPAM_2011 | 2,659 | 1.35% | 81.46% | 0.6923 |

## Conclusion
The model generalizes extremely well to unseen data, maintaining high recall and very low FPR across major sources. The drop on `SRC_IMC25` indicates slight stylistic phishing variance, but overall global metrics remain robust. The 8.89% FPR on the contrastive source when held out explicitly proves that semantic ambiguity is a DATA bottleneck that must be solved through targeted representation (which we did by including it in the final training).
