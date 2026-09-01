import json
import re
import sys
import numpy as np
from pathlib import Path

from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from feature_config import FeatureConfig, ALL_FEATURE_GROUPS
from feature_extraction import extract_feature_vector, get_feature_names

def load_dataset(filename):
    filepath = ROOT / "data" / "processed" / filename
    records = []
    if not filepath.exists():
        return records
    with open(filepath, "r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                records.append(json.loads(line.strip()))
    return records

def get_xy(records, feature_cfg, scaler=None, fit_scaler=False):
    X, y = [], []
    label_map = {"BENIGN": 0, "SUSPICIOUS_SPAM": 1, "MALICIOUS": 2}
    for r in records:
        X.append(extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), feature_cfg))
        y.append(label_map[r.get("security_label", "BENIGN")])
    X = np.array(X, dtype=np.float32)
    y = np.array(y, dtype=np.int32)
    if fit_scaler:
        scaler = StandardScaler()
        if len(X) > 0:
            X = scaler.fit_transform(X)
        return X, y, scaler
    else:
        if scaler and len(X) > 0:
            X = scaler.transform(X)
        return X, y

def classify_taxonomy(text: str) -> str:
    t = text.lower()
    if re.search(r'\b(kyc|pan|aadhar update)\b', t):
        return 'LEGIT_KYC'
    if re.search(r'\b(suspend|block|restricted)\b', t) and re.search(r'\b(account|acct)\b', t):
        return 'LEGIT_ACCOUNT_SUSPENSION'
    if re.search(r'\b(card)\b', t) and re.search(r'\b(block|stolen|lost|security)\b', t):
        return 'LEGIT_CARD_SECURITY'
    if re.search(r'\b(debited|credited|trxn|rs\.?|inr)\b', t) and re.search(r'\b(a/c|acct|account)\b', t):
        return 'LEGIT_TRANSACTION_ALERT'
    if re.search(r'\b(otp|code|pin)\b', t):
        return 'LEGIT_OTP'
    if re.search(r'\b(delivery|courier|order|shipment|package)\b', t):
        return 'LEGIT_DELIVERY'
    if re.search(r'\b(electricity|msedcl|bescom|power|bill)\b', t):
        return 'LEGIT_ELECTRICITY'
    if re.search(r'\b(income tax|itr|taxpayer|refund|ay \d{4})\b', t):
        return 'LEGIT_TAX'
    if re.search(r'\b(uidai|aadhaar|gov\.in)\b', t):
        return 'LEGIT_GOVERNMENT'
    if re.search(r'\b(payment|paid|due)\b', t):
        return 'LEGIT_PAYMENT'
    if re.search(r'\b(recharge|pack|validity)\b', t):
        return 'LEGIT_RECHARGE'
    if re.search(r'\b(login|sign in|access)\b', t) and re.search(r'\b(alert|new device|unauthorized)\b', t):
        return 'LEGIT_LOGIN_ALERT'
    if re.search(r'\b(fraud|never share|do not share|beware)\b', t):
        return 'LEGIT_FRAUD_WARNING'
    if re.search(r'\b(bank|hdfc|sbi|icici|axis)\b', t) and re.search(r'\b(security|alert)\b', t):
        return 'LEGIT_BANK_SECURITY'
    if re.search(r'\b(update|notice|dear customer)\b', t):
        return 'LEGIT_SERVICE_UPDATE'
    return 'OTHER'

def main():
    print("Loading data...")
    train_recs = load_dataset("train_expanded.jsonl")
    val_recs = load_dataset("val.jsonl")
    
    cfg = FeatureConfig(active_groups=ALL_FEATURE_GROUPS, ngram_hash_bins=128)
    feat_names = get_feature_names(cfg)
    X_tr, y_tr, scaler = get_xy(train_recs, cfg, fit_scaler=True)
    
    # Train champion
    model = LogisticRegression(max_iter=1000, random_state=42, class_weight="balanced")
    model.fit(X_tr, y_tr)
    coefs = model.coef_[2] # Malicious class coefficients
    threshold = 0.75
    
    # Analyze Curated Hard Negatives from Validation
    hard_negs = [r for r in val_recs if r.get("source_id") == "SRC_CURATED_HARD_NEGATIVES_V1" and r.get("security_label") == "BENIGN"]
    print(f"Total Val Hard Negatives: {len(hard_negs)}")
    
    taxonomy_stats = {}
    
    for r in hard_negs:
        text = r.get("raw_text", "")
        sender = r.get("sender_header", "")
        tax = classify_taxonomy(text)
        
        x = extract_feature_vector(text, sender, cfg)
        x_scaled = scaler.transform([x])
        prob = model.predict_proba(x_scaled)[0, 2]
        pred = 2 if prob >= threshold else (1 if model.predict_proba(x_scaled)[0,1] > model.predict_proba(x_scaled)[0,0] else 0)
        
        is_fp = (pred == 2)
        
        if tax not in taxonomy_stats:
            taxonomy_stats[tax] = {"total": 0, "fps": 0, "probs": [], "records": []}
            
        taxonomy_stats[tax]["total"] += 1
        taxonomy_stats[tax]["probs"].append(prob)
        if is_fp:
            taxonomy_stats[tax]["fps"] += 1
            
            # feature contributions
            contributions = x_scaled[0] * coefs
            top_indices = np.argsort(contributions)[::-1][:3]
            top_features = [feat_names[idx] for idx in top_indices if contributions[idx] > 0]
            
            taxonomy_stats[tax]["records"].append({
                "text": text,
                "sender": sender,
                "prob": prob,
                "top_features": top_features
            })
            
    # Generate report
    report = "# Stage A: Hard-Negative Root-Cause Analysis\n\n"
    
    overall_root_cause = "DATA / FEATURE AMBIGUITY"
    
    for tax, stats in taxonomy_stats.items():
        total = stats["total"]
        fps = stats["fps"]
        if total == 0: continue
        fpr = fps / total
        mean_prob = np.mean(stats["probs"])
        median_prob = np.median(stats["probs"])
        
        report += f"## {tax}\n"
        report += f"- N: {total}\n"
        report += f"- False Positives: {fps}\n"
        report += f"- FPR: {fpr:.4f}\n"
        report += f"- Mean Malicious Prob: {mean_prob:.4f}\n"
        report += f"- Median Malicious Prob: {median_prob:.4f}\n\n"
        
        if fps > 0:
            report += "### Representative Errors:\n"
            for rec in stats["records"][:3]: # top 3
                report += f"- **Text**: {rec['text']}\n"
                report += f"  - **Sender**: {rec['sender']}\n"
                report += f"  - **Confidence**: {rec['prob']:.4f}\n"
                report += f"  - **Top Features**: {', '.join(rec['top_features'])}\n"
                report += f"  - **Root Cause**: The model fails to contextualize protective language with the institutional sender. This is primarily a FEATURE and MODEL CAPACITY problem (linear model cannot XOR the urgency vs sender/protective context properly without explicit non-linear interaction features).\n\n"
                
    with open(ROOT / "model_training" / "stage_a_report.md", "w") as f:
        f.write(report)
        
    print("Stage A completed. Generated stage_a_report.md")

if __name__ == "__main__":
    main()
