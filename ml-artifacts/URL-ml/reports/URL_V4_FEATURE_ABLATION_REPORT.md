# URL-ML V4 Feature Ablation Report

All thresholds were selected from validation only. TEST and hard-source numbers below are evaluation-only.

| Candidate | Features | TEST FPR | TEST recall | Hard FPR | Hard recall | Hard AUC | Size |
|---|---:|---:|---:|---:|---:|---:|---:|
| deterministic only | 15 | 0.852% | 94.51% | 48.85% | 95.48% | 0.960 | 13.22 MB |
| host + path | 58 | 0.742% | 99.59% | 83.10% | 98.57% | 0.956 | 15.53 MB |
| host + path + query | 81 | 0.757% | 99.57% | 86.52% | 98.10% | 0.955 | 14.02 MB |
| host + path + query + interactions | 92 | 0.735% | 99.57% | 92.04% | 98.34% | 0.945 | 13.01 MB |
| hashed character + deterministic | 1,092 | 1.119% | 99.56% | 44.26% | 97.38% | 0.941 | 12.4 KB |
| scoped hashed character + deterministic | 1,592 | 0.713% | 99.53% | 48.24% | 97.03% | 0.925 | 16.4 KB |

Conclusion: every expanded family improves clean source-disjoint validation/TEST recall, but none improves the hard-source population to the release gate. The larger character representation is compact on disk but does not provide the required generalization.
