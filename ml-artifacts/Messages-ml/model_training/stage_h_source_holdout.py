import json
import sys
import numpy as np
from pathlib import Path
from collections import defaultdict

from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import recall_score, f1_score

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

def get_xy(records, feature_cfg):
    X, y, sources = [], [], []
    label_map = {"BENIGN": 0, "SUSPICIOUS_SPAM": 1, "MALICIOUS": 2}
    for r in records:
        X.append(extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), feature_cfg))
        y.append(label_map[r.get("security_label", "BENIGN")])
        sources.append(r.get("source_id", "UNKNOWN"))
    return np.array(X, dtype=np.float32), np.array(y, dtype=np.int32), np.array(sources)

def main():
    print("Loading V3 Data for Leave-One-Source-Out (LOSO) Analysis...")
    train_recs = load_dataset("train_expanded_v3.jsonl")
    
    cfg = FeatureConfig(active_groups=ALL_FEATURE_GROUPS, ngram_hash_bins=128)
    X_tr, y_tr, src_tr = get_xy(train_recs, cfg)
    
    unique_sources = np.unique(src_tr)
    print(f"Total Unique Sources: {len(unique_sources)}")
    
    results = {}
    for holdout_src in unique_sources:
        # Only evaluate large enough sources
        src_mask = src_tr == holdout_src
        if np.sum(src_mask) < 100: continue
        
        train_mask = ~src_mask
        X_train, y_train = X_tr[train_mask], y_tr[train_mask]
        X_test, y_test = X_tr[src_mask], y_tr[src_mask]
        
        scaler = StandardScaler()
        X_train_scaled = scaler.fit_transform(X_train)
        X_test_scaled = scaler.transform(X_test)
        
        model = LogisticRegression(max_iter=1000, random_state=42, class_weight="balanced")
        model.fit(X_train_scaled, y_train)
        
        preds = model.predict(X_test_scaled)
        
        f1 = f1_score(y_test, preds, average="macro", zero_division=0)
        rec = recall_score(y_test, preds, labels=[2], average="macro", zero_division=0)
        
        results[holdout_src] = {"macro_f1": f1, "malicious_recall": rec, "size": int(np.sum(src_mask))}
        print(f"Holdout: {holdout_src} (N={np.sum(src_mask)}) -> F1: {f1:.4f}, Recall: {rec:.4f}")

    with open(ROOT / "model_training" / "stage_h_loso_results.json", "w") as f:
        json.dump(results, f, indent=2)

if __name__ == "__main__":
    main()
