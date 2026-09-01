# ML Integration Contract — Sentinel AI (Forensically Verified)

This document establishes the forensically verified specification, mathematical contract, and golden parity tolerances for integrating **URL-ML Champion V7** and **Messages-ML Champion V2** into Sentinel AI.

---

## 1. URL-ML Champion V7 Contract

### 1.1 Specification Overview
- **Model Type**: HistGradientBoostingClassifier (Binary Classification: Benign vs Malicious Phishing)
- **Deployment Artifact**: `v7_champion_portable.json` (1,005 KB)
- **Total Trees**: 350 binary decision trees
- **Base Score (\( \text{init\_score} \))**: `-0.4490818963047636`
- **Learning Rate**: `0.06`
- **Loss Function**: `log_loss` (binary cross-entropy)
- **Input Dimension**: Exactly 67 continuous/deterministic float features
- **Decision Threshold (\( \tau \))**: `0.22588723` (calibrated operating point)
- **Safe-Domain Gating (\( \text{conf\_bound} \))**: `0.80`
- **Safe-Domain Gating Floor**: `0.001`

### 1.2 SafeDomainAdjudicator Behavior (Proven in Training Source `v7_classifier.py`)
Safe-domain gating is an explicit component of the production `V7GatedURLClassifier` in `v7_classifier.py`:
1. `has_threat` is active if ANY of the following hold:
   - `BrandImpersonationScore > 0`
   - `SuspiciousTLD > 0`
   - `IsDomainIP > 0`
   - `PathSuspiciousExtension > 0`
   - `QueryRedirect > 0`
   - `PathSuspiciousWords > 0`
   - `QuerySuspiciousWords > 0`
   - `PathDoubleEncoded > 0`
   - `HasAtSymbol > 0`
   - `HostPunycode > 0`
   - `HasPort > 0`
   - `PathConsecutiveSlashes > 0`
   - `HasNonAscii > 0`
   - `HasNestedURL > 0`
   - `NoOfSubDomain >= 2`
2. `is_safe_brand = (Safe_Brand_Domain == 1.0) and (not has_threat)`
3. If `is_safe_brand` and `raw_probability < 0.80`, `final_probability` is clamped to `0.001`.
4. Classification: If `final_probability >= 0.22588723`, label is `MALICIOUS`, else `BENIGN`.

