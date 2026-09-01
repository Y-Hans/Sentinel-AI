# URL-ML V7 Dataset Remediation Report

---

## 1. Dataset Remediation Objective

To remedy the historical distribution collapse (100% root-homepage benign URLs in `cleaned_dataset.csv`) without violating strict evaluation protection rules:
- **Zero domain overlap** with the Validation split ($44,106$ rows).
- **Zero domain overlap** with the TEST split ($50,511$ rows).
- **Zero domain overlap** with `hard_dataset.csv` ($2,800$ rows).
- **Zero derivation / paraphrasing** from protected evaluation datasets.

---

## 2. Dataset Synthesis Methodology (`v7_structural_benign.csv`)

The dataset was generated using `scripts/generate_v7_dataset.py` by sampling from the pool of $75,830$ valid, non-protected registrable domains available exclusively within the training split of `cleaned_dataset.csv`.

### 2.1 Category Breakdown & Representation

| Web Structural Archetype | Row Count | Description / URL Patterns |
| :--- | :--- | :--- |
| **`v7_structural_search_tracking_queries`** | 5,059 | Query parameters, sorting, filtering, pagination (`/search?q=...`, `/browse?cat=...`) |
| **`v7_structural_general_pages`** | 5,057 | Common site navigation (`/about`, `/contact`, `/careers`, `/legal/terms`) |
| **`v7_structural_documentation_api`** | 5,029 | REST API endpoints, versioning, pull requests, issues (`/api/v1/...`, `/pull/2031`, `/issues/1042`) |
| **`v7_structural_account_auth`** | 5,010 | Legitimate account portals, SSO callbacks, 2FA (`/account/profile`, `/auth/sso/callback`) |
| **`v7_structural_internal_redirects`** | 4,999 | Safe internal navigation redirects (`/redirect?target=%2Fdashboard`, `/goto?url=...`) |
| **`v7_structural_cdn_assets`** | 4,984 | Static bundle assets, scripts, stylesheets, fonts (`/assets/js/bundle.min.js`, `.woff2`) |
| **`v7_structural_payment_commerce`** | 4,979 | E-commerce checkout, cart, invoices, order tracking (`/checkout/review`, `/billing/invoices`) |
| **`v7_structural_media_editorial`** | 4,954 | Dated blog posts, podcast episodes, tech news (`/news/2026/08/...`, `/posts/2026/...`) |
| **`v7_structural_localized_navigation`** | 4,929 | Multi-language localized routes (`/en-us/...`, `/de-de/...`, `/fr-fr/...`, `/ja-jp/...`) |
| **Historical Provider Remediation V3** | 3,290 | Legitimate provider sub-paths from remediation v3 |
| **TOTAL REMEDIATED BENIGN SET** | **48,290** | **Unique Registrable Domains: 15,045** |

---

## 3. Cryptographic Provenance & Leakage Verification

- **File Path**: `URL-ml/data/v7_structural_benign.csv`
- **File SHA256**: `4a34e661060c35ae26d3417b28ed2ada074d8b017bee528b5f7e2d5bcf722e6b`
- **Total Rows**: $48,290$
- **Total Unique Registrable Domains**: $15,045$
- **HTTP Scheme Ratio**: $20.00\%$ ($9,658$ HTTP / $38,632$ HTTPS)
- **Protected Domain Overlap Verification**:
  - `Validation Domain Overlap`: **0 domains** ($0.0\%$)
  - `TEST Domain Overlap`: **0 domains** ($0.0\%$)
  - `Hard Dataset Domain Overlap`: **0 domains** ($0.0\%$)

---

## 4. Training Set Composition

With the addition of `v7_structural_benign.csv`, the final training set reached:
- **Benign Training Samples**: $80,359$ (cleaned baseline) $+ 48,290$ (remediated structural) $= \mathbf{128,649}$
- **Malicious Training Samples**: $\mathbf{60,819}$
- **Total Training Set**: $\mathbf{189,468}$ URLs across $\mathbf{90,875}$ unique domains.
- **Benign Structural Diversity**: Over $37.5\%$ of all benign training URLs now contain multi-segment paths, queries, numbers, and HTTP protocols.
