import json
import time
from pathlib import Path
import sys
import numpy as np

ROOT = Path("C:/Users/user/Programing files/Yajat/Projects/sentinel-ml/Messages-ml")
sys.path.insert(0, str(ROOT / "scripts"))

from feature_config import FeatureConfig
from feature_extraction import extract_feature_vector
from sklearn.linear_model import LogisticRegression
from sklearn.neural_network import MLPClassifier
from sklearn.preprocessing import StandardScaler
from sentence_transformers import SentenceTransformer
from sklearn.metrics import accuracy_score, f1_score, confusion_matrix
import torch

def load_dataset(filename):
    filepath = ROOT / "data" / "processed" / filename
    records = []
    with open(filepath, "r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                records.append(json.loads(line.strip()))
    return records

def predict_with_threshold(probs, t):
    preds = np.argmax(probs, axis=1)
    for i in range(len(preds)):
        if probs[i][2] >= t:
            preds[i] = 2
        elif preds[i] == 2 and probs[i][2] < t:
            sub_probs = probs[i][:2]
            preds[i] = np.argmax(sub_probs)
    return preds

def main():
    device = "cuda" if torch.cuda.is_available() else "cpu"
    print(f"Loading SentenceTransformer model on {device}...")
    sbert_model = SentenceTransformer("all-MiniLM-L6-v2", device=device)
    
    print("Loading datasets...")
    train_recs = load_dataset("train_expanded_v2.jsonl") 
    val_recs = load_dataset("val.jsonl")
    
    cfg = FeatureConfig()
    label_map = {"BENIGN": 0, "SUSPICIOUS_SPAM": 1, "MALICIOUS": 2}
    
    train_texts = [r.get("raw_text", "") for r in train_recs]
    y_tr = np.array([label_map[r.get("security_label", "BENIGN")] for r in train_recs])
    val_texts = [r.get("raw_text", "") for r in val_recs]
    y_va = np.array([label_map[r.get("security_label", "BENIGN")] for r in val_recs])
    
    print("Extracting Embeddings...")
    X_tr_emb = sbert_model.encode(train_texts, batch_size=128, show_progress_bar=True)
    X_va_emb = sbert_model.encode(val_texts, batch_size=128, show_progress_bar=True)
    
    print("Extracting Deterministic features...")
    X_tr_det = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in train_recs])
    X_va_det = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in val_recs])
    
    X_tr = np.hstack((X_tr_det, X_tr_emb))
    X_va = np.hstack((X_va_det, X_va_emb))
    
    scaler = StandardScaler()
    X_tr = scaler.fit_transform(X_tr)
    X_va = scaler.transform(X_va)
    
    # Hard negative evaluation setup
    hn_recs = [r for r in val_recs if r.get('source_id') == 'SRC_CURATED_HARD_NEGATIVES_V1' and r.get('security_label') == 'BENIGN']
    hn_texts = [r.get("raw_text", "") for r in hn_recs]
    X_hn_emb = sbert_model.encode(hn_texts, batch_size=32, show_progress_bar=False)
    X_hn_det = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in hn_recs])
    X_hn = scaler.transform(np.hstack((X_hn_det, X_hn_emb)))

    def evaluate_model(name, clf):
        print(f"\nTraining {name} on Embeddings + Det...")
        clf.fit(X_tr, y_tr)
        probs = clf.predict_proba(X_va)
        hn_probs = clf.predict_proba(X_hn)
        
        print(f"\n--- Threshold Analysis ({name}) ---")
        best_t = None
        for t in [0.5, 0.7, 0.9, 0.95, 0.98, 0.99, 0.995, 0.999]:
            preds = predict_with_threshold(probs, t)
                    
            cm = confusion_matrix(y_va, preds, labels=[0, 1, 2])
            ben_fpr = (cm[0][1] + cm[0][2]) / max(1, sum(cm[0]))
            mal_rec = cm[2][2] / max(1, sum(cm[2]))
            
            hn_preds = predict_with_threshold(hn_probs, t)
            hn_fp = sum(1 for p in hn_preds if p > 0)
            hn_fpr = hn_fp / max(1, len(hn_preds))
            
            print(f"Thresh {t:.3f}: Benign FPR {ben_fpr:.4f}, Mal Rec {mal_rec:.4f}, HN FPR {hn_fpr:.4f}")
            if ben_fpr <= 0.01 and hn_fpr <= 0.01 and mal_rec >= 0.8:
                best_t = t
                
        return best_t

    lr = LogisticRegression(max_iter=1000, random_state=42, class_weight='balanced')
    evaluate_model("Logistic Regression", lr)
    
    mlp = MLPClassifier(hidden_layer_sizes=(64, 32), max_iter=200, random_state=42, early_stopping=True)
    evaluate_model("MLP (64, 32)", mlp)

if __name__ == "__main__":
    main()
