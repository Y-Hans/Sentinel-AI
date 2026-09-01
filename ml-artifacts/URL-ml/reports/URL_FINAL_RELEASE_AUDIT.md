# URL-ML Final Release Audit

## Decision

**MODEL_REMEDIATION_REQUIRED**

Champion: **extratrees_full**; threshold: **0.3330**.

## Gates

- Test: {'n': 43433, 'accuracy': 0.9884880160246817, 'precision': 0.9839315397401958, 'recall': 0.9852660131927748, 'f1': 0.9845983242976836, 'benign_fpr': 0.009591356754373071, 'confusion_matrix': [[26951, 261], [239, 15982]], 'roc_auc': 0.998290960582915, 'log_loss': 0.03996231887170748}
- Hard/source holdout: {'n': 2800, 'accuracy': 0.30464285714285716, 'precision': 0.3016499282639885, 'recall': 1.0, 'f1': 0.4634885643427942, 'benign_fpr': 0.9938744257274119, 'confusion_matrix': [[12, 1947], [0, 841]], 'roc_auc': 0.5125695060269411, 'log_loss': 7.873599309598741}
- Adversarial: separate report
- Resources: separate report

This is a conservative statistical model freeze for URL-ML only. Messages-ML and Android integration were not modified.
