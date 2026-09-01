# URL-ML V4 Optimization Walkthrough — Final

V4 started from the preserved V3 state: a frozen 15-feature ExtraTrees champion and a documented hard-source collapse. The previous reports were read in full before changes.

First, V4 created a deterministic Kotlin-portable representation containing the prior 15 scalars plus host morphology, path morphology, query structure, and protocol/component interactions. It then evaluated compact hashed 3–5 character n-grams over full URL, host, path, and query. Finally, it ran a focused remediation-weight sweep from 5× through 160×.

All training used only the clean source training groups plus the explicitly logged V3 remediation CSV. Validation selected thresholds; TEST and hard-source were evaluation-only. The fixed source-disjoint TEST split contained 50,511 rows, and hard-source contained 2,800 protected rows. OOD was unavailable.

The expanded representation substantially improved clean TEST recall to about 99.5%, but hard-source benign FPR stayed 44–92% for the basic ablations. Increasing remediation weight reduced it monotonically to 12.40% at 160×, while still missing the ≤1% gate. Character hashing was compact but did not improve hard-source AUC or FPR sufficiently.

Final decision: **NOT READY**. No challenger dominates the frozen champion under the complete release framework, so no promotion or packaging occurred. The next scientifically justified campaign requires new adjudicated source-diverse legitimate coverage and a fresh adversarial/source-holdout harness, not more cosmetic feature or threshold sweeps.
