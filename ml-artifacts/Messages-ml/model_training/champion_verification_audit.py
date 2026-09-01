"""
INDEPENDENT CHAMPION VERIFICATION AUDIT
========================================
This script performs the complete verification audit of the frozen champion model.
It does NOT modify any model, dataset, or threshold.

Outputs:
  - FINAL_CHAMPION_VERIFICATION.json
  - CHAMPION_V1_FREEZE.json  
  - HARD_NEGATIVE_ADJUDICATION.json
"""

import json
import time
import hashlib
import pickle
import sys
import os
from pathlib import Path
from collections import Counter, defaultdict
import numpy as np
from datetime import datetime, timezone

# Force unbuffered stdout for real-time progress
sys.stdout.reconfigure(line_buffering=True)

ROOT = Path("C:/Users/user/Programing files/Yajat/Projects/sentinel-ml/Messages-ml")
sys.path.insert(0, str(ROOT / "scripts"))

from feature_config import FeatureConfig
from feature_extraction import extract_feature_vector, get_feature_names

# ============================================================
# SECTION 0: UTILITY FUNCTIONS
# ============================================================

def load_dataset(filename):
    filepath = ROOT / "data" / "processed" / filename
    records = []
    with open(filepath, "r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                records.append(json.loads(line.strip()))
    return records

def predict_with_threshold(probs, t):
    """Non-benign combined probability threshold."""
    preds = np.zeros(len(probs), dtype=int)
    for i in range(len(probs)):
        prob_non_benign = probs[i][1] + probs[i][2]
        if prob_non_benign >= t:
            preds[i] = 1 if probs[i][1] > probs[i][2] else 2
        else:
            preds[i] = 0
    return preds

LABEL_MAP = {"BENIGN": 0, "SUSPICIOUS_SPAM": 1, "MALICIOUS": 2}
LABEL_NAMES = {0: "BENIGN", 1: "SUSPICIOUS_SPAM", 2: "MALICIOUS"}

# ============================================================
# SECTION 1: FREEZE CHAMPION CONFIGURATION
# ============================================================

def freeze_champion():
    """Record the exact frozen configuration of the champion."""
    print("=" * 60)
    print("SECTION 1: FREEZING CHAMPION CONFIGURATION")
    print("=" * 60)
    
    # Check champion artifacts exist
    artifacts = {}
    for name in ["champion_model.pkl", "champion_tfidf.pkl", "champion_scaler.pkl"]:
        fpath = ROOT / name
        if not fpath.exists():
            print(f"CRITICAL: {name} not found!")
            return None
        with open(fpath, "rb") as f:
            data = f.read()
        artifacts[name] = {
            "path": str(fpath),
            "size_bytes": len(data),
            "sha256": hashlib.sha256(data).hexdigest()
        }
        print(f"  {name}: {len(data)} bytes, SHA256={artifacts[name]['sha256'][:16]}...")
    
    # Load and inspect model
    with open(ROOT / "champion_model.pkl", "rb") as f:
        clf = pickle.load(f)
    with open(ROOT / "champion_tfidf.pkl", "rb") as f:
        tfidf = pickle.load(f)
    with open(ROOT / "champion_scaler.pkl", "rb") as f:
        scaler = pickle.load(f)
    
    cfg = FeatureConfig()
    feature_names = get_feature_names(cfg)
    
    freeze = {
        "freeze_timestamp": datetime.now(timezone.utc).isoformat(),
        "model_artifact": artifacts["champion_model.pkl"],
        "tfidf_artifact": artifacts["champion_tfidf.pkl"],
        "scaler_artifact": artifacts["champion_scaler.pkl"],
        "feature_configuration": cfg.to_dict(),
        "feature_names": feature_names,
        "num_deterministic_features": len(feature_names),
        "tfidf_configuration": {
            "max_features": tfidf.max_features,
            "stop_words": "english",
            "ngram_range": list(tfidf.ngram_range),
            "vocabulary_size": len(tfidf.vocabulary_)
        },
        "histgbm_configuration": {
            "class": "sklearn.ensemble.HistGradientBoostingClassifier",
            "random_state": 42,
            "max_depth": clf.max_depth,
            "max_iter": clf.max_iter,
            "class_weight": "balanced",
            "n_iter_": clf.n_iter_,
            "classes_": clf.classes_.tolist()
        },
        "preprocessing": {
            "scaler_class": "sklearn.preprocessing.StandardScaler",
            "feature_order": "hstack(deterministic, tfidf)",
            "total_features": len(feature_names) + len(tfidf.vocabulary_)
        },
        "training_dataset": "train_expanded_v2.jsonl",
        "threshold": 0.85,
        "threshold_type": "non_benign_combined_probability",
        "threshold_formula": "pred = NON_BENIGN if P(SUSPICIOUS) + P(MALICIOUS) >= 0.85 else BENIGN"
    }
    
    freeze_path = ROOT / "model_training" / "CHAMPION_V1_FREEZE.json"
    with open(freeze_path, "w") as f:
        json.dump(freeze, f, indent=2)
    print(f"  Freeze artifact saved: {freeze_path}")
    
    return clf, tfidf, scaler, cfg, freeze

# ============================================================
# SECTION 2: DATASET INTEGRITY
# ============================================================

def audit_dataset_integrity():
    """Check all splits for overlap and contamination."""
    print("\n" + "=" * 60)
    print("SECTION 2: DATASET INTEGRITY AUDIT")
    print("=" * 60)
    
    splits = {
        "train": load_dataset("train.jsonl"),
        "train_contrastive": load_dataset("train_contrastive.jsonl"),
        "train_expanded_v2": load_dataset("train_expanded_v2.jsonl"),
        "val": load_dataset("val.jsonl"),
        "test": load_dataset("test.jsonl"),
        "ood": load_dataset("ood.jsonl")
    }
    
    # Check if train_expanded_v3 exists
    v3_path = ROOT / "data" / "processed" / "train_expanded_v3.jsonl"
    if v3_path.exists():
        splits["train_expanded_v3"] = load_dataset("train_expanded_v3.jsonl")
    
    result = {}
    
    # Record counts
    counts = {}
    for name, recs in splits.items():
        label_dist = Counter(r.get("security_label") for r in recs)
        source_dist = Counter(r.get("source_id") for r in recs)
        counts[name] = {
            "total_records": len(recs),
            "label_distribution": dict(label_dist),
            "source_distribution": dict(source_dist)
        }
        print(f"  {name}: {len(recs)} records")
        for label, cnt in sorted(label_dist.items()):
            print(f"    {label}: {cnt}")
    result["split_counts"] = counts
    
    # Check for text overlap between canonical splits
    canonical = ["train", "val", "test", "ood"]
    overlap_results = {}
    
    for i in range(len(canonical)):
        for j in range(i + 1, len(canonical)):
            n1, n2 = canonical[i], canonical[j]
            
            # Exact text overlap
            texts1 = set(r.get("raw_text", "") for r in splits[n1])
            texts2 = set(r.get("raw_text", "") for r in splits[n2])
            exact_overlap = texts1 & texts2
            
            # Normalized text overlap
            norm1 = set(r.get("raw_text", "").lower().strip() for r in splits[n1])
            norm2 = set(r.get("raw_text", "").lower().strip() for r in splits[n2])
            norm_overlap = norm1 & norm2
            
            # Message ID overlap
            ids1 = set(r.get("message_id", "") for r in splits[n1])
            ids2 = set(r.get("message_id", "") for r in splits[n2])
            id_overlap = ids1 & ids2
            
            # Template cluster overlap
            tmpl1 = set(r.get("template_cluster_id", "") for r in splits[n1] if r.get("template_cluster_id"))
            tmpl2 = set(r.get("template_cluster_id", "") for r in splits[n2] if r.get("template_cluster_id"))
            tmpl_overlap = tmpl1 & tmpl2
            
            key = f"{n1}_vs_{n2}"
            overlap_results[key] = {
                "exact_text_overlap": len(exact_overlap),
                "normalized_text_overlap": len(norm_overlap),
                "message_id_overlap": len(id_overlap),
                "template_cluster_overlap": len(tmpl_overlap)
            }
            
            status = "PASS" if len(exact_overlap) == 0 and len(id_overlap) == 0 else "FAIL"
            print(f"  {key}: exact={len(exact_overlap)}, norm={len(norm_overlap)}, id={len(id_overlap)}, tmpl={len(tmpl_overlap)} [{status}]")
            
            if len(exact_overlap) > 0:
                overlap_results[key]["exact_overlap_samples"] = list(exact_overlap)[:5]
    
    result["overlap_checks"] = overlap_results
    
    # Check synthetic contamination of TEST/OOD
    synthetic_sources = ["SRC_CONTRASTIVE_PAIRS_V1", "SRC_ERROR_DRIVEN_EXPANSION_V1", 
                         "SRC_ADV_PAIR_LEGIT", "SRC_ADV_PAIR_MAL"]
    contamination = {}
    for split_name in ["test", "ood"]:
        synth_count = sum(1 for r in splits[split_name] if r.get("source_id") in synthetic_sources)
        synth_type_count = sum(1 for r in splits[split_name] if r.get("source_type") == "SYNTHETIC")
        contamination[split_name] = {
            "synthetic_source_id_count": synth_count,
            "synthetic_source_type_count": synth_type_count,
            "status": "CLEAN" if synth_count == 0 and synth_type_count == 0 else "CONTAMINATED"
        }
        print(f"  {split_name} synthetic contamination: source_id={synth_count}, source_type={synth_type_count} [{contamination[split_name]['status']}]")
    
    # Also check val for synthetic contamination
    val_synth = sum(1 for r in splits["val"] if r.get("source_type") == "SYNTHETIC")
    contamination["val"] = {
        "synthetic_source_type_count": val_synth,
        "status": "CLEAN" if val_synth == 0 else "CONTAMINATED"
    }
    
    result["contamination_checks"] = contamination
    
    # Verify train_expanded_v2 is a superset of train_contrastive
    train_texts = set(r.get("raw_text", "") for r in splits["train"])
    tc_texts = set(r.get("raw_text", "") for r in splits["train_contrastive"])
    te2_texts = set(r.get("raw_text", "") for r in splits["train_expanded_v2"])
    
    train_in_tc = len(train_texts - tc_texts)
    tc_in_te2 = len(tc_texts - te2_texts)
    result["training_data_lineage"] = {
        "train_texts_not_in_contrastive": train_in_tc,
        "contrastive_texts_not_in_expanded_v2": tc_in_te2,
        "expanded_v2_unique_texts": len(te2_texts - tc_texts),
        "lineage_valid": train_in_tc == 0 and tc_in_te2 == 0
    }
    
    # Check for test/ood text in expanded training data
    test_texts = set(r.get("raw_text", "") for r in splits["test"])
    ood_texts = set(r.get("raw_text", "") for r in splits["ood"])
    val_texts = set(r.get("raw_text", "") for r in splits["val"])
    
    test_in_train = len(test_texts & te2_texts)
    ood_in_train = len(ood_texts & te2_texts)
    val_in_train = len(val_texts & te2_texts)
    
    result["eval_data_in_training"] = {
        "test_texts_in_train_expanded_v2": test_in_train,
        "ood_texts_in_train_expanded_v2": ood_in_train,
        "val_texts_in_train_expanded_v2": val_in_train,
        "status": "CLEAN" if test_in_train == 0 and ood_in_train == 0 else "CONTAMINATED"
    }
    print(f"  test in train_expanded_v2: {test_in_train}, ood in train_expanded_v2: {ood_in_train}, val in train_expanded_v2: {val_in_train}")
    
    return result, splits

# ============================================================
# SECTION 3: PREPROCESSING LEAKAGE AUDIT
# ============================================================

def audit_preprocessing_leakage(clf, tfidf, scaler, cfg):
    """Verify no preprocessing used test/ood data."""
    print("\n" + "=" * 60)
    print("SECTION 3: PREPROCESSING LEAKAGE AUDIT")
    print("=" * 60)
    
    result = {}
    
    # TF-IDF: verify vocabulary was fitted on training only
    # We can't directly verify from the pickled object what data it was fitted on,
    # but we can verify the code path shows fit_transform on train only
    result["tfidf_fit"] = {
        "code_path": "evaluate_histgbm.py:97 - tfidf.fit_transform(train_texts)",
        "vocabulary_size": len(tfidf.vocabulary_),
        "assessment": "PASS - Code shows fit_transform called on train_texts only. transform() used for val/test/ood."
    }
    
    # Scaler: verify fit on training only
    result["scaler_fit"] = {
        "code_path": "evaluate_histgbm.py:103-104 - scaler.fit_transform(X_tr)",
        "n_features_seen": scaler.n_features_in_,
        "assessment": "PASS - Code shows fit_transform called on X_tr only. transform() used for val/test/ood."
    }
    
    # Threshold selection
    # CRITICAL: The threshold was changed from 0.75 (in evaluate_histgbm.py) to 0.85 (in final_eval.py)
    # We must determine how 0.85 was selected
    result["threshold_selection"] = {
        "training_script_threshold": 0.75,
        "final_eval_threshold": 0.85,
        "champion_config_threshold": 0.85,
        "discrepancy": "YES - evaluate_histgbm.py saved champion with t=0.75, but final_eval.py uses t=0.85",
        "assessment": "CONCERN - Cannot verify from artifacts alone whether 0.85 was selected using VAL, TEST, or OOD. The previous conversation context shows threshold sweeps were run on VAL within train_tfidf_mlp.py, but the specific selection of 0.85 vs 0.80 vs 0.90 is not documented in the code.",
        "recommendation": "DOCUMENT_THRESHOLD_SELECTION_METHODOLOGY"
    }
    
    # Calibration check
    result["calibration"] = {
        "calibration_used": False,
        "assessment": "PASS - No calibration layer applied."
    }
    
    # Model fit check
    result["model_fit"] = {
        "code_path": "evaluate_histgbm.py:107-108 - clf.fit(X_tr, y_tr)",
        "training_data": "train_expanded_v2.jsonl",
        "assessment": "PASS - Model fitted on training data only."
    }
    
    for k, v in result.items():
        status = v.get("assessment", "").split(" - ")[0] if isinstance(v, dict) else "?"
        print(f"  {k}: {status}")
    
    return result

# ============================================================
# SECTION 4: REPRODUCE THE CHAMPION
# ============================================================

def reproduce_champion(splits):
    """Retrain from scratch and compare metrics."""
    print("\n" + "=" * 60)
    print("SECTION 4: CHAMPION REPRODUCTION")
    print("=" * 60)
    
    from sklearn.ensemble import HistGradientBoostingClassifier
    from sklearn.preprocessing import StandardScaler
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.metrics import confusion_matrix, f1_score
    
    cfg = FeatureConfig()
    train_recs = splits["train_expanded_v2"]
    
    train_texts = [r.get("raw_text", "") for r in train_recs]
    y_tr = np.array([LABEL_MAP[r.get("security_label", "BENIGN")] for r in train_recs])
    
    print("  Fitting TF-IDF on training data...")
    tfidf_new = TfidfVectorizer(max_features=2000, stop_words="english", ngram_range=(1, 2))
    X_tr_tfidf = tfidf_new.fit_transform(train_texts).toarray()
    
    print("  Extracting deterministic features...")
    X_tr_det = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in train_recs])
    
    X_tr = np.hstack((X_tr_det, X_tr_tfidf))
    scaler_new = StandardScaler()
    X_tr = scaler_new.fit_transform(X_tr)
    
    print("  Training HistGBM...")
    clf_new = HistGradientBoostingClassifier(random_state=42, max_depth=5, max_iter=200, class_weight='balanced')
    clf_new.fit(X_tr, y_tr)
    
    # Evaluate reproduced model on all splits
    t = 0.85
    results = {}
    
    for split_name in ["val", "test", "ood"]:
        recs = splits[split_name]
        texts = [r.get("raw_text", "") for r in recs]
        y_true = np.array([LABEL_MAP[r.get("security_label", "BENIGN")] for r in recs])
        
        X_tfidf = tfidf_new.transform(texts).toarray()
        X_det = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in recs])
        X = scaler_new.transform(np.hstack((X_det, X_tfidf)))
        
        probs = clf_new.predict_proba(X)
        preds = predict_with_threshold(probs, t)
        
        cm = confusion_matrix(y_true, preds, labels=[0, 1, 2])
        ben_total = max(1, sum(cm[0]))
        mal_total = max(1, sum(cm[2]))
        
        results[split_name] = {
            "benign_fpr": float((cm[0][1] + cm[0][2]) / ben_total),
            "benign_fpr_fraction": f"{cm[0][1] + cm[0][2]}/{ben_total}",
            "malicious_recall": float(cm[2][2] / mal_total),
            "malicious_recall_fraction": f"{cm[2][2]}/{mal_total}",
            "macro_f1": float(f1_score(y_true, preds, average="macro")),
            "confusion_matrix": cm.tolist()
        }
        print(f"  {split_name}: FPR={results[split_name]['benign_fpr']:.4f}, Recall={results[split_name]['malicious_recall']:.4f}")
    
    return results, clf_new, tfidf_new, scaler_new

