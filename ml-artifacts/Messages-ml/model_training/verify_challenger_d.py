"""
INDEPENDENT VERIFICATION OF CHALLENGER_D_charngram_3x
+ ADVERSARIAL GENERALIZATION TEST
+ CREDENTIAL RECALL CHECK
+ NOVEL UTILITY MESSAGES TEST

This script determines whether CHALLENGER_D genuinely learned the utility 
distinction or just shifted the threshold. If it fails generalization,
we continue experimenting.
"""

import json, pickle, sys, time
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

def evaluate_messages(clf, tfidf_word, tfidf_char, scaler, cfg, messages, labels, threshold, tag=""):
    """Evaluate a list of messages. Returns predictions and probabilities."""
    texts = [m for m in messages]
    X_word = tfidf_word.transform(texts).toarray()
    X_char = tfidf_char.transform(texts).toarray()
    X_det = np.array([extract_feature_vector(t, None, cfg) for t in texts])
    X = scaler.transform(np.hstack((X_det, X_word, X_char)))
    probs = clf.predict_proba(X)
    preds = predict_with_threshold(probs, threshold)
    
    print(f"\n  --- {tag} ---")
    n_correct = 0
    for i, (msg, true_label, pred, prob) in enumerate(zip(messages, labels, preds, probs)):
        p_nb = prob[1] + prob[2]
        pred_name = LABEL_NAMES[pred]
        correct = (pred == LABEL_MAP.get(true_label, -1))
        n_correct += int(correct)
        marker = "OK" if correct else "MISS"
        print(f"  [{marker}] true={true_label:12s} pred={pred_name:12s} P(nb)={p_nb:.3f} | {msg[:100]}")
    
    accuracy = n_correct / max(1, len(messages))
    print(f"  Accuracy: {n_correct}/{len(messages)} = {accuracy:.2%}")
    return preds, probs, accuracy


