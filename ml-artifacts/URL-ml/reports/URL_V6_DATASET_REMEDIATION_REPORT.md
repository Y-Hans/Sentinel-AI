# URL-ML V6 Dataset Remediation Report

Clean fit data: 141,178 rows from `cleaned_dataset.csv`; remediation v3: 3,430 benign rows across 48 unique registrable domains; V6 synthetic expansion: 400 benign rows across 20 explicitly synthetic `.example` domains. Synthetic rows are marked `v6_synthetic_structural_benign` and have no claimed real-world provenance.

Hashes: clean `6c71c39d...caf2f`; remediation v3 `0f8da3fb...527e`; synthetic `ea6849ac...663b`; protected hard `fc0966ed...177f`. The protected set was evaluation-only. Existing remediation has two suffix-level overlaps with protected URLs (`co.uk`, `gov.uk`); no protected URL was copied into the new V6 dataset.

The synthetic expansion did not improve protected generalization. DATA_REQUIRED therefore means new, genuinely sourced and adjudicated benign URLs, domain-disjoint from all protected evaluations, specifically covering providers/CDNs, HTTP, deep/query-heavy workflows, redirects/tracking, localized/multilingual, encoded/long URLs, and security/account/payment terminology.
