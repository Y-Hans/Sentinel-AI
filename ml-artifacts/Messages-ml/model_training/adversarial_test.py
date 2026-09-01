import json
from pathlib import Path
import sys
import numpy as np

ROOT = Path("C:/Users/user/Programing files/Yajat/Projects/sentinel-ml/Messages-ml")
sys.path.insert(0, str(ROOT / "scripts"))

from feature_config import FeatureConfig
from feature_extraction import extract_feature_vector
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler

ADVERSARIAL_CASES = [
    {
        "type": "LEGIT_TAX_NOTICE",
        "label": "BENIGN",
        "text": "INCOMETAX DEPT: Non-filing of itr 23-24 may attract latefee u/s 234F. File return @ incometax.gov.in asap.",
        "sender": "AD-INCOMET"
    },
    {
        "type": "LEGIT_TAX_NOTICE_HINGLISH",
        "label": "BENIGN",
        "text": "Income Tax dept se notice: ITR file nahi kiya toh penalty lag sakta hai. Official site incometax.gov.in par file karein.",
        "sender": "AD-INCOMET"
    },
    {
        "type": "LEGIT_ELECTRICITY",
        "label": "BENIGN",
        "text": "MSEDCL: ur electricity bill Rs1250 due on 15th. Pay via mahadiscom app b4 deadline 2 avoid penalty.",
        "sender": "AX-MAHADIS"
    },
    {
        "type": "LEGIT_ELECTRICITY_HINGLISH",
        "label": "BENIGN",
        "text": "MSEDCL: Aapka light bill Rs 1250 due hai. Penalty se bachne ke liye official app se pay karein.",
        "sender": "AX-MAHADIS"
    },
    {
        "type": "LEGIT_KYC_WARNING",
        "label": "BENIGN",
        "text": "UIDAI alert: Aadhaar auth done today. If u did not do this, lock it @ resident.uidai.gov.in immediately!!",
        "sender": "AD-UIDAIG"
    },
    {
        "type": "MALICIOUS_SUBTLE",
        "label": "MALICIOUS",
        "text": "Dear customer, your account requires a routine security check. Please verify your details at http://sbi-secur-check.info",
        "sender": "VM-SBISEC"
    },
    {
        "type": "MALICIOUS_SUBTLE_NO_URL",
        "label": "MALICIOUS",
        "text": "Dear customer, your SBI account is locked. Please send your ATM PIN to this number to unlock it.",
        "sender": "+919876543210"
    }
]

def load_and_train():
    train_recs = [json.loads(line) for line in open(ROOT / 'data/processed/train_contrastive.jsonl', encoding='utf-8')]
    label_map = {'BENIGN': 0, 'SUSPICIOUS_SPAM': 1, 'MALICIOUS': 2}
    cfg = FeatureConfig(active_groups={"STRUCTURAL", "URGENCY", "FEAR_THREAT", "AUTH", "OTP_INTENT", "FINANCIAL", "CTA", "SENDER", "LEGIT_INTENT", "NGRAM_HASH"}, ngram_hash_bins=64)
    
    X_tr = np.array([extract_feature_vector(r.get('raw_text',''), r.get('sender_header'), cfg) for r in train_recs])
    y_tr = np.array([label_map[r.get('security_label', 'BENIGN')] for r in train_recs])
    
    scaler = StandardScaler()
    X_tr = scaler.fit_transform(X_tr)
    
    lr = LogisticRegression(max_iter=1000, random_state=42, class_weight='balanced')
    lr.fit(X_tr, y_tr)
    
    return lr, scaler, cfg, label_map

def main():
    print("Training baseline LR + N-Gram on contrastive data...")
    lr, scaler, cfg, label_map = load_and_train()
    
    print("\n--- ADVERSARIAL ROBUSTNESS TEST ---")
    
    X_adv = np.array([extract_feature_vector(c['text'], c['sender'], cfg) for c in ADVERSARIAL_CASES])
    X_adv = scaler.transform(X_adv)
    
    preds = lr.predict(X_adv)
    probs = lr.predict_proba(X_adv)
    
    rev_label = {v: k for k, v in label_map.items()}
    
    passed = 0
    for i, c in enumerate(ADVERSARIAL_CASES):
        pred_label = rev_label[preds[i]]
        success = (pred_label == c['label'])
        passed += 1 if success else 0
        print(f"[{c['type']}] Expected: {c['label']}, Predicted: {pred_label} (Prob Malicious: {probs[i][2]:.4f}) - {'PASS' if success else 'FAIL'}")
        
    print(f"\nPassed {passed}/{len(ADVERSARIAL_CASES)} adversarial cases.")

if __name__ == "__main__":
    main()