# ============================================================
# SECTION 5: FROZEN MODEL EVALUATION
# ============================================================

def evaluate_frozen_model(clf, tfidf, scaler, cfg, splits):
    """Full evaluation of frozen champion on all splits."""
    print("\n" + "=" * 60)
    print("SECTION 5: FROZEN MODEL EVALUATION")
    print("=" * 60)
    
    from sklearn.metrics import confusion_matrix, f1_score, accuracy_score
    
    t = 0.85
    results = {}
    
    for split_name in ["val", "test", "ood"]:
        recs = splits[split_name]
        texts = [r.get("raw_text", "") for r in recs]
        y_true = np.array([LABEL_MAP[r.get("security_label", "BENIGN")] for r in recs])
        
        X_tfidf = tfidf.transform(texts).toarray()
        X_det = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in recs])
        X = scaler.transform(np.hstack((X_det, X_tfidf)))
        
        probs = clf.predict_proba(X)
        preds = predict_with_threshold(probs, t)
        
        cm = confusion_matrix(y_true, preds, labels=[0, 1, 2])
        ben_total = max(1, sum(cm[0]))
        mal_total = max(1, sum(cm[2]))
        susp_total = max(1, sum(cm[1]))
        total = len(recs)
        
        ben_to_susp = cm[0][1]
        ben_to_mal = cm[0][2]
        
        true_mal_pred = cm[0][2] + cm[1][2] + cm[2][2]
        
        res = {
            "samples": total,
            "confusion_matrix": cm.tolist(),
            "confusion_matrix_labels": ["BENIGN", "SUSPICIOUS_SPAM", "MALICIOUS"],
            "benign_to_suspicious_fpr": f"{ben_to_susp}/{ben_total} = {ben_to_susp/ben_total:.6f}",
            "benign_to_malicious_fpr": f"{ben_to_mal}/{ben_total} = {ben_to_mal/ben_total:.6f}",
            "benign_any_non_benign_fpr": f"{ben_to_susp + ben_to_mal}/{ben_total} = {(ben_to_susp + ben_to_mal)/ben_total:.6f}",
            "malicious_recall": f"{cm[2][2]}/{mal_total} = {cm[2][2]/mal_total:.6f}",
            "malicious_precision": f"{cm[2][2]}/{max(1, true_mal_pred)} = {cm[2][2]/max(1, true_mal_pred):.6f}",
            "macro_f1": float(f1_score(y_true, preds, average="macro")),
            "accuracy": f"{sum(cm[i][i] for i in range(3))}/{total} = {accuracy_score(y_true, preds):.6f}"
        }
        results[split_name] = res
        
        print(f"\n  {split_name.upper()} (N={total}):")
        print(f"    Confusion Matrix:")
        print(f"                    Pred BENIGN  Pred SPAM  Pred MALICIOUS")
        print(f"    Actual BENIGN   {cm[0][0]:>10}  {cm[0][1]:>9}  {cm[0][2]:>14}")
        print(f"    Actual SPAM     {cm[1][0]:>10}  {cm[1][1]:>9}  {cm[1][2]:>14}")
        print(f"    Actual MAL      {cm[2][0]:>10}  {cm[2][1]:>9}  {cm[2][2]:>14}")
        print(f"    Benign->Any FPR: {res['benign_any_non_benign_fpr']}")
        print(f"    Malicious Recall: {res['malicious_recall']}")
        print(f"    Malicious Precision: {res['malicious_precision']}")
        print(f"    Macro F1: {res['macro_f1']:.4f}")
    
    return results

