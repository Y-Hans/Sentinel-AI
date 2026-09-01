import json
import sys
import numpy as np
from pathlib import Path

from sklearn.neural_network import MLPClassifier
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import confusion_matrix, precision_score, recall_score, f1_score

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from feature_config import FeatureConfig, ALL_FEATURE_GROUPS
from feature_extraction import extract_feature_vector

def load_dataset(filename):
    filepath = ROOT / "data" / "processed" / filename
    records = []
    if not filepath.exists(): return records
    with open(filepath, "r", encoding="utf-8") as f:
        for line in f:
            if line.strip(): records.append(json.loads(line.strip()))
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
        if len(X) > 0: X = scaler.fit_transform(X)
        return X, y, scaler
    else:
        if scaler and len(X) > 0: X = scaler.transform(X)
        return X, y

def custom_predict(model, X, threshold):
    probs = model.predict_proba(X)
    preds = []
    for i in range(len(probs)):
        if probs[i, 2] >= threshold: preds.append(2)
        else:
            if probs[i, 1] > probs[i, 0]: preds.append(1)
            else: preds.append(0)
    return np.array(preds)

def evaluate(model, records, feature_cfg, scaler, threshold):
    if not records: return {}
    X, y = get_xy(records, feature_cfg, scaler, False)
    preds = custom_predict(model, X, threshold)
    cm = confusion_matrix(y, preds, labels=[0,1,2]).tolist()
    benign_true = sum(cm[0])
    benign_called_mal = cm[0][2]
    mal_recall = recall_score(y, preds, labels=[2], average="macro", zero_division=0)
    return {
        "macro_f1": f1_score(y, preds, average="macro", zero_division=0),
        "malicious_recall": mal_recall,
        "benign_fpr": benign_called_mal / benign_true if benign_true > 0 else 0,
        "confusion_matrix": cm,
        "sample_count": len(y)
    }

def save_result(res):
    registry_path = ROOT / "model_training" / "autonomous_optimization_results.json"
    registry = []
    if registry_path.exists():
        with open(registry_path, "r") as f:
            try:
                registry = json.load(f)
                if not isinstance(registry, list): registry = [registry]
            except json.JSONDecodeError: registry = []
    registry.append(res)
    with open(registry_path, "w") as f: json.dump(registry, f, indent=2)

def main():
    print("Loading data for Stage C (Nonlinear Models)...")
    train_recs = load_dataset("train_expanded.jsonl")
    val_recs = load_dataset("val.jsonl")
    
    cfg = FeatureConfig(active_groups=ALL_FEATURE_GROUPS, ngram_hash_bins=128)
    X_tr, y_tr, scaler = get_xy(train_recs, cfg, fit_scaler=True)
    hn_records = [r for r in val_recs if r.get("source_id") == "SRC_CURATED_HARD_NEGATIVES_V1"]
    
    configs = [(16,), (32,), (64,), (32, 16)]
    threshold = 0.5 # Default for MLP, can be optimized later
    
    for hls in configs:
        print(f"Training MLPClassifier hidden_layer_sizes={hls}...")
        model = MLPClassifier(hidden_layer_sizes=hls, max_iter=300, random_state=42, early_stopping=True)
        model.fit(X_tr, y_tr)
        
        val_res = evaluate(model, val_recs, cfg, scaler, threshold)
        hn_res = evaluate(model, hn_records, cfg, scaler, threshold)
        
        res = {
            "dataset_version": "v2_expanded",
            "features": ALL_FEATURE_GROUPS + ["INTERACTION_FEATURES"],
            "model": f"MLP {hls}",
            "threshold": threshold,
            "validation_metrics": val_res,
            "hard_negative_metrics": hn_res,
            "stage": "STAGE_C",
            "parameter_count": sum(coef.size for coef in model.coefs_) + sum(inter.size for inter in model.intercepts_)
        }
        save_result(res)
        print(f"MLP {hls} -> Val F1: {val_res['macro_f1']:.4f}, Val Recall: {val_res['malicious_recall']:.4f}, HN FPR: {hn_res['benign_fpr']:.4f}")

if __name__ == "__main__":
    main()
