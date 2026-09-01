import json
import time
from pathlib import Path
import sys
import numpy as np
import pickle

ROOT = Path("C:/Users/user/Programing files/Yajat/Projects/sentinel-ml/Messages-ml")
sys.path.insert(0, str(ROOT / "scripts"))

from feature_config import FeatureConfig
from feature_extraction import extract_feature_vector
from sklearn.metrics import accuracy_score, f1_score, confusion_matrix, precision_score

def load_dataset(filename):
    filepath = ROOT / "data" / "processed" / filename
    records = []
    with open(filepath, "r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                records.append(json.loads(line.strip()))
    return records

def predict_with_threshold(probs, t):
    preds = np.zeros(len(probs), dtype=int)
    for i in range(len(probs)):
        prob_non_benign = probs[i][1] + probs[i][2]
        if prob_non_benign >= t:
            preds[i] = 1 if probs[i][1] > probs[i][2] else 2
        else:
            preds[i] = 0
    return preds

def evaluate_subset(name, records, clf, tfidf, scaler, cfg, t):
    label_map = {"BENIGN": 0, "SUSPICIOUS_SPAM": 1, "MALICIOUS": 2}
    y_true = np.array([label_map[r.get("security_label", "BENIGN")] for r in records])
    
    texts = [r.get("raw_text", "") for r in records]
    X_tfidf = tfidf.transform(texts).toarray()
    X_det = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in records])
    X = scaler.transform(np.hstack((X_det, X_tfidf)))
    
    start = time.time()
    probs = clf.predict_proba(X)
    preds = predict_with_threshold(probs, t)
    inf_time = (time.time() - start) * 1000.0 / max(1, len(records)) # ms per message
    
    cm = confusion_matrix(y_true, preds, labels=[0, 1, 2])
    
    benign_total = max(1, sum(cm[0]))
    suspicious_total = max(1, sum(cm[1]))
    malicious_total = max(1, sum(cm[2]))
    
    ben_to_susp = cm[0][1] / benign_total
    ben_to_mal = cm[0][2] / benign_total
    ben_any_fpr = ben_to_susp + ben_to_mal
    
    mal_rec = cm[2][2] / malicious_total
    
    true_mal = cm[2][2]
    pred_mal = cm[0][2] + cm[1][2] + cm[2][2]
    mal_prec = true_mal / max(1, pred_mal)
    
    macro_f1 = f1_score(y_true, preds, average="macro")
    
    return {
        "dataset": name,
        "samples": len(records),
        "benign_fpr": ben_any_fpr,
        "benign_to_suspicious_fpr": ben_to_susp,
        "benign_to_malicious_fpr": ben_to_mal,
        "malicious_recall": mal_rec,
        "malicious_precision": mal_prec,
        "macro_f1": macro_f1,
        "inference_latency_ms": inf_time,
        "confusion_matrix": cm.tolist()
    }

def main():
    print("Loading independent final evaluator...")
    cfg = FeatureConfig()
    
    with open("champion_model.pkl", "rb") as f:
        clf = pickle.load(f)
    with open("champion_tfidf.pkl", "rb") as f:
        tfidf = pickle.load(f)
    with open("champion_scaler.pkl", "rb") as f:
        scaler = pickle.load(f)
        
    t = 0.85
    print(f"Loaded frozen artifacts. Threshold locked at {t}")
    
    val_recs = load_dataset("val.jsonl")
    test_recs = load_dataset("test.jsonl")
    ood_recs = load_dataset("ood.jsonl")
    
    hn_recs = [r for r in val_recs if r.get('source_id') == 'SRC_CURATED_HARD_NEGATIVES_V1' and r.get('security_label') == 'BENIGN']
    
    results = {}
    results["TEST"] = evaluate_subset("TEST", test_recs, clf, tfidf, scaler, cfg, t)
    results["OOD"] = evaluate_subset("OOD", ood_recs, clf, tfidf, scaler, cfg, t)
    results["HARD_NEGATIVES"] = evaluate_subset("HARD_NEGATIVES", hn_recs, clf, tfidf, scaler, cfg, t)
    
    # Source Holdout checks on Test + Val
    all_recs = val_recs + test_recs
    sources = set([r.get("source_id") for r in all_recs])
    results["SOURCES"] = {}
    for src in sources:
        src_recs = [r for r in all_recs if r.get("source_id") == src]
        if len(src_recs) < 50: continue
        results["SOURCES"][src] = evaluate_subset(src, src_recs, clf, tfidf, scaler, cfg, t)
        
    # Language checks (Multilingual expansion)
    langs = set([r.get("language", "en") for r in all_recs])
    results["LANGUAGES"] = {}
    for lang in langs:
        lang_recs = [r for r in all_recs if r.get("language") == lang]
        if len(lang_recs) < 50: continue
        results["LANGUAGES"][lang] = evaluate_subset(lang, lang_recs, clf, tfidf, scaler, cfg, t)

    with open("FINAL_EVALUATION.json", "w") as f:
        json.dump(results, f, indent=2)
        
    print(json.dumps(results, indent=2))
    print("Final evaluation completed.")

if __name__ == "__main__":
    main()
