# URL-ML Optimization Walkthrough V3

V1/V2/V3 remediation established that source-diverse targeted benign data can reduce the hard-source collapse: 99.39% baseline FPR became 16.69% after V2 and 5.67% after targeted V3 HTTP/path coverage. The representation forensic phase then quantified 327 residual false positives across 67 domains, dominated by ordinary/deep provider paths and non-HTTPS/non-recognized providers.

The focused representation phase tested the existing-feature representation with targeted HTTP/path data, two remediation weights, compact ExtraTrees, compact linear references, and a validation-selected hybrid. The strongest finalist was `challenger_hardsource_v3_extra40`: TEST FPR 0.963%, TEST malicious recall 89.40%, hard-source FPR 5.67%, hard-source recall 97.03%, and 4.82 MB. Lower weighting recovered only 90.50% TEST recall while hard-source FPR rose to 16.13%.

Validation-only threshold Pareto analysis found no operating point that preserves the earlier TEST security profile and reaches low hard-source FPR. Source-holdout, adversarial, resource, and independent-retraining audits were completed. Final decision: **BOTH_REQUIRED**—additional adjudicated source coverage and a stronger domain-independent representation/model are both required. No model was promoted.
