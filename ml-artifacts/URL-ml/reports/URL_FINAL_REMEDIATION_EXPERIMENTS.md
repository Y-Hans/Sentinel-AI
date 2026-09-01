# URL-ML Focused Final Remediation Experiments

Fresh finalist trained from scratch on remediation v3 (3,430 benign URLs across 49 source-diverse domains; 686 HTTP rows), targeting residual ordinary/deep non-HTTPS paths.

- Threshold: 0.4590 (dense validation-only selection)
- Validation FPR/recall: 0.0100/0.7943
- TEST FPR/recall: 0.0096/0.8940
- Hard-source FPR/recall: 0.0567/0.9703
- Model size: 4,817,785 bytes

The prior v2 finalist and frozen champion were preserved.
