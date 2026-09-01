"""
CHALLENGER EXPERIMENT ROUND 3: Close the Credential Recall Gap
================================================================
CHALLENGER_F (depth=7, iter=300, t=0.85) nearly passes all gates:
  TEST: FPR=0.000, Recall=0.887  OOD: FPR=0.000, Recall=0.892
  HN: FPR=0.000  Credential Recall: 0.788 (needs >=0.80)

Strategy:
1. MORE credential/OTP training examples (5x the current count)
2. Fine-grained threshold search around 0.80-0.88 for depth=7 model
3. Try depth=7 with more iterations (400, 500) 
4. Try depth=8
5. Try learning_rate tuning
"""

import json, pickle, sys, time, hashlib
import numpy as np
from pathlib import Path
from collections import Counter
from datetime import datetime, timezone

ROOT = Path("C:/Users/user/Programing files/Yajat/Projects/sentinel-ml/Messages-ml")
sys.path.insert(0, str(ROOT / "scripts"))
sys.stdout.reconfigure(line_buffering=True)

from feature_config import FeatureConfig
from feature_extraction import extract_feature_vector
from sklearn.ensemble import HistGradientBoostingClassifier
from sklearn.preprocessing import StandardScaler
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics import confusion_matrix, f1_score

LABEL_MAP = {"BENIGN": 0, "SUSPICIOUS_SPAM": 1, "MALICIOUS": 2}
LABEL_NAMES = {0: "BENIGN", 1: "SUSPICIOUS_SPAM", 2: "MALICIOUS"}

