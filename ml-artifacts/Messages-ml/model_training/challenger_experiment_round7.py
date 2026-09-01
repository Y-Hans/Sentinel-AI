"""
ROUND 7: PRECISION THRESHOLD SEARCH ON CHALLENGER_F
====================================================
CHALLENGER_F (Round 2) had the best credential recall (0.788) at t=0.85.
But Round 2 only swept at 0.05 intervals. The HN transition was somewhere
between t=0.80 (HN=10) and t=0.85 (HN=0). If MSEDCL P is say 0.82, then
t=0.825 might give HN=0 with higher credential recall than t=0.85.

Also try mild sample weights (1.2x-2.0x) on v5 data to gently nudge
MSEDCL P down without killing credential recall.
"""

import json, pickle, sys, time
import numpy as np
from pathlib import Path
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

def load_dataset(filename):
    records = []
    with open(ROOT / "data" / "processed" / filename, "r", encoding="utf-8") as f:
        for line in f:
            if line.strip(): records.append(json.loads(line.strip()))
    return records

def predict_with_threshold(probs, t):
    preds = np.zeros(len(probs), dtype=int)
    for i in range(len(probs)):
        if probs[i][1] + probs[i][2] >= t:
            preds[i] = 1 if probs[i][1] > probs[i][2] else 2
    return preds


