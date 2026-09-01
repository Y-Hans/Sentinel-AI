# URL-ML Error Analysis

Family metrics are reported for the domain-disjoint test split. The feature set is structural/lexical and contains no external reputation or content-fetch signal; compromised legitimate domains and semantically ambiguous login/payment URLs remain material risks.

Champion test family metrics:

- phishing_keywords: recall 0.9981, benign FPR 0.2206, n=1121
- shortened: recall 0.9893, benign FPR 0.0020, n=1751
- obfuscated: recall 0.9948, benign FPR 0.0000, n=384
- ip_address: recall 1.0000, benign FPR 0.0000, n=146
- punycode_idn: recall 0.9655, benign FPR 0.0000, n=29
- unusual_port: recall 1.0000, benign FPR 0.0000, n=24
- query_heavy: recall 0.9919, benign FPR 0.7778, n=1499
- redirect_like: recall 0.9870, benign FPR 0.0000, n=154
- brand_impersonation_like: recall 0.8913, benign FPR 0.2609, n=1150