def main():
    print("=" * 70)
    print("INDEPENDENT VERIFICATION: CHALLENGER_D_charngram_3x")
    print(f"Timestamp: {datetime.now(timezone.utc).isoformat()}")
    print("=" * 70)
    
    cfg = FeatureConfig()
    
    # Load challenger D artifacts
    print("Loading CHALLENGER_D artifacts...")
    with open(ROOT / "challenger_model_CHALLENGER_D_charngram_3x.pkl", "rb") as f:
        clf = pickle.load(f)
    with open(ROOT / "challenger_tfidf_word_CHALLENGER_D_charngram_3x.pkl", "rb") as f:
        tfidf_word = pickle.load(f)
    with open(ROOT / "challenger_tfidf_char_CHALLENGER_D_charngram_3x.pkl", "rb") as f:
        tfidf_char = pickle.load(f)
    with open(ROOT / "challenger_scaler_CHALLENGER_D_charngram_3x.pkl", "rb") as f:
        scaler = pickle.load(f)
    
    THRESHOLD = 0.90
    
    # ================================================================
    # TEST 1: Reproduce reported metrics on TEST/OOD/HN
    # ================================================================
    print("\n" + "=" * 60)
    print("TEST 1: REPRODUCTION OF REPORTED METRICS")
    print("=" * 60)
    
    for split_name in ["val", "test", "ood"]:
        recs = load_dataset(f"{split_name}.jsonl")
        texts = [r.get("raw_text", "") for r in recs]
        y = np.array([LABEL_MAP[r["security_label"]] for r in recs])
        X_w = tfidf_word.transform(texts).toarray()
        X_c = tfidf_char.transform(texts).toarray()
        X_d = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in recs])
        X = scaler.transform(np.hstack((X_d, X_w, X_c)))
        probs = clf.predict_proba(X)
        preds = predict_with_threshold(probs, THRESHOLD)
        cm = confusion_matrix(y, preds, labels=[0, 1, 2])
        ben = max(1, sum(cm[0])); mal = max(1, sum(cm[2]))
        fpr = (cm[0][1] + cm[0][2]) / ben
        rec = cm[2][2] / mal
        f1 = f1_score(y, preds, average="macro")
        print(f"  {split_name.upper()}: FPR={fpr:.4f} ({cm[0][1]+cm[0][2]}/{ben}) Recall={rec:.4f} ({cm[2][2]}/{mal}) F1={f1:.4f}")
    
    # HN
    val_recs = load_dataset("val.jsonl")
    hn = [r for r in val_recs if r.get("source_id") == "SRC_CURATED_HARD_NEGATIVES_V1" and r.get("security_label") == "BENIGN"]
    hn_texts = [r.get("raw_text", "") for r in hn]
    X_hw = tfidf_word.transform(hn_texts).toarray()
    X_hc = tfidf_char.transform(hn_texts).toarray()
    X_hd = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in hn])
    X_h = scaler.transform(np.hstack((X_hd, X_hw, X_hc)))
    hn_probs = clf.predict_proba(X_h)
    hn_preds = predict_with_threshold(hn_probs, THRESHOLD)
    hn_fp = sum(1 for p in hn_preds if p > 0)
    print(f"  HN: FPR={hn_fp/max(1,len(hn)):.4f} ({hn_fp}/{len(hn)})")
    
    # ================================================================
    # TEST 2: CREDENTIAL REQUEST RECALL
    # ================================================================
    print("\n" + "=" * 60)
    print("TEST 2: CREDENTIAL REQUEST RECALL")
    print("=" * 60)
    
    all_recs = load_dataset("val.jsonl") + load_dataset("test.jsonl") + load_dataset("ood.jsonl")
    cred = [r for r in all_recs if "CREDENTIAL_REQUEST" in r.get("threat_vectors", [])]
    if cred:
        cr_texts = [r.get("raw_text", "") for r in cred]
        y_cr = np.array([LABEL_MAP[r["security_label"]] for r in cred])
        X_cr_w = tfidf_word.transform(cr_texts).toarray()
        X_cr_c = tfidf_char.transform(cr_texts).toarray()
        X_cr_d = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in cred])
        X_cr = scaler.transform(np.hstack((X_cr_d, X_cr_w, X_cr_c)))
        cr_probs = clf.predict_proba(X_cr)
        cr_preds = predict_with_threshold(cr_probs, THRESHOLD)
        cr_cm = confusion_matrix(y_cr, cr_preds, labels=[0, 1, 2])
        cr_mal = max(1, sum(cr_cm[2]))
        cr_rec = cr_cm[2][2] / cr_mal
        print(f"  CREDENTIAL_REQUEST: Recall={cr_rec:.4f} ({cr_cm[2][2]}/{cr_mal}) N={len(cred)}")
        print(f"  Gate: >= 0.80? {'PASS' if cr_rec >= 0.80 else 'FAIL'}")
    
    # Other threat vectors
    for tv in ["BANK_KYC_SUSPENSION", "DELIVERY_SCAM", "APK_MALWARE_DROPPER", 
                "OTP_DISCLOSURE_REQUEST", "ELECTRICITY_DISCONNECTION_SCAM"]:
        tv_recs = [r for r in all_recs if tv in r.get("threat_vectors", [])]
        if len(tv_recs) >= 5:
            tv_texts = [r.get("raw_text", "") for r in tv_recs]
            y_tv = np.array([LABEL_MAP[r["security_label"]] for r in tv_recs])
            X_tv_w = tfidf_word.transform(tv_texts).toarray()
            X_tv_c = tfidf_char.transform(tv_texts).toarray()
            X_tv_d = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in tv_recs])
            X_tv = scaler.transform(np.hstack((X_tv_d, X_tv_w, X_tv_c)))
            tv_probs = clf.predict_proba(X_tv)
            tv_preds = predict_with_threshold(tv_probs, THRESHOLD)
            tv_cm = confusion_matrix(y_tv, tv_preds, labels=[0, 1, 2])
            tv_mal = max(1, sum(tv_cm[2]))
            tv_rec = tv_cm[2][2] / tv_mal
            print(f"  {tv}: Recall={tv_rec:.4f} ({tv_cm[2][2]}/{tv_mal})")
    
    # ================================================================
    # TEST 3: ADVERSARIAL GENERALIZATION - NOVEL UTILITY MESSAGES
    # ================================================================
    print("\n" + "=" * 60)
    print("TEST 3: NOVEL UTILITY MESSAGES (GENERALIZATION)")
    print("=" * 60)
    print("Testing with utility messages from UNSEEN institutions")
    
    novel_benign = [
        # Unseen institutions
        "WBSEDCL: Your electricity bill for consumer no 567890 is Rs.1340. Due date 28-Sep-2026. Pay via official WBSEDCL app.",
        "APSPDCL: Monthly electricity bill Rs.980 generated. Pay before 25-Sep at official APSPDCL portal.",
        "PSPCL Punjab: Bijli bill Rs.1120 due on 22-Sep. Pay through official PSPCL website pspcl.in.",
        "JVVNL Rajasthan: Electricity bill Rs.850 pending. Last date 20-Sep. Pay via official Jaipur Vidyut app.",
        "MGVCL Gujarat: Your electricity bill of Rs.1560 is due. Pay online through official MGVCL portal.",
        "Kerala KSEB: Bill Rs.720 generated for consumer 345678. Due 18-Sep. Use official KSEB Online app.",
        # Unseen gas/water utilities  
        "Gujarat Gas: Monthly gas bill Rs.480 for August. Pay before 22-Sep via official GGL portal.",
        "Chennai Metro Water: Water bill Rs.350 for Q3 2026. Pay through official CMWSSB portal.",
        "Bangalore Water Supply: BWSSB bill Rs.290 due by 25-Sep. Pay at official BWSSB counter or online.",
        # Unseen telecom
        "MTNL Mumbai: Your landline bill Rs.380 for August. Due 20-Sep. Pay via official MTNL portal mtnl.in.",
        # Insurance premium
        "New India Assurance: Policy renewal premium Rs.12,500 due by 30-Sep. Pay at official portal newindia.co.in.",
    ]
    novel_benign_labels = ["BENIGN"] * len(novel_benign)
    
    novel_malicious = [
        # Electricity scams with unseen institutions
        "WBSEDCL Alert: Electricity disconnection scheduled tonight 9PM. Click http://wbsedcl-pay.xyz to pay now.",
        "APSPDCL: Your power connection will be terminated within 2 hours. Pay Rs.2000 at http://apspdcl-bill.in immediately.",
        "PSPCL Warning: Bijli bill overdue. Connection cut tonight. Update KYC at http://pspcl-kyc.xyz to avoid.",
        "JVVNL: Penalty of Rs.5000 for unauthorized usage. Pay via UPI: jvvnl-penalty@ybl within 1 hour.",
        "MGVCL: Your meter reading shows tampering. Download report: http://mgvcl-meter.xyz. Legal action pending.",
        "KSEB Alert: Connection disconnected for non-payment. Share Aadhaar and bank details to reconnect: 9876543210.",
        # Gas/water utility scams
        "Gujarat Gas: Gas supply will be cut off today. Call 8765432109 and pay Rs.3000 immediately.",
        "CMWSSB: Water connection termination notice. Pay Rs.1500 at http://cmwssb-pay.xyz before midnight.",
    ]
    novel_malicious_labels = ["MALICIOUS"] * len(novel_malicious)
    
    all_novel = novel_benign + novel_malicious
    all_labels = novel_benign_labels + novel_malicious_labels
    
    preds, probs, acc = evaluate_messages(
        clf, tfidf_word, tfidf_char, scaler, cfg, 
        all_novel, all_labels, THRESHOLD, "NOVEL UTILITY MESSAGES")
    
    # Separate benign/malicious accuracy
    ben_correct = sum(1 for i, l in enumerate(all_labels) if l == "BENIGN" and preds[i] == 0)
    mal_correct = sum(1 for i, l in enumerate(all_labels) if l == "MALICIOUS" and preds[i] == 2)
    ben_total = sum(1 for l in all_labels if l == "BENIGN")
    mal_total = sum(1 for l in all_labels if l == "MALICIOUS")
    print(f"\n  Novel benign accuracy: {ben_correct}/{ben_total} = {ben_correct/max(1,ben_total):.2%}")
    print(f"  Novel malicious recall: {mal_correct}/{mal_total} = {mal_correct/max(1,mal_total):.2%}")
    novel_fpr = 1 - ben_correct / max(1, ben_total)
    
    # ================================================================
    # TEST 4: ADVERSARIAL SEMANTIC PAIRS
    # ================================================================
    print("\n" + "=" * 60)
    print("TEST 4: ADVERSARIAL SEMANTIC PAIRS")
    print("=" * 60)
    print("High lexical overlap, opposite intent")
    
    adversarial_pairs = [
        # Pair 1: KYC
        ("HDFC Bank: Complete your KYC update at nearest HDFC branch before 30-Sep. Carry original Aadhaar and PAN.", "BENIGN"),
        ("HDFC Bank: Complete your KYC update at http://hdfc-kyc.xyz before midnight or account will be blocked.", "MALICIOUS"),
        # Pair 2: Account security
        ("SBI: Suspicious login attempt detected on your account. If this was not you, call 1800-11-2211 immediately.", "BENIGN"),
        ("SBI: Suspicious login attempt detected on your account. Click http://sbi-verify.xyz to verify your identity now.", "MALICIOUS"),
        # Pair 3: OTP
        ("PNB: OTP 834521 for net banking login. Do NOT share with anyone. If not initiated, call 1800-180-2222.", "BENIGN"),
        ("PNB: Your net banking access requires OTP verification. Reply with OTP received on your registered number.", "MALICIOUS"),
        # Pair 4: Bill payment
        ("Tata Power: Bill Rs.1890 generated. Pay before 25-Sep via official Tata Power app or tatapower.com.", "BENIGN"),
        ("Tata Power: Bill Rs.1890 overdue. Connection cut today. Pay now at http://tatapower-pay.xyz.", "MALICIOUS"),
        # Pair 5: Aadhaar
        ("UIDAI: Your Aadhaar has been used for authentication on 15-Aug. If unauthorized, lock at uidai.gov.in.", "BENIGN"),
        ("UIDAI: Your Aadhaar verification expired. Update biometric at http://aadhaar-update.xyz within 24 hours.", "MALICIOUS"),
        # Pair 6: Delivery
        ("Amazon: Your order #123456 will be delivered today. OTP for delivery verification: 4523. Do not share.", "BENIGN"),
        ("Amazon: Your order #123456 refund of Rs.2500 approved. Reply with OTP to process refund to your account.", "MALICIOUS"),
        # Pair 7: Government
        ("Income Tax: Refund of Rs.15,000 for AY 2025-26 processed. Will be credited to registered bank account.", "BENIGN"),
        ("Income Tax: Refund of Rs.15,000 pending. Enter bank details at http://it-refund.xyz to receive immediately.", "MALICIOUS"),
    ]
    
    adv_texts = [p[0] for p in adversarial_pairs]
    adv_labels = [p[1] for p in adversarial_pairs]
    
    a_preds, a_probs, a_acc = evaluate_messages(
        clf, tfidf_word, tfidf_char, scaler, cfg,
        adv_texts, adv_labels, THRESHOLD, "ADVERSARIAL PAIRS")
    
    adv_ben_correct = sum(1 for i, l in enumerate(adv_labels) if l == "BENIGN" and a_preds[i] == 0)
    adv_mal_correct = sum(1 for i, l in enumerate(adv_labels) if l == "MALICIOUS" and a_preds[i] == 2)
    print(f"\n  Adversarial benign accuracy: {adv_ben_correct}/{len([l for l in adv_labels if l=='BENIGN'])}")
    print(f"  Adversarial malicious recall: {adv_mal_correct}/{len([l for l in adv_labels if l=='MALICIOUS'])}")
    
    # ================================================================
    # TEST 5: SOURCE HOLDOUT
    # ================================================================
    print("\n" + "=" * 60)
    print("TEST 5: SOURCE HOLDOUT")
    print("=" * 60)
    
    for split_name in ["val", "test", "ood"]:
        recs = load_dataset(f"{split_name}.jsonl")
        sources = Counter(r.get("source_id", "UNK") for r in recs)
        for src, cnt in sources.most_common():
            if cnt >= 20:
                src_recs = [r for r in recs if r.get("source_id") == src]
                s_texts = [r.get("raw_text", "") for r in src_recs]
                y_s = np.array([LABEL_MAP[r["security_label"]] for r in src_recs])
                X_sw = tfidf_word.transform(s_texts).toarray()
                X_sc = tfidf_char.transform(s_texts).toarray()
                X_sd = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in src_recs])
                X_s = scaler.transform(np.hstack((X_sd, X_sw, X_sc)))
                s_probs = clf.predict_proba(X_s)
                s_preds = predict_with_threshold(s_probs, THRESHOLD)
                s_cm = confusion_matrix(y_s, s_preds, labels=[0, 1, 2])
                ben = max(1, sum(s_cm[0])) if sum(s_cm[0]) > 0 else 0
                mal = max(1, sum(s_cm[2])) if sum(s_cm[2]) > 0 else 0
                fpr = (s_cm[0][1]+s_cm[0][2])/max(1,ben) if ben > 0 else 0
                rec = s_cm[2][2]/max(1,mal) if mal > 0 else 0
                print(f"  {split_name.upper()} {src[:40]:40s} N={cnt:5d} FPR={fpr:.4f} Recall={rec:.4f}")
    
    # ================================================================
    # TEST 6: LATENCY
    # ================================================================
    print("\n" + "=" * 60)
    print("TEST 6: INFERENCE LATENCY")
    print("=" * 60)
    
    test_msg = "MSEDCL: Consumer No 123456, bill amount Rs.1500 is due on 15-Sep. Pay promptly through Mahavitaran official app."
    
    # Warm up
    for _ in range(5):
        xw = tfidf_word.transform([test_msg]).toarray()
        xc = tfidf_char.transform([test_msg]).toarray()
        xd = np.array([extract_feature_vector(test_msg, None, cfg)])
        x = scaler.transform(np.hstack((xd, xw, xc)))
        clf.predict_proba(x)
    
    latencies = []
    for _ in range(100):
        t0 = time.perf_counter()
        xw = tfidf_word.transform([test_msg]).toarray()
        xc = tfidf_char.transform([test_msg]).toarray()
        xd = np.array([extract_feature_vector(test_msg, None, cfg)])
        x = scaler.transform(np.hstack((xd, xw, xc)))
        clf.predict_proba(x)
        latencies.append((time.perf_counter() - t0) * 1000)
    
    latencies.sort()
    print(f"  p50: {latencies[49]:.2f} ms")
    print(f"  p95: {latencies[94]:.2f} ms")
    print(f"  p99: {latencies[98]:.2f} ms")
    
    # ================================================================
    # FINAL VERDICT
    # ================================================================
    print("\n" + "=" * 70)
    print("VERIFICATION SUMMARY")
    print("=" * 70)
    
    issues = []
    
    # Check all gates
    if novel_fpr > 0.10:
        issues.append(f"Novel utility benign FPR too high: {novel_fpr:.2%}")
    if cr_rec < 0.80:
        issues.append(f"Credential request recall too low: {cr_rec:.4f} < 0.80")
    if a_acc < 0.70:
        issues.append(f"Adversarial accuracy too low: {a_acc:.2%}")
    
    if issues:
        print("  VERDICT: CHALLENGER_D NEEDS REMEDIATION")
        for iss in issues:
            print(f"    - {iss}")
    else:
        print("  VERDICT: CHALLENGER_D PASSES INDEPENDENT VERIFICATION")
    
    print("\n" + "=" * 70)
    print("VERIFICATION COMPLETE")
    print("=" * 70)

if __name__ == "__main__":
    main()
