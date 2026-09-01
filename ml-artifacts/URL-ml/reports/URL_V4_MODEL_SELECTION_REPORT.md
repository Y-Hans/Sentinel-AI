# URL-ML V4 Model Selection Report

V4 evaluated ExtraTrees on deterministic representations and logistic regression on hashed character representations. The predeclared remediation-weight sweep used ExtraTrees with 40 trees and the 92-feature deterministic representation.

Increasing the safe-remediation weight produced the following hard-source FPR/recall pairs: 5× = 65.70%/98.93%, 10× = 47.27%/97.38%, 20× = 36.04%/97.27%, 40× = 19.50%/97.15%, 80× = 18.73%/96.67%, 160× = 12.40%/96.79%. Clean TEST FPR remained 0.815–0.918%, with recall 98.83–99.49%.

No candidate was promoted. The 160× result is the best hard-source point observed in the safe V4 sweep, but it fails the preferred ≤1% hard-source FPR gate and does not clearly dominate the frozen champion under the complete framework. No TEST/OOD/hard-source result was used to select a threshold.
