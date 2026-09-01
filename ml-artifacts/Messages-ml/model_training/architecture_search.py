import json
import time
from pathlib import Path
import sys
import numpy as np

ROOT = Path("C:/Users/user/Programing files/Yajat/Projects/sentinel-ml/Messages-ml")
sys.path.insert(0, str(ROOT / "scripts"))
sys.path.insert(0, str(ROOT / "evaluation"))

from feature_config import FeatureConfig
from feature_extraction import extract_feature_vector
from sklearn.linear_model import LogisticRegression
from sklearn.neural_network import MLPClassifier
from sklearn.ensemble import HistGradientBoostingClassifier
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import accuracy_score, f1_score, precision_score, recall_score, confusion_matrix
from train_models import evaluate_classifier

def load_dataset(filename):
    filepath = ROOT / "data" / "processed" / filename
    records = []
    with open(filepath, "r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                records.append(json.loads(line.strip()))
    return records

def prepare_data(train_recs, val_recs, feature_cfg, target_key="security_label"):
    label_map = {"BENIGN": 0, "SUSPICIOUS_SPAM": 1, "MALICIOUS": 2}
    
    def get_xy(records):
        X, y = [], []
        for r in records:
            X.append(extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), feature_cfg))
            y.append(label_map[r.get(target_key, "BENIGN")])
        return np.array(X, dtype=np.float32), np.array(y, dtype=np.int32)
        
    X_train, y_train = get_xy(train_recs)
    X_val, y_val = get_xy(val_recs)
    
    scaler = StandardScaler()
    X_train_scaled = scaler.fit_transform(X_train) if len(X_train) > 0 else X_train
    X_val_scaled = scaler.transform(X_val) if len(X_val) > 0 else X_val
    
    return X_train_scaled, y_train, X_val_scaled, y_val, scaler, label_map

def run_experiment(model, name, X_tr, y_tr, X_va, y_va, label_map, val_recs, scaler, feature_cfg):
    start = time.time()
    model.fit(X_tr, y_tr)
    train_time = time.time() - start
    
    val_metrics = evaluate_classifier(model, X_va, y_va, label_map)
    
    # Hard negatives eval
    hn_recs = [r for r in val_recs if r.get('source_id') == 'SRC_CURATED_HARD_NEGATIVES_V1' and r.get('security_label') == 'BENIGN']
    X_hn = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), feature_cfg) for r in hn_recs])
    X_hn = scaler.transform(X_hn)
    
    hn_preds = model.predict(X_hn)
    fp = sum(1 for p in hn_preds if p == label_map["MALICIOUS"])
    total_hn = len(hn_preds)
    hn_fpr = fp / total_hn if total_hn > 0 else 0
    
    return {
        "model_name": name,
        "train_time": train_time,
        "macro_f1": val_metrics.get("macro_f1", 0),
        "malicious_recall": val_metrics.get("malicious_recall", 0),
        "benign_fpr": val_metrics.get("benign_fpr", 0),
        "hard_negative_fpr": hn_fpr,
        "inference_time": val_metrics.get("inference_time_seconds", 0)
    }

def main():
    print("Loading datasets...")
    train_recs = load_dataset("train_contrastive.jsonl")  # Using contrastive!
    val_recs = load_dataset("val.jsonl")
    
    # Baseline configuration
    cfg = FeatureConfig()
    X_tr, y_tr, X_va, y_va, scaler, label_map = prepare_data(train_recs, val_recs, cfg)
    
    # N-Gram configuration
    cfg_ngram = FeatureConfig(active_groups={"STRUCTURAL", "URGENCY", "FEAR_THREAT", "AUTH", "OTP_INTENT", "FINANCIAL", "CTA", "SENDER", "LEGIT_INTENT", "NGRAM_HASH"}, ngram_hash_bins=64)
    X_tr_ng, y_tr_ng, X_va_ng, y_va_ng, scaler_ng, _ = prepare_data(train_recs, val_recs, cfg_ngram)

    results = []

    models = [
        (LogisticRegression(max_iter=1000, random_state=42, class_weight='balanced'), "1_Improved_LR", False),
        (HistGradientBoostingClassifier(random_state=42, max_depth=5, max_iter=100), "2_HistGBM_Light", False),
        (MLPClassifier(hidden_layer_sizes=(32, 16), max_iter=200, random_state=42, early_stopping=True), "3_MLP_32_16", False),
        (MLPClassifier(hidden_layer_sizes=(64, 32), max_iter=200, random_state=42, early_stopping=True), "3_MLP_64_32", False),
        (LogisticRegression(max_iter=1000, random_state=42, class_weight='balanced'), "4_LR_NGram", True),
        (HistGradientBoostingClassifier(random_state=42, max_depth=5, max_iter=100), "4_GBM_NGram", True),
        (MLPClassifier(hidden_layer_sizes=(64, 32), max_iter=200, random_state=42, early_stopping=True), "5_MLP_NGram", True),
    ]

    for model, name, use_ngram in models:
        print(f"Training {name}...")
        if use_ngram:
            res = run_experiment(model, name, X_tr_ng, y_tr_ng, X_va_ng, y_va_ng, label_map, val_recs, scaler_ng, cfg_ngram)
        else:
            res = run_experiment(model, name, X_tr, y_tr, X_va, y_va, label_map, val_recs, scaler, cfg)
        results.append(res)
        print(res)
        
    # Write report
    with open(ROOT / "model_training" / "architecture_search_results.json", "w") as f:
        json.dump(results, f, indent=2)

if __name__ == "__main__":
    main()
