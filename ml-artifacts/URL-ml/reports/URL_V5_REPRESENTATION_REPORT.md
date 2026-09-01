# URL-ML V5 Representation and Model Report

V5 retained the established 92 deterministic, Kotlin-portable scalar representation: historical 15 features plus host morphology, path morphology, query structure, and protocol/component interactions. V4 already evaluated feature-family ablations and compact hashed character representations; feature-count inflation did not solve hard-source generalization.

The novel V5 model-class test used HistGradientBoosting with the 92 features at remediation weights 40x and 160x. The 160x point reached hard-source FPR 26.90%, compared with V4's best safe ExtraTrees point at 12.40%; it was rejected despite a compact 701,854-byte bundle. HGB therefore does not improve the release frontier.

Combined evidence across V3/V4/V5 covers ExtraTrees, Random Forest, GradientBoosting, logistic/SGD, hashed character models, deterministic ablations, and compact hybrid/weighted candidates. The remaining failure is not credibly solved by another blind lightweight model sweep; new adjudicated source coverage is the justified next experiment.
