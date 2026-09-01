import json
from pathlib import Path
import sys
import numpy as np
import pickle

ROOT = Path("C:/Users/user/Programing files/Yajat/Projects/sentinel-ml/Messages-ml")
sys.path.insert(0, str(ROOT / "scripts"))

from feature_config import FeatureConfig
from feature_extraction import extract_feature_vector
from sklearn.ensemble import HistGradientBoostingClassifier
from sklearn.preprocessing import StandardScaler
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics import confusion_matrix

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

def main():
    train_recs = load_dataset("train_expanded_v2.jsonl") 
    test_recs = load_dataset("test.jsonl")
    
    cfg = FeatureConfig()
    label_map = {"BENIGN": 0, "SUSPICIOUS_SPAM": 1, "MALICIOUS": 2}
    
    train_texts = [r.get("raw_text", "") for r in train_recs]
    y_tr = np.array([label_map[r.get("security_label", "BENIGN")] for r in train_recs])
    test_texts = [r.get("raw_text", "") for r in test_recs]
    y_te = np.array([label_map[r.get("security_label", "BENIGN")] for r in test_recs])
    
    tfidf = TfidfVectorizer(max_features=2000, stop_words="english", ngram_range=(1, 2))
    X_tr_tfidf = tfidf.fit_transform(train_texts).toarray()
    X_te_tfidf = tfidf.transform(test_texts).toarray()
    
    X_tr_det = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in train_recs])
    X_te_det = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in test_recs])
    
    X_tr = np.hstack((X_tr_det, X_tr_tfidf))
    X_te = np.hstack((X_te_det, X_te_tfidf))
    scaler = StandardScaler()
    X_tr = scaler.fit_transform(X_tr)
    X_te = scaler.transform(X_te)
    
    configs = [
        {"max_iter": 50, "max_depth": 5, "max_leaf_nodes": 15},
        {"max_iter": 100, "max_depth": 5, "max_leaf_nodes": 15},
        {"max_iter": 100, "max_depth": 5, "max_leaf_nodes": 31},
        {"max_iter": 50, "max_depth": 4, "max_leaf_nodes": 15}
    ]
    
    for c in configs:
        clf = HistGradientBoostingClassifier(random_state=42, class_weight='balanced', **c)
        clf.fit(X_tr, y_tr)
        size_kb = len(pickle.dumps(clf)) / 1024
        
        probs = clf.predict_proba(X_te)
        preds = predict_with_threshold(probs, 0.75)
        cm = confusion_matrix(y_te, preds, labels=[0, 1, 2])
        ben_fpr = (cm[0][1] + cm[0][2]) / max(1, sum(cm[0]))
        mal_rec = cm[2][2] / max(1, sum(cm[2]))
        
        print(f"Config {c} | Size: {size_kb:.2f} KB | Benign FPR: {ben_fpr:.4f} | Mal Rec: {mal_rec:.4f}")

if __name__ == "__main__":
    main()
