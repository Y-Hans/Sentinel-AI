# Final Release Decision

**CONDITION B — DATASET_REMEDIATION_REQUIRED**

The current model is demonstrably strong on the principal untouched TEST/OOD security gates, but release readiness is not established. The credential metric uses a threat-vector tag whose schema definition is not equivalent to “actual credential theft”; the independently derived population contains a large ambiguous stratum. Original-label recall is 79.19%, while verified-malicious recall is 100.00%. That decomposition shows why 79.19% alone cannot establish a model defect.

The current reconstruction satisfies the hard-negative slice at 0.00% record-level and 0.00% unique-message FPR, but the frozen V1 artifact reproduces the historical MSEDCL defect at 13.89% record-level FPR (10 replicated records). This version distinction is preserved rather than erased.

No challenger was trained. No champion, TEST/OOD file, URL-ml file, package, TFLite artifact, Android integration, commit, or push was created or modified by this audit.

Reproduce with `python Messages-ml/model_training/final_release_gate_audit.py`; inspect the accompanying JSON for exact hashes, rows, probabilities, and examples.
