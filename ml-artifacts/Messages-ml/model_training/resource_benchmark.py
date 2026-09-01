import json
import time
from pathlib import Path
import sys
import numpy as np
import pickle
import os

ROOT = Path("C:/Users/user/Programing files/Yajat/Projects/sentinel-ml/Messages-ml")
sys.path.insert(0, str(ROOT / "scripts"))

from feature_config import FeatureConfig
from feature_extraction import extract_feature_vector
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler
from sklearn.feature_extraction.text import TfidfVectorizer

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
    
    # Save models
    model_dir = ROOT / "model_training" / "final_artifacts"
    model_dir.mkdir(exist_ok=True)
    
    with open(model_dir / "tfidf.pkl", "wb") as f:
        pickle.dump(tfidf, f)
    with open(model_dir / "scaler.pkl", "wb") as f:
        pickle.dump(scaler, f)
    with open(model_dir / "model.pkl", "wb") as f:
        pickle.dump(clf, f)
        
    s_tfidf = os.path.getsize(model_dir / "tfidf.pkl") / 1024
    s_scaler = os.path.getsize(model_dir / "scaler.pkl") / 1024
    s_model = os.path.getsize(model_dir / "model.pkl") / 1024
    s_total = s_tfidf + s_scaler + s_model
    
    # Benchmark Inference
    val_recs = load_dataset("val.jsonl")
    val_texts = [r.get("raw_text", "") for r in val_recs]
    
    start = time.time()
    for i in range(100):
        # Measure end to end for a single record to simulate p99 latency
        rec = val_recs[i]
        t = [rec.get("raw_text", "")]
        x_tf = tfidf.transform(t).toarray()
        x_det = np.array([extract_feature_vector(rec.get("raw_text", ""), rec.get("sender_header"), cfg)])
        x_all = scaler.transform(np.hstack((x_det, x_tf)))
        probs = clf.predict_proba(x_all)
        pred = 2 if probs[0][2] >= 0.61 else np.argmax(probs)
    inf_time_per_100 = time.time() - start
    
    res = {
        "parameter_count": clf.coef_.size + clf.intercept_.size,
        "serialized_size_kb": s_total,
        "tfidf_size_kb": s_tfidf,
        "model_size_kb": s_model,
        "scaler_size_kb": s_scaler,
        "inference_latency_ms": (inf_time_per_100 / 100) * 1000
    }
    
    print(json.dumps(res, indent=2))
    
    with open(ROOT / "model_training" / "champion_model_metadata.json", "w") as f:
        json.dump(res, f, indent=2)

if __name__ == "__main__":
    main()
