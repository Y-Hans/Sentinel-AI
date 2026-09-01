import json
import time
from pathlib import Path
import sys
import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import f1_score, precision_score, recall_score, confusion_matrix

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
sys.path.insert(0, str(ROOT / "evaluation"))

from feature_config import FeatureConfig
from feature_extraction import extract_feature_vector, get_feature_names
from rules_engine import evaluate_rules


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

def evaluate_features(train_recs, val_recs, feature_cfg):
    X_tr, y_tr, scaler = get_xy(train_recs, feature_cfg, fit_scaler=True)
    X_va, y_va = get_xy(val_recs, feature_cfg, scaler=scaler)
    
    model = LogisticRegression(max_iter=1000, random_state=42, class_weight="balanced")
    
    # Measure fit time
    model.fit(X_tr, y_tr)
    
    # Measure inference time
    start_time = time.time()
    preds = model.predict(X_va)
    inf_time = time.time() - start_time
    
    f1_macro = f1_score(y_va, preds, average="macro", zero_division=0)
    prec_mal = precision_score(y_va, preds, labels=[2], average="macro", zero_division=0)
    rec_mal = recall_score(y_va, preds, labels=[2], average="macro", zero_division=0)
    cm = confusion_matrix(y_va, preds, labels=[0, 1, 2]).tolist()
    
    benign_true = sum(cm[0]) if len(cm) > 0 else 0
    benign_called_mal = cm[0][2] if len(cm) > 0 else 0
    fpr = float(benign_called_mal / benign_true) if benign_true > 0 else 0.0
    
    param_count = X_tr.shape[1] * len(model.classes_) + len(model.classes_)
    
    return {
        "feature_count": X_tr.shape[1],
        "macro_f1": float(f1_macro),
        "malicious_precision": float(prec_mal),
        "malicious_recall": float(rec_mal),
        "benign_fpr": fpr,
        "fp_over_n": f"{benign_called_mal}/{benign_true}",
        "parameter_count": param_count,
        "inference_time_seconds": inf_time
    }

def rule_ablation(val_recs, model, feature_cfg, scaler):
    X_va, y_va = get_xy(val_recs, feature_cfg, scaler=scaler)
    y_pred_ml = model.predict(X_va)
    
    # ML Only
    ml_metrics = {
        "macro_f1": float(f1_score(y_va, y_pred_ml, average="macro", zero_division=0)),
        "malicious_recall": float(recall_score(y_va, y_pred_ml, labels=[2], average="macro", zero_division=0))
    }
    cm = confusion_matrix(y_va, y_pred_ml, labels=[0, 1, 2]).tolist()
    ml_metrics["benign_fpr"] = float(cm[0][2] / sum(cm[0])) if sum(cm[0]) > 0 else 0.0
    
    # ML + Rules (Overall)
    y_pred_hybrid = []
    for i, r in enumerate(val_recs):
        signals = evaluate_rules(r.get("raw_text", ""), r.get("sender_header"))
        has_critical = any(s.severity in ("HIGH", "CRITICAL") for s in signals)
        has_legit_warn = any(s.signal_type in ("LEGIT_OTP_WARNING", "LEGIT_DELIVERY_OTP") for s in signals)
        if has_legit_warn:
            y_pred_hybrid.append(0)
        elif has_critical:
            y_pred_hybrid.append(2)
        else:
            y_pred_hybrid.append(y_pred_ml[i])
            
    hybrid_metrics = {
        "macro_f1": float(f1_score(y_va, y_pred_hybrid, average="macro", zero_division=0)),
        "malicious_recall": float(recall_score(y_va, y_pred_hybrid, labels=[2], average="macro", zero_division=0))
    }
    cm_h = confusion_matrix(y_va, y_pred_hybrid, labels=[0, 1, 2]).tolist()
    hybrid_metrics["benign_fpr"] = float(cm_h[0][2] / sum(cm_h[0])) if sum(cm_h[0]) > 0 else 0.0
    
    return {
        "ml_only": ml_metrics,
        "ml_plus_rules": hybrid_metrics,
        "delta_fpr": hybrid_metrics["benign_fpr"] - ml_metrics["benign_fpr"],
        "delta_recall": hybrid_metrics["malicious_recall"] - ml_metrics["malicious_recall"]
    }

def main():
    train_recs = load_dataset("train.jsonl")
    val_recs = load_dataset("val.jsonl")
    
    print("Running feature ablations...")
    ablation_stages = [
        ("STRUCTURAL_ONLY", ["STRUCTURAL"]),
        ("STRUCTURAL_URGENCY", ["STRUCTURAL", "URGENCY"]),
        ("STRUCTURAL_URGENCY_FEAR", ["STRUCTURAL", "URGENCY", "FEAR_THREAT"]),
        ("STRUCTURAL_URGENCY_FEAR_AUTH", ["STRUCTURAL", "URGENCY", "FEAR_THREAT", "AUTH", "OTP_INTENT"]),
        ("STRUCTURAL_URGENCY_FEAR_AUTH_FIN", ["STRUCTURAL", "URGENCY", "FEAR_THREAT", "AUTH", "OTP_INTENT", "FINANCIAL"]),
        ("STRUCTURAL_URGENCY_FEAR_AUTH_FIN_CTA", ["STRUCTURAL", "URGENCY", "FEAR_THREAT", "AUTH", "OTP_INTENT", "FINANCIAL", "CTA"]),
        ("FULL_DETERMINISTIC", ["STRUCTURAL", "URGENCY", "FEAR_THREAT", "AUTH", "OTP_INTENT", "FINANCIAL", "CTA", "SENDER"]),
        ("FULL_WITH_NGRAM", ["STRUCTURAL", "URGENCY", "FEAR_THREAT", "AUTH", "OTP_INTENT", "FINANCIAL", "CTA", "SENDER", "NGRAM_HASH"]),
    ]
    
    results = {}
    for stage_name, groups in ablation_stages:
        print(f"Evaluating {stage_name}...")
        cfg = FeatureConfig(active_groups=groups)
        results[stage_name] = evaluate_features(train_recs, val_recs, cfg)
        
    print("Running rule ablations...")
    full_cfg = FeatureConfig(active_groups=["STRUCTURAL", "URGENCY", "FEAR_THREAT", "AUTH", "OTP_INTENT", "FINANCIAL", "CTA", "SENDER"])
    X_tr, y_tr, scaler = get_xy(train_recs, full_cfg, fit_scaler=True)
    model = LogisticRegression(max_iter=1000, random_state=42, class_weight="balanced")
    model.fit(X_tr, y_tr)
    
    results["rule_ablation"] = rule_ablation(val_recs, model, full_cfg, scaler)
    
    results_dir = Path(__file__).resolve().parent / "results"
    results_dir.mkdir(exist_ok=True)
    with open(results_dir / "ablation_results.json", "w", encoding="utf-8") as f:
        json.dump(results, f, indent=2)
        
    print("Ablation experiments completed. Results saved.")

if __name__ == "__main__":
    main()
