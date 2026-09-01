# URL-ML Focused Final Remediation Forensic Analysis

Finalist threshold: `0.4525`. Residual hard-source benign false positives: **327 / 1959 (16.69%)**.

## Residual clusters

- **deep_path**: n=58; domains=35; mean score=0.582; path depth=3.24; query rate=0.000; security rate=0.000; payment rate=0.000.
- **other**: n=220; domains=62; mean score=0.593; path depth=1.36; query rate=0.000; security rate=0.000; payment rate=0.000.
- **query**: n=29; domains=23; mean score=0.562; path depth=1.83; query rate=1.000; security rate=0.000; payment rate=0.000.
- **security/payment**: n=20; domains=19; mean score=0.608; path depth=1.85; query rate=0.000; security rate=0.200; payment rate=0.800.

## Comparison groups

The JSON contains feature means for residual false positives, correctly classified hard benign URLs, hard malicious URLs, TEST benign URLs, and TEST malicious URLs. No held-out row was used to create training data or select a threshold.

Focused conclusion: residual errors are distributed across 67 domains and are dominated by ordinary/deep paths rather than a single security-keyword family. The v3 HTTP/path expansion directly reduced the residual holdout FPR, confirming missing coverage; its TEST recall cost shows the current representation remains inadequate.
