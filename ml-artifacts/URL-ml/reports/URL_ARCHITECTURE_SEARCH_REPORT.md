# URL-ML Architecture Search

Targeted candidates: balanced logistic regression, HistGradientBoosting, and compact ExtraTrees using the existing full deterministic feature set. Thresholds were selected on validation only under a 1% benign-FPR constraint.

- **linear_logloss_full**: validation recall 0.8160, test recall 0.9232, test FPR 0.0086, hard holdout recall 0.9905
- **gradientboosting_full**: validation recall 0.9605, test recall 0.9257, test FPR 0.0041, hard holdout recall 0.8597
- **extratrees_full**: validation recall 0.9879, test recall 0.9853, test FPR 0.0096, hard holdout recall 1.0000

Champion: **extratrees_full**.
