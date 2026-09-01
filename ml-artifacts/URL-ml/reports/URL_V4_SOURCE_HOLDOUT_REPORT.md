# URL-ML V4 Source-Holdout Report

The fixed TEST split is a registrable-domain-disjoint source holdout from the clean source pool: 50,511 rows, with 44,106 validation rows and 141,649 training rows before remediation. The hard-source population is a separate protected source holdout of 2,800 rows and was never fit.

The 92-feature model reaches 0.735% TEST benign FPR and 99.57% malicious recall, but 92.04% hard-source benign FPR. The best safe remediation-weight result (160×) reaches 0.918% TEST FPR, 98.83% TEST recall, and 12.40% hard-source FPR. This confirms strong ordinary unseen-domain performance does not imply hard-source generalization.

No valid separate OOD dataset was available.