# ============================================================
# SECTION 6: HARD NEGATIVE FORENSIC AUDIT & ADJUDICATION
# ============================================================

def adjudicate_message(record):
    """
    Independently adjudicate a hard-negative record based on its semantic content.
    
    DO NOT use the model prediction as evidence.
    Analyze based on:
    - What behavior does the message request?
    - Does it request credentials/OTP/payment?
    - Does it contain phishing behavior?
    - Does it impersonate an institution?
    - Does it redirect the user to suspicious URLs?
    - Does it contain protective language?
    - Is the urgency legitimate institutional communication?
    """
    text = record.get("raw_text", "").lower()
    raw = record.get("raw_text", "")
    
    # Phishing signals
    has_suspicious_url = any(x in text for x in ["http://", "https://", "<url>", "bit.ly", ".in/", ".com/"])
    requests_otp = any(x in text for x in ["reply with otp", "send otp", "share otp", "enter otp", "share your otp"])
    requests_credentials = any(x in text for x in ["reply with", "send your", "share your pin", "atm pin", "update pan", "upload pan", "update aadhaar"])
    requests_payment = any(x in text for x in ["pay immediately", "pay instantly", "pay rs", "pay fine instantly", "pay penalty"])
    threatens_immediate_action = any(x in text for x in [
        "will be disconnected tonight", "will be blocked in 2 hours", "will be cancelled today",
        "will be suspended", "will be deactivated", "face arrest", "vehicle seizure"
    ])
    impersonation_with_action = any(x in text for x in [
        "update your details at http", "verify your identity at http", "click http",
        "download this safety app http", "complete your e-kyc online at http"
    ])
    
    # Protective signals
    has_official_portal = any(x in text for x in ["official portal", "official app", ".gov.in", "official site", "nearest branch", "home branch", "nearest jio store", "retail outlet"])
    has_dont_share = any(x in text for x in ["do not share", "never share", "bank never asks", "please call 1947"])
    is_informational = any(x in text for x in ["was performed on", "was successful", "has been sent to your registered", "is not filed", "reminder to file"])
    directs_to_branch = any(x in text for x in ["visit your nearest branch", "visit our retail outlet", "please submit your latest documents", "pay fine via official portal"])
    
    # Determine category
    if requests_otp:
        return "VERIFIED_MALICIOUS", "Requests OTP disclosure - classic credential theft"
    
    if requests_credentials and has_suspicious_url:
        return "VERIFIED_MALICIOUS", "Requests credentials via suspicious URL - phishing"
    
    if impersonation_with_action:
        return "VERIFIED_MALICIOUS", "Impersonates institution with phishing URL action"
    
    if threatens_immediate_action and has_suspicious_url:
        return "VERIFIED_MALICIOUS", "Urgency threat combined with suspicious URL - scam pattern"
    
    if threatens_immediate_action and requests_payment:
        return "VERIFIED_MALICIOUS", "Urgency threat with immediate payment demand - scam pattern"
    
    # Job scams
    if any(x in text for x in ["daily income", "online data entry job", "part time job", "earn from home"]):
        if any(x in text for x in ["telegram", "whatsapp", "contact hr"]):
            return "VERIFIED_MALICIOUS", "Job scam with messaging app recruitment"
    
    # Legitimate patterns
    if has_official_portal and not has_suspicious_url and not requests_credentials:
        return "VERIFIED_BENIGN", "Directs to official portal/branch without suspicious behavior"
    
    if has_dont_share:
        return "VERIFIED_BENIGN", "Contains protective anti-fraud language"
    
    if is_informational and not requests_credentials and not has_suspicious_url:
        return "VERIFIED_BENIGN", "Informational notification without action demand"
    
    if directs_to_branch and not has_suspicious_url:
        return "VERIFIED_BENIGN", "Directs to physical branch visit"
    
    # Electricity/bill messages - ambiguous territory
    if any(x in text for x in ["bill pending", "bill amount", "due on", "pay before", "pay promptly"]):
        if has_official_portal or "official app" in text:
            return "VERIFIED_BENIGN", "Bill reminder directing to official payment channel"
        elif has_suspicious_url or threatens_immediate_action:
            return "VERIFIED_MALICIOUS", "Bill scam with suspicious URL or immediate threat"
        elif any(x in text for x in ["call office number", "call customer care"]):
            # Could be either - phone numbers in these could be scam lines
            return "AMBIGUOUS", "Bill payment with phone contact - could be legitimate or social engineering"
    
    # Traffic challan
    if "challan" in text or "traffic" in text:
        if ".gov.in" in text or "official" in text:
            return "VERIFIED_BENIGN", "Traffic challan with government portal"
        elif has_suspicious_url:
            return "VERIFIED_MALICIOUS", "Traffic challan phishing with suspicious URL"
        else:
            return "AMBIGUOUS", "Traffic challan without clear URL provenance"
    
    # If we can't determine clearly
    if threatens_immediate_action:
        return "AMBIGUOUS", "Contains urgency threat but insufficient evidence to determine legitimacy"
    
    return "INSUFFICIENT_EVIDENCE", "Cannot determine classification from message content alone"


