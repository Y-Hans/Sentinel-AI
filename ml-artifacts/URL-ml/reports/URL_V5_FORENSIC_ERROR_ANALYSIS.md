# URL-ML V5 Forensic Error Analysis

The decisive error is benign source shift, not malicious detection capacity. The protected hard-source population contains legitimate URLs whose structural morphology overlaps phishing-like URLs: provider/CDN infrastructure, deep ordinary paths, HTTP URLs, query-heavy links, authentication/account workflows, redirects/tracking patterns, and long or encoded paths. V4 forensic reports identify this family-level pattern; V5 HGB changes the ranking of scores but does not remove it.

At the V5 160x operating point, the hard-source confusion matrix is `[[1432, 527], [31, 810]]`: 527 benign false positives and 31 malicious false negatives. The clean TEST confusion matrix is `[[27063, 290], [111, 23047]]`. This contrast indicates source distribution shift: clean TEST recall remains 99.52%, while hard-source benign FPR is 26.90%.

The appropriate intervention is new adjudicated, source-diverse benign training coverage with domain/provider separation. Copying or transforming protected rows would violate the protocol and was not done.
