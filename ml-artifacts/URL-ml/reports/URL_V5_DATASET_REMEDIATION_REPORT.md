# URL-ML V5 Dataset and Remediation Report

V5 reused remediation v3: 3,430 benign rows, SHA-256 `0f8da3fba16a33e6bbf9217cb6d24308cf28c2ab8b0988aeacbb39d21fad527e`. Historical provenance identifies these as source-diverse remediation rows across 49 domains, including HTTP and ordinary/deep path coverage. They were fit-only additions.

The fixed clean source is `cleaned_dataset.csv` (SHA-256 `6c71c39dd0291b8ef58b31610ac892cd6fe2186876908d42a7b24dcbf06caf2f`). Protected `hard_dataset.csv` (SHA-256 `fc0966ed0deaea81496d5a3f384b8cdbdcd2019a2c58cbea153f6f0728ef177f`) was evaluation-only. No protected row was copied, paraphrased, template-transformed, or augmented.

V5 did not generate additional synthetic remediation because the existing remediation-weight frontier and the HGB comparison show that template/weight changes alone do not close the gap. New data required: genuinely source-diverse and adjudicated legitimate provider, CDN, localized/multilingual, account-workflow, redirect/tracking, long, encoded, and query-heavy URLs, with registrable-domain separation from protected evaluation.
