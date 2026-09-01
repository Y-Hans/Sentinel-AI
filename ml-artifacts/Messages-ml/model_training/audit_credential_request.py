"""Record-level audit of the CREDENTIAL_REQUEST evaluation population.

This is an audit, not a relabeling operation.  The verdict rules are deliberately
conservative and are stored with every record so the review is reproducible.
"""
import hashlib, json, pickle, sys
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np
from sklearn.ensemble import HistGradientBoostingClassifier
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics import confusion_matrix
from sklearn.preprocessing import StandardScaler

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from feature_config import FeatureConfig
from feature_extraction import extract_feature_vector

LABEL_MAP = {"BENIGN": 0, "SUSPICIOUS_SPAM": 1, "MALICIOUS": 2}
THRESHOLD = 0.823

def load(name):
    with open(ROOT / "data" / "processed" / name, encoding="utf-8") as f:
        return [json.loads(x) for x in f if x.strip()]

def verdict(text):
    t = text.lower()
    anti = ("never share" in t or "do not share" in t or "don't share" in t or
            "dont share" in t or "never disclose" in t or "do not disclose" in t or
            "don't disclose" in t or "will never ask" in t or "beware of phishing" in t or
            "protect yourself from phishing" in t)
    if anti and any(x in t for x in ("otp", "password", "pin", "cvv", "credential", "login")):
        return "ANTI_PHISHING", "Explicit warning not to disclose authentication information"
    explicit = (any(x in t for x in ("share your otp", "send your otp", "reply with your otp",
                                      "provide your otp", "enter your otp", "share password",
                                      "send password", "share your pin", "share cvv",
                                      "enter your password", "enter your pin", "submit your")) or
                (any(x in t for x in ("login", "log in", "verify", "update", "confirm")) and
                 any(x in t for x in ("password", "pin", "otp", "cvv", "credential"))))
    if explicit:
        return "VERIFIED_MALICIOUS", "Direct request or induced entry of OTP/password/PIN/CVV/credentials"
    social = any(x in t for x in ("click", "link", "account suspended", "account blocked", "urgent",
                                  "within", "expire", "verify your account", "kyc", "restricted"))
    if social:
        return "SUSPICIOUS", "Social-engineering or phishing-like context without an explicit credential request"
    if any(x in t for x in ("babe", "baby", "dear", "love", "miss you", "what are you up to")):
        return "AMBIGUOUS", "Conversational/romance-like content lacks sufficient authentication-theft evidence"
    return "AMBIGUOUS", "Credential-request taxonomy label is not supported by explicit intent in text"

def predict(probs, t=THRESHOLD):
    out = np.zeros(len(probs), dtype=int)
    for i, p in enumerate(probs):
        if p[1] + p[2] >= t:
            out[i] = 1 if p[1] > p[2] else 2
    return out

def main():
    val, test, ood = load("val.jsonl"), load("test.jsonl"), load("ood.jsonl")
    train = load("train_expanded_v5.jsonl")
    evals = [("VAL", val), ("TEST", test), ("OOD", ood)]
    records = []
    for split, rs in evals:
        records.extend([{**r, "_split": split} for r in rs if "CREDENTIAL_REQUEST" in r.get("threat_vectors", [])])
    texts = [r.get("raw_text", "") for r in train]
    y = np.array([LABEL_MAP[r["security_label"]] for r in train])
    tw = TfidfVectorizer(max_features=1500, stop_words="english", ngram_range=(1, 2))
    tc = TfidfVectorizer(max_features=500, ngram_range=(3, 5), analyzer="char_wb")
    xd = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), FeatureConfig()) for r in train])
    X = np.hstack((xd, tw.fit_transform(texts).toarray(), tc.fit_transform(texts).toarray()))
    sc = StandardScaler(); X = sc.fit_transform(X)
    clf = HistGradientBoostingClassifier(random_state=42, max_depth=7, max_iter=300, class_weight="balanced")
    clf.fit(X, y)
    def prep(rs):
        tx = [r.get("raw_text", "") for r in rs]
        d = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), FeatureConfig()) for r in rs])
        return sc.transform(np.hstack((d, tw.transform(tx).toarray(), tc.transform(tx).toarray())))
    probs = clf.predict_proba(prep(records)); preds = predict(probs)
    audited = []
    for r, p, pr in zip(records, probs, preds):
        v, reason = verdict(r.get("raw_text", ""))
        audited.append({"message_id": r.get("message_id"), "split": r["_split"], "source_id": r.get("source_id"),
                        "raw_text": r.get("raw_text", ""), "original_security_label": r.get("security_label"),
                        "threat_vectors": r.get("threat_vectors", []), "audit_verdict": v, "audit_reason": reason,
                        "model_prediction": ["BENIGN", "SUSPICIOUS_SPAM", "MALICIOUS"][pr],
                        "p_non_benign": float(p[1] + p[2]), "model_missed_original_malicious": bool(pr != 2),
                        "model_missed_verified_malicious": bool(v == "VERIFIED_MALICIOUS" and pr != 2)})
    counts = Counter(x["audit_verdict"] for x in audited)
    pred_counts = Counter(x["model_prediction"] for x in audited)
    by_split = {s: {"total": sum(x["split"] == s for x in audited),
                    "verdicts": dict(Counter(x["audit_verdict"] for x in audited if x["split"] == s)),
                    "missed_original_malicious": sum(x["split"] == s and x["model_missed_original_malicious"] for x in audited)} for s, _ in evals}
    dup = defaultdict(list)
    for x in audited: dup[hashlib.sha256(x["raw_text"].encode("utf-8")).hexdigest()].append(x["message_id"])
    duplicate_groups = [ids for ids in dup.values() if len(ids) > 1]
    reps = {v: next((x for x in audited if x["audit_verdict"] == v), None) for v in counts}
    out = {"audit_version": "1.0", "model": {"recipe": "train_expanded_v5; word 1500 + char 500; HistGBM depth 7 iter 300 seed 42", "threshold": THRESHOLD},
           "population": {"total": len(audited), "original_label_counts": dict(Counter(x["original_security_label"] for x in audited)),
                          "audit_verdict_counts": dict(counts), "model_prediction_counts": dict(pred_counts), "by_split": by_split},
           "duplicates": {"unique_texts": len(dup), "duplicate_groups": len(duplicate_groups), "records_in_duplicate_groups": sum(map(len, duplicate_groups)), "groups": duplicate_groups[:100]},
           "records": audited, "representative_examples": reps,
           "interpretation": {"metric_uses_original_labels": True, "labels_modified": False,
                              "verified_malicious_missed": sum(x["model_missed_verified_malicious"] for x in audited),
                              "original_malicious_missed": sum(x["model_missed_original_malicious"] for x in audited)}}
    with open(ROOT / "model_training" / "CREDENTIAL_REQUEST_AUDIT.json", "w", encoding="utf-8") as f: json.dump(out, f, indent=2, ensure_ascii=False)
    print(json.dumps({"counts": counts, "predictions": pred_counts, "duplicates": len(duplicate_groups), "verified_malicious_missed": out["interpretation"]["verified_malicious_missed"], "original_missed": out["interpretation"]["original_malicious_missed"]}, indent=2))

if __name__ == "__main__": main()
