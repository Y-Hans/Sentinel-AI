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
from sklearn.metrics import f1_score, confusion_matrix

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
    return preds

def main():
    print("Loading datasets...")
    train_recs = load_dataset("train_contrastive.jsonl") 
    test_recs = load_dataset("test.jsonl")
    val_recs = load_dataset("val.jsonl")
    ood_recs = load_dataset("ood.jsonl")
    
    all_recs = train_recs + val_recs + test_recs
    sources = set([r.get("source_id") for r in all_recs])
    
    cfg = FeatureConfig()
    label_map = {"BENIGN": 0, "SUSPICIOUS_SPAM": 1, "MALICIOUS": 2}
    t = 0.61
    
    results = []
    
    for held_out_source in sources:
        # We need at least some examples to evaluate
        eval_recs = [r for r in all_recs if r.get("source_id") == held_out_source]
        if len(eval_recs) < 50:
            continue
            
        print(f"\n--- Holding out {held_out_source} ---")
        
        tr_recs = [r for r in train_recs if r.get("source_id") != held_out_source]
        
        train_texts = [r.get("raw_text", "") for r in tr_recs]
        y_tr = np.array([label_map[r.get("security_label", "BENIGN")] for r in tr_recs])
        
        tfidf = TfidfVectorizer(max_features=2000, stop_words="english", ngram_range=(1, 2))
        X_tr_tfidf = tfidf.fit_transform(train_texts).toarray()
        X_tr_det = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in tr_recs])
        
        X_tr = np.hstack((X_tr_det, X_tr_tfidf))
        scaler = StandardScaler()
        X_tr = scaler.fit_transform(X_tr)
        
        clf = LogisticRegression(max_iter=1000, random_state=42, class_weight='balanced')
        clf.fit(X_tr, y_tr)
        
        # Evaluate on the held out source
        eval_texts = [r.get("raw_text", "") for r in eval_recs]
        X_eval_tfidf = tfidf.transform(eval_texts).toarray()
        X_eval_det = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in eval_recs])
        X_eval = scaler.transform(np.hstack((X_eval_det, X_eval_tfidf)))
        y_eval = np.array([label_map[r.get("security_label", "BENIGN")] for r in eval_recs])
        
        preds = predict_with_threshold(clf, X_eval, t)
        
        cm = confusion_matrix(y_eval, preds, labels=[0, 1, 2])
        benign_true = sum(cm[0])
        ben_fpr = (cm[0][1] + cm[0][2]) / benign_true if benign_true > 0 else 0
        mal_true = sum(cm[2])
        mal_rec = cm[2][2] / mal_true if mal_true > 0 else 0
        macro_f1 = f1_score(y_eval, preds, average="macro")
        
        res = {
            "source": held_out_source,
            "sample_count": len(eval_recs),
            "FPR": float(ben_fpr),
            "malicious_recall": float(mal_rec),
            "Macro-F1": float(macro_f1)
        }
        results.append(res)
        print(f"Samples: {res['sample_count']}, Benign FPR: {res['FPR']:.4f}, Malicious Recall: {res['malicious_recall']:.4f}, Macro-F1: {res['Macro-F1']:.4f}")

    with open(ROOT / "model_training" / "source_holdout_results.json", "w") as f:
        json.dump(results, f, indent=2)

if __name__ == "__main__":
    main()
