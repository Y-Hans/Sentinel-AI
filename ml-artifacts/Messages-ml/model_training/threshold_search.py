import json
import time
from pathlib import Path
import sys
import numpy as np

from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import confusion_matrix, precision_score, recall_score, f1_score

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from feature_config import FeatureConfig, ALL_FEATURE_GROUPS
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

def main():
    train_recs = load_dataset("train_expanded.jsonl")
    val_recs = load_dataset("val.jsonl")
    
    cfg = FeatureConfig(active_groups=ALL_FEATURE_GROUPS, ngram_hash_bins=128)
    X_tr, y_tr, scaler = get_xy(train_recs, cfg, fit_scaler=True)
    X_va, y_va = get_xy(val_recs, cfg, scaler=scaler)
    
    # Train the best candidate so far
    model = LogisticRegression(max_iter=1000, random_state=42, class_weight="balanced")
    model.fit(X_tr, y_tr)
    
    # Predict probabilities on Validation set
    probs = model.predict_proba(X_va)
    
    # We care about the MALICIOUS class which is index 2
    mal_probs = probs[:, 2]
    
    thresholds = np.linspace(0.01, 0.99, 99)
    results = []
    
    for th in thresholds:
        # custom prediction based on threshold for malicious class
        # if malicious prob > th, predict 2
        # else fallback to argmax between 0 and 1
        
        preds = []
        for i in range(len(mal_probs)):
            if mal_probs[i] >= th:
                preds.append(2)
            else:
                if probs[i, 1] > probs[i, 0]:
                    preds.append(1)
                else:
                    preds.append(0)
                    
        y_pred = np.array(preds)
        cm = confusion_matrix(y_va, y_pred, labels=[0,1,2])
        benign_true = sum(cm[0])
        benign_called_mal = cm[0][2]
        
        benign_fpr = benign_called_mal / benign_true if benign_true > 0 else 0
        mal_recall = recall_score(y_va, y_pred, labels=[2], average="macro", zero_division=0)
        mal_prec = precision_score(y_va, y_pred, labels=[2], average="macro", zero_division=0)
        macro_f1 = f1_score(y_va, y_pred, average="macro", zero_division=0)
        
        # also compute FPR for benign -> any non-benign (1 or 2)
        benign_called_non_benign = cm[0][1] + cm[0][2]
        benign_to_any_fpr = benign_called_non_benign / benign_true if benign_true > 0 else 0
        
        results.append({
            "threshold": th,
            "benign_fpr": benign_fpr,
            "benign_to_any_fpr": benign_to_any_fpr,
            "malicious_recall": mal_recall,
            "malicious_precision": mal_prec,
            "macro_f1": macro_f1
        })
        
    # Find operating points
    targets = [0.05, 0.03, 0.02, 0.01]
    op_points = {}
    
    for tgt in targets:
        # find the threshold that gives FPR <= tgt with max recall
        valid = [r for r in results if r["benign_fpr"] <= tgt]
        if valid:
            best = max(valid, key=lambda x: x["malicious_recall"])
            op_points[f"FPR <= {tgt}"] = best
            
    print("Operating Points:")
    for k, v in op_points.items():
        print(f"{k}: Thresh={v['threshold']:.2f}, FPR={v['benign_fpr']:.4f}, Recall={v['malicious_recall']:.4f}, Prec={v['malicious_precision']:.4f}, F1={v['macro_f1']:.4f}")
        
    with open(ROOT / "model_training" / "threshold_search_results.json", "w") as f:
        json.dump(op_points, f, indent=2)

if __name__ == "__main__":
    main()
