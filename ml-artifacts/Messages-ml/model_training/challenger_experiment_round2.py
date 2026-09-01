"""
CHALLENGER EXPERIMENT ROUND 2: Fix Credential Recall while preserving HN FPR
==============================================================================

CHALLENGER_D (t=0.90) passes HN gates but credential recall drops to 74.5%.
OTP disclosure recall also drops to 50%.

Root cause: The high threshold (0.90) needed to suppress MSEDCL FPs also
suppresses many credential request detections. 

Strategy:
1. Add more diverse credential theft training examples
2. Add OTP disclosure scam training examples  
3. Combine with utility expansion data
4. Test multiple architectures:
   - HistGBM with deeper trees
   - HistGBM with more iterations
   - ExtraTrees as alternative
   - Two separate TF-IDF + feature configs
5. Sweep thresholds and find a point where ALL gates pass simultaneously
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
from sklearn.ensemble import HistGradientBoostingClassifier, ExtraTreesClassifier
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

def generate_credential_expansion():
    """Generate diverse credential theft and OTP scam examples."""
    records = []
    SRC = "SRC_CREDENTIAL_EXPANSION_V1"
    
    # === CREDENTIAL PHISHING - diverse institutions and patterns ===
    credential_malicious = [
        # Bank credential phishing
        "SBI: Your account has been suspended due to unusual activity. Click http://sbi-reactivate.xyz to verify identity.",
        "PNB: Net banking access restricted. Update your credentials at http://pnb-secure.in within 4 hours.",
        "BOI: Your debit card is blocked. Submit PAN and Aadhaar at http://boi-verify.xyz to unblock.",
        "Canara Bank: Account KYC update mandatory by 30-Sep. Visit http://canara-kyc.com or face deactivation.",
        "IDBI: Unauthorized transaction of Rs.25,000 detected. Verify at http://idbi-alert.xyz immediately.",
        "Axis Bank: Your mobile banking will be deactivated. Update details at http://axis-mb.xyz.",
        "Kotak: CIBIL score update required. Submit documents at http://kotak-cibil.xyz.",
        "UCO Bank: Your account is flagged for money laundering. Share KYC documents at 9876543210 or face arrest.",
        "Federal Bank: Debit card expiring. Update card details at http://fed-cardupdate.xyz before tomorrow.",
        "IndusInd Bank: Login blocked due to wrong password. Reset at http://indusind-reset.xyz.",
        
        # UPI/Payment credential theft
        "NPCI: Your UPI ID has been compromised. Reset PIN at http://upi-reset.xyz to secure your account.",
        "PhonePe: Account suspended. Verify identity by sharing UPI PIN and linked bank details at 8765432109.",
        "GPay: Suspicious transaction detected. Click http://gpay-verify.xyz and enter UPI PIN to investigate.",
        "Paytm: KYC expired. Your wallet will be frozen in 2 hours. Update at http://paytm-kyc.xyz.",
        "BHIM: UPI transaction limit exceeded. Share Aadhaar for enhanced KYC at http://bhim-upgrade.xyz.",
        
        # Government/institutional credential theft
        "IRCTC: Your booking failed. Re-enter card details at http://irctc-payment.xyz to complete reservation.",
        "DigiLocker: Account verification pending. Upload Aadhaar and PAN at http://digilocker-verify.xyz.",
        "EPFO: PF withdrawal approved. Share bank account details and Aadhaar at http://epfo-claim.xyz.",
        "Passport Seva: Appointment confirmed. Complete payment at http://passport-fee.xyz using credit card.",
        "LPG Subsidy: Rs.2,500 subsidy credited. Enter bank details at http://lpg-subsidy.xyz to claim.",
        
        # Insurance credential theft
        "LIC: Bonus of Rs.50,000 on your policy. Click http://lic-bonus.xyz and enter policy details.",
        "Star Health: Cashless claim approved. Share hospital ID and bank details at http://star-claim.xyz.",
        
        # Telecom credential theft
        "Jio: Your SIM will be blocked in 24 hours. Complete e-KYC at http://jio-ekyc.xyz with Aadhaar.",
        "Airtel: Number ported without consent. Verify at http://airtel-verify.xyz or lose your number.",
        "Vi: Mobile data service suspended. Re-verify at http://vi-verify.xyz using OTP.",
    ]
    
    # === OTP DISCLOSURE SCAMS ===
    otp_malicious = [
        "Your OTP for bank verification is being sent. Reply with the OTP code to complete the process.",
        "Amazon Customer Care: Refund of Rs.2,450 for cancelled order initiated. Reply with OTP received to credit funds.",
        "SBI: Transaction of Rs.15,000 initiated from your account. Reply with OTP to cancel this transaction.",
        "ICICI: Suspicious login detected. Share the OTP received on your phone to block unauthorized access.",
        "PhonePe: Refund of Rs.500 processing. Enter OTP sent to your number to receive immediately.",
        "Flipkart: Your cashback of Rs.750 is ready. Share OTP to credit to your wallet.",
        "Paytm: Excess payment detected. Reply with OTP to get Rs.1,200 refund.",
        "GPay: Security alert. Send OTP to verify its you and prevent account suspension.",
        "PNB: Loan pre-approval ready. Share OTP received to confirm acceptance.",
        "HDFC: Credit card upgrade available. Reply with OTP to activate premium card benefits.",
        "Your electricity account refund Rs.800 approved. Reply with OTP received on this number.",
        "Delivery failed for your order. Share OTP with delivery agent at 9876543210 to reschedule.",
        "Insurance claim Rs.25,000 approved. Forward OTP from your bank to process payment.",
        "Tax refund Rs.12,000 ready. Enter the OTP sent to complete verification at http://tax-otp.xyz.",
        "Job offer confirmed. Share OTP from your bank account to verify salary credit details.",
    ]

    # === BENIGN OTP / SECURITY MESSAGES ===
    otp_benign = [
        "SBI: OTP 458921 for net banking login. Do NOT share this OTP with anyone. Valid for 5 minutes.",
        "ICICI: Your OTP is 673421 for fund transfer. Never share OTP via phone or SMS. Call 1860-120-7777 if not you.",
        "PNB: 891234 is your OTP for UPI registration. Do not share. If unauthorized, call 1800-180-2222.",
        "HDFC: OTP 234567 for online shopping. Never share OTP with anyone including bank staff.",
        "Axis Bank: Your OTP 567890 for adding beneficiary. If you did not initiate, call 1860-419-5555.",
        "Kotak: 345678 is OTP for mobile banking login. Bank never asks for OTP. Report fraud at 1860-266-2666.",
        "Amazon: Your OTP is 4567 for order verification. Do not share this code with delivery agents.",
        "Flipkart: Verification code 8901 for your purchase. Valid for 10 minutes. Do not share.",
        "Google: Your verification code is 123456. Do not share this code with anyone.",
        "PhonePe: OTP 789012 for UPI transaction. Never share your OTP or UPI PIN with anyone.",
    ]
    
    # === BENIGN CREDENTIAL/KYC MESSAGES ===
    kyc_benign = [
        "SBI: Your KYC is due for renewal. Please visit your nearest SBI branch with original Aadhaar and PAN card.",
        "ICICI: Complete your video KYC from the ICICI iMobile app. No documents needed. Takes 2 minutes.",
        "HDFC: KYC verification completed successfully. Your account services will continue uninterrupted.",
        "PNB: Re-KYC drive ends 30-Sep. Visit nearest PNB branch with Aadhaar. Contact 1800-180-2222 for queries.",
        "Axis Bank: Your annual KYC update is pending. Schedule appointment at nearest branch via Axis Mobile app.",
        "Kotak: KYC documents received and verified. Thank you for prompt submission.",
        "UIDAI: Your Aadhaar card has been dispatched via Speed Post. Track at indiapost.gov.in using consignment no.",
        "Income Tax: E-verification of ITR for AY 2026-27 completed successfully via net banking. No action needed.",
        "EPFO: Your PF account updated with latest contribution. Check balance at epfindia.gov.in.",
        "DigiLocker: Your Aadhaar and PAN have been linked successfully. Access documents at digilocker.gov.in.",
    ]
    
    for text in credential_malicious:
        records.append(make_record(text, "MALICIOUS", SRC, "CREDENTIAL_PHISHING"))
    for text in otp_malicious:
        records.append(make_record(text, "MALICIOUS", SRC, "OTP_DISCLOSURE_REQUEST"))
    for text in otp_benign:
        records.append(make_record(text, "BENIGN", SRC, "LEGITIMATE_OTP"))
    for text in kyc_benign:
        records.append(make_record(text, "BENIGN", SRC, "LEGITIMATE_KYC"))
    
    return records


def full_evaluate(clf, tfidf_word, tfidf_char, scaler, cfg, threshold, val_recs, test_recs, ood_recs, hn_benign, all_recs, name):
    """Full evaluation pipeline."""
    results = {"threshold": threshold, "name": name}
    
    for split_name, recs in [("val", val_recs), ("test", test_recs), ("ood", ood_recs)]:
        texts = [r.get("raw_text", "") for r in recs]
        y = np.array([LABEL_MAP[r["security_label"]] for r in recs])
        Xw = tfidf_word.transform(texts).toarray()
        Xc = tfidf_char.transform(texts).toarray()
        Xd = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in recs])
        X = scaler.transform(np.hstack((Xd, Xw, Xc)))
        probs = clf.predict_proba(X)
        preds = predict_with_threshold(probs, threshold)
        cm = confusion_matrix(y, preds, labels=[0, 1, 2])
        ben = max(1, sum(cm[0])); mal = max(1, sum(cm[2]))
        results[split_name] = {
            "fpr": (cm[0][1]+cm[0][2])/ben, "recall": cm[2][2]/mal,
            "f1": f1_score(y, preds, average="macro"),
            "cm": cm.tolist()
        }
    
    # HN
    hn_texts = [r.get("raw_text", "") for r in hn_benign]
    Xhw = tfidf_word.transform(hn_texts).toarray()
    Xhc = tfidf_char.transform(hn_texts).toarray()
    Xhd = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in hn_benign])
    Xh = scaler.transform(np.hstack((Xhd, Xhw, Xhc)))
    hn_probs = clf.predict_proba(Xh)
    hn_preds = predict_with_threshold(hn_probs, threshold)
    hn_fp = sum(1 for p in hn_preds if p > 0)
    results["hn_fpr"] = hn_fp / max(1, len(hn_benign))
    results["hn_fp"] = hn_fp
    
    # Credential recall
    cred = [r for r in all_recs if "CREDENTIAL_REQUEST" in r.get("threat_vectors", [])]
    if cred:
        cr_texts = [r.get("raw_text", "") for r in cred]
        y_cr = np.array([LABEL_MAP[r["security_label"]] for r in cred])
        Xcw = tfidf_word.transform(cr_texts).toarray()
        Xcc = tfidf_char.transform(cr_texts).toarray()
        Xcd = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in cred])
        Xc_full = scaler.transform(np.hstack((Xcd, Xcw, Xcc)))
        cr_probs = clf.predict_proba(Xc_full)
        cr_preds = predict_with_threshold(cr_probs, threshold)
        cr_cm = confusion_matrix(y_cr, cr_preds, labels=[0, 1, 2])
        cr_mal = max(1, sum(cr_cm[2]))
        results["cred_recall"] = cr_cm[2][2] / cr_mal
    
    # Size
    results["size_kb"] = (len(pickle.dumps(clf)) + len(pickle.dumps(tfidf_word)) + 
                          len(pickle.dumps(tfidf_char)) + len(pickle.dumps(scaler))) / 1024
    
    return results


def train_challenger(name, train_recs, cfg, val_recs, test_recs, ood_recs, hn_benign, all_recs,
                     max_depth=5, max_iter=200, word_features=1500, char_features=500):
    """Train a single challenger with given hyperparameters."""
    print(f"\n  Training: {name} (depth={max_depth}, iter={max_iter}, word={word_features}, char={char_features})")
    
    texts = [r.get("raw_text", "") for r in train_recs]
    y = np.array([LABEL_MAP[r["security_label"]] for r in train_recs])
    
    tfidf_word = TfidfVectorizer(max_features=word_features, stop_words="english", ngram_range=(1, 2))
    Xw = tfidf_word.fit_transform(texts).toarray()
    tfidf_char = TfidfVectorizer(max_features=char_features, ngram_range=(3, 5), analyzer='char_wb')
    Xc = tfidf_char.fit_transform(texts).toarray()
    Xd = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in train_recs])
    X = np.hstack((Xd, Xw, Xc))
    
    scaler = StandardScaler()
    X = scaler.fit_transform(X)
    
    clf = HistGradientBoostingClassifier(random_state=42, max_depth=max_depth, max_iter=max_iter, class_weight='balanced')
    clf.fit(X, y)
    
    # Threshold sweep on VAL
    val_texts = [r.get("raw_text", "") for r in val_recs]
    y_val = np.array([LABEL_MAP[r["security_label"]] for r in val_recs])
    Xvw = tfidf_word.transform(val_texts).toarray()
    Xvc = tfidf_char.transform(val_texts).toarray()
    Xvd = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in val_recs])
    Xv = scaler.transform(np.hstack((Xvd, Xvw, Xvc)))
    val_probs = clf.predict_proba(Xv)
    
    hn_texts = [r.get("raw_text", "") for r in hn_benign]
    Xhw = tfidf_word.transform(hn_texts).toarray()
    Xhc = tfidf_char.transform(hn_texts).toarray()
    Xhd = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in hn_benign])
    Xh = scaler.transform(np.hstack((Xhd, Xhw, Xhc)))
    hn_probs = clf.predict_proba(Xh)
    
    # Credential recall per threshold
    cred = [r for r in val_recs if "CREDENTIAL_REQUEST" in r.get("threat_vectors", [])]
    cr_probs = None
    if cred:
        cr_texts = [r.get("raw_text", "") for r in cred]
        y_cr = np.array([LABEL_MAP[r["security_label"]] for r in cred])
        Xcw = tfidf_word.transform(cr_texts).toarray()
        Xcc = tfidf_char.transform(cr_texts).toarray()
        Xcd = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in cred])
        Xc_full = scaler.transform(np.hstack((Xcd, Xcw, Xcc)))
        cr_probs = clf.predict_proba(Xc_full)
    
    print(f"  {'T':>6} | {'FPR':>8} | {'Rec':>8} | {'F1':>8} | {'HN_FP':>5} | {'Cred_R':>8}")
    
    best_t = None
    best_f1 = -1
    
    for t in np.arange(0.50, 0.96, 0.05):
        t = round(t, 2)
        vp = predict_with_threshold(val_probs, t)
        cm = confusion_matrix(y_val, vp, labels=[0, 1, 2])
        fpr = (cm[0][1]+cm[0][2]) / max(1, sum(cm[0]))
        rec = cm[2][2] / max(1, sum(cm[2]))
        f1 = f1_score(y_val, vp, average="macro")
        
        hp = predict_with_threshold(hn_probs, t)
        hn_fp = sum(1 for p in hp if p > 0)
        
        cr_rec = 0
        if cr_probs is not None:
            cp = predict_with_threshold(cr_probs, t)
            cr_cm = confusion_matrix(y_cr, cp, labels=[0, 1, 2])
            cr_rec = cr_cm[2][2] / max(1, sum(cr_cm[2]))
        
        # All gates must pass on VAL
        all_pass = (fpr <= 0.015 and rec >= 0.80 and hn_fp == 0 and cr_rec >= 0.75)
        
        if all_pass and f1 > best_f1:
            best_f1 = f1
            best_t = t
        
        marker = " <--" if t == best_t and best_t is not None else ""
        print(f"  {t:6.2f} | {fpr:8.4f} | {rec:8.4f} | {f1:8.4f} | {hn_fp:5d} | {cr_rec:8.4f}{marker}")
    
    if best_t is None:
        print(f"  WARNING: No threshold passes all gates on VAL. Picking best HN-passing t...")
        # Fall back: pick threshold with best F1 where HN=0
        for t in np.arange(0.95, 0.49, -0.05):
            t = round(t, 2)
            hp = predict_with_threshold(hn_probs, t)
            hn_fp = sum(1 for p in hp if p > 0)
            if hn_fp == 0:
                vp = predict_with_threshold(val_probs, t)
                cm = confusion_matrix(y_val, vp, labels=[0, 1, 2])
                fpr = (cm[0][1]+cm[0][2]) / max(1, sum(cm[0]))
                if fpr <= 0.015:
                    best_t = t
                    break
        if best_t is None:
            best_t = 0.95
    
    print(f"  Selected: t={best_t}")
    
    # Full eval at best threshold
    res = full_evaluate(clf, tfidf_word, tfidf_char, scaler, cfg, best_t,
                       val_recs, test_recs, ood_recs, hn_benign, all_recs, name)
    
    print(f"  TEST: FPR={res['test']['fpr']:.4f} Rec={res['test']['recall']:.4f}")
    print(f"  OOD:  FPR={res['ood']['fpr']:.4f} Rec={res['ood']['recall']:.4f}")
    print(f"  HN:   FPR={res['hn_fpr']:.4f} ({res['hn_fp']}/{len(hn_benign)})")
    print(f"  CRED: {res.get('cred_recall', 'N/A')}")
    print(f"  Size: {res['size_kb']:.0f} KB")
    
    return res, clf, tfidf_word, tfidf_char, scaler


def main():
    print("=" * 70)
    print("CHALLENGER EXPERIMENT ROUND 2: CREDENTIAL + UTILITY")
    print(f"Timestamp: {datetime.now(timezone.utc).isoformat()}")
    print("=" * 70)
    
    cfg = FeatureConfig()
    val_recs = load_dataset("val.jsonl")
    test_recs = load_dataset("test.jsonl")
    ood_recs = load_dataset("ood.jsonl")
    all_recs = val_recs + test_recs + ood_recs
    
    hn_benign = [r for r in val_recs if r.get("source_id") == "SRC_CURATED_HARD_NEGATIVES_V1" and r.get("security_label") == "BENIGN"]
    
    # Load base utility expansion (from Round 1)
    base_recs = load_dataset("train_expanded_v4_3x.jsonl")
    print(f"Base: {len(base_recs)} records")
    
    # Generate credential expansion
    cred_records = generate_credential_expansion()
    print(f"Credential expansion: {len(cred_records)} new records")
    
    # Combined dataset
    combined = base_recs + cred_records * 3  # 3x replication for credential data too
    print(f"Combined: {len(combined)} records")
    
    # Save as v5
    v5_path = ROOT / "data" / "processed" / "train_expanded_v5.jsonl"
    with open(v5_path, "w", encoding="utf-8") as f:
        for r in combined:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(f"Saved: {v5_path} ({len(combined)} records)")
    
    results = {}
    all_artifacts = {}
    
    # ============================================================
    # CHALLENGER E: depth=5, iter=200 (same as champion config)
    # ============================================================
    print("\n" + "=" * 60)
    print("CHALLENGER E: HistGBM d5/i200 + Utility + Credential")
    print("=" * 60)
    res_e, clf_e, tw_e, tc_e, sc_e = train_challenger(
        "CHALLENGER_E", combined, cfg, val_recs, test_recs, ood_recs, hn_benign, all_recs)
    results["CHALLENGER_E"] = res_e
    all_artifacts["CHALLENGER_E"] = (clf_e, tw_e, tc_e, sc_e)
    
    # ============================================================
    # CHALLENGER F: depth=7, iter=300 (deeper model)
    # ============================================================
    print("\n" + "=" * 60)
    print("CHALLENGER F: HistGBM d7/i300 + Utility + Credential")
    print("=" * 60)
    res_f, clf_f, tw_f, tc_f, sc_f = train_challenger(
        "CHALLENGER_F", combined, cfg, val_recs, test_recs, ood_recs, hn_benign, all_recs,
        max_depth=7, max_iter=300)
    results["CHALLENGER_F"] = res_f
    all_artifacts["CHALLENGER_F"] = (clf_f, tw_f, tc_f, sc_f)
    
    # ============================================================
    # CHALLENGER G: depth=5, iter=300, more word features
    # ============================================================
    print("\n" + "=" * 60)
    print("CHALLENGER G: HistGBM d5/i300 + 2500 word + 750 char")
    print("=" * 60)
    res_g, clf_g, tw_g, tc_g, sc_g = train_challenger(
        "CHALLENGER_G", combined, cfg, val_recs, test_recs, ood_recs, hn_benign, all_recs,
        max_depth=5, max_iter=300, word_features=2500, char_features=750)
    results["CHALLENGER_G"] = res_g
    all_artifacts["CHALLENGER_G"] = (clf_g, tw_g, tc_g, sc_g)
    
    # ============================================================
    # CHALLENGER H: depth=6, iter=250
    # ============================================================
    print("\n" + "=" * 60)
    print("CHALLENGER H: HistGBM d6/i250")
    print("=" * 60)
    res_h, clf_h, tw_h, tc_h, sc_h = train_challenger(
        "CHALLENGER_H", combined, cfg, val_recs, test_recs, ood_recs, hn_benign, all_recs,
        max_depth=6, max_iter=250)
    results["CHALLENGER_H"] = res_h
    all_artifacts["CHALLENGER_H"] = (clf_h, tw_h, tc_h, sc_h)
    
    # ============================================================
    # COMPARISON TABLE
    # ============================================================
    print("\n" + "=" * 70)
    print("ROUND 2 RESULTS COMPARISON")
    print("=" * 70)
    
    champion = {"test_fpr": 0.0019, "test_rec": 0.8432, "ood_fpr": 0.0, "ood_rec": 0.8375, 
                "hn_fpr": 0.2439, "cred": 0.7063, "size": 1630}
    chall_d = {"test_fpr": 0.0, "test_rec": 0.8625, "ood_fpr": 0.0, "ood_rec": 0.8476,
               "hn_fpr": 0.0, "cred": 0.7454, "size": 1717}
    
    print(f"\n  {'Model':>25s} | {'T FPR':>7} | {'T Rec':>7} | {'O FPR':>7} | {'O Rec':>7} | {'HN FPR':>7} | {'Cred':>7} | {'Size':>6} | {'PASS':>4}")
    print(f"  {'CHAMPION_V1':>25s} | {champion['test_fpr']:7.4f} | {champion['test_rec']:7.4f} | {champion['ood_fpr']:7.4f} | {champion['ood_rec']:7.4f} | {champion['hn_fpr']:7.4f} | {champion['cred']:7.4f} | {champion['size']:5d}K | {'NO':>4}")
    print(f"  {'CHALLENGER_D':>25s} | {chall_d['test_fpr']:7.4f} | {chall_d['test_rec']:7.4f} | {chall_d['ood_fpr']:7.4f} | {chall_d['ood_rec']:7.4f} | {chall_d['hn_fpr']:7.4f} | {chall_d['cred']:7.4f} | {chall_d['size']:5d}K | {'NO':>4}")
    
    best_name = None
    best_f1 = -1
    
    for name, res in results.items():
        t_fpr = res['test']['fpr']
        t_rec = res['test']['recall']
        o_fpr = res['ood']['fpr']
        o_rec = res['ood']['recall']
        h_fpr = res['hn_fpr']
        cr = res.get('cred_recall', 0)
        sz = res.get('size_kb', 0)
        
        passes = (t_fpr <= 0.01 and t_rec >= 0.80 and o_fpr <= 0.01 and o_rec >= 0.80 and 
                 h_fpr <= 0.01 and cr >= 0.80)
        pass_str = "YES" if passes else "NO"
        
        if passes and res['test'].get('f1', 0) > best_f1:
            best_f1 = res['test']['f1']
            best_name = name
        
        marker = " ***" if name == best_name else ""
        print(f"  {name:>25s} | {t_fpr:7.4f} | {t_rec:7.4f} | {o_fpr:7.4f} | {o_rec:7.4f} | {h_fpr:7.4f} | {cr:7.4f} | {sz:5.0f}K | {pass_str:>4}{marker}")
    
    # ============================================================
    # SAVE BEST CHALLENGER
    # ============================================================
    print("\n" + "=" * 70)
    if best_name:
        print(f"PROMOTED: {best_name} passes ALL gates including credential recall!")
        clf, tw, tc, sc = all_artifacts[best_name]
        with open(ROOT / f"challenger_model_{best_name}.pkl", "wb") as f:
            pickle.dump(clf, f)
        with open(ROOT / f"challenger_tfidf_word_{best_name}.pkl", "wb") as f:
            pickle.dump(tw, f)
        with open(ROOT / f"challenger_tfidf_char_{best_name}.pkl", "wb") as f:
            pickle.dump(tc, f)
        with open(ROOT / f"challenger_scaler_{best_name}.pkl", "wb") as f:
            pickle.dump(sc, f)
        print(f"Saved artifacts for {best_name}")
    else:
        print("NO CHALLENGER PASSES ALL GATES")
        print("Need to continue experimentation")
    
    # Append to registry
    with open(ROOT / "model_training" / "autonomous_optimization_results.json", "a") as f:
        for name, res in results.items():
            entry = {
                "experiment_id": name, "round": 2,
                "timestamp": datetime.now(timezone.utc).isoformat(),
                "decision": "PROMOTED" if name == best_name else "REJECTED",
                "threshold": res["threshold"],
                "test_fpr": res["test"]["fpr"], "test_recall": res["test"]["recall"],
                "ood_fpr": res["ood"]["fpr"], "ood_recall": res["ood"]["recall"],
                "hn_fpr": res["hn_fpr"],
                "credential_recall": res.get("cred_recall", None),
                "size_kb": res.get("size_kb", 0)
            }
            f.write(json.dumps(entry) + "\n")
    
    print("=" * 70)
    print("ROUND 2 COMPLETE")
    print("=" * 70)

if __name__ == "__main__":
    main()