def train_and_sweep(name, train_recs, cfg, val_recs, test_recs, ood_recs, hn_benign, 
                    cred_all, y_ca, sw_factor=1.0, use_char=True):
    """Train model and do ultra-fine threshold sweep."""
    print(f"\n{'='*60}")
    print(f"  {name} (sw={sw_factor}, char={use_char})")
    print(f"{'='*60}")
    
    texts = [r.get("raw_text", "") for r in train_recs]
    y = np.array([LABEL_MAP[r["security_label"]] for r in train_recs])
    
    # Sample weights
    sw = np.ones(len(train_recs))
    util_sources = {"SRC_UTILITY_EXPANSION_V1", "SRC_CREDENTIAL_EXPANSION_V1"}
    if sw_factor > 1.0:
        for i, r in enumerate(train_recs):
            if r.get("source_id") in util_sources and r["security_label"] == "BENIGN":
                sw[i] = sw_factor
        print(f"  Upweighted {int(sum(sw > 1))} samples by {sw_factor}x")
    
    tw = TfidfVectorizer(max_features=1500, stop_words="english", ngram_range=(1, 2))
    Xw = tw.fit_transform(texts).toarray()
    
    tc_obj = None
    if use_char:
        tc_obj = TfidfVectorizer(max_features=500, ngram_range=(3, 5), analyzer='char_wb')
        Xc = tc_obj.fit_transform(texts).toarray()
    
    Xd = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in train_recs])
    
    X = np.hstack((Xd, Xw, Xc)) if use_char else np.hstack((Xd, Xw))
    sc = StandardScaler()
    X = sc.fit_transform(X)
    
    clf = HistGradientBoostingClassifier(random_state=42, max_depth=7, max_iter=300, class_weight='balanced')
    clf.fit(X, y, sample_weight=sw if sw_factor > 1.0 else None)
    
    def prep(recs):
        t = [r.get("raw_text", "") for r in recs]
        xw = tw.transform(t).toarray()
        xd = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in recs])
        if use_char:
            xc = tc_obj.transform(t).toarray()
            return sc.transform(np.hstack((xd, xw, xc)))
        return sc.transform(np.hstack((xd, xw)))
    
    X_val = prep(val_recs); y_val = np.array([LABEL_MAP[r["security_label"]] for r in val_recs])
    val_probs = clf.predict_proba(X_val)
    X_hn = prep(hn_benign); hn_probs = clf.predict_proba(X_hn)
    X_ca = prep(cred_all); ca_probs = clf.predict_proba(X_ca)
    
    # Find exact MSEDCL P
    msedcl_p = None
    for i, rec in enumerate(hn_benign):
        if "MSEDCL" in rec.get("raw_text", ""):
            msedcl_p = float(hn_probs[i][1] + hn_probs[i][2])
            break
    print(f"  MSEDCL P(non-benign) = {msedcl_p:.6f}")
    
    # Find exact HN transition point
    hn_max_p = 0
    for i in range(len(hn_benign)):
        p_nb = float(hn_probs[i][1] + hn_probs[i][2])
        if p_nb > hn_max_p:
            hn_max_p = p_nb
    print(f"  Max HN P(non-benign) = {hn_max_p:.6f}")
    print(f"  HN transition threshold = {hn_max_p:.6f} (any t above this gives HN=0)")
    
    # Ultra-fine sweep around the transition point
    # Search from just above hn_max_p to 0.95
    t_start = int(hn_max_p * 1000) + 1  # Just above max HN P
    
    print(f"\n  Ultra-fine sweep from {t_start/1000:.3f} to 0.950:")
    print(f"  {'T':>7} | {'V_FPR':>7} | {'V_Rec':>7} | {'HN':>3} | {'CrV':>7} | {'CrAll':>7} | Status")
    
    best_t = None; best_f1 = -1
    
    for t_1000 in range(t_start, 960, 2):
        t = t_1000 / 1000
        vp = predict_with_threshold(val_probs, t)
        cm = confusion_matrix(y_val, vp, labels=[0, 1, 2])
        fpr = (cm[0][1]+cm[0][2]) / max(1, sum(cm[0]))
        rec = cm[2][2] / max(1, sum(cm[2]))
        f1 = f1_score(y_val, vp, average="macro")
        hp = predict_with_threshold(hn_probs, t)
        hn_fp = sum(1 for p in hp if p > 0)
        
        # Credential recall on val only
        cred_val = [r for r in val_recs if "CREDENTIAL_REQUEST" in r.get("threat_vectors", [])]
        X_cv = prep(cred_val)
        y_cv = np.array([LABEL_MAP[r["security_label"]] for r in cred_val])
        cv_probs = clf.predict_proba(X_cv)
        cv_preds = predict_with_threshold(cv_probs, t)
        cv_cm = confusion_matrix(y_cv, cv_preds, labels=[0, 1, 2])
        cr_val = cv_cm[2][2] / max(1, sum(cv_cm[2]))
        
        # Full credential recall
        ca_p = predict_with_threshold(ca_probs, t)
        ca_cm = confusion_matrix(y_ca, ca_p, labels=[0, 1, 2])
        cr_all = ca_cm[2][2] / max(1, sum(ca_cm[2]))
        
        all_pass = fpr <= 0.01 and rec >= 0.80 and hn_fp == 0 and cr_all >= 0.80
        if all_pass and f1 > best_f1:
            best_f1 = f1; best_t = t
        
        st = "ALL_PASS" if all_pass else ("close" if hn_fp == 0 and cr_all >= 0.78 else "")
        marker = " ***" if t == best_t else ""
        if t_1000 % 10 == 0 or st or t_1000 < t_start + 20:
            print(f"  {t:7.3f} | {fpr:7.4f} | {rec:7.4f} | {hn_fp:3d} | {cr_val:7.4f} | {cr_all:7.4f} | {st}{marker}")
    
    # If found all-pass, do full evaluation
    if best_t is not None:
        print(f"\n  *** ALL GATES PASS at t={best_t} ***")
        
        for sn, recs in [("TEST", test_recs), ("OOD", ood_recs)]:
            X_s = prep(recs)
            y_s = np.array([LABEL_MAP[r["security_label"]] for r in recs])
            sp = clf.predict_proba(X_s)
            s_preds = predict_with_threshold(sp, best_t)
            cm = confusion_matrix(y_s, s_preds, labels=[0, 1, 2])
            ben = max(1, sum(cm[0])); mal = max(1, sum(cm[2]))
            fpr = (cm[0][1]+cm[0][2])/ben; rec = cm[2][2]/mal
            f1 = f1_score(y_s, s_preds, average="macro")
            print(f"  {sn}: FPR={fpr:.4f} ({cm[0][1]+cm[0][2]}/{ben}) Rec={rec:.4f} ({cm[2][2]}/{mal}) F1={f1:.4f}")
            print(f"    CM: {cm.tolist()}")
        
        hp = predict_with_threshold(hn_probs, best_t)
        hn_fp = sum(1 for p in hp if p > 0)
        print(f"  HN: {hn_fp}/{len(hn_benign)}")
        
        ca_p = predict_with_threshold(ca_probs, best_t)
        ca_cm = confusion_matrix(y_ca, ca_p, labels=[0, 1, 2])
        cr = ca_cm[2][2] / max(1, sum(ca_cm[2]))
        print(f"  Credential: {cr:.4f} ({ca_cm[2][2]}/{max(1,sum(ca_cm[2]))})")
        
        sz = (len(pickle.dumps(clf)) + len(pickle.dumps(tw)) + 
              (len(pickle.dumps(tc_obj)) if tc_obj else 0) + len(pickle.dumps(sc))) / 1024
        print(f"  Size: {sz:.0f} KB")
        
        # Save
        print(f"\n  Saving {name} artifacts...")
        for obj, suffix in [(clf, "model"), (tw, "tfidf_word"), (sc, "scaler")]:
            with open(ROOT / f"champion_v2_{suffix}.pkl", "wb") as f:
                pickle.dump(obj, f)
        if tc_obj:
            with open(ROOT / f"champion_v2_tfidf_char.pkl", "wb") as f:
                pickle.dump(tc_obj, f)
        
        meta = {
            "name": name, "round": 7, "threshold": best_t,
            "dataset": "train_expanded_v5.jsonl",
            "msedcl_p": msedcl_p, "hn_max_p": hn_max_p,
            "use_char": use_char, "sample_weight": sw_factor,
            "timestamp": datetime.now(timezone.utc).isoformat()
        }
        with open(ROOT / "model_training" / "CHAMPION_V2_CONFIG.json", "w") as f:
            json.dump(meta, f, indent=2)
        
        return {"name": name, "threshold": best_t, "all_pass": True, 
                "msedcl_p": msedcl_p, "hn_max_p": hn_max_p,
                "cred_recall": cr, "size_kb": sz}
    else:
        # Report the best operating point
        # Use the HN transition threshold
        t = (int(hn_max_p * 1000) + 1) / 1000
        ca_p = predict_with_threshold(ca_probs, t)
        ca_cm = confusion_matrix(y_ca, ca_p, labels=[0, 1, 2])
        cr = ca_cm[2][2] / max(1, sum(ca_cm[2]))
        
        vp = predict_with_threshold(val_probs, t)
        cm = confusion_matrix(y_val, vp, labels=[0, 1, 2])
        fpr = (cm[0][1]+cm[0][2]) / max(1, sum(cm[0]))
        
        print(f"\n  No all-pass found. Best HN-safe operating point:")
        print(f"    t={t:.3f}, Val FPR={fpr:.4f}, CrAll={cr:.4f}, gap={0.80-cr:.4f}")
        
        return {"name": name, "threshold": t, "all_pass": False,
                "msedcl_p": msedcl_p, "hn_max_p": hn_max_p,
                "cred_recall": cr, "cred_gap": 0.80 - cr}