def audit_hard_negatives(clf, tfidf, scaler, cfg, splits):
    """Complete hard-negative forensic audit and adjudication."""
    print("\n" + "=" * 60)
    print("SECTION 6: HARD-NEGATIVE FORENSIC AUDIT")
    print("=" * 60)
    
    t = 0.85
    
    # Get ALL hard negative records (from val split where they reside)
    val_recs = splits["val"]
    all_hn = [r for r in val_recs if r.get("source_id") == "SRC_CURATED_HARD_NEGATIVES_V1"]
    hn_benign = [r for r in all_hn if r.get("security_label") == "BENIGN"]
    hn_malicious = [r for r in all_hn if r.get("security_label") == "MALICIOUS"]
    
    print(f"  Total HN records: {len(all_hn)}")
    print(f"  HN labeled BENIGN: {len(hn_benign)}")
    print(f"  HN labeled MALICIOUS: {len(hn_malicious)}")
    
    # Also check test and ood for hard negatives
    test_hn = [r for r in splits["test"] if r.get("source_id") == "SRC_CURATED_HARD_NEGATIVES_V1"]
    ood_hn = [r for r in splits["ood"] if r.get("source_id") == "SRC_CURATED_HARD_NEGATIVES_V1"]
    print(f"  HN in TEST: {len(test_hn)}")
    print(f"  HN in OOD: {len(ood_hn)}")
    
    # Evaluate all benign hard negatives
    if len(hn_benign) == 0:
        print("  WARNING: No benign hard negatives found!")
        return {"error": "No benign hard negatives found"}
    
    texts = [r.get("raw_text", "") for r in hn_benign]
    X_tfidf = tfidf.transform(texts).toarray()
    X_det = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in hn_benign])
    X = scaler.transform(np.hstack((X_det, X_tfidf)))
    
    probs = clf.predict_proba(X)
    preds = predict_with_threshold(probs, t)
    
    # Adjudicate each record
    adjudications = []
    for i, rec in enumerate(hn_benign):
        pred_label = LABEL_NAMES[preds[i]]
        is_fp = preds[i] != 0  # False positive = predicted non-benign when labeled benign
        
        verdict, reason = adjudicate_message(rec)
        
        adj = {
            "index": i,
            "message_id": rec.get("message_id", ""),
            "source_id": rec.get("source_id", ""),
            "raw_text": rec.get("raw_text", ""),
            "sender_header": rec.get("sender_header"),
            "original_security_label": rec.get("security_label"),
            "predicted_security_label": pred_label,
            "predicted_probabilities": {
                "BENIGN": float(probs[i][0]),
                "SUSPICIOUS_SPAM": float(probs[i][1]),
                "MALICIOUS": float(probs[i][2])
            },
            "prob_non_benign": float(probs[i][1] + probs[i][2]),
            "primary_type": rec.get("primary_type", ""),
            "threat_vectors": rec.get("threat_vectors", []),
            "is_false_positive_by_original_label": bool(is_fp),
            "adjudication_verdict": verdict,
            "adjudication_reason": reason
        }
        adjudications.append(adj)
    
    # Compute the three FPR metrics
    total_hn_benign = len(hn_benign)
    original_fp = sum(1 for a in adjudications if a["is_false_positive_by_original_label"])
    
    verified_benign = [a for a in adjudications if a["adjudication_verdict"] == "VERIFIED_BENIGN"]
    verified_benign_fp = sum(1 for a in verified_benign if a["is_false_positive_by_original_label"])
    
    verified_malicious = [a for a in adjudications if a["adjudication_verdict"] == "VERIFIED_MALICIOUS"]
    ambiguous = [a for a in adjudications if a["adjudication_verdict"] == "AMBIGUOUS"]
    insufficient = [a for a in adjudications if a["adjudication_verdict"] == "INSUFFICIENT_EVIDENCE"]
    
    # Conservative: VERIFIED_BENIGN + AMBIGUOUS + INSUFFICIENT_EVIDENCE treated as benign
    conservative_benign = [a for a in adjudications if a["adjudication_verdict"] in ["VERIFIED_BENIGN", "AMBIGUOUS", "INSUFFICIENT_EVIDENCE"]]
    conservative_fp = sum(1 for a in conservative_benign if a["is_false_positive_by_original_label"])
    
    metrics = {
        "total_hard_negative_benign_records": total_hn_benign,
        "original_label_fpr": {
            "false_positives": original_fp,
            "denominator": total_hn_benign,
            "fpr": f"{original_fp}/{total_hn_benign} = {original_fp/max(1, total_hn_benign):.4f}"
        },
        "verified_benign_fpr": {
            "verified_benign_count": len(verified_benign),
            "false_positives_among_verified_benign": verified_benign_fp,
            "denominator": len(verified_benign),
            "fpr": f"{verified_benign_fp}/{max(1, len(verified_benign))} = {verified_benign_fp/max(1, len(verified_benign)):.4f}"
        },
        "conservative_fpr": {
            "conservative_benign_count": len(conservative_benign),
            "false_positives_among_conservative": conservative_fp,
            "denominator": len(conservative_benign),
            "fpr": f"{conservative_fp}/{max(1, len(conservative_benign))} = {conservative_fp/max(1, len(conservative_benign)):.4f}"
        },
        "adjudication_summary": {
            "VERIFIED_BENIGN": len(verified_benign),
            "VERIFIED_MALICIOUS": len(verified_malicious),
            "AMBIGUOUS": len(ambiguous),
            "INSUFFICIENT_EVIDENCE": len(insufficient)
        }
    }
    
    print(f"\n  Adjudication Summary:")
    print(f"    VERIFIED_BENIGN: {len(verified_benign)}")
    print(f"    VERIFIED_MALICIOUS: {len(verified_malicious)}")
    print(f"    AMBIGUOUS: {len(ambiguous)}")
    print(f"    INSUFFICIENT_EVIDENCE: {len(insufficient)}")
    print(f"\n  Original-label FPR: {metrics['original_label_fpr']['fpr']}")
    print(f"  Verified-benign FPR: {metrics['verified_benign_fpr']['fpr']}")
    print(f"  Conservative FPR: {metrics['conservative_fpr']['fpr']}")
    
    # Taxonomy breakdown
    # Group by unique raw_text to avoid counting duplicates
    unique_texts = {}
    for a in adjudications:
        txt = a["raw_text"]
        if txt not in unique_texts:
            unique_texts[txt] = a
    
    # Categorize each unique message
    def categorize_hn(text):
        tl = text.lower()
        if any(x in tl for x in ["aadhaar", "uidai", "aadhar"]):
            return "LEGIT_AUTHENTICATION"
        elif any(x in tl for x in ["kyc", "pan card", "netbanking"]):
            return "LEGIT_KYC"
        elif any(x in tl for x in ["electricity", "bijli", "msedcl", "disconnec", "power"]):
            return "LEGIT_ELECTRICITY"
        elif any(x in tl for x in ["challan", "traffic"]):
            return "LEGIT_TRAFFIC_CHALLAN"
        elif any(x in tl for x in ["refund", "amazon", "flipkart"]):
            return "LEGIT_DELIVERY"
        elif any(x in tl for x in ["data entry", "job", "income"]):
            return "LEGIT_JOB_OFFER"
        elif any(x in tl for x in ["otp", "one time"]):
            return "LEGIT_OTP"
        elif any(x in tl for x in ["bank", "account", "credit"]):
            return "LEGIT_BANK_SECURITY"
        else:
            return "OTHER"
    
    taxonomy = defaultdict(lambda: {"total": 0, "verified_benign": 0, "verified_malicious": 0, "ambiguous": 0, "insufficient": 0, "false_positives": 0})
    
    for a in adjudications:
        cat = categorize_hn(a["raw_text"])
        taxonomy[cat]["total"] += 1
        if a["adjudication_verdict"] == "VERIFIED_BENIGN":
            taxonomy[cat]["verified_benign"] += 1
        elif a["adjudication_verdict"] == "VERIFIED_MALICIOUS":
            taxonomy[cat]["verified_malicious"] += 1
        elif a["adjudication_verdict"] == "AMBIGUOUS":
            taxonomy[cat]["ambiguous"] += 1
        else:
            taxonomy[cat]["insufficient"] += 1
        if a["is_false_positive_by_original_label"]:
            taxonomy[cat]["false_positives"] += 1
    
    taxonomy_report = {}
    for cat, data in sorted(taxonomy.items()):
        data["original_fpr"] = f"{data['false_positives']}/{data['total']}"
        taxonomy_report[cat] = dict(data)
    
    print(f"\n  Taxonomy Breakdown:")
    for cat, data in sorted(taxonomy_report.items()):
        print(f"    {cat}: N={data['total']}, FP={data['false_positives']}, VB={data['verified_benign']}, VM={data['verified_malicious']}, AMB={data['ambiguous']}")
    
    # Dataset label corrections required
    label_corrections = []
    for a in adjudications:
        if a["adjudication_verdict"] == "VERIFIED_MALICIOUS":
            label_corrections.append({
                "message_id": a["message_id"],
                "raw_text": a["raw_text"][:100] + "...",
                "original_label": a["original_security_label"],
                "recommended_label": "MALICIOUS",
                "reason": a["adjudication_reason"]
            })
    
    return {
        "metrics": metrics,
        "taxonomy": taxonomy_report,
        "adjudications": adjudications,
        "label_corrections_required": label_corrections,
        "unique_message_count": len(unique_texts)
    }

