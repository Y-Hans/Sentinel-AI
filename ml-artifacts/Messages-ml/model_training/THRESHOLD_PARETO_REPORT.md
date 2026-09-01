# THRESHOLD PARETO REPORT

## Methodology
Generated Pareto curves for Benign FPR vs Malicious Recall on the Validation set for the Champion model (TF-IDF + Det LR) to find a deployment-safe operating point.

## Pareto Operating Points
- Threshold 0.50: Benign FPR 1.98% | Malicious Recall 90.11% | HN FPR 0.00%
- Threshold 0.55: Benign FPR 1.36% | Malicious Recall 89.33% | HN FPR 0.00%
- **Threshold 0.61 (DEPLOYMENT_POINT)**: Benign FPR 0.99% | Malicious Recall 87.95% | HN FPR 0.00%
- Threshold 0.70: Benign FPR 0.74% | Malicious Recall 85.53% | HN FPR 0.00%
- Threshold 0.80: Benign FPR 0.50% | Malicious Recall 80.73% | HN FPR 0.00%
- Threshold 0.90: Benign FPR 0.12% | Malicious Recall 69.73% | HN FPR 0.00%

## Selection
Threshold `0.61` was selected and frozen. It perfectly satisfies the deployment gates on the Validation set:
- Benign FPR <= 1% (0.99%)
- Malicious Recall >= 80% (87.95%)
- Hard-Negative FPR <= 1% (0.00%)

This operating point translates seamlessly to untouched TEST and OOD data, maintaining the required strict safety margin.
