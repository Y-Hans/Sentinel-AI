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
from train_models import evaluate_classifier

def load_dataset(filename):
    filepath = ROOT / "data" / "processed" / filename
    records = []
    with open(filepath, "r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                records.append(json.loads(line.strip()))
    return records

def main():
    print("Loading datasets...")
    train_recs = load_dataset("train_contrastive.jsonl") 
    val_recs = load_dataset("val.jsonl")
    
    cfg = FeatureConfig()
    label_map = {"BENIGN": 0, "SUSPICIOUS_SPAM": 1, "MALICIOUS": 2}
    
    train_texts = [r.get("raw_text", "") for r in train_recs]
    val_texts = [r.get("raw_text", "") for r in val_recs]
    
    y_tr = np.array([label_map[r.get("security_label", "BENIGN")] for r in train_recs])
    y_va = np.array([label_map[r.get("security_label", "BENIGN")] for r in val_recs])
    
    print("Extracting TF-IDF...")
    tfidf = TfidfVectorizer(max_features=2000, stop_words="english", ngram_range=(1, 2))
    X_tr_tfidf = tfidf.fit_transform(train_texts).toarray()
    X_va_tfidf = tfidf.transform(val_texts).toarray()
    
    print("Extracting Deterministic features...")
    X_tr_det = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in train_recs])
    X_va_det = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in val_recs])
    
    X_tr = np.hstack((X_tr_det, X_tr_tfidf))
    X_va = np.hstack((X_va_det, X_va_tfidf))
    
    scaler = StandardScaler()
    X_tr = scaler.fit_transform(X_tr)
    X_va = scaler.transform(X_va)
    
    print("Training Logistic Regression (TF-IDF + Det)...")
    clf = LogisticRegression(max_iter=1000, random_state=42, class_weight='balanced')
    
    start = time.time()
    clf.fit(X_tr, y_tr)
    train_time = time.time() - start
    
    val_metrics = evaluate_classifier(clf, X_va, y_va, label_map)
    print(val_metrics)
    
    # Hard negatives eval prep
    hn_recs = [r for r in val_recs if r.get('source_id') == 'SRC_CURATED_HARD_NEGATIVES_V1' and r.get('security_label') == 'BENIGN']
    hn_texts = [r.get("raw_text", "") for r in hn_recs]
    
    X_hn_tfidf = tfidf.transform(hn_texts).toarray()
    X_hn_det = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in hn_recs])
    
    X_hn = np.hstack((X_hn_det, X_hn_tfidf))
    X_hn = scaler.transform(X_hn)
    total_hn = len(X_hn)

    # Threshold Sweep
    print("\n--- Threshold Analysis ---")
    probs = clf.predict_proba(X_va)
    
    best_op = None
    for t in np.arange(0.50, 0.99, 0.01):
        preds = (probs >= t).argmax(axis=1) # Need to handle multiclass properly
        
        # For multiclass, if we want to change threshold for Malicious (class 2)
        # We can just say: if prob(Malicious) > t -> Malicious, else argmax of remaining
        
        preds_custom = np.argmax(probs, axis=1)
        # Apply custom threshold for Malicious and Suspicious
        for i in range(len(preds_custom)):
            if probs[i][2] >= t:
                preds_custom[i] = 2
            elif probs[i][1] >= t: # not strictly needed, but let's just threshold Malicious
                pass
                
        cm = confusion_matrix(y_va, preds_custom, labels=[0, 1, 2])
        ben_fpr = (cm[0][1] + cm[0][2]) / max(1, sum(cm[0]))
        mal_rec = cm[2][2] / max(1, sum(cm[2]))
        
        # HN test at this threshold
        probs_hn = clf.predict_proba(X_hn)
        preds_hn = np.argmax(probs_hn, axis=1)
        for i in range(len(preds_hn)):
            if probs_hn[i][2] >= t:
                preds_hn[i] = 2
        
        hn_fp = sum(1 for p in preds_hn if p > 0)
        hn_fpr = hn_fp / total_hn if total_hn > 0 else 0
        
        if ben_fpr <= 0.01 and mal_rec >= 0.80 and hn_fpr <= 0.01:
            if best_op is None or mal_rec > best_op['mal_rec']:
                best_op = {'t': t, 'ben_fpr': ben_fpr, 'mal_rec': mal_rec, 'hn_fpr': hn_fpr}
                
        print(f"Thresh: {t:.2f} | Benign FPR: {ben_fpr:.4f} | Malicious Recall: {mal_rec:.4f} | HN FPR: {hn_fpr:.4f}")
        
    if best_op:
        print(f"\nFOUND DEPLOYABLE OPERATING POINT: Threshold {best_op['t']:.2f}")
        print(f"Benign FPR: {best_op['ben_fpr']:.4f}")
        print(f"Malicious Recall: {best_op['mal_rec']:.4f}")
        print(f"Hard Negative FPR: {best_op['hn_fpr']:.4f}")
    else:
        print("\nNo operating point satisfies all deployment gates.")    
    print(f"Train Time: {train_time:.2f}s")
    print(f"Macro F1: {val_metrics['macro_f1']:.4f}")
    print(f"Malicious Recall: {val_metrics['malicious_recall']:.4f}")
    print(f"Benign FPR: {val_metrics['benign_fpr']:.4f}")
    print(f"Hard Negative FPR: {hn_fpr:.4f} ({fp}/{total_hn})")
    
if __name__ == "__main__":
    main()
