import json
import sys
from pathlib import Path
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from feature_config import FeatureConfig
from feature_extraction import extract_feature_vector, get_feature_names

from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler

def load_dataset(filename):
    filepath = ROOT / "data" / "processed" / filename
    records = []
    with open(filepath, "r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                records.append(json.loads(line.strip()))
    return records

def analyze():
    train_recs = load_dataset("train.jsonl")
    val_recs = load_dataset("val.jsonl")
    
    feature_cfg = FeatureConfig()
    feat_names = get_feature_names(feature_cfg)
    
    label_map = {"BENIGN": 0, "SUSPICIOUS_SPAM": 1, "MALICIOUS": 2}
    
    X_tr = []
    y_tr = []
    for r in train_recs:
        X_tr.append(extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), feature_cfg))
        y_tr.append(label_map[r.get("security_label", "BENIGN")])
        
    scaler = StandardScaler()
    X_tr_scaled = scaler.fit_transform(X_tr)
    
    model = LogisticRegression(max_iter=1000, random_state=42, class_weight="balanced")
    model.fit(X_tr_scaled, y_tr)
    
    coefs = model.coef_[2] # coefficients for MALICIOUS class
    
    hard_negs = [r for r in val_recs if r.get("source_id") == "SRC_CURATED_HARD_NEGATIVES_V1" and r.get("security_label") == "BENIGN"]
    
    print(f"Total hard negative benign records in VAL: {len(hard_negs)}")
    
    fp_count = 0
    for r in hard_negs:
        x = extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), feature_cfg)
        x_scaled = scaler.transform([x])
        pred = model.predict(x_scaled)[0]
        
        if pred == 2:
            fp_count += 1
            print("-" * 50)
            print(f"FALSE POSITIVE: {r.get('raw_text')}")
            print(f"Sender: {r.get('sender_header')}")
            
            # analyze feature contributions
            contributions = x_scaled[0] * coefs
            top_indices = np.argsort(contributions)[::-1][:5]
            print("Top features pushing towards MALICIOUS:")
            for idx in top_indices:
                if contributions[idx] > 0:
                    print(f"  {feat_names[idx]}: value={x[idx]:.2f}, scaled={x_scaled[0][idx]:.2f}, weight={coefs[idx]:.2f}, contrib={contributions[idx]:.2f}")
                    
    print(f"\nTotal FPs: {fp_count}/{len(hard_negs)} ({(fp_count/len(hard_negs))*100:.2f}%)")

if __name__ == "__main__":
    analyze()
