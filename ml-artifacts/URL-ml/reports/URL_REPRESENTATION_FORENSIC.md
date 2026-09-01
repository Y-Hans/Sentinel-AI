# URL-ML Representation Forensic Record

The current 15-feature representation omits path-token composition, delimiter/query structure, protocol interaction, and domain-independent lexical structure. Focused residual analysis found 327 finalist false positives across 67 registrable domains; 220 were ordinary/deep provider paths. Residuals were disproportionately non-HTTPS and non-recognized-provider URLs.

Removing identity-like features had previously left hard-source FPR at 99.39%, so identity ablation was not a sufficient fix. The targeted HTTP/path expansion reduced hard-source FPR further, proving missing coverage, but the associated TEST recall loss demonstrates that the representation/model still cannot separate the two populations reliably.

Quantitative details are in `URL_FINAL_REMEDIATION_FORENSIC.json` and `URL_FINAL_REMEDIATION_RESIDUAL_FALSE_POSITIVES.csv`.
