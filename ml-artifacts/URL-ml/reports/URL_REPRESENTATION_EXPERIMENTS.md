# URL-ML Representation Experiments

The focused phase tested only evidence-supported upgrades: targeted HTTP/path data with the existing representation, remediation weighting, compact ExtraTrees, compact linear baselines, and a validation-selected hybrid reference. No broad hyperparameter grid was run.

The strongest representation/data finalist was `challenger_hardsource_v3_extra40`: TEST FPR 0.963%, TEST malicious recall 89.40%, hard-source FPR 5.67%, hard-source malicious recall 97.03%, and 4.82 MB. Lower remediation weight produced TEST recall 90.50% but hard-source FPR 16.13%. Neither satisfies the desired joint operating point.

Full experiment records are in `URL_FINAL_REMEDIATION_EXPERIMENTS.json`, `EXPERIMENT_REGISTRY.jsonl`, and `URL_FINAL_RELEASE_AUDIT_V3.json`.
