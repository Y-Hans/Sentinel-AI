import json
import time
from pathlib import Path
import sys
import numpy as np

ROOT = Path("C:/Users/user/Programing files/Yajat/Projects/sentinel-ml/Messages-ml")
sys.path.insert(0, str(ROOT / "scripts"))

from feature_config import FeatureConfig
from feature_extraction import extract_feature_vector
from sklearn.ensemble import HistGradientBoostingClassifier
from sklearn.preprocessing import StandardScaler
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics import accuracy_score, f1_score, confusion_matrix

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
            # If Non-Benign, pick the argmax of (Suspicious, Malicious)
            preds[i] = 1 if probs[i][1] > probs[i][2] else 2
        else:
            preds[i] = 0
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
    probs = clf.predict_proba(X)
    preds = predict_with_threshold(probs, t)
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
        hn_probs = clf.predict_proba(X_hn)
        hn_preds = predict_with_threshold(hn_probs, t)
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
    
    print("Training HistGBM (TF-IDF + Det)...")
    clf = HistGradientBoostingClassifier(random_state=42, max_depth=5, max_iter=200, class_weight='balanced')
    clf.fit(X_tr, y_tr)
    
    t = 0.75
    print(f"\n================ Evaluating with Threshold {t} ================")
    val_res = run_evaluation("VALIDATION", val_recs, clf, tfidf, scaler, cfg, t)
    test_res = run_evaluation("TEST", test_recs, clf, tfidf, scaler, cfg, t)
    ood_res = run_evaluation("OOD", ood_recs, clf, tfidf, scaler, cfg, t)
    
    print("Exporting champion artifacts...")
    import pickle
    with open("champion_model.pkl", "wb") as f:
        pickle.dump(clf, f)
    with open("champion_tfidf.pkl", "wb") as f:
        pickle.dump(tfidf, f)
    with open("champion_scaler.pkl", "wb") as f:
        pickle.dump(scaler, f)
    
    model_bytes = pickle.dumps(clf)
    tfidf_bytes = pickle.dumps(tfidf)
    print(f"\nModel size: {len(model_bytes) / 1024:.2f} KB")
    print(f"TF-IDF size: {len(tfidf_bytes) / 1024:.2f} KB")

if __name__ == "__main__":
    main()