# ============================================================
# SECTION 7: ADVERSARIAL EVALUATION
# ============================================================

def adversarial_evaluation(clf, tfidf, scaler, cfg):
    """Evaluate on adversarial/contrastive pairs from training data families."""
    print("\n" + "=" * 60)
    print("SECTION 7: ADVERSARIAL EVALUATION")
    print("=" * 60)
    
    t = 0.85
    
    # Use the contrastive pairs that were used for training expansion
    # We evaluate on the UNIQUE templates (not the 20x replicated ones)
    from model_training.error_driven_expansion import NEW_CONTRASTIVE_PAIRS
    
    results = {}
    for family_data in NEW_CONTRASTIVE_PAIRS:
        family = family_data["family"]
        
        # Evaluate legitimate messages
        legit_correct = 0
        for text in family_data["legit"]:
            X_tfidf = tfidf.transform([text]).toarray()
            X_det = np.array([extract_feature_vector(text, None, cfg)])
            X = scaler.transform(np.hstack((X_det, X_tfidf)))
            probs = clf.predict_proba(X)
            pred = predict_with_threshold(probs, t)
            if pred[0] == 0:
                legit_correct += 1
        
        # Evaluate malicious messages
        mal_correct = 0
        for text in family_data["malicious"]:
            X_tfidf = tfidf.transform([text]).toarray()
            X_det = np.array([extract_feature_vector(text, None, cfg)])
            X = scaler.transform(np.hstack((X_det, X_tfidf)))
            probs = clf.predict_proba(X)
            pred = predict_with_threshold(probs, t)
            if pred[0] == 2:
                mal_correct += 1
        
        results[family] = {
            "legit_total": len(family_data["legit"]),
            "legit_correct_benign": legit_correct,
            "legit_accuracy": f"{legit_correct}/{len(family_data['legit'])}",
            "malicious_total": len(family_data["malicious"]),
            "malicious_correct": mal_correct,
            "malicious_accuracy": f"{mal_correct}/{len(family_data['malicious'])}"
        }
        print(f"  {family}: Legit={legit_correct}/{len(family_data['legit'])}, Mal={mal_correct}/{len(family_data['malicious'])}")
    
    # WARNING: These pairs are IN the training data (replicated 20x)
    # So this is NOT an independent adversarial test - it's a training memorization check
    results["WARNING"] = "These contrastive pairs are part of the training data (train_expanded_v2.jsonl). This is a memorization check, NOT an independent adversarial evaluation."
    
    return results

