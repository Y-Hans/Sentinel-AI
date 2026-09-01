# Final Model Selection & Optimization Report

## Pareto Analysis (Stage G)
**FPR <= 0.1** (Threshold: 0.16)
- Malicious Recall: 0.9193
- Macro F1: 0.7385
- Hard Negative FPR: 0.2683

**FPR <= 0.05** (Threshold: 0.34)
- Malicious Recall: 0.8182
- Macro F1: 0.7348
- Hard Negative FPR: 0.2683

**FPR <= 0.03** (Threshold: 0.50)
- Malicious Recall: 0.7314
- Macro F1: 0.7044
- Hard Negative FPR: 0.2439

**FPR <= 0.02** (Threshold: 0.62)
- Malicious Recall: 0.6533
- Macro F1: 0.6732
- Hard Negative FPR: 0.2439

## Final TEST & OOD Evaluation (Stage I)
- **TEST Macro F1**: 0.5527
- **TEST Malicious Recall**: 0.3766
- **TEST Benign FPR**: 0.0000
- **TEST Curated Hard Negative FPR**: 0.0000

- **OOD Macro F1**: 0.5486
- **OOD Malicious Recall**: 0.3778
- **OOD Benign FPR**: 0.0000

## Final Decision (Stage J)
**ARCHITECTURE_REDESIGN_REQUIRED**

Despite exhausting linear models, MLPs, feature interactions, weighting strategies, diverse semantic data expansion, and adversarial pairs, the model mathematically cannot achieve FPR <= 1% with Malicious Recall >= 80% using the current representation. The lexical overlap between legitimate institutional warnings (UIDAI, Income Tax) and threat vectors requires an architecture capable of deeper contextual embedding (e.g. Transformers).