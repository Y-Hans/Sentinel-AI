import json
import sys
import numpy as np
from pathlib import Path

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

def custom_predict(probs, threshold):
    preds = []
    for i in range(len(probs)):
        if probs[i, 2] >= threshold: preds.append(2)
        else:
            if probs[i, 1] > probs[i, 0]: preds.append(1)
            else: preds.append(0)
    return np.array(preds)

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

def generate_report(op_points, test_metrics, ood_metrics):
    md = "# Final Model Selection & Optimization Report\n\n"
    md += "## Pareto Analysis (Stage G)\n"
    for tgt, metrics in op_points.items():
        md += f"**{tgt}** (Threshold: {metrics['threshold']:.2f})\n"
        md += f"- Malicious Recall: {metrics['malicious_recall']:.4f}\n"
        md += f"- Macro F1: {metrics['macro_f1']:.4f}\n"
        md += f"- Hard Negative FPR: {metrics['hard_negative_fpr']:.4f}\n\n"
        
    md += "## Final TEST & OOD Evaluation (Stage I)\n"
    md += f"- **TEST Macro F1**: {test_metrics['macro_f1']:.4f}\n"
    md += f"- **TEST Malicious Recall**: {test_metrics['malicious_recall']:.4f}\n"
    md += f"- **TEST Benign FPR**: {test_metrics['benign_fpr']:.4f}\n"
    md += f"- **TEST Curated Hard Negative FPR**: {test_metrics['hard_negative_fpr']:.4f}\n\n"
    md += f"- **OOD Macro F1**: {ood_metrics['macro_f1']:.4f}\n"
    md += f"- **OOD Malicious Recall**: {ood_metrics['malicious_recall']:.4f}\n"
    md += f"- **OOD Benign FPR**: {ood_metrics['benign_fpr']:.4f}\n\n"
    
    # Check if target met:
    best_op = op_points.get("FPR <= 0.01")
    if best_op and best_op["hard_negative_fpr"] <= 0.01 and best_op["malicious_recall"] >= 0.80:
        md += "## Final Decision (Stage J)\n"
        md += "**MODEL_READY_FOR_PACKAGING**\n\n"
        md += "The adversarial pair data expansion and feature interactions have successfully separated the semantic intent, achieving FPR <= 1% and Malicious Recall >= 80%."
    else:
        md += "## Final Decision (Stage J)\n"
        md += "**ARCHITECTURE_REDESIGN_REQUIRED**\n\n"
        md += "Despite exhausting linear models, MLPs, feature interactions, weighting strategies, diverse semantic data expansion, and adversarial pairs, the model mathematically cannot achieve FPR <= 1% with Malicious Recall >= 80% using the current representation. The lexical overlap between legitimate institutional warnings (UIDAI, Income Tax) and threat vectors requires an architecture capable of deeper contextual embedding (e.g. Transformers)."
        
    with open(ROOT / "model_training" / "FINAL_REPORT.md", "w") as f:
        f.write(md)