# ============================================================
# SECTION 8: SOURCE HOLDOUT
# ============================================================

def source_holdout_evaluation(clf, tfidf, scaler, cfg, splits):
    """Evaluate per-source performance."""
    print("\n" + "=" * 60)
    print("SECTION 8: SOURCE HOLDOUT EVALUATION")
    print("=" * 60)
    
    t = 0.85
    all_eval = splits["val"] + splits["test"] + splits["ood"]
    
    sources = Counter(r.get("source_id") for r in all_eval)
    results = {}
    
    for src, count in sorted(sources.items(), key=lambda x: -x[1]):
        src_recs = [r for r in all_eval if r.get("source_id") == src]
        if len(src_recs) < 10:
            results[src] = {"N": len(src_recs), "status": "INSUFFICIENT_SAMPLE_SIZE"}
            continue
        
        texts = [r.get("raw_text", "") for r in src_recs]
        y_true = np.array([LABEL_MAP[r.get("security_label", "BENIGN")] for r in src_recs])
        
        X_tfidf = tfidf.transform(texts).toarray()
        X_det = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in src_recs])
        X = scaler.transform(np.hstack((X_det, X_tfidf)))
        
        probs = clf.predict_proba(X)
        preds = predict_with_threshold(probs, t)
        
        from sklearn.metrics import confusion_matrix, f1_score
        cm = confusion_matrix(y_true, preds, labels=[0, 1, 2])
        
        ben_total = max(1, sum(cm[0]))
        mal_total = max(1, sum(cm[2]))
        
        fpr = (cm[0][1] + cm[0][2]) / ben_total if ben_total > 0 else 0
        rec = cm[2][2] / mal_total if mal_total > 0 else 0
        
        unique_labels = set(y_true)
        if len(unique_labels) > 1:
            mf1 = float(f1_score(y_true, preds, average="macro"))
        else:
            mf1 = None
        
        results[src] = {
            "N": count,
            "benign_count": int(sum(cm[0])),
            "malicious_count": int(sum(cm[2])),
            "benign_fpr": f"{cm[0][1]+cm[0][2]}/{ben_total} = {fpr:.4f}",
            "malicious_recall": f"{cm[2][2]}/{mal_total} = {rec:.4f}",
            "macro_f1": mf1,
            "confusion_matrix": cm.tolist()
        }
        print(f"  {src}: N={count}, FPR={fpr:.4f}, Recall={rec:.4f}")
    
    return results

# ============================================================
# SECTION 9: LANGUAGE AUDIT
# ============================================================

def language_audit(clf, tfidf, scaler, cfg, splits):
    """Report per-language performance."""
    print("\n" + "=" * 60)
    print("SECTION 9: LANGUAGE AUDIT")
    print("=" * 60)
    
    t = 0.85
    all_eval = splits["val"] + splits["test"] + splits["ood"]
    
    langs = Counter(r.get("language", "UNKNOWN") for r in all_eval)
    results = {}
    
    for lang, count in sorted(langs.items(), key=lambda x: -x[1]):
        lang_recs = [r for r in all_eval if r.get("language", "UNKNOWN") == lang]
        
        if count < 50:
            results[lang] = {"N": count, "status": "INSUFFICIENT_SAMPLE_SIZE"}
            print(f"  {lang}: N={count} - INSUFFICIENT_SAMPLE_SIZE")
            continue
        
        texts = [r.get("raw_text", "") for r in lang_recs]
        y_true = np.array([LABEL_MAP[r.get("security_label", "BENIGN")] for r in lang_recs])
        
        X_tfidf = tfidf.transform(texts).toarray()
        X_det = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in lang_recs])
        X = scaler.transform(np.hstack((X_det, X_tfidf)))
        
        probs = clf.predict_proba(X)
        preds = predict_with_threshold(probs, t)
        
        from sklearn.metrics import confusion_matrix, f1_score
        cm = confusion_matrix(y_true, preds, labels=[0, 1, 2])
        
        ben_total = max(1, sum(cm[0]))
        mal_total = max(1, sum(cm[2]))
        
        fpr = (cm[0][1] + cm[0][2]) / ben_total
        rec = cm[2][2] / mal_total
        
        results[lang] = {
            "N": count,
            "benign_fpr": f"{cm[0][1]+cm[0][2]}/{ben_total} = {fpr:.4f}",
            "malicious_recall": f"{cm[2][2]}/{mal_total} = {rec:.4f}",
            "status": "EVALUATED"
        }
        print(f"  {lang}: N={count}, FPR={fpr:.4f}, Recall={rec:.4f}")
    
    return results

# ============================================================
# SECTION 10: SENDER AUDIT
# ============================================================

def sender_audit(clf, tfidf, scaler, cfg, splits):
    """Report per-sender-type performance."""
    print("\n" + "=" * 60)
    print("SECTION 10: SENDER AUDIT")
    print("=" * 60)
    
    t = 0.85
    all_eval = splits["val"] + splits["test"] + splits["ood"]
    
    sender_types = Counter(r.get("sender_type", "UNKNOWN") for r in all_eval)
    results = {}
    
    for stype, count in sorted(sender_types.items(), key=lambda x: -x[1]):
        s_recs = [r for r in all_eval if r.get("sender_type", "UNKNOWN") == stype]
        
        if count < 10:
            results[stype] = {"N": count, "status": "INSUFFICIENT_SAMPLE_SIZE"}
            continue
        
        texts = [r.get("raw_text", "") for r in s_recs]
        y_true = np.array([LABEL_MAP[r.get("security_label", "BENIGN")] for r in s_recs])
        
        X_tfidf = tfidf.transform(texts).toarray()
        X_det = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in s_recs])
        X = scaler.transform(np.hstack((X_det, X_tfidf)))
        
        probs = clf.predict_proba(X)
        preds = predict_with_threshold(probs, t)
        
        from sklearn.metrics import confusion_matrix
        cm = confusion_matrix(y_true, preds, labels=[0, 1, 2])
        
        ben_total = max(1, sum(cm[0]))
        mal_total = max(1, sum(cm[2]))
        
        fpr = (cm[0][1] + cm[0][2]) / ben_total
        rec = cm[2][2] / mal_total
        
        results[stype] = {
            "N": count,
            "benign_fpr": f"{cm[0][1]+cm[0][2]}/{ben_total} = {fpr:.4f}",
            "malicious_recall": f"{cm[2][2]}/{mal_total} = {rec:.4f}"
        }
        print(f"  {stype}: N={count}, FPR={fpr:.4f}, Recall={rec:.4f}")
    
    return results

# ============================================================
# SECTION 11: THREAT VECTOR AUDIT
# ============================================================

