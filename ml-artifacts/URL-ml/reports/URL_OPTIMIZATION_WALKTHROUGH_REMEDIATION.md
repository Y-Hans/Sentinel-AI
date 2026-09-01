# URL-ML Optimization Walkthrough — Autonomous Hard-Source Remediation

Frozen champion → exact baseline reproduction → hard-source forensic analysis → controlled source-invariance ablation → source-diverse v1 remediation (980 benign URLs/49 unseen domains) → v2 targeted expansion (2,450 total) → fresh challenger training → threshold-only validation selection → hard-source/test/source-holdout evaluation → adversarial and resource audit → independent retraining → final decision.

The remediation improved hard-source benign FPR from 99.39% to 16.69% at the finalist operating point, with hard-source recall 96.91%. The improvement is genuine and not a threshold-only artifact: hard-source ROC AUC rose from 0.513 to 0.978. However, TEST malicious recall is 92.44% at TEST FPR 1.08%, and no validation-selected operating point reconciles the hard-source and ordinary TEST gates.

The frozen champion remains at `model/champion/model.joblib`; no model was promoted, no TEST/OOD data entered training or tuning, and Messages-ML/Android were untouched.
