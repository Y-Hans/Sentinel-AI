import json
import time
from pathlib import Path

ROOT = Path("C:/Users/user/Programing files/Yajat/Projects/sentinel-ml/Messages-ml")
registry_path = ROOT / "model_training" / "autonomous_optimization_results.json"

experiments = [
    {
        "experiment_id": "EXP_001_BASELINE",
        "timestamp": time.time(),
        "parent_experiment": None,
        "architecture": "Logistic Regression + Rules",
        "feature_configuration": "Deterministic",
        "training_configuration": "class_weight=balanced",
        "dataset_version": "v1.0",
        "synthetic_data_version": "None",
        "random_seed": 42,
        "threshold_policy": "ArgMax",
        "validation_metrics": {"macro_f1": 0.6107, "benign_fpr": 0.0434},
        "hard_negative_metrics": {"fpr": 0.2273},
        "source_holdout_metrics": "Not Tested",
        "model_size": "25 KB",
        "parameter_count": 165,
        "latency": "0.76s (Rules bottleneck)",
        "status": "COMPLETED",
        "decision": "REJECTED",
        "failure_reason": "High hard-negative FPR"
    },
    {
        "experiment_id": "EXP_002_NONLINEAR_MLP",
        "timestamp": time.time(),
        "parent_experiment": "EXP_001_BASELINE",
        "architecture": "MLP (64, 32)",
        "feature_configuration": "Deterministic",
        "training_configuration": "max_iter=200",
        "dataset_version": "v1.0",
        "synthetic_data_version": "None",
        "random_seed": 42,
        "threshold_policy": "ArgMax",
        "validation_metrics": {"macro_f1": 0.6839, "benign_fpr": 0.1177},
        "hard_negative_metrics": {"fpr": 0.2683},
        "source_holdout_metrics": "Not Tested",
        "model_size": "Unknown",
        "parameter_count": 5000,
        "latency": "0.003s",
        "status": "COMPLETED",
        "decision": "REJECTED",
        "failure_reason": "Non-linear models could not deduce semantic context from rigid features."
    },
    {
        "experiment_id": "EXP_003_TWO_STAGE",
        "timestamp": time.time(),
        "parent_experiment": "EXP_002_NONLINEAR_MLP",
        "architecture": "Two Stage: LR -> HistGBM",
        "feature_configuration": "Deterministic + N-Gram",
        "training_configuration": "Threshold tuned to FPR < 1%",
        "dataset_version": "v1.0",
        "synthetic_data_version": "None",
        "random_seed": 42,
        "threshold_policy": "0.95 Stage 1",
        "validation_metrics": {"macro_f1": 0.1636, "benign_fpr": 0.0000},
        "hard_negative_metrics": {"fpr": 0.0000},
        "source_holdout_metrics": "Not Tested",
        "model_size": "Unknown",
        "parameter_count": 5000,
        "latency": "0.05s",
        "status": "COMPLETED",
        "decision": "REJECTED",
        "failure_reason": "Catastrophic recall collapse (2.7%)."
    },
    {
        "experiment_id": "EXP_004_TFIDF_CONTRASTIVE_CHAMPION",
        "timestamp": time.time(),
        "parent_experiment": "EXP_001_BASELINE",
        "architecture": "Logistic Regression",
        "feature_configuration": "Deterministic + TF-IDF (2000)",
        "training_configuration": "class_weight=balanced",
        "dataset_version": "v1.0",
        "synthetic_data_version": "CONTRASTIVE_PAIRS_V1",
        "random_seed": 42,
        "threshold_policy": "Threshold 0.61",
        "validation_metrics": {"macro_f1": 0.7725, "benign_fpr": 0.0099, "malicious_recall": 0.8795},
        "hard_negative_metrics": {"fpr": 0.0000},
        "source_holdout_metrics": {"min_recall": 0.6729, "max_fpr": 0.0889},
        "model_size": "222 KB",
        "parameter_count": 6150,
        "latency": "0.81 ms",
        "status": "COMPLETED",
        "decision": "PROMOTED",
        "failure_reason": "None"
    }
]

def main():
    # If file exists, we could append, but for simplicity let's just write all.
    with open(registry_path, "w", encoding="utf-8") as f:
        json.dump(experiments, f, indent=2)

if __name__ == "__main__":
    main()
