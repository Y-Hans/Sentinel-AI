# URL-ML V7 Feature Representation Report (V7.5)

---

## 1. Feature Representation Overview

The V7.5 feature representation (`v7_features.py`) consists of **67 deterministic, float32 numerical features**. It is designed for maximum discrimination against malicious threats while preventing false alarms on realistic web structures.

---

## 2. Feature Category Taxonomies

### 2.1 Host Morphology & Anomaly Indicators (21 Features)
- `DomainLength`: Character count of host.
- `IsDomainIP`: Binary indicator for IPv4 / IPv6 hosts.
- `IsIPv4`: Binary indicator specifically for IPv4 hosts.
- `NoOfSubDomain`: Effective subdomain depth accounting for multi-label public suffixes (`co.uk`, `gov.uk`, etc.).
- `SuspiciousTLD`: Binary flag for high-risk TLDs (`.xyz`, `.top`, `.buzz`, `.cf`, `.gq`, `.tk`, `.ml`, `.fit`, `.icu`, `.sbs`, etc.).
- `BrandImpersonationScore`: Continuous score ($[0.0, 1.0]$) measuring Leet-speak, edit distance, and token matching against 200+ legitimate brand trademarks in subdomains/prefixes.
- `KnownBrandDomain`: Binary flag indicating the registrable domain ITSELF is a recognized brand/institution.
- `HostHyphenCount`: Count of hyphens in hostname.
- `HostUnderscoreCount`: Count of underscores in hostname.
- `HostDigitRatio`: Ratio of digits to host length.
- `HostEntropy`: Shannon character entropy of the hostname.
- `HostPunycode`: Binary flag for Internationalized Domain Names (`xn--`).
- `HostHexPattern`: Binary flag for 8+ consecutive hex characters in hostname.
- `HostVowelRatio`: Ratio of vowels in registrable domain label.
- `HostNumericLabels`: Count of fully numeric hostname labels (e.g. `192.168.1.1`).
- `IsHTTPS`: Binary indicator for `https://` protocol.
- `HasPort`: Binary flag for non-standard ports (excluding 80 and 443).
- `HasAtSymbol`: Binary flag for credential embedding (`user@host`).
- `HasNonAscii`: Binary flag for non-ASCII homoglyph character substitutions.
- `URLUppercaseRatio`: Ratio of uppercase characters (case manipulation detection).
- `URLCharEntropy`: Shannon entropy across the entire URL string.

### 2.2 Path Morphology & Syntax Indicators (11 Features)
- `PathLength`: Character length of the normalized path.
- `PathDepth`: Number of non-empty path segments (evaluates to $0$ on trailing slashes like `https://domain.com/`).
- `PathConsecutiveSlashes`: Count of double slashes `//` in path.
- `PathTraversalCount`: Count of `../` and `/./` directory traversals.
- `PathEntropy`: Shannon character entropy of the decoded path.
- `PathSuspiciousExtension`: Binary flag for executable/payload extensions (`.exe`, `.scr`, `.apk`, `.vbs`, `.iso`, `.bat`, etc.).
- `PathSuspiciousWords`: Count of credential/security keywords (`login`, `signin`, `verify`, `wallet`, `token`, `checkout`) including normalized separator-stripped variants (`l-o-g-i-n`).
- `PathEncodedCount`: Count of percent-encoded triplets (`%xx`).
- `PathDoubleEncoded`: Binary flag for `%25` double-encoding evasion.
- `PathAtSymbol`: Binary flag for `@` symbol in path.
- `PathDigitRatio`: Digit ratio in normalized path.
- `PathHexHash`: Binary flag for 16+ hexadecimal string in path.

### 2.3 Query & Parameter Indicators (10 Features)
- `QueryLength`: Character length of query string.
- `QueryParamCount`: Count of `&`-separated parameters.
- `QueryEncodedCount`: Count of percent-encoded triplets in query.
- `QueryRedirect`: Binary flag for open redirect parameters (`url=http...`, `next=https...`, `dest=...`).
- `QueryAtSymbol`: Binary flag for `@` symbol in query.
- `QueryLongValue`: Binary flag for parameter values exceeding 64 characters.
- `QueryEntropy`: Shannon entropy of query string.
- `QuerySuspiciousWords`: Count of credential/phishing keywords in query string.
- `QueryHexHash`: Binary flag for 16+ hexadecimal string in query.
- `HasNestedURL`: Binary flag for embedded protocol schemes (`http://`, `https://`) in paths or queries.

### 2.4 Threat Interaction & Safety Gating Features (25 Features)
- `Risk_SuspiciousWord_on_SuspiciousTLD`: Suspicious keywords on high-risk TLDs.
- `Risk_SuspiciousWord_on_BrandImpersonation`: Suspicious keywords on impersonated brand hosts.
- `Risk_SuspiciousWord_on_IP`: Suspicious keywords on raw IP hosts.
- `Risk_SuspiciousWord_on_Subdomain`: Suspicious keywords on deep subdomains ($\ge 2$).
- `Risk_BrandImpersonation_on_SuspiciousTLD`: Brand impersonation on high-risk TLDs.
- `Risk_BrandImpersonation_on_Subdomain`: Brand impersonation on subdomains.
- `Risk_IP_with_Path`: Path presence on raw IP address hosts.
- `Risk_HTTP_with_BrandImpersonation`: Plain HTTP on brand impersonation hosts.
- `Risk_HTTP_with_SuspiciousTLD`: Plain HTTP on high-risk TLDs.
- `Risk_HTTP_with_SuspiciousWords`: Plain HTTP with credential phishing keywords.
- `Risk_Redirect_on_SuspiciousHost`: Open redirect on suspicious host contexts.
- `Risk_SuspiciousExt_on_SuspiciousHost`: Payload download extensions on suspicious hosts.
- `Risk_PathDigit_on_SuspiciousHost`: Path digits active only on suspicious hosts.
- `Risk_PathDepth_on_SuspiciousHost`: Path depth active only on suspicious hosts.
- `Risk_PathEntropy_on_SuspiciousHost`: Path entropy active only on suspicious hosts.
- `Risk_Hyphen_with_BrandImp`: Host hyphens on brand impersonation hosts.
- `Risk_Hyphen_with_SuspiciousTLD`: Host hyphens on high-risk TLDs.
- `Risk_Hyphen_with_Subdomain`: Host hyphens on deep subdomains.
- `Risk_Digits_with_BrandImp`: Host digits on brand impersonation hosts.
- `Risk_Digits_with_SuspiciousTLD`: Host digits on high-risk TLDs.
- `Safe_Clean_Domain`: Binary indicator for clean standard domain with zero host threat markers.
- `Safe_Brand_Domain`: Binary indicator for recognized brand/institution domain with zero threat markers.

---

## 3. Determinism & Mobile Portability

1. **Zero External Network Dependencies**: All features are computed strictly from the URL string in-memory.
2. **Deterministic Output**: Given any URL string, `extract_v7_features` produces identical float32 feature vectors across all OS platforms (Windows, Linux, macOS, Android, iOS).
3. **Pure Kotlin Feasibility**: The feature extractor utilizes only basic string manipulations, regexes, and character arithmetic, mapping directly to Kotlin standard library functions (`java.net.URI`, `Regex`, `String.count`).
