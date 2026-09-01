# URL-ML V4 Representation Forensic Report

V3 identified missing host/path/query/protocol structure and domain-independent lexical information. V4 implemented those dimensions without changing Messages-ML or Android integration.

The clean training boundary is `data/cleaned_dataset.csv` (235,795 rows, labels normalized to 0=safe/1=phishing). `data/hard_dataset.csv` (2,800 rows) was never fit, used for feature selection, or used for threshold selection. The V3 remediation CSV was the only additional training input.

The deterministic representation has 92 features: the historical 15 plus host morphology, path morphology, query structure, and protocol/component interactions. Hashed character variants contain 1,092 and 1,592 effective features, respectively, with four scoped 3–5 character n-gram streams and no explicit domain vocabulary.

The forensic result is mixed: deterministic structure greatly improves clean unseen-domain performance, but hard-source benign rejection remains poor without much stronger source-diverse coverage. Character hashing does not fix the shift and has lower hard-source AUC than the deterministic representation. Therefore the principal remaining limitation is source coverage/distribution, not merely feature dimensionality.