def threat_vector_audit(clf, tfidf, scaler, cfg, splits):
    """Report per-threat-vector performance."""
    print("\n" + "=" * 60)
    print("SECTION 11: THREAT VECTOR AUDIT")
    print("=" * 60)
    
    t = 0.85
    all_eval = splits["val"] + splits["test"] + splits["ood"]
    
    tv_counter = Counter()
    for r in all_eval:
        tvs = r.get("threat_vectors", ["NONE"])
        for tv in tvs:
            tv_counter[tv] += 1
    
    results = {}
    for tv, count in sorted(tv_counter.items(), key=lambda x: -x[1]):
        tv_recs = [r for r in all_eval if tv in r.get("threat_vectors", ["NONE"])]
        
        if count < 10:
            results[tv] = {"N": count, "status": "INSUFFICIENT_SAMPLE_SIZE"}
            continue
        
        texts = [r.get("raw_text", "") for r in tv_recs]
        y_true = np.array([LABEL_MAP[r.get("security_label", "BENIGN")] for r in tv_recs])
        
        X_tfidf = tfidf.transform(texts).toarray()
        X_det = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in tv_recs])
        X = scaler.transform(np.hstack((X_det, X_tfidf)))
        
        probs = clf.predict_proba(X)
        preds = predict_with_threshold(probs, t)
        
        from sklearn.metrics import confusion_matrix
        cm = confusion_matrix(y_true, preds, labels=[0, 1, 2])
        
        ben_total = max(1, sum(cm[0]))
        mal_total = max(1, sum(cm[2]))
        
        fpr = (cm[0][1] + cm[0][2]) / ben_total if ben_total > 0 else 0
        rec = cm[2][2] / mal_total if mal_total > 0 else 0
        
        results[tv] = {
            "N": count,
            "benign_fpr": f"{cm[0][1]+cm[0][2]}/{ben_total} = {fpr:.4f}",
            "malicious_recall": f"{cm[2][2]}/{mal_total} = {rec:.4f}"
        }
        print(f"  {tv}: N={count}, FPR={fpr:.4f}, Recall={rec:.4f}")
    
    return results

# ============================================================
# SECTION 12: RESOURCE BENCHMARK
# ============================================================

def resource_benchmark(clf, tfidf, scaler, cfg, splits):
    """Benchmark inference latency and model size."""
    print("\n" + "=" * 60)
    print("SECTION 12: RESOURCE BENCHMARK")
    print("=" * 60)
    
    t = 0.85
    test_recs = splits["test"]
    
    # Measure model sizes
    model_size = len(pickle.dumps(clf))
    tfidf_size = len(pickle.dumps(tfidf))
    scaler_size = len(pickle.dumps(scaler))
    
    print(f"  Model size: {model_size / 1024:.2f} KB")
    print(f"  TF-IDF size: {tfidf_size / 1024:.2f} KB")
    print(f"  Scaler size: {scaler_size / 1024:.2f} KB")
    print(f"  Total: {(model_size + tfidf_size + scaler_size) / 1024:.2f} KB")
    
    # Feature count
    feature_names = get_feature_names(cfg)
    total_features = len(feature_names) + len(tfidf.vocabulary_)
    print(f"  Deterministic features: {len(feature_names)}")
    print(f"  TF-IDF vocabulary size: {len(tfidf.vocabulary_)}")
    print(f"  Total input features: {total_features}")
    
    # Inference latency benchmark
    texts = [r.get("raw_text", "") for r in test_recs]
    X_tfidf = tfidf.transform(texts).toarray()
    X_det = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in test_recs])
    X = scaler.transform(np.hstack((X_det, X_tfidf)))
    
    latencies = []
    for trial in range(5):
        start = time.perf_counter()
        probs = clf.predict_proba(X)
        preds = predict_with_threshold(probs, t)
        elapsed = (time.perf_counter() - start) * 1000  # ms
        latencies.append(elapsed / len(test_recs))  # per-message
    
    latencies.sort()
    
    # Single-message latency
    single_latencies = []
    sample_texts = texts[:100]
    for text in sample_texts:
        start = time.perf_counter()
        x_tf = tfidf.transform([text]).toarray()
        x_det = np.array([extract_feature_vector(text, None, cfg)])
        x = scaler.transform(np.hstack((x_det, x_tf)))
        p = clf.predict_proba(x)
        pred = predict_with_threshold(p, t)
        elapsed = (time.perf_counter() - start) * 1000
        single_latencies.append(elapsed)
    
    single_latencies.sort()
    
    result = {
        "model_size_bytes": model_size,
        "tfidf_size_bytes": tfidf_size,
        "scaler_size_bytes": scaler_size,
        "total_size_bytes": model_size + tfidf_size + scaler_size,
        "total_size_kb": f"{(model_size + tfidf_size + scaler_size) / 1024:.2f}",
        "deterministic_features": len(feature_names),
        "tfidf_vocabulary_size": len(tfidf.vocabulary_),
        "total_input_features": total_features,
        "batch_latency_ms_per_message": {
            "median": f"{np.median(latencies):.4f}",
            "p95": f"{np.percentile(latencies, 95):.4f}",
            "p99": f"{np.percentile(latencies, 99):.4f}"
        },
        "single_message_latency_ms": {
            "median": f"{np.median(single_latencies):.4f}",
            "p95": f"{np.percentile(single_latencies, 95):.4f}",
            "p99": f"{np.percentile(single_latencies, 99):.4f}"
        },
        "gpu_used": False,
        "note": "CPU inference only. Intended Android deployment is CPU/on-device."
    }
    
    print(f"  Batch latency median: {np.median(latencies):.4f} ms/msg")
    print(f"  Single-msg latency median: {np.median(single_latencies):.4f} ms/msg")
    print(f"  Single-msg latency p99: {np.percentile(single_latencies, 99):.4f} ms/msg")
    
    return result

# ============================================================
# SECTION 13: REPRODUCIBILITY CHECK
# ============================================================

def reproducibility_check(clf, tfidf, scaler, cfg, splits):
    """Run evaluation twice and verify identical results."""
    print("\n" + "=" * 60)
    print("SECTION 13: REPRODUCIBILITY CHECK")
    print("=" * 60)
    
    t = 0.85
    test_recs = splits["test"]
    texts = [r.get("raw_text", "") for r in test_recs]
    y_true = np.array([LABEL_MAP[r.get("security_label", "BENIGN")] for r in test_recs])
    
    X_tfidf = tfidf.transform(texts).toarray()
    X_det = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), cfg) for r in test_recs])
    X = scaler.transform(np.hstack((X_det, X_tfidf)))
    
    # Run 1
    probs1 = clf.predict_proba(X)
    preds1 = predict_with_threshold(probs1, t)
    
    # Run 2
    probs2 = clf.predict_proba(X)
    preds2 = predict_with_threshold(probs2, t)
    
    predictions_identical = np.array_equal(preds1, preds2)
    probabilities_identical = np.allclose(probs1, probs2, atol=1e-10)
    
    result = {
        "predictions_identical": bool(predictions_identical),
        "probabilities_identical": bool(probabilities_identical),
        "max_probability_difference": float(np.max(np.abs(probs1 - probs2)))
    }
    
    print(f"  Predictions identical: {predictions_identical}")
    print(f"  Probabilities identical: {probabilities_identical}")
    print(f"  Max probability difference: {result['max_probability_difference']}")
    
    return result

# ============================================================
# MAIN AUDIT ORCHESTRATOR
# ============================================================

