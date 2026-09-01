import json
import time
import hashlib
from pathlib import Path
import sys
import numpy as np

from sklearn.linear_model import LogisticRegression
from sklearn.neural_network import MLPClassifier
from sklearn.metrics import accuracy_score, f1_score, precision_score, recall_score, confusion_matrix
from sklearn.preprocessing import StandardScaler, LabelEncoder
from sklearn.dummy import DummyClassifier

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
sys.path.insert(0, str(ROOT / "evaluation"))

from feature_config import FeatureConfig
from feature_extraction import extract_feature_vector, get_feature_names
from rules_engine import evaluate_rules
from metrics import calculate_binary_metrics


def load_dataset(filename):
    filepath = ROOT / "data" / "processed" / filename
    records = []
    with open(filepath, "r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                records.append(json.loads(line.strip()))
    return records


def prepare_data(train_recs, val_recs, test_recs, feature_cfg, target_key="security_label", label_map=None):
    if label_map is None:
        if target_key == "security_label":
            label_map = {"BENIGN": 0, "SUSPICIOUS_SPAM": 1, "MALICIOUS": 2}
        else:
            # We'll compute it dynamically if not provided
            unique_labels = set(r.get(target_key, "UNKNOWN") for r in train_recs + val_recs + test_recs)
            label_map = {lbl: i for i, lbl in enumerate(sorted(unique_labels))}
            
    def get_xy(records):
        X, y = [], []
        for r in records:
            X.append(extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), feature_cfg))
            y.append(label_map[r.get(target_key, "UNKNOWN") if target_key == "primary_type" else r.get(target_key, "BENIGN")])
        return np.array(X, dtype=np.float32), np.array(y, dtype=np.int32)
        
    X_train, y_train = get_xy(train_recs)
    X_val, y_val = get_xy(val_recs)
    X_test, y_test = get_xy(test_recs)
    
    scaler = StandardScaler()
    X_train_scaled = scaler.fit_transform(X_train) if len(X_train) > 0 else X_train
    X_val_scaled = scaler.transform(X_val) if len(X_val) > 0 else X_val
    X_test_scaled = scaler.transform(X_test) if len(X_test) > 0 else X_test
    
    return X_train_scaled, y_train, X_val_scaled, y_val, X_test_scaled, y_test, label_map


def evaluate_classifier(model, X, y, label_map):
    if len(X) == 0:
        return {}
        
    start_time = time.time()
    preds = model.predict(X)
    inf_time = time.time() - start_time
    
    # Metrics
    acc = accuracy_score(y, preds)
    f1_macro = f1_score(y, preds, average="macro", zero_division=0)
    f1_weighted = f1_score(y, preds, average="weighted", zero_division=0)
    prec_macro = precision_score(y, preds, average="macro", zero_division=0)
    rec_macro = recall_score(y, preds, average="macro", zero_division=0)
    cm = confusion_matrix(y, preds).tolist()
    
    metrics = {
        "accuracy": float(acc),
        "macro_f1": float(f1_macro),
        "weighted_f1": float(f1_weighted),
        "macro_precision": float(prec_macro),
        "macro_recall": float(rec_macro),
        "confusion_matrix": cm,
        "inference_time_seconds": inf_time,
        "sample_count": len(X)
    }
    
    # Specific class metrics if security label
    if "MALICIOUS" in label_map and "BENIGN" in label_map:
        mal_idx = label_map["MALICIOUS"]
        ben_idx = label_map["BENIGN"]
        
        # Calculate per class
        prec_per = precision_score(y, preds, average=None, zero_division=0)
        rec_per = recall_score(y, preds, average=None, zero_division=0)
        
        metrics["malicious_precision"] = float(prec_per[mal_idx]) if mal_idx < len(prec_per) else 0.0
        metrics["malicious_recall"] = float(rec_per[mal_idx]) if mal_idx < len(rec_per) else 0.0
        metrics["benign_recall"] = float(rec_per[ben_idx]) if ben_idx < len(rec_per) else 0.0
        
        # calculate FPR for Benign (FP / True Benign)
        # FP for Malicious class (we care about calling benign things malicious)
        if len(cm) > max(mal_idx, ben_idx):
            benign_true = sum(cm[ben_idx])
            benign_called_malicious = cm[ben_idx][mal_idx]
            metrics["benign_fpr"] = float(benign_called_malicious / benign_true) if benign_true > 0 else 0.0
            metrics["fp_over_n"] = f"{benign_called_malicious}/{benign_true}"
            
    return metrics


def train_eval(model, name, X_tr, y_tr, X_va, y_va, label_map):
    model.fit(X_tr, y_tr)
    return {
        "model_name": name,
        "val_metrics": evaluate_classifier(model, X_va, y_va, label_map)
    }