### 1.3 Deterministic 67 Feature Ordering (Alphabetical)
1. `BrandImpersonationScore` (0.0 .. 1.0)
2. `DomainLength` (float)
3. `HasAtSymbol` (0.0 / 1.0)
4. `HasNestedURL` (0.0 / 1.0)
5. `HasNonAscii` (0.0 / 1.0)
6. `HasPort` (0.0 / 1.0)
7. `HostDigitRatio` (0.0 .. 1.0)
8. `HostEntropy` (float)
9. `HostHexPattern` (0.0 / 1.0)
10. `HostHyphenCount` (float)
11. `HostNumericLabels` (float)
12. `HostPunycode` (0.0 / 1.0)
13. `HostUnderscoreCount` (float)
14. `HostVowelRatio` (0.0 .. 1.0)
15. `IsDomainIP` (0.0 / 1.0)
16. `IsHTTPS` (0.0 / 1.0)
17. `IsIPv4` (0.0 / 1.0)
18. `KnownBrandDomain` (0.0 / 1.0)
19. `NoOfSubDomain` (float)
20. `PathAtSymbol` (0.0 / 1.0)
21. `PathConsecutiveSlashes` (float)
22. `PathDepth` (float)
23. `PathDigitRatio` (0.0 .. 1.0)
24. `PathDoubleEncoded` (0.0 / 1.0)
25. `PathEncodedCount` (float)
26. `PathEntropy` (float)
27. `PathHexHash` (0.0 / 1.0)
28. `PathLength` (float)
29. `PathSuspiciousExtension` (0.0 / 1.0)
30. `PathSuspiciousWords` (float count)
31. `PathTraversalCount` (float)
32. `QueryAtSymbol` (0.0 / 1.0)
33. `QueryEncodedCount` (float)
34. `QueryEntropy` (float)
35. `QueryHexHash` (0.0 / 1.0)
36. `QueryLength` (float)
37. `QueryLongValue` (0.0 / 1.0)
38. `QueryParamCount` (float)
39. `QueryRedirect` (0.0 / 1.0)
40. `QuerySuspiciousWords` (float count)
41. `Risk_BrandImpersonation_on_Subdomain` (float interaction)
42. `Risk_BrandImpersonation_on_SuspiciousTLD` (float interaction)
43. `Risk_Digits_with_BrandImp` (float interaction)
44. `Risk_Digits_with_SuspiciousTLD` (float interaction)
45. `Risk_HTTP_with_BrandImpersonation` (float interaction)
46. `Risk_HTTP_with_SuspiciousTLD` (float interaction)
47. `Risk_HTTP_with_SuspiciousWords` (float interaction)
48. `Risk_Hyphen_with_BrandImp` (float interaction)
49. `Risk_Hyphen_with_Subdomain` (float interaction)
50. `Risk_Hyphen_with_SuspiciousTLD` (float interaction)
51. `Risk_IP_with_Path` (float interaction)
52. `Risk_PathDepth_on_SuspiciousHost` (float interaction)
53. `Risk_PathDigit_on_SuspiciousHost` (float interaction)
54. `Risk_PathEntropy_on_SuspiciousHost` (float interaction)
55. `Risk_Redirect_on_SuspiciousHost` (float interaction)
56. `Risk_SuspiciousExt_on_SuspiciousHost` (float interaction)
57. `Risk_SuspiciousWord_on_BrandImpersonation` (float interaction)
58. `Risk_SuspiciousWord_on_IP` (float interaction)
59. `Risk_SuspiciousWord_on_Subdomain` (float interaction)
60. `Risk_SuspiciousWord_on_SuspiciousTLD` (float interaction)
61. `Safe_Brand_Domain` (0.0 / 1.0)
62. `Safe_Clean_Domain` (0.0 / 1.0)
63. `SpecialCharRatio` (0.0 .. 1.0)
64. `SuspiciousTLD` (0.0 / 1.0)
65. `URLCharEntropy` (float)
66. `URLLength` (float)
67. `URLUppercaseRatio` (0.0 .. 1.0)

### 1.4 Golden Dataset Provenance & Parity Results (151 Records)
- **File**: `golden_urls.json`
- **Corpus**: 60 Benign Top Domains, 20 Indian Portals, 35 Phishing/Impersonations, 15 Malicious IP/APK links, 21 Evasion/Punycode/Shortener cases.
- **Label Parity**: **151 / 151 (100.0%) identical match**.
- **Max Feature Diff**: \( 2.38 \times 10^{-7} \) (single-precision IEEE float rounding in Shannon entropy).
- **Max Raw Probability Diff**: \( 0.000000 \).
- **Max Final Probability Diff**: \( 0.000000 \).

---

## 2. Messages-ML Champion V2 Contract

### 2.1 Specification Overview
- **Model Type**: Multi-class HistGradientBoostingClassifier (3 Classes: `0: BENIGN`, `1: SUSPICIOUS_SPAM`, `2: MALICIOUS`)
- **Deployment Artifacts**:
  - `champion_v2_word_vocab_idf.json` (1,500 word vocabulary + IDF + stop words)
  - `champion_v2_char_vocab_idf.json` (500 character n-gram vocabulary + IDF)
  - `champion_v2_scaler.json` (2,070 dimension mean and scale standardizer)
  - `champion_v2_trees.json` (103 iterations \(\times\) 3 classes = 309 decision trees)