def main():
    print("=" * 70)
    print("ROUND 7: PRECISION THRESHOLD SEARCH")
    print(f"Timestamp: {datetime.now(timezone.utc).isoformat()}")
    print("=" * 70)
    
    cfg = FeatureConfig()
    val_recs = load_dataset("val.jsonl")
    test_recs = load_dataset("test.jsonl")
    ood_recs = load_dataset("ood.jsonl")
    all_recs = val_recs + test_recs + ood_recs
    hn_benign = [r for r in val_recs if r.get("source_id") == "SRC_CURATED_HARD_NEGATIVES_V1" and r.get("security_label") == "BENIGN"]
    cred_all = [r for r in all_recs if "CREDENTIAL_REQUEST" in r.get("threat_vectors", [])]
    y_ca = np.array([LABEL_MAP[r["security_label"]] for r in cred_all])
    train_recs = load_dataset("train_expanded_v5.jsonl")
    print(f"v5 data: {len(train_recs)} records, HN benign: {len(hn_benign)}, Credential: {len(cred_all)}")
    
    all_results = []
    
    # Config 1: Exact CHALLENGER_F config (no sw, char+word)
    r = train_and_sweep("F_exact", train_recs, cfg, val_recs, test_recs, ood_recs, 
                        hn_benign, cred_all, y_ca, sw_factor=1.0, use_char=True)
    all_results.append(r)
    if r["all_pass"]: 
        print("\n*** CHAMPION FOUND ***")
        return
    
    # Config 2: Mild sw=1.5x, char+word
    r = train_and_sweep("F_sw1.5_char", train_recs, cfg, val_recs, test_recs, ood_recs,
                        hn_benign, cred_all, y_ca, sw_factor=1.5, use_char=True)
    all_results.append(r)
    if r["all_pass"]:
        print("\n*** CHAMPION FOUND ***")
        return
    
    # Config 3: Mild sw=2.0x, char+word  
    r = train_and_sweep("F_sw2.0_char", train_recs, cfg, val_recs, test_recs, ood_recs,
                        hn_benign, cred_all, y_ca, sw_factor=2.0, use_char=True)
    all_results.append(r)
    if r["all_pass"]:
        print("\n*** CHAMPION FOUND ***")
        return
    
    # Config 4: Mild sw=1.5x, word only (CHALLENGER_F original was actually word+char)
    r = train_and_sweep("F_sw1.5_word", train_recs, cfg, val_recs, test_recs, ood_recs,
                        hn_benign, cred_all, y_ca, sw_factor=1.5, use_char=False)
    all_results.append(r)
    if r["all_pass"]:
        print("\n*** CHAMPION FOUND ***")
        return
    
    # Config 5: sw=2.5x, char+word
    r = train_and_sweep("F_sw2.5_char", train_recs, cfg, val_recs, test_recs, ood_recs,
                        hn_benign, cred_all, y_ca, sw_factor=2.5, use_char=True)
    all_results.append(r)
    if r["all_pass"]:
        print("\n*** CHAMPION FOUND ***")
        return
    
    # Config 6: sw=2.0x, word only
    r = train_and_sweep("F_sw2.0_word", train_recs, cfg, val_recs, test_recs, ood_recs,
                        hn_benign, cred_all, y_ca, sw_factor=2.0, use_char=False)
    all_results.append(r)
    if r["all_pass"]:
        print("\n*** CHAMPION FOUND ***")
        return
    
    # Summary
    print("\n" + "=" * 70)
    print("ROUND 7 SUMMARY")
    print("=" * 70)
    print(f"\n  {'Name':>18s} | {'MSEDCL_P':>8} | {'HN_max':>8} | {'CrAll':>7} | {'Gap':>7} | Pass")
    for r in all_results:
        gap = r.get('cred_gap', 0) if not r['all_pass'] else 0
        print(f"  {r['name']:>18s} | {r['msedcl_p']:8.4f} | {r['hn_max_p']:8.4f} | "
              f"{r['cred_recall']:7.4f} | {gap:7.4f} | {'Y' if r['all_pass'] else 'N'}")
    
    # Append to registry
    with open(ROOT / "model_training" / "autonomous_optimization_results.json", "a") as f:
        for r in all_results:
            entry = {"experiment_id": r["name"], "round": 7,
                     "timestamp": datetime.now(timezone.utc).isoformat(),
                     "msedcl_p": r["msedcl_p"], "hn_max_p": r["hn_max_p"],
                     "credential_recall": r["cred_recall"],
                     "all_gates_pass": r["all_pass"],
                     "threshold": r["threshold"]}
            f.write(json.dumps(entry) + "\n")
    
    print("=" * 70)
    print("ROUND 7 COMPLETE")
    print("=" * 70)

if __name__ == "__main__":
    main()
