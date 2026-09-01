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
from sklearn.neural_network import MLPClassifier
from sklearn.preprocessing import StandardScaler
from sentence_transformers import SentenceTransformer
from train_models import evaluate_classifier

def load_dataset(filename):
    filepath = ROOT / "data" / "processed" / filename
    records = []
    with open(filepath, "r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                records.append(json.loads(line.strip()))
    return records

def prepare_data(train_recs, val_recs, feature_cfg, model):
    label_map = {"BENIGN": 0, "SUSPICIOUS_SPAM": 1, "MALICIOUS": 2}
    
    def get_xy(records):
        X_det, X_emb, y = [], [], []
        texts = [r.get("raw_text", "") for r in records]
        
        # Batch encode
        print(f"Encoding {len(texts)} texts...")
        embs = model.encode(texts, batch_size=128, show_progress_bar=True)
        
        for i, r in enumerate(records):
            det_feat = extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), feature_cfg)
            X_det.append(det_feat)
            X_emb.append(embs[i])
            y.append(label_map[r.get("security_label", "BENIGN")])
            
        # Concatenate deterministic features and embeddings
        X = np.hstack([np.array(X_det, dtype=np.float32), np.array(X_emb, dtype=np.float32)])
        return X, np.array(y, dtype=np.int32)
        
    X_train, y_train = get_xy(train_recs)
    X_val, y_val = get_xy(val_recs)
    
    scaler = StandardScaler()
    X_train_scaled = scaler.fit_transform(X_train) if len(X_train) > 0 else X_train
    X_val_scaled = scaler.transform(X_val) if len(X_val) > 0 else X_val
    
    return X_train_scaled, y_train, X_val_scaled, y_val, scaler, label_map

def run_experiment(clf, name, X_tr, y_tr, X_va, y_va, label_map, val_recs, scaler, feature_cfg, sbert_model):
    start = time.time()
    clf.fit(X_tr, y_tr)
    train_time = time.time() - start
    
    val_metrics = evaluate_classifier(clf, X_va, y_va, label_map)
    
    # Hard negatives eval
    hn_recs = [r for r in val_recs if r.get('source_id') == 'SRC_CURATED_HARD_NEGATIVES_V1' and r.get('security_label') == 'BENIGN']
    
    texts = [r.get("raw_text", "") for r in hn_recs]
    embs = sbert_model.encode(texts, batch_size=32, show_progress_bar=False)
    
    X_det = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), feature_cfg) for r in hn_recs])
    X_hn = np.hstack([X_det, embs])
    X_hn = scaler.transform(X_hn)
    
    hn_preds = clf.predict(X_hn)
    fp = sum(1 for p in hn_preds if p == label_map["MALICIOUS"])
    total_hn = len(hn_preds)
    hn_fpr = fp / total_hn if total_hn > 0 else 0
    
    return {
        "model_name": name,
        "train_time": train_time,
        "macro_f1": val_metrics.get("macro_f1", 0),
        "malicious_recall": val_metrics.get("malicious_recall", 0),
        "benign_fpr": val_metrics.get("benign_fpr", 0),
        "hard_negative_fpr": hn_fpr,
        "inference_time": val_metrics.get("inference_time_seconds", 0)
    }

def main():
    print("Loading SentenceTransformer model...")
    sbert_model = SentenceTransformer("all-MiniLM-L6-v2")
    
    print("Loading datasets...")
    train_recs = load_dataset("train_contrastive.jsonl") 
    val_recs = load_dataset("val.jsonl")
    
    cfg = FeatureConfig()
    X_tr, y_tr, X_va, y_va, scaler, label_map = prepare_data(train_recs, val_recs, cfg, sbert_model)

    results = []

    models = [
        (LogisticRegression(max_iter=1000, random_state=42, class_weight='balanced'), "LR_SentenceEmbeddings"),
        (MLPClassifier(hidden_layer_sizes=(64, 32), max_iter=200, random_state=42, early_stopping=True), "MLP_SentenceEmbeddings"),
    ]

    for model, name in models:
        print(f"Training {name}...")
        res = run_experiment(model, name, X_tr, y_tr, X_va, y_va, label_map, val_recs, scaler, cfg, sbert_model)
        results.append(res)
        print(res)
        
    with open(ROOT / "model_training" / "semantic_model_results.json", "w") as f:
        json.dump(results, f, indent=2)

if __name__ == "__main__":
    main()
