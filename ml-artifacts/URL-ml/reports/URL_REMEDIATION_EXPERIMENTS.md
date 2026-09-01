# URL-ML Remediation Experiments

All challengers were trained from scratch. The frozen champion artifact was not used as a training starting point and no TEST/OOD/hard-source row was added to training. Thresholds were selected by a dense validation-only sweep under 1% benign FPR.

- **challenger_hardsource_v1_full_w1**: remediation weight 1; threshold 0.2675; validation FPR/recall 0.0099/0.9881; TEST FPR/recall 0.0097/0.9836; hard-source FPR/recall 0.4176/0.9988; size 12,931,161 bytes.
- **challenger_hardsource_v1_full_w5**: remediation weight 5; threshold 0.2515; validation FPR/recall 0.0099/0.9903; TEST FPR/recall 0.0100/0.9855; hard-source FPR/recall 0.4094/0.9905; size 13,486,201 bytes.
- **challenger_hardsource_v1_full_w10**: remediation weight 10; threshold 0.2450; validation FPR/recall 0.0099/0.9905; TEST FPR/recall 0.0104/0.9852; hard-source FPR/recall 0.4002/0.9845; size 12,917,401 bytes.
- **challenger_hardsource_v1_structural_w5**: remediation weight 5; threshold 0.2875; validation FPR/recall 0.0096/0.9918; TEST FPR/recall 0.0094/0.9876; hard-source FPR/recall 0.3946/0.9834; size 15,497,401 bytes.
