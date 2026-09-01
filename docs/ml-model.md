# Machine Learning Models

Sentinel AI runs two specialized, zero-dependency machine learning models natively on-device in pure Kotlin. These models complement heuristic rule engines and emit isolated `ThreatEvidence` to the `RiskFusionEngine`.

---

## 1. URL-ML Champion V7

### Overview
- **Model Type:** Histogram-based Gradient Boosting Classifier (`HistGradientBoostingClassifier`)
- **Number of Features:** Exactly 67 continuous float features in canonical alphabetical order.
- **Tree Count:** 350 trees (single-class binary classification).
- **Hyperparameters:** Base score $= -0.4490818963047636$, Learning rate $= 0.06$, Sigmoid log-odds link function.
- **Decision Threshold:** $\tau = 0.22588723$.
- **Adjudication Rule:** Scores $\ge \tau$ classify as `PHISHING`, otherwise `BENIGN`. Safe-brand domains (matching trusted apex/subdomains without punycode/IP/hyphenation anomalies) undergo calibrated safe-brand probability clamping.
- **Validation Parity:** 151/151 golden labels match; numeric differences are reported separately (this is not bit-for-bit parity).

### Feature Set (67 Canonical Features)
Features are extracted deterministically in alphabetical order:
1. `brand_impersonation_score`
2. `char_entropy`
3. `count_ampersand`
4. `count_at`
5. `count_comma`
6. `count_dash`
7. `count_digits`
8. `count_dollar`
9. `count_dot`
10. `count_double_slash`
11. `count_equal`
12. `count_exclamation`
13. `count_hash`
14. `count_letters`
15. `count_percent`
16. `count_plus`
17. `count_query_params`
18. `count_question`
19. `count_semicolon`
20. `count_slash`
21. `count_subdomains`
22. `count_tilde`
23. `count_underscore`
24. `consecutive_digits_max`
25. `consecutive_letters_max`
26. `domain_entropy`
27. `domain_has_digits`
28. `domain_has_hyphen`
29. `domain_has_ip`
30. `domain_is_punycode`
31. `domain_length`
32. `domain_non_alpha_ratio`
33. `domain_vowel_ratio`
34. `has_credentials`
35. `has_fragment`
36. `has_ip`
37. `has_port`
38. `has_query`
39. `has_shortener`
40. `has_suspicious_keywords`
41. `has_suspicious_tld`
42. `is_https`
43. `longest_subdomain_len`
44. `path_entropy`
45. `path_has_digits`
46. `path_has_hyphen`
47. `path_length`
48. `path_slash_count`
49. `path_upper_ratio`
50. `query_length`
51. `query_param_count`
52. `ratio_digits_to_letters`
53. `ratio_digits_url`
54. `ratio_letters_url`
55. `ratio_special_url`
56. `subdomain_entropy`
57. `subdomain_length`
58. `tld_in_path`
59. `tld_in_subdomain`
60. `tld_length`
61. `url_entropy`
62. `url_length`
63. `url_non_alpha_ratio`
64. `url_upper_ratio`
65. `vowel_ratio`
66. `vowel_to_consonant_ratio`
67. `whitespace_count`

### Tree Evaluation & Binning
Continuous feature values are converted to bin indices using `searchsorted(side='left')` against 256 numerical threshold boundaries per feature. Tree traversal evaluates the left child if `feature_bin <= threshold_bin`, and the right child otherwise, accumulating leaf values into a raw score transformed by the standard logistic sigmoid:
$$\sigma(z) = \frac{1}{1 + e^{-z}}$$

---

## 2. Messages-ML Champion V2

### Overview
- **Model Type:** Multimodal Standardized Multiclass Histogram-based Gradient Boosting Classifier
- **Total Feature Dimension:** 2,070 dimensions (70 tabular deterministic features + 1,500 word TF-IDF + 500 char_wb TF-IDF).
- **Classes:** 3 classes (`0: BENIGN`, `1: SUSPICIOUS_SPAM`, `2: MALICIOUS`).
- **Tree Count:** 309 trees (103 boosting iterations $\times$ 3 classes).
- **Hyperparameters:** Multiclass log-loss with softmax output.
- **Adjudication Threshold:** $\tau = 0.704$ on the compound non-benign probability $P(\text{SUSPICIOUS\_SPAM}) + P(\text{MALICIOUS})$.
- **Adjudication Rule:** If $P(\text{non-benign}) \ge \tau$, output `SUSPICIOUS_SPAM` (if $P(\text{SUSP}) > P(\text{MAL})$) or `MALICIOUS` (otherwise). If $P(\text{non-benign}) < \tau$, output `BENIGN`.
- **Validation Parity:** 116/116 golden labels match; maximum observed deterministic-feature and probability differences are approximately 0.04166 and 0.024714 respectively.

