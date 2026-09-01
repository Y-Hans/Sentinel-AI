import json
import time
from pathlib import Path
import sys
import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import accuracy_score, f1_score, precision_score, recall_score, confusion_matrix

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
sys.path.insert(0, str(ROOT / "evaluation"))

from feature_config import FeatureConfig
from feature_extraction import extract_feature_vector
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

def compute_metrics(y_true, y_pred, labels=[0, 1, 2]):
    if len(y_true) == 0:
        return {}
    acc = accuracy_score(y_true, y_pred)
    f1_macro = f1_score(y_true, y_pred, average="macro", zero_division=0)
    prec = precision_score(y_true, y_pred, average="macro", zero_division=0)
    rec = recall_score(y_true, y_pred, average="macro", zero_division=0)
    cm = confusion_matrix(y_true, y_pred, labels=labels).tolist()
    
    benign_true = sum(cm[0]) if len(cm) > 0 else 0
    benign_called_mal = cm[0][2] if len(cm) > 0 else 0
    
    mal_prec = precision_score(y_true, y_pred, labels=[2], average="macro", zero_division=0)
    mal_rec = recall_score(y_true, y_pred, labels=[2], average="macro", zero_division=0)
    
    return {
        "accuracy": acc,
        "macro_f1": f1_macro,
        "malicious_precision": float(mal_prec),
        "malicious_recall": float(mal_rec),
        "benign_fpr": float(benign_called_mal / benign_true) if benign_true > 0 else 0.0,
        "fp_over_n": f"{benign_called_mal}/{benign_true}",
        "sample_count": len(y_true)
    }

def evaluate_otp(model, records, feature_cfg, scaler):
    otp_auth, otp_deliv, otp_warn, otp_reverse, otp_ambig = [], [], [], [], []
    
    for r in records:
        txt = r.get("raw_text", "").lower()
        if "otp" not in txt and "code" not in txt:
            continue
            
        signals = evaluate_rules(r.get("raw_text", ""), r.get("sender_header"))
        signal_types = [s.signal_type for s in signals]
        
        if "LEGIT_DELIVERY_OTP" in signal_types:
            otp_deliv.append(r)
        elif "LEGIT_OTP_WARNING" in signal_types:
            otp_warn.append(r)
        elif "OTP_DISCLOSURE_REQUEST" in signal_types:
            otp_reverse.append(r)
        elif r.get("primary_type") == "OTP_AUTH":
            otp_auth.append(r)
        else:
            otp_ambig.append(r)
            
    def eval_subset(subset):
        if not subset: return {"sample_count": 0}
        X, y = get_xy(subset, feature_cfg, scaler, False)
        preds = model.predict(X)
        return compute_metrics(y, preds)
        
    return {
        "legitimate_authentication_otp": eval_subset(otp_auth),
        "legitimate_delivery_otp": eval_subset(otp_deliv),
        "protective_otp_warning": eval_subset(otp_warn),
        "reverse_otp_theft": eval_subset(otp_reverse),
        "ambiguous_otp": eval_subset(otp_ambig)
    }

def evaluate_hard_negatives(model, records, feature_cfg, scaler):
    keywords = ["blocked", "suspended", "unauthorized", "kyc", "pan", "penalty", "otp", "warning", "security alert", "urgent"]
    hard_negs = []
    for r in records:
        if r.get("security_label") != "BENIGN":
            continue
        txt = r.get("raw_text", "").lower()
        if any(k in txt for k in keywords):
            hard_negs.append(r)
            
    if not hard_negs:
        return {"sample_count": 0}
        
    X, y = get_xy(hard_negs, feature_cfg, scaler, False)
    preds = model.predict(X)
    
    fp = sum(1 for true, pred in zip(y, preds) if pred == 2)
    n = len(y)
    
    return {
        "false_positives": fp,
        "total_samples": n,
        "fpr": fp / n if n > 0 else 0,
        "fp_over_n": f"{fp}/{n}"
    }