- **Baseline Prediction**: `[9.650932925393424e-05, -4.845708586817077e-05, -4.80522433853845e-05]`
- **Learning Rate**: `0.1`
- **Total Input Dimensions**: Exactly 2,070 continuous double features:
  - Indices `[0 .. 69]`: 70 deterministic structural, lexical, urgency, auth, financial, and sender features.
  - Indices `[70 .. 1569]`: 1,500 Word TF-IDF features (n-gram 1-2, English stop-word filtered, L2 normalized).
  - Indices `[1570 .. 2069]`: 500 Char_wb TF-IDF features (n-gram 3-5, L2 normalized).
- **Feature Scaling**: Standard scaler \( z_i = \frac{x_i - \mu_i}{\sigma_i} \) across all 2,070 features in 64-bit precision.
- **Adjudication**:
  - Non-benign probability \( P(\text{non-benign}) = P(\text{SUSPICIOUS\_SPAM}) + P(\text{MALICIOUS}) \).
  - Decision threshold \( \tau = 0.704 \).
  - If \( P(\text{non-benign}) \ge 0.704 \): label `SUSPICIOUS_SPAM` if \( P(\text{SUSP}) > P(\text{MAL}) \) else `MALICIOUS`.
  - Else: label `BENIGN`.

### 2.2 Preprocessing & Tokenization Contract
- **Word TF-IDF**:
  - Token pattern: Unicode word regex `(?u)\b\w\w+\b`.
  - 318 English stop words. Stop words are filtered out before unigram and bigram counting.
  - L2 normalized: \( v_i \leftarrow \frac{v_i}{\sqrt{\sum v_k^2}} \).
- **Char_wb TF-IDF**:
  - Words split on whitespace. Each word padded with boundary spaces: ` $w `.
  - Character n-grams generated for lengths 3, 4, and 5 within word boundaries.
  - L2 normalized.
- **Unicode Code Point Counting**:
  - In Java/Kotlin, character iteration and message length must use Unicode code points (`codePointCount`, `codePointAt`) rather than UTF-16 code units (`length`), ensuring surrogate pair emojis are handled identically to Python.

### 2.3 Golden Dataset Provenance & Parity Results (116 Records)
- **File**: `golden_messages.json`
- **Corpus**: 30 Benign Banking/OTP, 15 Delivery, 15 Casual, 15 Reward Spam, 15 Power Disconnection Phishing, 15 KYC/PAN Phishing, 5 OTP Theft, 3 APK Reference, 3 Evasion/Homoglyphs.
- **Label Parity**: **116 / 116 (100.0%) identical match**.
- **Max Deterministic Feature Diff**: \( 0.04166 \) (single edge case on non-BMP emoji avg word length).
- **Max Probability Diff**: \( 0.024714 \) (well within the safe decision boundary).

---

## 3. Existing Android Code Reuse & Integration Assessment

| Component | Status | Action |
| :--- | :--- | :--- |
| `RiskFusionEngine.kt` / `DefaultRiskFusionEngine.kt` | **Authoritative & Ready** | Fully reuse. Already contains `EvidenceCategory.URL_ML` and `MESSAGE_ML` rules. |
| `ThreatEvidence.kt` | **Authoritative & Ready** | Fully reuse. Emits atomic ML evidence without modifying core fusion. |
| `BrowserSelectionPolicy.kt` / `BrowserLauncher.kt` | **Authoritative & Ready** | Fully preserve. `GREEN + ALLOW` remains the sole fast path. |
| `IntentThreatAnalyzerImpl.kt` | **Production Integration Target** | Replace old 15-feature inference call with `UrlScanner` V7. |
| `NotificationThreatAnalyzer.kt` / `NotificationAgentCoordinator.kt` | **Production Integration Target** | Integrate `MessageScanner` V2 evidence alongside existing heuristic rules. |
| `com.sentinel.ai.ml.*` (legacy 15-feature classes) | **Legacy / Deprecated** | Upgrade to V7 / V2 zero-dependency Kotlin runtime engines. |
