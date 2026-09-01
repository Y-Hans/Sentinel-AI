# URL-ML Source-Invariance Audit

Dataset SHA-256: `cbabd318016cbe8f047c511431a44dbb6b74ab7796780ff73a359b8012a98380`

Hard-source rows were used only for evaluation; no hard-source row entered training or threshold selection.

## Controlled ablation

- **full** (15 features): threshold=0.333; validation FPR/recall=0.0099/0.9879; test FPR/recall=0.0096/0.9853; hard-source FPR/recall=0.9939/1.0000.
- **source_reduced_no_identity** (12 features): threshold=0.349; validation FPR/recall=0.0099/0.9923; test FPR/recall=0.0087/0.9917; hard-source FPR/recall=0.9939/0.9929.
- **domain_invariant_structure** (9 features): threshold=0.356; validation FPR/recall=0.0098/0.9918; test FPR/recall=0.0095/0.9901; hard-source FPR/recall=0.9939/0.9786.
- **base_only** (9 features): threshold=0.375; validation FPR/recall=0.0095/0.9881; test FPR/recall=0.0096/0.9882; hard-source FPR/recall=0.9939/0.9952.

## Interpretation

The ablations distinguish exact/identity-like domain signals from structural domain information. Results are evidence for representation choice, not a promotion decision. The frozen champion remains recoverable and was not overwritten.
