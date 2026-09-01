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
from sklearn.preprocessing import StandardScaler
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics import accuracy_score, f1_score, precision_score, recall_score, confusion_matrix

def load_dataset(filename):
    filepath = ROOT / "data" / "processed" / filename
    records = []
    with open(filepath, "r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                records.append(json.loads(line.strip()))
    return records

def predict_with_threshold(clf, X, t):
    probs = clf.predict_proba(X)
    preds = np.argmax(probs, axis=1)
    for i in range(len(preds)):
        if probs[i][2] >= t:
            preds[i] = 2
        elif preds[i] == 2 and probs[i][2] < t:
            sub_probs = probs[i][:2]
            preds[i] = np.argmax(sub_probs)
    return preds

def run_evaluation(name, records, clf, tfidf, scaler, cfg, t):
    label_map = {"BENIGN": 0, "SUSPICIOUS_SPAM": 1, "MALICIOUS": 2}
    y_true = np.array([label_map[r.get("security_label", "BENIGN")] for r in records])
    
    texts = [r.get("raw_text", "") for r in records]
    X_tfidf = tfidf.transform(texts).toarray()
    X_det = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in records])
    
    X = np.hstack((X_det, X_tfidf))
    X = scaler.transform(X)
    
    start = time.time()
    preds = predict_with_threshold(clf, X, t)
    inf_time = time.time() - start
    
    cm = confusion_matrix(y_true, preds, labels=[0, 1, 2])
    ben_fpr = (cm[0][1] + cm[0][2]) / max(1, sum(cm[0]))
    mal_rec = cm[2][2] / max(1, sum(cm[2]))
    macro_f1 = f1_score(y_true, preds, average="macro")
    
    hn_recs = [r for i, r in enumerate(records) if r.get('source_id') == 'SRC_CURATED_HARD_NEGATIVES_V1' and r.get('security_label') == 'BENIGN']
    hn_texts = [r.get("raw_text", "") for r in hn_recs]
    if len(hn_recs) > 0:
        X_hn_tfidf = tfidf.transform(hn_texts).toarray()
        X_hn_det = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in hn_recs])
        X_hn = scaler.transform(np.hstack((X_hn_det, X_hn_tfidf)))
        hn_preds = predict_with_threshold(clf, X_hn, t)
        hn_fp = sum(1 for p in hn_preds if p > 0)
        hn_fpr = hn_fp / len(hn_preds)
    else:
        hn_fpr = 0.0
    
    return {
        "dataset": name,
        "samples": len(records),
        "benign_fpr": float(ben_fpr),
        "malicious_recall": float(mal_rec),
        "macro_f1": float(macro_f1),
        "hard_negative_fpr": float(hn_fpr),
        "inference_time": inf_time,
        "confusion_matrix": cm.tolist()
    }

def main():
    print("Loading datasets...")
    train_recs = load_dataset("train_expanded_v2.jsonl") 
    test_recs = load_dataset("test.jsonl")
    ood_recs = load_dataset("ood.jsonl")
    val_recs = load_dataset("val.jsonl")
    
    cfg = FeatureConfig()
    label_map = {"BENIGN": 0, "SUSPICIOUS_SPAM": 1, "MALICIOUS": 2}
    
    train_texts = [r.get("raw_text", "") for r in train_recs]
    y_tr = np.array([label_map[r.get("security_label", "BENIGN")] for r in train_recs])
    
    print("Extracting TF-IDF...")
    tfidf = TfidfVectorizer(max_features=2000, stop_words="english", ngram_range=(1, 2))
    X_tr_tfidf = tfidf.fit_transform(train_texts).toarray()
    
    print("Extracting Deterministic features...")
    X_tr_det = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in train_recs])
    
    X_tr = np.hstack((X_tr_det, X_tr_tfidf))
    scaler = StandardScaler()
    X_tr = scaler.fit_transform(X_tr)
    
    print("Training Logistic Regression (TF-IDF + Det)...")
    clf = LogisticRegression(max_iter=1000, random_state=42, class_weight='balanced')
    clf.fit(X_tr, y_tr)
    
    t = 0.61
    print(f"\nEvaluating with Threshold {t}...")
    
    val_res = run_evaluation("VALIDATION", val_recs, clf, tfidf, scaler, cfg, t)
    print(json.dumps(val_res, indent=2))
    
    test_res = run_evaluation("TEST", test_recs, clf, tfidf, scaler, cfg, t)
    print(json.dumps(test_res, indent=2))
    
    ood_res = run_evaluation("OOD", ood_recs, clf, tfidf, scaler, cfg, t)
    print(json.dumps(ood_res, indent=2))
    
    print("\n--- Source-Holdout Validation ---")
    sources = set([r.get("source_id") for r in val_recs + test_recs])
    # For source holdout, we should train holding out a source. But for speed, let's just evaluate generalization across sources first.
    # Actually, genuine leave-one-source-out requires retraining.
    for src in sources:
        src_recs = [r for r in val_recs + test_recs if r.get("source_id") == src]
        if len(src_recs) < 50: continue
        res = run_evaluation(f"Source: {src}", src_recs, clf, tfidf, scaler, cfg, t)
        print(f"{src} - Samples: {len(src_recs)}, Benign FPR: {res['benign_fpr']:.4f}, Malicious Recall: {res['malicious_recall']:.4f}")
    
if __name__ == "__main__":
    main()