def model_0_rules_only(val_recs):
    label_map = {"BENIGN": 0, "SUSPICIOUS_SPAM": 1, "MALICIOUS": 2}
    y_true = []
    y_pred = []
    
    start_time = time.time()
    for r in val_recs:
        y_true.append(label_map.get(r.get("security_label", "BENIGN"), 0))
        signals = evaluate_rules(r.get("raw_text", ""), r.get("sender_header"))
        
        has_critical = any(s.severity in ("HIGH", "CRITICAL") for s in signals)
        has_legit_warn = any(s.signal_type in ("LEGIT_OTP_WARNING", "LEGIT_DELIVERY_OTP") for s in signals)
        
        if has_critical and not has_legit_warn:
            y_pred.append(label_map["MALICIOUS"])
        else:
            y_pred.append(label_map["BENIGN"])
            
    inf_time = time.time() - start_time
    
    y = np.array(y_true)
    preds = np.array(y_pred)
    
    acc = accuracy_score(y, preds)
    f1_macro = f1_score(y, preds, average="macro", zero_division=0)
    prec_macro = precision_score(y, preds, average="macro", zero_division=0)
    rec_macro = recall_score(y, preds, average="macro", zero_division=0)
    cm = confusion_matrix(y, preds, labels=[0, 1, 2]).tolist()
    
    benign_true = sum(cm[0])
    benign_called_mal = cm[0][2]
    
    return {
        "model_name": "MODEL_0_RULES_ONLY",
        "val_metrics": {
            "accuracy": float(acc),
            "macro_f1": float(f1_macro),
            "macro_precision": float(prec_macro),
            "macro_recall": float(rec_macro),
            "confusion_matrix": cm,
            "inference_time_seconds": inf_time,
            "benign_fpr": float(benign_called_mal / benign_true) if benign_true > 0 else 0.0,
            "fp_over_n": f"{benign_called_mal}/{benign_true}",
            "malicious_precision": float(precision_score(y, preds, labels=[2], average="macro", zero_division=0)),
            "malicious_recall": float(recall_score(y, preds, labels=[2], average="macro", zero_division=0)),
        }
    }


def model_7_hybrid(val_recs, X_va, y_va, ml_model, label_map):
    # Rule predictions overriding ML predictions
    y_pred_ml = ml_model.predict(X_va)
    y_pred_hybrid = []
    
    start_time = time.time()
    for i, r in enumerate(val_recs):
        signals = evaluate_rules(r.get("raw_text", ""), r.get("sender_header"))
        has_critical = any(s.severity in ("HIGH", "CRITICAL") for s in signals)
        has_legit_warn = any(s.signal_type in ("LEGIT_OTP_WARNING", "LEGIT_DELIVERY_OTP") for s in signals)
        
        if has_legit_warn:
            y_pred_hybrid.append(label_map["BENIGN"])
        elif has_critical:
            y_pred_hybrid.append(label_map["MALICIOUS"])
        else:
            y_pred_hybrid.append(y_pred_ml[i])
            
    inf_time = time.time() - start_time
    
    y = np.array(y_va)
    preds = np.array(y_pred_hybrid)
    
    acc = accuracy_score(y, preds)
    f1_macro = f1_score(y, preds, average="macro", zero_division=0)
    prec_macro = precision_score(y, preds, average="macro", zero_division=0)
    rec_macro = recall_score(y, preds, average="macro", zero_division=0)
    cm = confusion_matrix(y, preds, labels=[0,1,2]).tolist()
    
    benign_true = sum(cm[0]) if len(cm) > 0 else 0
    benign_called_mal = cm[0][2] if len(cm) > 0 else 0
    
    return {
        "model_name": "MODEL_7_HYBRID",
        "val_metrics": {
            "accuracy": float(acc),
            "macro_f1": float(f1_macro),
            "macro_precision": float(prec_macro),
            "macro_recall": float(rec_macro),
            "confusion_matrix": cm,
            "inference_time_seconds": inf_time,
            "benign_fpr": float(benign_called_mal / benign_true) if benign_true > 0 else 0.0,
            "fp_over_n": f"{benign_called_mal}/{benign_true}",
            "malicious_precision": float(precision_score(y, preds, labels=[2], average="macro", zero_division=0)),
            "malicious_recall": float(recall_score(y, preds, labels=[2], average="macro", zero_division=0)),
        }
    }
    

