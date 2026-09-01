# URL-ML V6 Forensic Analysis

The protected hard source contains 1,959 benign and 841 malicious URLs across 687 benign registrable domains. Its benign population is dominated by legitimate provider/public-service domains and includes HTTP (295 rows), query URLs (288 rows), and ordinary/deep workflow paths. The dominant failure is benign source shift: URL-only morphology overlaps phishing-like morphology.

The best V6 ordinary TEST point was the char model (0.786% FPR, 99.663% recall), but it labeled 1,368/1,959 protected benign URLs malicious. The best prior safe hard-source point remains V4 ExtraTrees at 12.404% FPR / 96.790% recall; V5 HGB was 26.901% / 96.314%. V6 synthetic structural coverage produced 36.651%–46.912% hard benign FPR, and character morphology produced 69.832%.

This pattern is not fixed by threshold movement: the validation-selected thresholds already enforce the 1% validation FPR policy, while protected AUC remains poor relative to ordinary TEST. The residual family-level problem is missing adjudicated coverage and semantic/provider context unavailable to the URL-only representation.
