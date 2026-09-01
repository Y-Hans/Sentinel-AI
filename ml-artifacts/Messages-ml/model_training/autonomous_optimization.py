import json
import time
from pathlib import Path
import sys
import numpy as np

from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import f1_score, confusion_matrix, precision_score, recall_score, accuracy_score

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from feature_config import FeatureConfig
from feature_extraction import extract_feature_vector

def load_dataset(filename):
    filepath = ROOT / "data" / "processed" / filename
    records = []
    if not filepath.exists():
        return records
    with open(filepath, "r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                records.append(json.loads(line.strip()))
    return records

def get_xy(records, feature_cfg, scaler=None, fit_scaler=False):
    X, y = [], []
    label_map = {"BENIGN": 0, "SUSPICIOUS_SPAM": 1, "MALICIOUS": 2}
    for r in records:
        X.append(extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), feature_cfg))
        y.append(label_map[r.get("security_label", "BENIGN")])
    
    X = np.array(X, dtype=np.float32)
    y = np.array(y, dtype=np.int32)
    
    if fit_scaler:
        scaler = StandardScaler()
        if len(X) > 0:
            X = scaler.fit_transform(X)
        return X, y, scaler
    else:
        if scaler and len(X) > 0:
            X = scaler.transform(X)
        return X, y

def evaluate_hard_negatives(model, records, feature_cfg, scaler):
    hard_negs = [r for r in records if r.get("security_label") == "BENIGN" and r.get("source_id") == "SRC_CURATED_HARD_NEGATIVES_V1"]
    if not hard_negs:
        return {"fpr": 0.0, "fp_count": 0, "total": 0}
    X, y = get_xy(hard_negs, feature_cfg, scaler, False)
    preds = model.predict(X)
    fp = sum(1 for true, pred in zip(y, preds) if pred == 2)
    return {"fpr": fp / len(y), "fp_count": fp, "total": len(y)}

def evaluate_model(model, X_va, y_va):
    preds = model.predict(X_va)
    acc = accuracy_score(y_va, preds)
    f1_macro = f1_score(y_va, preds, average="macro", zero_division=0)
    cm = confusion_matrix(y_va, preds, labels=[0,1,2]).tolist()
    benign_true = sum(cm[0])
    benign_fpr = cm[0][2] / benign_true if benign_true > 0 else 0
    mal_recall = recall_score(y_va, preds, labels=[2], average="macro", zero_division=0)
    return {
        "accuracy": acc,
        "macro_f1": f1_macro,
        "benign_fpr": benign_fpr,
        "malicious_recall": mal_recall,
        "confusion_matrix": cm
    }

def run_experiment(exp_id, feature_cfg_kwargs, model_kwargs):
    print(f"--- Running Experiment {exp_id} ---")
    train_recs = load_dataset("train_expanded.jsonl")
    val_recs = load_dataset("val.jsonl")
    
    cfg = FeatureConfig(**feature_cfg_kwargs)
    X_tr, y_tr, scaler = get_xy(train_recs, cfg, fit_scaler=True)
    X_va, y_va = get_xy(val_recs, cfg, scaler=scaler)
    
    model = LogisticRegression(**model_kwargs)
    model.fit(X_tr, y_tr)
    
    val_metrics = evaluate_model(model, X_va, y_va)
    hn_metrics = evaluate_hard_negatives(model, val_recs, cfg, scaler)
    
    result = {
        "experiment_id": exp_id,
        "timestamp": time.time(),
        "feature_config": feature_cfg_kwargs,
        "model_kwargs": model_kwargs,
        "val_metrics": val_metrics,
        "hard_negative_metrics": hn_metrics
    }
    
    print(f"Val Macro F1: {val_metrics['macro_f1']:.4f}")
    print(f"Val Benign FPR: {val_metrics['benign_fpr']:.4f}")
    print(f"Hard Negative FPR: {hn_metrics['fpr']:.4f} ({hn_metrics['fp_count']}/{hn_metrics['total']})")
    print(f"Malicious Recall: {val_metrics['malicious_recall']:.4f}")
    
    # Save to registry
    registry_path = ROOT / "model_training" / "autonomous_optimization_results.json"
    registry = []
    if registry_path.exists():
        with open(registry_path, "r") as f:
            registry = json.load(f)
    registry.append(result)
    with open(registry_path, "w") as f:
        json.dump(registry, f, indent=2)
        
    return result

if __name__ == "__main__":
    import sys
    exp_id = sys.argv[1] if len(sys.argv) > 1 else "EXP_001"
    run_experiment(exp_id, {}, {"max_iter": 1000, "random_state": 42, "class_weight": "balanced"})