def load_dataset(filename):
    records = []
    with open(ROOT / "data" / "processed" / filename, "r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                records.append(json.loads(line.strip()))
    return records

def predict_with_threshold(probs, t):
    preds = np.zeros(len(probs), dtype=int)
    for i in range(len(probs)):
        p_nb = probs[i][1] + probs[i][2]
        if p_nb >= t:
            preds[i] = 1 if probs[i][1] > probs[i][2] else 2
        else:
            preds[i] = 0
    return preds

def make_record(text, label, source_id, primary_type):
    h = hashlib.sha256(text.encode()).hexdigest()
    return {
        "message_id": h, "raw_text": text, "security_label": label,
        "source_id": source_id, "source_type": "SYNTHETIC",
        "primary_type": primary_type, "split": "TRAIN", "language": "en"
    }

def generate_expanded_credential_data():
    """Generate a much larger diverse set of credential theft examples."""
    records = []
    SRC = "SRC_CREDENTIAL_EXPANSION_V2"
    
    # Banks and institutions to vary
    banks = ["SBI", "PNB", "BOB", "BOI", "Canara Bank", "Union Bank", "Indian Bank",
             "IDBI Bank", "Central Bank", "Bank of Maharashtra", "UCO Bank", "IOB",
             "Punjab and Sind Bank", "HDFC Bank", "ICICI Bank", "Axis Bank", 
             "Kotak Mahindra Bank", "Yes Bank", "IndusInd Bank", "Federal Bank",
             "Bandhan Bank", "IDFC First Bank", "RBL Bank", "South Indian Bank",
             "Karur Vysya Bank", "City Union Bank", "Dhanlaxmi Bank"]
    
    # Credential phishing templates with institution variation
    phishing_templates = [
        "{bank}: Your account is temporarily suspended. Verify identity at http://{domain}.xyz to reactivate.",
        "{bank}: Dear customer, update your KYC details at http://{domain}-kyc.in before your account gets blocked.",
        "{bank}: Unauthorized login detected. Secure your account now at http://{domain}-secure.xyz.",
        "{bank}: Your debit card will expire today. Update card details at http://{domain}-card.xyz.",
        "{bank}: Net banking access blocked due to suspicious activity. Click http://{domain}-verify.in to unlock.",
        "{bank}: Your {bank} credit card payment failed. Retry at http://{domain}-pay.xyz.",
        "{bank}: CIBIL score update needed. Submit PAN and Aadhaar at http://{domain}-cibil.xyz.",
        "{bank}: Account flagged for review. Share last 4 digits of Aadhaar and account no at 9876543210.",
        "{bank}: Loan pre-approved Rs.5,00,000. Apply at http://{domain}-loan.xyz with PAN card details.",
        "{bank}: Fixed deposit matured. Enter bank details at http://{domain}-fd.xyz to process credit.",
    ]
    
    # Generate with diverse institutions
    import random
    random.seed(42)
    for template in phishing_templates:
        for bank in random.sample(banks, min(8, len(banks))):
            domain = bank.lower().replace(" ", "").replace("bank", "")[:8]
            text = template.format(bank=bank, domain=domain)
            records.append(make_record(text, "MALICIOUS", SRC, "CREDENTIAL_PHISHING"))
    
    # OTP disclosure - diverse patterns
    otp_scams = [
        "{bank}: Rs.{amt} deducted from your account for online purchase. If not you, reply with OTP to cancel.",
        "{bank}: Refund of Rs.{amt} initiated. Share OTP received to credit amount to your account.",
        "{bank}: Credit card upgrade available. Reply with OTP sent to your phone to activate.",
        "Amazon: Return pickup confirmed. Share OTP {code} with delivery person for pickup verification.",
        "Flipkart: Cashback of Rs.{amt} credited. Forward OTP to 8765432109 to redeem in wallet.",
        "{bank}: Insurance claim of Rs.{amt} approved. Share OTP for processing to your account.",
        "{bank}: Fixed deposit interest Rs.{amt} ready. Enter OTP at http://{domain}-otp.xyz to credit.",
        "PhonePe: Excess charge of Rs.{amt}. Reply with OTP to get instant refund.",
        "GPay: Transaction failed. Share OTP with customer care at 9876543210 to reverse charge.",
        "Paytm: Wallet security alert. Share OTP received to block unauthorized transactions.",
    ]
    
    for template in otp_scams:
        for bank in random.sample(banks[:15], 5):
            domain = bank.lower().replace(" ", "")[:8]
            amt = random.choice(["1500", "2450", "3000", "5000", "7500", "10000", "15000"])
            code = str(random.randint(1000, 9999))
            text = template.format(bank=bank, domain=domain, amt=amt, code=code)
            records.append(make_record(text, "MALICIOUS", SRC, "OTP_DISCLOSURE_REQUEST"))
    
    # Benign OTP messages - diverse
    otp_benign_templates = [
        "{bank}: {code} is your OTP for {purpose}. Do NOT share with anyone. Valid for {mins} mins.",
        "{bank}: OTP {code} for {purpose}. Never share OTP with bank staff or anyone. Call {phone} if not you.",
        "{bank}: Your one-time password is {code} for {purpose}. OTP expires in {mins} minutes.",
    ]
    purposes = ["net banking login", "fund transfer", "adding beneficiary", "card payment",
                 "UPI registration", "mobile banking", "online purchase", "NEFT transfer",
                 "bill payment", "FD booking"]
    
    for template in otp_benign_templates:
        for bank in random.sample(banks, 10):
            code = str(random.randint(100000, 999999))
            purpose = random.choice(purposes)
            mins = random.choice(["3", "5", "10", "15"])
            phone = random.choice(["1800-11-2211", "1860-500-1234", "1800-180-2222", "1860-120-7777"])
            text = template.format(bank=bank, code=code, purpose=purpose, mins=mins, phone=phone)
            records.append(make_record(text, "BENIGN", SRC, "LEGITIMATE_OTP"))
    
    # Benign KYC messages
    kyc_benign_templates = [
        "{bank}: Your KYC is due for renewal by {date}. Visit nearest {bank} branch with Aadhaar and PAN.",
        "{bank}: KYC verification completed successfully. Thank you for prompt submission.",
        "{bank}: Annual re-KYC pending. Book appointment via official {bank} app or call {phone}.",
        "{bank}: Documents received for KYC update. Processing may take 2-3 business days.",
    ]
    
    for template in kyc_benign_templates:
        for bank in random.sample(banks, 8):
            date = random.choice(["30-Sep-2026", "15-Oct-2026", "31-Dec-2026"])
            phone = random.choice(["1800-11-2211", "1860-500-1234"])
            text = template.format(bank=bank, date=date, phone=phone)
            records.append(make_record(text, "BENIGN", SRC, "LEGITIMATE_KYC"))
    
    # Government credential phishing
    govt_phishing = [
        "Income Tax: Refund Rs.{amt} pending. Enter bank details at http://itrefund-{n}.xyz to receive.",
        "EPFO: PF claim approved Rs.{amt}. Share Aadhaar and bank details at http://epfo-claim{n}.xyz.",
        "Passport Seva: Passport fee payment failed. Retry at http://passport-pay{n}.xyz with card details.",
        "DigiLocker: Verification failed. Upload Aadhaar at http://digilocker-verify{n}.xyz within 24 hrs.",
        "UIDAI: Aadhaar locked. Unlock by sharing biometric data at http://aadhaar-unlock{n}.xyz.",
        "IRCTC: Booking amount Rs.{amt} stuck. Re-enter payment details at http://irctc-refund{n}.xyz.",
        "LPG Subsidy: Rs.{amt} pending. Verify bank account at http://lpg-verify{n}.xyz to receive.",
        "PM Kisan: Rs.{amt} instalment ready. Update bank details at http://pmkisan-pay{n}.xyz.",
    ]
    
    for template in govt_phishing:
        for i in range(3):
            amt = random.choice(["2000", "5000", "8000", "12000", "15000", "25000"])
            n = random.randint(1, 99)
            text = template.format(amt=amt, n=n)
            records.append(make_record(text, "MALICIOUS", SRC, "CREDENTIAL_PHISHING"))
    
    # Government benign
    govt_benign = [
        "Income Tax: E-verification of your ITR for AY 2026-27 completed successfully. No action required.",
        "EPFO: Your PF contribution for Aug 2026 has been credited. Check balance at epfindia.gov.in.",
        "Passport Seva: Your passport application is under process. Track at passportindia.gov.in.",
        "DigiLocker: Your driving license has been successfully linked. Access at digilocker.gov.in.",
        "UIDAI: Aadhaar update request processed. Download e-Aadhaar at uidai.gov.in.",
        "IRCTC: Your booking PNR {code} confirmed. Check status at indianrailways.gov.in.",
        "LPG: Subsidy of Rs.250 credited to your linked bank account for Aug 2026.",
        "PM Kisan: Rs.2,000 instalment credited to your registered bank account. Check at pmkisan.gov.in.",
    ]
    for text in govt_benign:
        text = text.format(code=str(random.randint(1000000, 9999999)))
        records.append(make_record(text, "BENIGN", SRC, "GOVERNMENT_NOTIFICATION"))
    
    label_dist = Counter(r["security_label"] for r in records)
    print(f"  Generated {len(records)} credential/OTP records: {dict(label_dist)}")
    return records


def main():
    print("=" * 70)
    print("CHALLENGER EXPERIMENT ROUND 3: CREDENTIAL RECALL OPTIMIZATION")
    print(f"Timestamp: {datetime.now(timezone.utc).isoformat()}")
    print("=" * 70)
    
    cfg = FeatureConfig()
    val_recs = load_dataset("val.jsonl")
    test_recs = load_dataset("test.jsonl")
    ood_recs = load_dataset("ood.jsonl")
    all_recs = val_recs + test_recs + ood_recs
    
    hn_benign = [r for r in val_recs if r.get("source_id") == "SRC_CURATED_HARD_NEGATIVES_V1" and r.get("security_label") == "BENIGN"]
    
    # Load base (v4_3x includes utility expansion)
    base_recs = load_dataset("train_expanded_v4_3x.jsonl")
    print(f"Base: {len(base_recs)} records")
    
    # Generate expanded credential data
    cred_records = generate_expanded_credential_data()
    
    # Combine: base + credential data (2x replication to match utility data emphasis)
    combined = base_recs + cred_records * 2
    print(f"Combined v6: {len(combined)} records")
    
    # Save as v6
    v6_path = ROOT / "data" / "processed" / "train_expanded_v6.jsonl"
    with open(v6_path, "w", encoding="utf-8") as f:
        for r in combined:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(f"Saved: {v6_path}")
    
    # ============================================================
    # Helper: train + fine threshold sweep
    # ============================================================
    def train_eval(name, train_data, depth, iters, lr=0.1, word_f=1500, char_f=500):
        print(f"\n  Training: {name}")
        texts = [r.get("raw_text", "") for r in train_data]
        y = np.array([LABEL_MAP[r["security_label"]] for r in train_data])
        
        tw = TfidfVectorizer(max_features=word_f, stop_words="english", ngram_range=(1, 2))
        Xw = tw.fit_transform(texts).toarray()
        tc = TfidfVectorizer(max_features=char_f, ngram_range=(3, 5), analyzer='char_wb')
        Xc = tc.fit_transform(texts).toarray()
        Xd = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in train_data])
        X = np.hstack((Xd, Xw, Xc))
        sc = StandardScaler()
        X = sc.fit_transform(X)
        
        clf = HistGradientBoostingClassifier(random_state=42, max_depth=depth, max_iter=iters, 
                                              class_weight='balanced', learning_rate=lr)
        clf.fit(X, y)
        
        # Prepare eval sets
        def prepare(recs):
            t = [r.get("raw_text", "") for r in recs]
            Xw_ = tw.transform(t).toarray()
            Xc_ = tc.transform(t).toarray()
            Xd_ = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in recs])
            return sc.transform(np.hstack((Xd_, Xw_, Xc_)))
        
        X_val = prepare(val_recs)
        y_val = np.array([LABEL_MAP[r["security_label"]] for r in val_recs])
        val_probs = clf.predict_proba(X_val)
        
        X_hn = prepare(hn_benign)
        hn_probs = clf.predict_proba(X_hn)
        
        cred = [r for r in val_recs if "CREDENTIAL_REQUEST" in r.get("threat_vectors", [])]
        X_cr = prepare(cred)
        y_cr = np.array([LABEL_MAP[r["security_label"]] for r in cred])
        cr_probs = clf.predict_proba(X_cr)
        
        # Fine-grained threshold sweep
        print(f"  {'T':>6} | {'FPR':>8} | {'Rec':>8} | {'HN_FP':>5} | {'Cred_R':>8} | Status")
        
        best_t = None
        best_f1 = -1
        
        for t_int in range(78, 96):
            t = t_int / 100
            vp = predict_with_threshold(val_probs, t)
            cm = confusion_matrix(y_val, vp, labels=[0, 1, 2])
            fpr = (cm[0][1]+cm[0][2]) / max(1, sum(cm[0]))
            rec = cm[2][2] / max(1, sum(cm[2]))
            f1 = f1_score(y_val, vp, average="macro")
            
            hp = predict_with_threshold(hn_probs, t)
            hn_fp = sum(1 for p in hp if p > 0)
            
            cp = predict_with_threshold(cr_probs, t)
            cr_cm = confusion_matrix(y_cr, cp, labels=[0, 1, 2])
            cr_rec = cr_cm[2][2] / max(1, sum(cr_cm[2]))
            
            passes = (fpr <= 0.015 and rec >= 0.80 and hn_fp == 0 and cr_rec >= 0.78)
            status = "ALL_PASS" if (fpr <= 0.01 and rec >= 0.80 and hn_fp == 0 and cr_rec >= 0.80) else ("CLOSE" if passes else "")
            
            if fpr <= 0.015 and rec >= 0.80 and hn_fp == 0 and cr_rec >= 0.78 and f1 > best_f1:
                best_f1 = f1
                best_t = t
            
            if t_int % 2 == 0 or status:
                marker = " <--" if t == best_t and best_t is not None else ""
                print(f"  {t:6.2f} | {fpr:8.4f} | {rec:8.4f} | {hn_fp:5d} | {cr_rec:8.4f} | {status}{marker}")
        
        if best_t is None:
            # Fall back to highest threshold with HN=0
            for t_int in range(95, 77, -1):
                t = t_int / 100
                hp = predict_with_threshold(hn_probs, t)
                if sum(1 for p in hp if p > 0) == 0:
                    vp = predict_with_threshold(val_probs, t)
                    cm = confusion_matrix(y_val, vp, labels=[0, 1, 2])
                    if (cm[0][1]+cm[0][2]) / max(1, sum(cm[0])) <= 0.015:
                        best_t = t
                        break
            if best_t is None:
                best_t = 0.95
        
        print(f"  Selected: t={best_t}")
        
        # Full evaluation at best threshold
        res = {"name": name, "threshold": best_t}
        
        for split_name, recs in [("val", val_recs), ("test", test_recs), ("ood", ood_recs)]:
            X_s = prepare(recs)
            y_s = np.array([LABEL_MAP[r["security_label"]] for r in recs])
            sp = clf.predict_proba(X_s)
            s_preds = predict_with_threshold(sp, best_t)
            cm = confusion_matrix(y_s, s_preds, labels=[0, 1, 2])
            ben = max(1, sum(cm[0])); mal = max(1, sum(cm[2]))
            res[split_name] = {"fpr": (cm[0][1]+cm[0][2])/ben, "recall": cm[2][2]/mal,
                              "f1": f1_score(y_s, s_preds, average="macro"), "cm": cm.tolist()}
        
        hp = predict_with_threshold(hn_probs, best_t)
        hn_fp = sum(1 for p in hp if p > 0)
        res["hn_fpr"] = hn_fp / max(1, len(hn_benign))
        res["hn_fp"] = hn_fp
        
        # Full credential recall (val+test+ood)
        all_cred = [r for r in all_recs if "CREDENTIAL_REQUEST" in r.get("threat_vectors", [])]
        X_ac = prepare(all_cred)
        y_ac = np.array([LABEL_MAP[r["security_label"]] for r in all_cred])
        ac_probs = clf.predict_proba(X_ac)
        ac_preds = predict_with_threshold(ac_probs, best_t)
        ac_cm = confusion_matrix(y_ac, ac_preds, labels=[0, 1, 2])
        res["cred_recall"] = ac_cm[2][2] / max(1, sum(ac_cm[2]))
        
        res["size_kb"] = (len(pickle.dumps(clf)) + len(pickle.dumps(tw)) + 
                         len(pickle.dumps(tc)) + len(pickle.dumps(sc))) / 1024
        
        print(f"  TEST: FPR={res['test']['fpr']:.4f} Rec={res['test']['recall']:.4f} F1={res['test']['f1']:.4f}")
        print(f"  OOD:  FPR={res['ood']['fpr']:.4f} Rec={res['ood']['recall']:.4f}")
        print(f"  HN:   FPR={res['hn_fpr']:.4f} ({res['hn_fp']}/{len(hn_benign)})")
        print(f"  CRED: {res['cred_recall']:.4f}")
        print(f"  Size: {res['size_kb']:.0f} KB")
        
        return res, clf, tw, tc, sc
    
    results = {}
    artifacts = {}
    
    # CHALLENGER I: depth=7, iter=300 (same as F but with more credential data)
    print("\n" + "=" * 60)
    print("CHALLENGER I: d7/i300 + Large Credential Expansion")
    print("=" * 60)
    r, c, tw, tc, sc = train_eval("CHALLENGER_I", combined, 7, 300)
    results["CHALLENGER_I"] = r; artifacts["CHALLENGER_I"] = (c, tw, tc, sc)
    
    # CHALLENGER J: depth=7, iter=400
    print("\n" + "=" * 60)
    print("CHALLENGER J: d7/i400")
    print("=" * 60)
    r, c, tw, tc, sc = train_eval("CHALLENGER_J", combined, 7, 400)
    results["CHALLENGER_J"] = r; artifacts["CHALLENGER_J"] = (c, tw, tc, sc)
    
    # CHALLENGER K: depth=8, iter=300
    print("\n" + "=" * 60)
    print("CHALLENGER K: d8/i300")
    print("=" * 60)
    r, c, tw, tc, sc = train_eval("CHALLENGER_K", combined, 8, 300)
    results["CHALLENGER_K"] = r; artifacts["CHALLENGER_K"] = (c, tw, tc, sc)
    
    # CHALLENGER L: depth=7, iter=300, lr=0.05 (lower learning rate, more conservative)
    print("\n" + "=" * 60)
    print("CHALLENGER L: d7/i300, lr=0.05")
    print("=" * 60)
    r, c, tw, tc, sc = train_eval("CHALLENGER_L", combined, 7, 300, lr=0.05)
    results["CHALLENGER_L"] = r; artifacts["CHALLENGER_L"] = (c, tw, tc, sc)
    
    # CHALLENGER M: depth=7, iter=500, lr=0.05
    print("\n" + "=" * 60)
    print("CHALLENGER M: d7/i500, lr=0.05")
    print("=" * 60)
    r, c, tw, tc, sc = train_eval("CHALLENGER_M", combined, 7, 500, lr=0.05)
    results["CHALLENGER_M"] = r; artifacts["CHALLENGER_M"] = (c, tw, tc, sc)
    
    # ============================================================
    # COMPARISON
    # ============================================================
    print("\n" + "=" * 70)
    print("ROUND 3 COMPARISON")
    print("=" * 70)
    
    print(f"\n  {'Model':>20s} | {'T':>5} | {'T_FPR':>7} | {'T_Rec':>7} | {'O_FPR':>7} | {'O_Rec':>7} | {'HN':>4} | {'Cred':>7} | {'Size':>6} | Pass")
    
    best_name = None
    best_f1 = -1
    
    for name, res in results.items():
        t_fpr = res['test']['fpr']
        t_rec = res['test']['recall']
        o_fpr = res['ood']['fpr']
        o_rec = res['ood']['recall']
        h = res['hn_fp']
        cr = res.get('cred_recall', 0)
        sz = res.get('size_kb', 0)
        
        passes = (t_fpr <= 0.01 and t_rec >= 0.80 and o_fpr <= 0.01 and o_rec >= 0.80 and 
                 h == 0 and cr >= 0.80)
        
        if passes and res['test']['f1'] > best_f1:
            best_f1 = res['test']['f1']
            best_name = name
        
        marker = " ***" if name == best_name else ""
        print(f"  {name:>20s} | {res['threshold']:5.2f} | {t_fpr:7.4f} | {t_rec:7.4f} | {o_fpr:7.4f} | {o_rec:7.4f} | {h:4d} | {cr:7.4f} | {sz:5.0f}K | {'Y' if passes else 'N'}{marker}")
    
    # SAVE
    print("\n" + "=" * 70)
    if best_name:
        print(f"PROMOTED: {best_name}")
        c, tw, tc, sc = artifacts[best_name]
        for obj, suffix in [(c, "model"), (tw, "tfidf_word"), (tc, "tfidf_char"), (sc, "scaler")]:
            with open(ROOT / f"challenger_{suffix}_{best_name}.pkl", "wb") as f:
                pickle.dump(obj, f)
        print(f"Saved artifacts for {best_name}")
    else:
        print("NO CHALLENGER PASSES ALL GATES")
    
    with open(ROOT / "model_training" / "autonomous_optimization_results.json", "a") as f:
        for name, res in results.items():
            entry = {
                "experiment_id": name, "round": 3,
                "timestamp": datetime.now(timezone.utc).isoformat(),
                "decision": "PROMOTED" if name == best_name else "REJECTED",
                "threshold": res["threshold"],
                "test_fpr": res["test"]["fpr"], "test_recall": res["test"]["recall"],
                "test_f1": res["test"]["f1"],
                "ood_fpr": res["ood"]["fpr"], "ood_recall": res["ood"]["recall"],
                "hn_fpr": res["hn_fpr"], "hn_fp": res["hn_fp"],
                "credential_recall": res.get("cred_recall", None),
                "size_kb": res.get("size_kb", 0)
            }
            f.write(json.dumps(entry) + "\n")
    
    print("=" * 70)
    print("ROUND 3 COMPLETE")
    print("=" * 70)

if __name__ == "__main__":
    main()