### Feature Construction
1. **70 Deterministic Tabular Features:**
   - Structural indicators (message length, word count, character entropy, uppercase ratio, digit ratio, punctuation density).
   - Urgency & Psychological Coercion signals (urgency words, strict deadline words, time limit expressions).
   - Threat & Consequence indicators (account blocked, penalty/fine threats, police/legal action notices, electricity/service disconnection notices, unauthorized security alert alerts, mandatory KYC/PAN update demands).
   - Credential Harvesting indicators (OTP requests, PIN/password requests, numeric codes, OTP disclosure requests, delivery context filters).
   - Financial Signals (debit/credit transactions, currency amounts, masked account numbers, balance queries, UPI collect requests, lottery/cashback lures).
   - Technical & Contact vectors (URLs, URL shorteners, raw IPs, APK download links, phone numbers, UPI VPA addresses, WhatsApp/Telegram redirection).
   - Sender Channel signals (DLT headers, alpha senders, bank codes, short codes, sender categories).
2. **1,500 Word TF-IDF Features:**
   - Unigrams and bigrams matching Unicode word token pattern `(?u)\b\w\w+\b`.
   - Sublinear term frequency ($1 + \log(\text{tf})$), inverse document frequency weighting, and L2 unit-norm normalization.
3. **500 Character N-gram TF-IDF Features:**
   - Word-boundary character n-grams of length 3 to 5 (`char_wb`).
   - Sublinear term frequency, inverse document frequency weighting, and L2 unit-norm normalization.
4. **Standard Scaler:**
   - All 2,070 concatenated dimensions undergo 64-bit IEEE standard scaling:
   $$z_i = \frac{x_i - \mu_i}{\sigma_i}$$

---

## 3. Threat Evidence & Integration Contract

Neither model produces final application authorization decisions. Instead:
- `UrlScanner` emits `ThreatEvidence(category = EvidenceCategory.URL_ML, ...)`
- `MessageScanner` emits message-model results as evidence; the notification analyzer maps them to `EvidenceCategory.MESSAGE_ML` and `EvidenceType.MESSAGE_ML_SCORE`.

The `RiskFusionEngine` consumes these evidence items alongside heuristic rules, domain intelligence, and sender reputation to derive the final authoritative `ScanResult`.

```text
+-----------------------+     +--------------------------+
|  UrlScanner (V7)      |     |  MessageScanner (V2)     |
|  67-Dim HistGBM       |     |  2,070-Dim Multimodal    |
+-----------+-----------+     +------------+-------------+
            |                              |
            v                              v
    ThreatEvidence                 ThreatEvidence
   (EvidenceCategory.URL_ML)     (EvidenceCategory.MESSAGE_ML)
            |                              |
            +--------------+---------------+
                           |
                           v
              +--------------------------+
              |    RiskFusionEngine      |
              |  (Sole Risk Authority)   |
              +--------------+-----------+
                             |
                             v
                     Final ScanResult
                   (ALLOW / WARN / BLOCK)
```

---

## 4. Key Security & Performance Guarantees

1. **Zero External Runtime Dependencies:** Implemented in pure Kotlin. No TensorFlow Lite runtime, Python, ONNX, or JNI binary dependencies required.
2. **100% Offline Execution:** All vocabulary tables, IDF arrays, scaling parameters, bin thresholds, and tree structures are bundled locally in the APK assets.
3. **Deterministic & Fail-Safe Invariant:** Any asset loading or runtime inference error emits explicit `CRITICAL` ML-unavailable evidence (`EvidenceSeverity.CRITICAL`, confidence `1.0f`). The `RiskFusionEngine` escalates the verdict to `BLOCK` ($\ge 90$). ML failure is explicit security evidence, not silent absence of evidence, and never grants an unauthorized `ALLOW` bypass.