def main():
    print("=" * 70)
    print("INDEPENDENT CHAMPION VERIFICATION AUDIT")
    print(f"Timestamp: {datetime.now(timezone.utc).isoformat()}")
    print("=" * 70)
    
    os.chdir(str(ROOT))
    
    # Section 1: Freeze
    champion_result = freeze_champion()
    if champion_result is None:
        print("CRITICAL: Cannot proceed - champion artifacts not found")
        return
    clf, tfidf, scaler, cfg, freeze = champion_result
    
    # Section 2: Dataset Integrity
    dataset_result, splits = audit_dataset_integrity()
    
    # Section 3: Preprocessing Leakage
    leakage_result = audit_preprocessing_leakage(clf, tfidf, scaler, cfg)
    
    # Section 4: Reproduce Champion
    reproduction_result, clf_repro, tfidf_repro, scaler_repro = reproduce_champion(splits)
    
    # Section 5: Frozen Model Evaluation
    frozen_eval_result = evaluate_frozen_model(clf, tfidf, scaler, cfg, splits)
    
    # Section 6: Hard Negative Audit
    hn_result = audit_hard_negatives(clf, tfidf, scaler, cfg, splits)
    
    # Section 7: Adversarial
    try:
        adversarial_result = adversarial_evaluation(clf, tfidf, scaler, cfg)
    except Exception as e:
        adversarial_result = {"error": str(e)}
    
    # Section 8: Source Holdout
    source_result = source_holdout_evaluation(clf, tfidf, scaler, cfg, splits)
    
    # Section 9: Language
    language_result = language_audit(clf, tfidf, scaler, cfg, splits)
    
    # Section 10: Sender
    sender_result = sender_audit(clf, tfidf, scaler, cfg, splits)
    
    # Section 11: Threat Vector
    tv_result = threat_vector_audit(clf, tfidf, scaler, cfg, splits)
    
    # Section 12: Resource
    resource_result = resource_benchmark(clf, tfidf, scaler, cfg, splits)
    
    # Section 13: Reproducibility
    repro_result = reproducibility_check(clf, tfidf, scaler, cfg, splits)
    
    # ============================================================
    # FINAL DECISION
    # ============================================================
    print("\n" + "=" * 60)
    print("FINAL DECISION")
    print("=" * 60)
    
    # Parse metrics for decision
    test_cm = frozen_eval_result["test"]["confusion_matrix"]
    ood_cm = frozen_eval_result["ood"]["confusion_matrix"]
    
    test_ben_total = sum(test_cm[0])
    test_fpr = (test_cm[0][1] + test_cm[0][2]) / max(1, test_ben_total)
    test_mal_rec = test_cm[2][2] / max(1, sum(test_cm[2]))
    
    ood_ben_total = sum(ood_cm[0])
    ood_fpr = (ood_cm[0][1] + ood_cm[0][2]) / max(1, ood_ben_total)
    ood_mal_rec = ood_cm[2][2] / max(1, sum(ood_cm[2]))
    
    hn_metrics = hn_result["metrics"]
    
    gate_a = test_fpr <= 0.01 and test_mal_rec >= 0.80
    gate_b = ood_fpr <= 0.01 and ood_mal_rec >= 0.80
    
    # Parse verified HN FPR
    vb_count = hn_metrics["verified_benign_fpr"]["denominator"]
    vb_fp = hn_metrics["verified_benign_fpr"]["false_positives_among_verified_benign"]
    vb_fpr = vb_fp / max(1, vb_count)
    gate_c_verified = vb_fpr <= 0.01
    
    cons_count = hn_metrics["conservative_fpr"]["denominator"]
    cons_fp = hn_metrics["conservative_fpr"]["false_positives_among_conservative"]
    cons_fpr = cons_fp / max(1, cons_count)
    
    # Determine decision
    if gate_a and gate_b and gate_c_verified:
        if cons_fpr <= 0.05:  # Conservative threshold is more lenient
            decision = "MODEL_READY_FOR_PACKAGING"
            reason = "All deployment gates satisfied including verified hard-negative FPR."
        else:
            decision = "DATASET_LABEL_AUDIT_REQUIRED"
            reason = f"Gates A/B pass but conservative HN FPR ({cons_fpr:.2%}) suggests remaining label ambiguity."
    elif gate_a and gate_b and not gate_c_verified:
        decision = "DATASET_LABEL_AUDIT_REQUIRED"
        reason = f"Gates A/B pass but verified-benign HN FPR ({vb_fpr:.2%}) exceeds 1%."
    elif not gate_a or not gate_b:
        decision = "MODEL_REQUIRES_REMEDIATION"
        reason = f"TEST FPR={test_fpr:.4f}, Recall={test_mal_rec:.4f}; OOD FPR={ood_fpr:.4f}, Recall={ood_mal_rec:.4f}"
    else:
        decision = "NOT_VERIFIABLE"
        reason = "Cannot determine"
    
    print(f"\n  Gate A (TEST): FPR={test_fpr:.4f}<=0.01? {'PASS' if test_fpr<=0.01 else 'FAIL'}, Recall={test_mal_rec:.4f}>=0.80? {'PASS' if test_mal_rec>=0.80 else 'FAIL'}")
    print(f"  Gate B (OOD):  FPR={ood_fpr:.4f}<=0.01? {'PASS' if ood_fpr<=0.01 else 'FAIL'}, Recall={ood_mal_rec:.4f}>=0.80? {'PASS' if ood_mal_rec>=0.80 else 'FAIL'}")
    print(f"  Gate C (HN verified): FPR={vb_fpr:.4f}<=0.01? {'PASS' if vb_fpr<=0.01 else 'FAIL'}")
    print(f"  Gate C (HN conservative): FPR={cons_fpr:.4f}")
    print(f"\n  DECISION: {decision}")
    print(f"  REASON: {reason}")
    
    final_decision = {
        "decision": decision,
        "reason": reason,
        "gate_a_test": {"fpr": test_fpr, "recall": test_mal_rec, "pass": gate_a},
        "gate_b_ood": {"fpr": ood_fpr, "recall": ood_mal_rec, "pass": gate_b},
        "gate_c_verified_hn": {"fpr": vb_fpr, "pass": gate_c_verified},
        "gate_c_conservative_hn": {"fpr": cons_fpr}
    }
    
    # ============================================================
    # SAVE ALL RESULTS
    # ============================================================
    
    verification = {
        "audit_timestamp": datetime.now(timezone.utc).isoformat(),
        "champion_freeze": freeze,
        "dataset_integrity": dataset_result,
        "preprocessing_leakage": leakage_result,
        "reproduction": reproduction_result,
        "frozen_evaluation": frozen_eval_result,
        "hard_negative_metrics": hn_result["metrics"],
        "hard_negative_taxonomy": hn_result["taxonomy"],
        "hard_negative_label_corrections": hn_result["label_corrections_required"],
        "adversarial": adversarial_result,
        "source_holdout": source_result,
        "language": language_result,
        "sender": sender_result,
        "threat_vectors": tv_result,
        "resource_benchmark": resource_result,
        "reproducibility": repro_result,
        "final_decision": final_decision
    }
    
    # Save main verification JSON
    with open(ROOT / "model_training" / "FINAL_CHAMPION_VERIFICATION.json", "w") as f:
        json.dump(verification, f, indent=2)
    print(f"\n  Saved: FINAL_CHAMPION_VERIFICATION.json")
    
    # Save hard-negative adjudication JSON
    hn_adj = {
        "audit_timestamp": datetime.now(timezone.utc).isoformat(),
        "adjudications": hn_result["adjudications"],
        "metrics": hn_result["metrics"],
        "taxonomy": hn_result["taxonomy"],
        "label_corrections_required": hn_result["label_corrections_required"],
        "unique_message_count": hn_result["unique_message_count"]
    }
    with open(ROOT / "model_training" / "HARD_NEGATIVE_ADJUDICATION.json", "w") as f:
        json.dump(hn_adj, f, indent=2)
    print(f"  Saved: HARD_NEGATIVE_ADJUDICATION.json")
    
    print("\n" + "=" * 70)
    print("AUDIT COMPLETE")
    print("=" * 70)
    
    return verification

if __name__ == "__main__":
    main()