def evaluate_threat_vectors(model, records, feature_cfg, scaler):
    threat_vecs = {}
    for r in records:
        tv = r.get("threat_vectors", ["NONE"])
        if isinstance(tv, str):
            tv = [tv]
        for t in tv:
            threat_vecs.setdefault(t, []).append(r)
            
    res = {}
    for t, subset in threat_vecs.items():
        if len(subset) < 10:
            res[t] = "INSUFFICIENT SAMPLE SIZE"
        else:
            X, y = get_xy(subset, feature_cfg, scaler, False)
            preds = model.predict(X)
            res[t] = compute_metrics(y, preds)
            
    return res

def evaluate_generalization(model, records, feature_cfg, scaler, key="source_id"):
    subsets = {}
    for r in records:
        val = r.get(key, "UNKNOWN")
        if isinstance(val, list): val = val[0]
        subsets.setdefault(val, []).append(r)
        
    res = {}
    for val, subset in subsets.items():
        if len(subset) < 10:
            res[val] = "INSUFFICIENT SAMPLE SIZE"
        else:
            X, y = get_xy(subset, feature_cfg, scaler, False)
            preds = model.predict(X)
            res[val] = compute_metrics(y, preds)
    return res

def main():
    train_recs = load_dataset("train.jsonl")
    val_recs = load_dataset("val.jsonl")
    test_recs = load_dataset("test.jsonl")
    ood_recs = load_dataset("ood.jsonl")
    
    feature_cfg = FeatureConfig()
    X_tr, y_tr, scaler = get_xy(train_recs, feature_cfg, fit_scaler=True)
    
    # Train candidate model for evaluation (Logistic Regression Balanced)
    print("Training candidate model for detailed evaluation...")
    model = LogisticRegression(max_iter=1000, random_state=42, class_weight="balanced")
    model.fit(X_tr, y_tr)
    
    results = {}
    
    print("Evaluating OTP...")
    results["otp_evaluation"] = evaluate_otp(model, val_recs, feature_cfg, scaler)
    
    print("Evaluating Hard Negatives...")
    results["hard_negative_evaluation"] = evaluate_hard_negatives(model, val_recs, feature_cfg, scaler)
    
    print("Evaluating Threat Vectors...")
    results["threat_vector_evaluation"] = evaluate_threat_vectors(model, val_recs, feature_cfg, scaler)
    
    print("Evaluating Generalization...")
    results["source_generalization"] = evaluate_generalization(model, val_recs, feature_cfg, scaler, key="source_id")
    # Some older schemas might have language or sender_type
    results["language_generalization"] = evaluate_generalization(model, val_recs, feature_cfg, scaler, key="language")
    
    # Sender type from sender_header (simulated by parsing)
    sender_subsets = {"ALPHANUMERIC_HEADER": [], "PHONE_NUMBER": [], "SHORTCODE": [], "UNKNOWN": []}
    from sender_parser import parse_sender_header
    for r in val_recs:
        res = parse_sender_header(r.get("sender_header"))
        sender_subsets[res.sender_type].append(r)
        
    sender_res = {}
    for st, subset in sender_subsets.items():
        if len(subset) < 10:
            sender_res[st] = "INSUFFICIENT SAMPLE SIZE"
        else:
            X, y = get_xy(subset, feature_cfg, scaler, False)
            preds = model.predict(X)
            sender_res[st] = compute_metrics(y, preds)
    results["sender_generalization"] = sender_res
    
    print("Evaluating TEST and OOD...")
    X_te, y_te = get_xy(test_recs, feature_cfg, scaler, False)
    results["test_evaluation"] = compute_metrics(y_te, model.predict(X_te)) if len(y_te) > 0 else {}
    
    X_ood, y_ood = get_xy(ood_recs, feature_cfg, scaler, False)
    results["ood_evaluation"] = compute_metrics(y_ood, model.predict(X_ood)) if len(y_ood) > 0 else {}
    
    results_dir = Path(__file__).resolve().parent / "results"
    results_dir.mkdir(exist_ok=True)
    with open(results_dir / "evaluation_results.json", "w", encoding="utf-8") as f:
        json.dump(results, f, indent=2)
        
    print("Evaluation completed. Results saved.")

if __name__ == "__main__":
    main()
