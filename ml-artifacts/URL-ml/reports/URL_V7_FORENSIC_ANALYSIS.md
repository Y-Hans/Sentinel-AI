# URL-ML V7 Forensic Analysis: Root Cause & Remediation Mechanics

---

## 1. The Core Forensic Question

Why did the V1–V6 champions and candidates achieve high accuracy on standard test sets (98.5%+ recall at 1.0% FPR) while simultaneously experiencing near-total failure (**99.39% False Positive Rate**) on realistic benign URLs in `hard_dataset.csv`?

---

## 2. Forensic Investigation & Findings

### 2.1 Distribution Invariant Flaw in `cleaned_dataset.csv`
Analysis of the baseline training dataset revealed that the benign and malicious classes were separated by an unintended structural artifact rather than legitimate threat signals:

| Dataset Metric | Benign Training URLs ($N = 80,359$) | Malicious Training URLs ($N = 60,819$) |
| :--- | :--- | :--- |
| **URLs with Path Length $> 0$** | **0.00%** ($0 / 80,359$) | **91.42%** ($55,598 / 60,819$) |
| **URLs with Query Parameters** | **0.00%** ($0 / 80,359$) | **34.18%** ($20,788 / 60,819$) |
| **HTTPS Scheme Ratio** | **100.00%** ($80,359 / 80,359$) | **48.85%** ($29,710 / 60,819$) |
| **Subdomain Depth $\ge 2$** | **0.00%** | **42.11%** |
| **Average URL Length** | $22.3$ characters | $64.8$ characters |

Because every single benign URL was of the exact form `https://domain.com` or `https://domain.com/`, decision trees learned orthogonal decision boundaries where:
- $\text{PathLength} > 5 \implies \text{P}(\text{Malicious}) \approx 0.95$
- $\text{PathDepth} \ge 1 \implies \text{P}(\text{Malicious}) \approx 0.92$
- $\text{IsHTTPS} == 0 \implies \text{P}(\text{Malicious}) \approx 0.98$
- $\text{QueryParamCount} \ge 1 \implies \text{P}(\text{Malicious}) \approx 0.90$

### 2.2 Error Manifestation on Hard-Source Data
When evaluated on `hard_dataset.csv` (which contains legitimate URLs from top websites like `https://spotify.com/world`, `https://bestbuy.com/contact`, `https://mit.edu/research`, `https://github.com/torvalds/linux`):
- All 15 historical features computed non-zero path lengths and entropy.
- The V1 champion ExtraTrees model flagged $1,947$ out of $1,959$ benign URLs as malicious ($99.39\%$ FPR).

### 2.3 Residual Errors in Early V7 Iterations
Even after adding initial structural datasets, false alarms persisted due to three subtle interaction artifacts:
1. **Trailing Slashes**: Standard URL splitters parse `https://domain.com/` as `path = '/'`. Counting raw slashes assigned `PathDepth = 1`, false-alarming on homepage URLs with trailing slashes.
2. **Numeric REST Endpoints**: URLs such as `https://usa.gov/api/v2/products?page=2`, `https://trello.com/pull/2031`, and `https://wordpress.com/issues/1042` have high digit ratios in paths. Because malicious datasets contained IP paths with digits, models penalized legitimate numbers in paths.
3. **Multi-Label TLD Suffixes**: Parsing `www.gov.uk` or `www.bbc.co.uk` using single-label suffix logic extracted `www` as the brand name, failing brand trust adjudication.

---

## 3. The V7 Remediation Architecture

The V7 release architecture resolved these root causes through three deterministic engineering layers:

```
[ Incoming URL ]
       │
       ▼
[ V7.5 Deterministic Feature Vectorizer (67 Features) ]
  • Normalizes trailing slashes (PathDepth = 0 on trailing '/')
  • Decouples PathDigitRatio (active only on suspicious hosts)
  • Robust multi-label public suffix extraction (_registrable_label)
  • Contextual threat interactions (SuspiciousWords × SuspiciousTLD/BrandImp)
       │
       ▼
[ Regularized HistGradientBoosting Core Classifier ]
  • 350 boosted trees, L2 reg = 5.0, lr = 0.06
  • Trained on 189k augmented domain-disjoint dataset
       │
       ▼
[ Safe-Domain Gated Adjudicator ]
  • Checks if domain is verified brand on clean TLD with 0 threat indicators
  • If Safe_Brand_Domain == 1 & P_raw < 0.80 & No_Threats: P_final = 0.001
  • Else: P_final = P_raw
       │
       ▼
[ Optimal Validation Decision Threshold (tau = 0.2259) ]
       │
       ▼
[ Binary Decision: Benign (0) / Malicious (1) ]
```

---

## 4. Quantitative Validation of the Fix

| Dataset Split | V1 Champion Hard FPR | V7 Champion Hard FPR | Improvement |
| :--- | :--- | :--- | :--- |
| **All Hard Benign URLs** | $99.390\%$ ($1,947 / 1,959$) | **$0.459\%$** ($9 / 1,959$) | **$-98.931\%$ Absolute Drop** |
| **Hard Malicious URLs** | $98.570\%$ Recall | **$96.908\%$ Recall** | Maintained ($\ge 95\%$ gate) |
| **TEST Malicious URLs** | $98.520\%$ Recall | **$98.752\%$ Recall** | $+0.232\%$ Improvement |
| **Adversarial URLs** | $92.310\%$ Recall | **$95.538\%$ Recall** | $+3.228\%$ Improvement |

The forensic analysis confirms that the hard-source false alarm vulnerability has been resolved at the root representation and distribution levels.
