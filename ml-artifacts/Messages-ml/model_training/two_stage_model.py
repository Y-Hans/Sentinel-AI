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
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import accuracy_score, f1_score, precision_score, recall_score, confusion_matrix
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
    
    cfg = FeatureConfig(active_groups={"STRUCTURAL", "URGENCY", "FEAR_THREAT", "AUTH", "OTP_INTENT", "FINANCIAL", "CTA", "SENDER", "LEGIT_INTENT", "NGRAM_HASH"}, ngram_hash_bins=64)
    
    X_tr = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in train_recs])
    y_tr_full = np.array([{"BENIGN": 0, "SUSPICIOUS_SPAM": 1, "MALICIOUS": 2}[r.get("security_label", "BENIGN")] for r in train_recs])
    
    X_va = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in val_recs])
    y_va_full = np.array([{"BENIGN": 0, "SUSPICIOUS_SPAM": 1, "MALICIOUS": 2}[r.get("security_label", "BENIGN")] for r in val_recs])
    
    scaler = StandardScaler()
    X_tr = scaler.fit_transform(X_tr)
    X_va = scaler.transform(X_va)
    
    # Stage 1: BENIGN (0) vs NON_BENIGN (1)
    y_tr_s1 = np.where(y_tr_full == 0, 0, 1)
    
    print("Training Stage 1 (BENIGN vs NON_BENIGN)...")
    clf1 = LogisticRegression(max_iter=1000, random_state=42, class_weight='balanced')
    # clf1 = HistGradientBoostingClassifier(random_state=42, max_depth=5, max_iter=100, class_weight='balanced')
    clf1.fit(X_tr, y_tr_s1)
    
    # Stage 2: SUSPICIOUS_SPAM (1) vs MALICIOUS (2)
    print("Training Stage 2 (SUSPICIOUS_SPAM vs MALICIOUS)...")
    mask_s2 = (y_tr_full > 0)
    X_tr_s2 = X_tr[mask_s2]
    y_tr_s2 = y_tr_full[mask_s2]
    
    clf2 = HistGradientBoostingClassifier(random_state=42, max_depth=5, max_iter=100)
    clf2.fit(X_tr_s2, y_tr_s2)
    
    print("Evaluating Two-Stage Architecture...")
    # Inference
    start = time.time()
    
    # Get probabilities from Stage 1. Let's use a very strict threshold to classify as NON_BENIGN
    # to protect BENIGN. We want FPR < 1%.
    probs_s1 = clf1.predict_proba(X_va)[:, 1]
    
    # Search for best threshold on validation for Stage 1
    best_t1 = 0.5
    for t in np.arange(0.5, 0.99, 0.05):
        fp = sum(1 for i in range(len(y_va_full)) if probs_s1[i] >= t and y_va_full[i] == 0)
        total_b = sum(1 for y in y_va_full if y == 0)
        if fp / total_b <= 0.01:
            best_t1 = t
            break
            
    print(f"Selected Stage 1 Threshold: {best_t1:.2f}")
    
    preds_s1 = (probs_s1 >= best_t1).astype(int)
    
    final_preds = np.zeros_like(y_va_full)
    
    # For those predicted as NON_BENIGN (1), predict stage 2
    idx_non_benign = np.where(preds_s1 == 1)[0]
    if len(idx_non_benign) > 0:
        X_va_s2 = X_va[idx_non_benign]
        preds_s2 = clf2.predict(X_va_s2)
        final_preds[idx_non_benign] = preds_s2
        
    inf_time = time.time() - start
    
    # Metrics
    cm = confusion_matrix(y_va_full, final_preds, labels=[0, 1, 2])
    print(f"Confusion Matrix:\n{cm}")
    
    benign_true = sum(cm[0])
    benign_fpr = cm[0][1] + cm[0][2]
    print(f"Benign -> Any NonBenign FPR: {benign_fpr / benign_true:.4f}")
    
    mal_recall = cm[2][2] / sum(cm[2])
    print(f"Malicious Recall: {mal_recall:.4f}")
    print(f"Macro F1: {f1_score(y_va_full, final_preds, average='macro'):.4f}")
    
    # Hard negative test
    hn_recs = [r for r in val_recs if r.get('source_id') == 'SRC_CURATED_HARD_NEGATIVES_V1' and r.get('security_label') == 'BENIGN']
    X_hn = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in hn_recs])
    X_hn = scaler.transform(X_hn)
    
    probs_hn = clf1.predict_proba(X_hn)[:, 1]
    preds_hn_s1 = (probs_hn >= best_t1).astype(int)
    hn_fp = sum(1 for i in range(len(hn_recs)) if preds_hn_s1[i] == 1 and clf2.predict([X_hn[i]])[0] == 2)
    
    print(f"Hard Negative FPR: {hn_fp / len(hn_recs):.4f} ({hn_fp}/{len(hn_recs)})")
    
if __name__ == "__main__":
    main()
