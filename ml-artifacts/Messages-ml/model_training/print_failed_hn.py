import json
from pathlib import Path
import sys
import pickle
import numpy as np

ROOT = Path("C:/Users/user/Programing files/Yajat/Projects/sentinel-ml/Messages-ml")
sys.path.insert(0, str(ROOT / "scripts"))

from feature_config import FeatureConfig
from feature_extraction import extract_feature_vector

def main():
    val_recs = []
    with open(ROOT / "data" / "processed" / "val.jsonl", "r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                val_recs.append(json.loads(line.strip()))
                
    hn_recs = [r for r in val_recs if r.get('source_id') == 'SRC_CURATED_HARD_NEGATIVES_V1']
    
    with open("champion_model.pkl", "rb") as f:
        clf = pickle.load(f)
    with open("champion_tfidf.pkl", "rb") as f:
        tfidf = pickle.load(f)
    with open("champion_scaler.pkl", "rb") as f:
        scaler = pickle.load(f)
        
    cfg = FeatureConfig()
    texts = [r.get("raw_text", "") for r in hn_recs]
    X_tfidf = tfidf.transform(texts).toarray()
    X_det = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in hn_recs])
    X = scaler.transform(np.hstack((X_det, X_tfidf)))
    
    probs = clf.predict_proba(X)
    for i, r in enumerate(hn_recs):
        p_non = probs[i][1] + probs[i][2]
        if p_non >= 0.85:
            print(f"FAILED (Prob: {p_non:.3f}) - {r['raw_text']}")
            
if __name__ == "__main__":
    main()