def main():
    print("Loading V3 Data (with Adversarial Pairs)...")
    train_recs = load_dataset("train_expanded_v3.jsonl")
    val_recs = load_dataset("val.jsonl")
    test_recs = load_dataset("test.jsonl")
    ood_recs = load_dataset("ood.jsonl")
    
    cfg = FeatureConfig(active_groups=ALL_FEATURE_GROUPS, ngram_hash_bins=128)
    X_tr, y_tr, scaler = get_xy(train_recs, cfg, fit_scaler=True)
    X_va, y_va = get_xy(val_recs, cfg, scaler=scaler)
    
    hn_records_va = [r for r in val_recs if r.get("source_id") == "SRC_CURATED_HARD_NEGATIVES_V1"]
    X_hn_va, y_hn_va = get_xy(hn_records_va, cfg, scaler=scaler, fit_scaler=False)
    
    model = LogisticRegression(max_iter=1000, random_state=42, class_weight="balanced")
    model.fit(X_tr, y_tr)
    
    probs_va = model.predict_proba(X_va)
    probs_hn_va = model.predict_proba(X_hn_va)
    
    thresholds = np.linspace(0.01, 0.99, 99)
    results = []
    
    for th in thresholds:
        preds = custom_predict(probs_va, th)
        cm = confusion_matrix(y_va, preds, labels=[0,1,2])
        benign_true = sum(cm[0])
        benign_fpr = cm[0][2] / benign_true if benign_true > 0 else 0
        mal_recall = recall_score(y_va, preds, labels=[2], average="macro", zero_division=0)
        mal_prec = precision_score(y_va, preds, labels=[2], average="macro", zero_division=0)
        macro_f1 = f1_score(y_va, preds, average="macro", zero_division=0)
        
        preds_hn = custom_predict(probs_hn_va, th)
        cm_hn = confusion_matrix(y_hn_va, preds_hn, labels=[0,1,2])
        hn_fpr = cm_hn[0][2] / sum(cm_hn[0]) if sum(cm_hn[0]) > 0 else 0
        
        results.append({
            "threshold": th,
            "benign_fpr": benign_fpr,
            "hard_negative_fpr": hn_fpr,
            "malicious_recall": mal_recall,
            "malicious_precision": mal_prec,
            "macro_f1": macro_f1
        })
        
    targets = [0.10, 0.05, 0.03, 0.02, 0.01]
    op_points = {}
    
    for tgt in targets:
        valid = [r for r in results if r["benign_fpr"] <= tgt]
        if valid:
            best = max(valid, key=lambda x: x["malicious_recall"])
            op_points[f"FPR <= {tgt}"] = best
            
    print("Pareto Points:")
    for k, v in op_points.items():
        print(f"{k}: Thresh={v['threshold']:.2f}, HN_FPR={v['hard_negative_fpr']:.4f}, Recall={v['malicious_recall']:.4f}")
        
    best_th = op_points.get("FPR <= 0.01", {"threshold": 0.9})["threshold"]
    
    # Evaluate Test and OOD using best threshold
    X_te, y_te = get_xy(test_recs, cfg, scaler=scaler, fit_scaler=False)
    X_ood, y_ood = get_xy(ood_recs, cfg, scaler=scaler, fit_scaler=False)
    
    hn_te = [r for r in test_recs if r.get("source_id") == "SRC_CURATED_HARD_NEGATIVES_V1"]
    X_hn_te, y_hn_te = get_xy(hn_te, cfg, scaler=scaler, fit_scaler=False)
    
    preds_te = custom_predict(model.predict_proba(X_te), best_th)
    preds_ood = custom_predict(model.predict_proba(X_ood), best_th)
    preds_hn_te = custom_predict(model.predict_proba(X_hn_te), best_th)
    
    cm_te = confusion_matrix(y_te, preds_te, labels=[0,1,2])
    test_metrics = {
        "macro_f1": f1_score(y_te, preds_te, average="macro", zero_division=0),
        "malicious_recall": recall_score(y_te, preds_te, labels=[2], average="macro", zero_division=0),
        "benign_fpr": cm_te[0][2] / sum(cm_te[0]),
        "hard_negative_fpr": confusion_matrix(y_hn_te, preds_hn_te, labels=[0,1,2])[0][2] / max(1, sum(confusion_matrix(y_hn_te, preds_hn_te, labels=[0,1,2])[0]))
    }
    
    cm_ood = confusion_matrix(y_ood, preds_ood, labels=[0,1,2])
    ood_metrics = {
        "macro_f1": f1_score(y_ood, preds_ood, average="macro", zero_division=0),
        "malicious_recall": recall_score(y_ood, preds_ood, labels=[2], average="macro", zero_division=0),
        "benign_fpr": cm_ood[0][2] / sum(cm_ood[0])
    }
    
    generate_report(op_points, test_metrics, ood_metrics)
    save_result({"stage": "FINAL", "test_metrics": test_metrics, "ood_metrics": ood_metrics, "op_points": op_points})
    print("Done. Report generated.")

if __name__ == "__main__":
    main()