def main():
    print("Loading data...")
    train_recs = load_dataset("train.jsonl")
    val_recs = load_dataset("val.jsonl")
    test_recs = load_dataset("test.jsonl")
    
    print("Preparing features...")
    feature_cfg = FeatureConfig() # Default includes all except N-Gram
    
    X_tr, y_tr, X_va, y_va, _, _, label_map = prepare_data(train_recs, val_recs, test_recs, feature_cfg, "security_label")
    X_tr_intent, y_tr_intent, X_va_intent, y_va_intent, _, _, intent_map = prepare_data(train_recs, val_recs, test_recs, feature_cfg, "primary_type")

    results = []

    # Model 0: Rules Only
    print("Evaluating Model 0...")
    results.append(model_0_rules_only(val_recs))
    
    # Model 1: Majority Class
    print("Training Model 1...")
    dummy = DummyClassifier(strategy="most_frequent")
    results.append(train_eval(dummy, "MODEL_1_MAJORITY", X_tr, y_tr, X_va, y_va, label_map))
    
    # Model 2: Logistic Regression
    print("Training Model 2...")
    lr_none = LogisticRegression(max_iter=1000, random_state=42, class_weight=None)
    results.append(train_eval(lr_none, "MODEL_2_LR_NONE", X_tr, y_tr, X_va, y_va, label_map))
    
    lr_bal = LogisticRegression(max_iter=1000, random_state=42, class_weight="balanced")
    results.append(train_eval(lr_bal, "MODEL_2_LR_BALANCED", X_tr, y_tr, X_va, y_va, label_map))
    
    # Model 3: Intent Classifier
    print("Training Model 3 (Intent)...")
    lr_intent = LogisticRegression(max_iter=1000, random_state=42, class_weight="balanced")
    results.append(train_eval(lr_intent, "MODEL_3_INTENT_LR", X_tr_intent, y_tr_intent, X_va_intent, y_va_intent, intent_map))
    
    # Model 4: Dual-head linear (Essentially LR for Security + LR for Intent)
    print("Training Model 4 (Dual-head linear)...")
    res_dh_sec = train_eval(LogisticRegression(max_iter=1000, random_state=42, class_weight="balanced"), "MODEL_4_SECURITY_HEAD", X_tr, y_tr, X_va, y_va, label_map)
    res_dh_int = train_eval(LogisticRegression(max_iter=1000, random_state=42, class_weight="balanced"), "MODEL_4_INTENT_HEAD", X_tr_intent, y_tr_intent, X_va_intent, y_va_intent, intent_map)
    results.append({"model_name": "MODEL_4_DUAL_LINEAR", "val_metrics": {"security": res_dh_sec["val_metrics"], "intent": res_dh_int["val_metrics"]}})
    
    # Model 5: Small MLP security
    print("Training Model 5...")
    mlp_32_16 = MLPClassifier(hidden_layer_sizes=(32, 16), max_iter=200, random_state=42, early_stopping=True)
    results.append(train_eval(mlp_32_16, "MODEL_5_MLP_32_16", X_tr, y_tr, X_va, y_va, label_map))
    
    mlp_64_32 = MLPClassifier(hidden_layer_sizes=(64, 32), max_iter=200, random_state=42, early_stopping=True)
    results.append(train_eval(mlp_64_32, "MODEL_5_MLP_64_32", X_tr, y_tr, X_va, y_va, label_map))
    
    mlp_96_48 = MLPClassifier(hidden_layer_sizes=(96, 48), max_iter=200, random_state=42, early_stopping=True)
    results.append(train_eval(mlp_96_48, "MODEL_5_MLP_96_48", X_tr, y_tr, X_va, y_va, label_map))
    
    # Model 6: Small dual-head MLP
    print("Training Model 6...")
    res_dh_mlp_sec = train_eval(MLPClassifier(hidden_layer_sizes=(32, 16), max_iter=200, random_state=42, early_stopping=True), "MODEL_6_MLP_SECURITY_HEAD", X_tr, y_tr, X_va, y_va, label_map)
    res_dh_mlp_int = train_eval(MLPClassifier(hidden_layer_sizes=(32, 16), max_iter=200, random_state=42, early_stopping=True), "MODEL_6_MLP_INTENT_HEAD", X_tr_intent, y_tr_intent, X_va_intent, y_va_intent, intent_map)
    results.append({"model_name": "MODEL_6_DUAL_MLP", "val_metrics": {"security": res_dh_mlp_sec["val_metrics"], "intent": res_dh_mlp_int["val_metrics"]}})
    
    # Model 7: Rule + ML hybrid
    print("Training Model 7 (Hybrid)...")
    results.append(model_7_hybrid(val_recs, X_va, y_va, lr_bal, label_map))  # Using LR balanced as the ML base
    
    # Save results
    results_dir = Path(__file__).resolve().parent / "results"
    results_dir.mkdir(exist_ok=True)
    with open(results_dir / "baseline_results.json", "w", encoding="utf-8") as f:
        json.dump(results, f, indent=2)
        
    print("Training completed. Results saved to results/baseline_results.json")

if __name__ == "__main__":
    main()
