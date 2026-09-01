# URL-ML V6 Final Release Audit

Decision: **DATA_REQUIRED**. No candidate passes the complete release gate.

| Metric | Best V6 result | Gate | Status |
|---|---:|---:|---|
| TEST benign FPR | 0.786% | <=1.00% | PASS |
| TEST malicious recall | 99.663% | >=98.00% | PASS |
| Protected hard benign FPR | 36.651% (structural) / 69.832% (lexical) | <=1.00% | FAIL |
| Protected hard malicious recall | 97.503% / 100.000% | >=95.00% | PASS |
| Adversarial recall | 99.667% / 99.590% | >=95.00% | PASS |
| Bundle size | 921,407 bytes HGB; 2,523,835 bytes lexical | <=10 MB | PASS |
| Deterministic offline inference | verified for lexical artifact | required | PASS |

The best V6 ordinary TEST candidate is `v6_char_tfidf_logreg`, but it is not release-ready because protected benign FPR fails by a wide margin. Independent serialized-artifact evaluation reproduced its hard confusion matrix exactly: `[[591,1368],[0,841]]`. Compilation passed. The existing TFLite smoke test passed for the historical scalar artifact; V6 lexical TF-IDF has no Android/TFLite qualification and was not promoted.
