# URL-ML Final Release Audit V3

## Decision

**BOTH_REQUIRED**

The best representation-remediation finalist, `challenger_hardsource_v3_extra40`, achieved TEST FPR 0.963% and TEST malicious recall 89.40%, with hard-source FPR 5.67% and hard-source malicious recall 97.03%. It is resource-safe at 4.82 MB, but it does not preserve the required TEST security performance while solving the hard-source shift. No challenger was promoted.

Independent retraining reproduced the same thresholded predictions and metrics. OOD remains `OOD_UNAVAILABLE`. The frozen champion, TEST/OOD/hard data, historical reports, Messages-ML, and Android were preserved.
