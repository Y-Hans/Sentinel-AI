import json
from pathlib import Path
import sys
import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.calibration import CalibratedClassifierCV, calibration_curve
from sklearn.metrics import brier_score_loss
from sklearn.preprocessing import StandardScaler

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
sys.path.insert(0, str(ROOT / "evaluation"))

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


def main():
    train_recs = load_dataset("train.jsonl")
    val_recs = load_dataset("val.jsonl")
    
    feature_cfg = FeatureConfig()
    X_tr, y_tr, scaler = get_xy(train_recs, feature_cfg, fit_scaler=True)
    X_va, y_va = get_xy(val_recs, feature_cfg, scaler=scaler)
    
    # We calibrate for the binary malicious vs non-malicious task
    # because calibration for 3 classes via CalibratedClassifierCV with isotonic is applied per class (OneVsRest).
    
    print("Training candidate model...")
    model = LogisticRegression(max_iter=1000, random_state=42, class_weight="balanced")
    model.fit(X_tr, y_tr)
    
    print("Fitting calibrator...")
    from sklearn.isotonic import IsotonicRegression
    iso = IsotonicRegression(out_of_bounds='clip')
    
    probs_va = model.predict_proba(X_va)[:, 2]
    y_va_binary = (y_va == 2).astype(int)
    iso.fit(probs_va, y_va_binary)
    probs_malicious = iso.predict(probs_va)
    
    brier = brier_score_loss(y_va_binary, probs_malicious)
    prob_true, prob_pred = calibration_curve(y_va_binary, probs_malicious, n_bins=10)
    
    results = {
        "brier_score": float(brier),
        "calibration_curve": {
            "prob_true": prob_true.tolist(),
            "prob_pred": prob_pred.tolist()
        }
    }
    
    results_dir = Path(__file__).resolve().parent / "results"
    results_dir.mkdir(exist_ok=True)
    with open(results_dir / "calibration_results.json", "w", encoding="utf-8") as f:
        json.dump(results, f, indent=2)
        
    print(f"Calibration completed. Brier Score: {brier:.4f}")

if __name__ == "__main__":
    main()
