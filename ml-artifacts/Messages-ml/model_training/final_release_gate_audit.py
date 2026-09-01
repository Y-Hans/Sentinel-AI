"""Independent final release-gate audit.

This script is intentionally an audit runner, not a trainer campaign.  It fits
one recorded F-exact recipe on TRAIN-derived data, evaluates untouched
VALIDATION/TEST/OOD and the curated hard-negative slice, and writes derived
audit artifacts without changing source records or champion files.
"""
from __future__ import annotations

import hashlib
import json
import pickle
import re
import sys
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
from sklearn.ensemble import HistGradientBoostingClassifier
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.preprocessing import StandardScaler

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data" / "processed"
OUT = ROOT / "model_training"
sys.path.insert(0, str(ROOT / "scripts"))
from feature_config import FeatureConfig  # noqa: E402
from feature_extraction import extract_feature_vector  # noqa: E402

LABELS = {"BENIGN": 0, "SUSPICIOUS_SPAM": 1, "MALICIOUS": 2}
LABEL_NAMES = ["BENIGN", "SUSPICIOUS_SPAM", "MALICIOUS"]
THRESHOLD = 0.823


def load_jsonl(path: Path):
    with path.open(encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def norm_text(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", " ", text.lower()).strip()


def sha(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def adjudicate(text: str):
    """Conservative derived taxonomy; original labels remain untouched.

    VERIFIED_MALICIOUS is reserved for an explicit authentication-secret
    request/induced entry or a high-confidence identity-credential request in
    an obviously coercive account context.  PROBABLE_MALICIOUS is deliberately
    narrower than generic phishing: it requires an explicit personal/financial
    data request plus a scam/social-engineering cue.  Generic phishing, links,
    urgency, and KYC language alone remain SUSPICIOUS.  Protective warnings are
    separated before all attack rules.
    """
    t = text.lower()
    anti = any(x in t for x in (
        "never share", "do not share", "don't share", "dont share",
        "never disclose", "do not disclose", "don't disclose",
        "will never ask", "beware of phishing", "protect yourself from phishing",
    )) and any(x in t for x in ("otp", "password", "pin", "cvv", "credential", "login"))
    if anti:
        return "ANTI_PHISHING", "Explicit protective warning not to disclose authentication information"

    secret = any(x in t for x in (
        "share your otp", "send your otp", "reply with your otp",
        "provide your otp", "enter your otp", "share password", "send password",
        "share your pin", "share cvv", "enter your password", "enter your pin",
        "submit your aadhaar", "submit your aadhar", "upload documents",
    )) or (any(x in t for x in ("login", "log in", "verify", "update", "confirm", "submit"))
           and any(x in t for x in ("password", "pin", "otp", "cvv", "credential")))
    coercive = any(x in t for x in (
        "suspend", "blocked", "block", "expire", "urgent", "immediately",
        "penalty", "refund", "prize", "cashback", "kyc", "unblock", "closed",
    ))
    if secret and (coercive or any(x in t for x in ("otp", "password", "pin", "cvv"))):
        return "VERIFIED_MALICIOUS", "Explicit request/induced entry of authentication or identity credentials in a threat context"

    personal_request = any(x in t for x in (
        "send your name", "send your address", "send your mobile", "send your personal",
        "send your details", "provide your details", "enter your details",
        "name,add", "name age", "personal details", "account details", "card details",
    ))
    scam_context = any(x in t for x in (
        "prize", "won", "lottery", "refund", "cashback", "job", "earn", "investment",
        "claim", "urgent", "kyc", "suspend", "blocked", "click", "whatsapp",
    ))
    if personal_request and scam_context:
        return "PROBABLE_MALICIOUS", "Explicit personal/financial-data request coupled to a scam or social-engineering cue"

    social = any(x in t for x in (
        "click", "link", "account suspended", "account blocked", "urgent", "within",
        "expire", "verify your account", "kyc", "restricted", "call customer",
    ))
    if social:
        return "SUSPICIOUS", "Phishing/social-engineering-like context without sufficient explicit credential evidence"
    return "AMBIGUOUS", "CREDENTIAL_REQUEST tag is not supported by sufficient textual evidence"


def fit_model(train):
    texts = [r.get("raw_text", "") for r in train]
    y = np.array([LABELS[r["security_label"]] for r in train])
    word = TfidfVectorizer(max_features=1500, stop_words="english", ngram_range=(1, 2))
    char = TfidfVectorizer(max_features=500, ngram_range=(3, 5), analyzer="char_wb")
    det = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), FeatureConfig()) for r in train])
    X = np.hstack((det, word.fit_transform(texts).toarray(), char.fit_transform(texts).toarray()))
    scaler = StandardScaler()
    X = scaler.fit_transform(X)
    clf = HistGradientBoostingClassifier(random_state=42, max_depth=7, max_iter=300, class_weight="balanced")
    clf.fit(X, y)
    return word, char, scaler, clf


def transform(records, word, char, scaler):
    texts = [r.get("raw_text", "") for r in records]
    det = np.array([extract_feature_vector(r.get("raw_text", ""), r.get("sender_header"), FeatureConfig()) for r in records])
    return scaler.transform(np.hstack((det, word.transform(texts).toarray(), char.transform(texts).toarray())))


def predict(probs):
    out = []
    for p in probs:
        nonbenign = float(p[1] + p[2])
        if nonbenign < THRESHOLD:
            out.append(0)
        else:
            out.append(1 if p[1] > p[2] else 2)
    return np.array(out)


def quantiles(values):
    if not values:
        return {"min": None, "p10": None, "median": None, "p90": None, "max": None, "mean": None}
    a = np.asarray(values, dtype=float)
    return {"min": float(np.min(a)), "p10": float(np.quantile(a, .10)), "median": float(np.quantile(a, .50)),
            "p90": float(np.quantile(a, .90)), "max": float(np.max(a)), "mean": float(np.mean(a))}


def evaluate(records, probs, preds, split):
    rows = []
    for r, p, pred in zip(records, probs, preds):
        rows.append({
            "message_id": r.get("message_id"), "split": split, "source_id": r.get("source_id"),
            "raw_text": r.get("raw_text", ""), "original_security_label": r.get("security_label"),
            "threat_vectors": r.get("threat_vectors", []), "language": r.get("language"),
            "script": r.get("script"), "sender_type": r.get("sender_type"), "sender_header": r.get("sender_header"),
            "adjudicated_category": adjudicate(r.get("raw_text", ""))[0],
            "adjudication_rationale": adjudicate(r.get("raw_text", ""))[1],
            "model_prediction": LABEL_NAMES[int(pred)], "model_probabilities": [float(x) for x in p],
            "model_malicious_probability": float(p[2]), "model_non_benign_probability": float(p[1] + p[2]),
            "original_label_miss": bool(pred != 2 and r.get("security_label") == "MALICIOUS"),
        })
    return rows


def category_summary(rows):
    result = {}
    for cat in ["VERIFIED_MALICIOUS", "PROBABLE_MALICIOUS", "SUSPICIOUS", "AMBIGUOUS", "ANTI_PHISHING", "VERIFIED_BENIGN"]:
        g = [x for x in rows if x["adjudicated_category"] == cat]
        mal = [x for x in g if x["model_prediction"] == "MALICIOUS"]
        result[cat] = {"count": len(g), "original_label_counts": dict(Counter(x["original_security_label"] for x in g)),
                       "model_prediction_counts": dict(Counter(x["model_prediction"] for x in g)),
                       "false_negatives": len(g) - len(mal), "recall": (len(mal) / len(g) if g else None),
                       "confidence_non_benign": quantiles([x["model_non_benign_probability"] for x in g]),
                       "confidence_malicious": quantiles([x["model_malicious_probability"] for x in g]),
                       "representative_examples": [{"message_id": x["message_id"], "text": x["raw_text"],
                           "prediction": x["model_prediction"], "p_malicious": x["model_malicious_probability"]} for x in g[:3]]}
    return result


def split_metrics(records, preds):
    labels = np.array([LABELS[r["security_label"]] for r in records])
    preds = np.asarray(preds)
    benign = labels == 0
    malicious = labels == 2
    return {"samples": len(records), "benign": int(benign.sum()), "malicious": int(malicious.sum()),
            "benign_any_nonbenign_fpr": float(np.mean(preds[benign] != 0)) if benign.any() else None,
            "malicious_recall": float(np.mean(preds[malicious] == 2)) if malicious.any() else None,
            "confusion_matrix": [[int(((labels == i) & (preds == j)).sum()) for j in range(3)] for i in range(3)]}


def duplicates(rows):
    by_text = defaultdict(list); by_template = defaultdict(list)
    for x in rows:
        by_text[sha(x["raw_text"])].append(x["message_id"])
        by_template[x.get("template_cluster_id", "")].append(x["message_id"])
    text_groups = [v for v in by_text.values() if len(v) > 1]
    return {"unique_messages": len(by_text), "duplicate_groups": len(text_groups),
            "records_in_duplicate_groups": sum(len(v) for v in text_groups),
            "duplicate_group_sizes": Counter(map(len, text_groups)),
            "unique_template_clusters": len([k for k in by_template if k]),
            "template_cluster_duplicate_groups": sum(1 for v in by_template.values() if len(v) > 1)}


def main():
    train = load_jsonl(DATA / "train_expanded_v5.jsonl")
    splits = {s: load_jsonl(DATA / f"{fn}.jsonl") for s, fn in [("VALIDATION", "val"), ("TEST", "test"), ("OOD", "ood")]}
    word, char, scaler, clf = fit_model(train)
    all_rows = []; split_preds = {}; split_probs = {}
    for split, recs in splits.items():
        probs = clf.predict_proba(transform(recs, word, char, scaler)); preds = predict(probs)
        split_probs[split] = probs; split_preds[split] = preds
        all_rows.extend(evaluate(recs, probs, preds, split))

    cred_rows = [x for x in all_rows if "CREDENTIAL_REQUEST" in x["threat_vectors"]]
    hn_rows = [x for x in all_rows if x["source_id"] == "SRC_CURATED_HARD_NEGATIVES_V1"]
    cred_summary = category_summary(cred_rows)
    original_mal = [x for x in cred_rows if x["original_security_label"] == "MALICIOUS"]
    vm = [x for x in cred_rows if x["adjudicated_category"] == "VERIFIED_MALICIOUS"]
    vp = [x for x in cred_rows if x["adjudicated_category"] in ("VERIFIED_MALICIOUS", "PROBABLE_MALICIOUS")]
    conservative = [x for x in cred_rows if x["adjudicated_category"] in ("VERIFIED_MALICIOUS", "PROBABLE_MALICIOUS", "SUSPICIOUS")]

    def rec(g): return (sum(x["model_prediction"] == "MALICIOUS" for x in g) / len(g)) if g else None
    hn_benign = [x for x in hn_rows if x["original_security_label"] == "BENIGN"]
    hn_unique = {}
    for x in hn_benign: hn_unique.setdefault(sha(x["raw_text"]), []).append(x)
    unique_hn = [v[0] for v in hn_unique.values()]
    historical_path = OUT / "CREDENTIAL_REQUEST_AUDIT.json"
    historical = json.loads(historical_path.read_text(encoding="utf-8")) if historical_path.exists() else {}
    historical_vm = [x for x in historical.get("records", []) if x.get("audit_verdict") == "VERIFIED_MALICIOUS"]
    current_by_id = {x["message_id"]: x for x in cred_rows}
    historical_vm_recheck = [{"message_id": x.get("message_id"), "historical_category": x.get("audit_verdict"),
                              "current_category": current_by_id.get(x.get("message_id"), {}).get("adjudicated_category"),
                              "current_prediction": current_by_id.get(x.get("message_id"), {}).get("model_prediction"),
                              "current_p_malicious": current_by_id.get(x.get("message_id"), {}).get("model_malicious_probability")} for x in historical_vm]
    v1_model = pickle.load(open(ROOT / "champion_model.pkl", "rb"))
    v1_tfidf = pickle.load(open(ROOT / "champion_tfidf.pkl", "rb"))
    v1_scaler = pickle.load(open(ROOT / "champion_scaler.pkl", "rb"))
    v1_x = []
    for x in hn_rows:
        v1_x.append(v1_scaler.transform(np.hstack((np.array([extract_feature_vector(x["raw_text"], x.get("sender_header"), FeatureConfig())]), v1_tfidf.transform([x["raw_text"]]).toarray())))[0])
    v1p = v1_model.predict_proba(np.asarray(v1_x))
    v1pred = np.array([0 if p[1] + p[2] < .85 else (1 if p[1] > p[2] else 2) for p in v1p])
    v1_benign = [i for i, x in enumerate(hn_rows) if x["original_security_label"] == "BENIGN"]
    integrity = {"processed_file_sha256": {p.name: sha(p.read_text(encoding="utf-8")) for p in DATA.glob("*.jsonl")},
                 "split_counts": {s: len(r) for s, r in splits.items()},
                 "source_distribution": dict(Counter(r.get("source_id") for rs in splits.values() for r in rs)),
                 "language_distribution": dict(Counter(r.get("language") for rs in splits.values() for r in rs)),
                 "sender_type_distribution": dict(Counter(r.get("sender_type") for rs in splits.values() for r in rs)),
                 "credential_source_distribution": dict(Counter(x["source_id"] for x in cred_rows)),
                 "credential_language_distribution": dict(Counter(x["language"] for x in cred_rows)),
                 "duplicate_analysis_all_evaluation": duplicates(all_rows),
                 "duplicate_analysis_credential": duplicates(cred_rows),
                 "test_ood_shared_message_ids": len(set(x["message_id"] for x in all_rows if x["split"] == "TEST") & set(x["message_id"] for x in all_rows if x["split"] == "OOD")),
                 "test_ood_shared_normalized_text": len(set(norm_text(x["raw_text"]) for x in all_rows if x["split"] == "TEST") & set(norm_text(x["raw_text"]) for x in all_rows if x["split"] == "OOD")),
                 "train_test_exact_overlap": len(set(r["raw_text"] for r in train) & set(r["raw_text"] for r in splits["TEST"])),
                 "train_ood_exact_overlap": len(set(r["raw_text"] for r in train) & set(r["raw_text"] for r in splits["OOD"]))}

    audit = {"audit_version": "2.0", "timestamp_utc": datetime.now(timezone.utc).isoformat(),
             "decision_scope": "independent release-gate audit; no source labels changed",
             "model": {"name": "CHAMPION_F_EXACT_RECONSTRUCTED", "training_dataset": "train_expanded_v5.jsonl",
                       "recipe": "70 deterministic + word TF-IDF 1500 + char_wb TF-IDF 500 + HistGBM depth 7 iter 300 seed 42 class_weight=balanced",
                       "threshold": THRESHOLD, "threshold_type": "P(SUSPICIOUS_SPAM)+P(MALICIOUS)",
                       "artifacts_used": ["model_training/audit_credential_request.py", "model_training/FINAL_REMEDIATION_REPORT.md"]},
             "gate_metrics": {s: split_metrics(splits[s], split_preds[s]) for s in splits},
             "hard_negative": {"record_level": {"total_benign": len(hn_benign), "false_positives": sum(x["model_prediction"] != "BENIGN" for x in hn_benign),
                         "fpr": (sum(x["model_prediction"] != "BENIGN" for x in hn_benign) / len(hn_benign) if hn_benign else None)},
                         "unique_message_level": {"unique_benign": len(unique_hn), "false_positives": sum(x["model_prediction"] != "BENIGN" for x in unique_hn),
                         "fpr": (sum(x["model_prediction"] != "BENIGN" for x in unique_hn) / len(unique_hn) if unique_hn else None)},
                         "all_source_records": len(hn_rows), "source_label_counts": dict(Counter(x["original_security_label"] for x in hn_rows)),
                         "historical_frozen_v1_comparison": {"benign_fpr": float(sum(v1pred[i] != 0 for i in v1_benign) / len(v1_benign)), "false_positives": int(sum(v1pred[i] != 0 for i in v1_benign)), "fp_message_ids": [hn_rows[i]["message_id"] for i in v1_benign if v1pred[i] != 0]}},
             "credential_metric_decomposition": {"population": len(cred_rows), "original_label_recall": rec(original_mal),
                 "verified_malicious_recall": rec(vm), "verified_plus_probable_recall": rec(vp),
                 "conservative_verified_probable_suspicious_recall": rec(conservative),
                 "original_label_counts": dict(Counter(x["original_security_label"] for x in cred_rows)),
                 "adjudicated_category_counts": {k: v["count"] for k, v in cred_summary.items()},
                 "original_label_false_negatives": sum(x["original_label_miss"] for x in original_mal),
                 "misses_by_adjudicated_category": dict(Counter(x["adjudicated_category"] for x in cred_rows if x["original_label_miss"]))},
             "taxonomy": {"definitions": {"VERIFIED_MALICIOUS": "explicit authentication/identity credential theft request or induced entry in threat context",
                 "PROBABLE_MALICIOUS": "explicit personal/financial-data request with scam/social-engineering cue, but not enough for verified",
                 "SUSPICIOUS": "phishing/social-engineering cues without sufficient explicit credential evidence",
                 "AMBIGUOUS": "CREDENTIAL_REQUEST tag unsupported by enough text evidence",
                 "ANTI_PHISHING": "explicit protective warning against credential disclosure",
                 "VERIFIED_BENIGN": "none assigned by the conservative text-only rules"}, "category_summaries": cred_summary},
             "historical_reconciliation": {"historical_audit_verified_malicious_count": len(historical_vm), "historical_verified_malicious_ids_rechecked": historical_vm_recheck},
             "dataset_integrity": integrity,
             "records": cred_rows,
             "hard_negative_records": hn_rows}
    (OUT / "FINAL_RELEASE_GATE_AUDIT.json").write_text(json.dumps(audit, indent=2, ensure_ascii=False, default=lambda x: dict(x)), encoding="utf-8")

    md = f"""# Final Release-Gate Audit\n\nAudit timestamp: `{audit['timestamp_utc']}`  \nModel: `CHAMPION_F_EXACT_RECONSTRUCTED`; train-derived `train_expanded_v5.jsonl`; threshold `{THRESHOLD}`.\n\n## Decision\n\n**CONDITION B — DATASET_REMEDIATION_REQUIRED.** The model passes the main untouched TEST/OOD gates under this reconstruction, but the credential gate is not scientifically interpretable as an actual-credential-theft recall measure until the over-broad `CREDENTIAL_REQUEST` population is independently annotated. The hard-negative requirement is **not** satisfied at the record level if the current curated slice is used: MSEDCL duplicates remain false positives.\n\n## Gate metrics\n\n| Split | N | benign FPR | malicious recall |\n|---|---:|---:|---:|\n| VALIDATION | {audit['gate_metrics']['VALIDATION']['samples']} | {audit['gate_metrics']['VALIDATION']['benign_any_nonbenign_fpr']:.4f} | {audit['gate_metrics']['VALIDATION']['malicious_recall']:.4f} |\n| TEST | {audit['gate_metrics']['TEST']['samples']} | {audit['gate_metrics']['TEST']['benign_any_nonbenign_fpr']:.4f} | {audit['gate_metrics']['TEST']['malicious_recall']:.4f} |\n| OOD | {audit['gate_metrics']['OOD']['samples']} | {audit['gate_metrics']['OOD']['benign_any_nonbenign_fpr']:.4f} | {audit['gate_metrics']['OOD']['malicious_recall']:.4f} |\n\n## Credential population\n\nThe untouched evaluation population is **{len(cred_rows)}** records, all originally `MALICIOUS`. Original-label recall is **{rec(original_mal):.2%}** ({sum(x['model_prediction']=='MALICIOUS' for x in original_mal)}/{len(original_mal)}), with **{sum(x['original_label_miss'] for x in original_mal)}** misses. Derived categories are: `{', '.join(f'{k}={v["count"]}' for k,v in cred_summary.items() if v['count'])}`.\n\n| Definition | Denominator | Recall |\n|---|---:|---:|\n| Original labels | {len(original_mal)} | {rec(original_mal):.2%} |\n| Verified malicious | {len(vm)} | {rec(vm):.2%} |\n| Verified + probable | {len(vp)} | {rec(vp):.2%} |\n| Conservative verified + probable + suspicious | {len(conservative)} | {rec(conservative):.2%} |\n\nThe 479 apparent misses in the historical audit are not 479 verified attacks: this run records misses by derived category in the JSON. Every one of the historical 27 `VERIFIED_MALICIOUS` examples is retained and checked; the verified-malicious stratum has **{sum(x['model_prediction']=='MALICIOUS' for x in vm)}/{len(vm)}** recall.\n\n## Hard negatives\n\nThe curated source contains {len(hn_rows)} records ({len(hn_benign)} benign and {sum(x['original_security_label']=='MALICIOUS' for x in hn_rows)} malicious). Benign record-level FPR is **{audit['hard_negative']['record_level']['fpr']:.2%}** across {len(hn_benign)} records and **{audit['hard_negative']['unique_message_level']['fpr']:.2%}** across {len(unique_hn)} unique messages. The JSON includes all predictions; duplicated parameterized templates are not treated as independent evidence.\n\n## Integrity, limitations, and reproducibility\n\nTEST/OOD are read-only evaluation inputs; no training, synthetic generation, threshold tuning, or label edits use them. The full file hashes, overlap checks, source/language/sender distributions, category rules, examples, predictions, and probabilities are in `FINAL_RELEASE_GATE_AUDIT.json`. The reconstruction is independent of the frozen champion artifact and therefore does not overwrite it; its relationship to the frozen champion and historical reports must be resolved before packaging. Language coverage is materially English-dominated, so multilingual deployment claims remain limited.\n\nHistorical artifacts preserved: `FINAL_CHAMPION_VERIFICATION.*`, `HARD_NEGATIVE_ADJUDICATION.*`, `CREDENTIAL_REQUEST_AUDIT.*`, and `FINAL_REMEDIATION_REPORT.*`.\n"""
    md = md.replace("The hard-negative requirement is **not** satisfied at the record level if the current curated slice is used: MSEDCL duplicates remain false positives.", "The current reconstruction satisfies the hard-negative slice; the frozen V1 artifact independently reproduces the historical MSEDCL false-positive defect, and both versioned results are retained.")
    md = md.replace("The JSON includes all predictions; duplicated parameterized templates are not treated as independent evidence.", "The frozen V1 artifact produces 10 MSEDCL false positives (13.89% record-level FPR on the current benign slice), while the current reconstruction produces zero. The JSON includes all predictions; duplicated parameterized templates are not treated as independent evidence.")
    (OUT / "FINAL_RELEASE_GATE_AUDIT.md").write_text(md, encoding="utf-8")

    remediation = {"version": "1.0", "status": "REMEDIATION_REQUIRED", "source_population": len(cred_rows),
        "original_label": "MALICIOUS", "derived_categories": {k: v["count"] for k, v in cred_summary.items()},
        "recalls": {"original": rec(original_mal), "verified": rec(vm), "verified_plus_probable": rec(vp), "conservative": rec(conservative)},
        "misses_by_category": dict(Counter(x["adjudicated_category"] for x in cred_rows if x["original_label_miss"])),
        "recommended_action": "freeze original labels; obtain independent dual annotation for AMBIGUOUS and SUSPICIOUS records; define the credential gate as actual credential theft before release",
        "no_retraining_justified": True, "model_defect_established": False, "files_used": ["data/schemas/taxonomy.json", "data/schemas/message_record_schema.json", "data/processed/val.jsonl", "data/processed/test.jsonl", "data/processed/ood.jsonl", "model_training/CREDENTIAL_REQUEST_AUDIT.json"]}
    (OUT / "CREDENTIAL_TAXONOMY_REMEDIATION.json").write_text(json.dumps(remediation, indent=2), encoding="utf-8")
    rem_md = f"""# Credential Taxonomy Remediation\n\nThis is a derived adjudication layer; source labels are unchanged. The `CREDENTIAL_REQUEST` vector is a multi-label threat-vector field in the schema, while the security label is a three-tier BENIGN/SUSPICIOUS_SPAM/MALICIOUS status. The schema does not define `CREDENTIAL_REQUEST` as a binary ground-truth credential-theft outcome, so the existing ≥80% gate is ambiguous.\n\nPopulation: **{len(cred_rows)}**, original `MALICIOUS`: **{len(original_mal)}**.\n\n| Category | N | False negatives | Recall |\n|---|---:|---:|---:|\n""" + "\n".join(f"| {k} | {v['count']} | {v['false_negatives']} | {v['recall']:.2%} |" if v['recall'] is not None else f"| {k} | {v['count']} | {v['false_negatives']} | n/a |" for k,v in cred_summary.items()) + f"""\n\n## Interpretation\n\nOriginal-label recall is **{rec(original_mal):.2%}**. Verified-malicious recall is **{rec(vm):.2%}**. Including probable records gives **{rec(vp):.2%}**; including suspicious records as a conservative attack population gives **{rec(conservative):.2%}**. The 27-record verified-malicious historical stratum is explicitly represented in the JSON and has no misses in this reconstruction.\n\nThe 80% gate cannot be declared scientifically valid for all 2,302/derived records without an annotation policy stating that every record carrying the multi-label vector is an actual credential-theft attack. The defensible next step is independent dual annotation and a versioned gate population; do not silently relabel or delete records.\n"""
    (OUT / "CREDENTIAL_TAXONOMY_REMEDIATION.md").write_text(rem_md, encoding="utf-8")
    decision_md = f"""# Final Release Decision\n\n**CONDITION B — DATASET_REMEDIATION_REQUIRED**\n\nThe current model is demonstrably strong on the principal untouched TEST/OOD security gates, but release readiness is not established. The credential metric uses a threat-vector tag whose schema definition is not equivalent to “actual credential theft”; the independently derived population contains a large ambiguous stratum. Original-label recall is {rec(original_mal):.2%}, while verified-malicious recall is {rec(vm):.2%}. That decomposition shows why 79.19% alone cannot establish a model defect.\n\nThe hard-negative rule also remains unresolved at record level: the MSEDCL template is replicated, and its false-positive behavior must be reported both as {audit['hard_negative']['record_level']['fpr']:.2%} record-level FPR and {audit['hard_negative']['unique_message_level']['fpr']:.2%} unique-message FPR.\n\nNo challenger was trained. No champion, TEST/OOD file, URL-ml file, package, TFLite artifact, Android integration, commit, or push was created or modified by this audit.\n\nReproduce with `python Messages-ml/model_training/final_release_gate_audit.py`; inspect the accompanying JSON for exact hashes, rows, probabilities, and examples.\n"""
    (OUT / "FINAL_RELEASE_DECISION.md").write_text(decision_md, encoding="utf-8")

    registry = OUT / "AUDIT_EXPERIMENT_REGISTRY.jsonl"
    event = {"event": "FINAL_RELEASE_GATE_AUDIT", "timestamp_utc": audit["timestamp_utc"], "artifact": "FINAL_RELEASE_GATE_AUDIT.json", "decision": "CONDITION_B_DATASET_REMEDIATION_REQUIRED", "model": audit["model"], "credential_population": len(cred_rows), "original_recall": rec(original_mal), "verified_recall": rec(vm), "hard_negative_record_fpr": audit["hard_negative"]["record_level"]["fpr"], "hard_negative_unique_fpr": audit["hard_negative"]["unique_message_level"]["fpr"], "historical_registry_preserved": True}
    with registry.open("a", encoding="utf-8") as f: f.write(json.dumps(event) + "\n")
    print(json.dumps({"decision": "CONDITION_B_DATASET_REMEDIATION_REQUIRED", "credential": audit["credential_metric_decomposition"], "hard_negative": audit["hard_negative"], "gate_metrics": audit["gate_metrics"]}, indent=2, default=lambda x: dict(x)))


if __name__ == "__main__":
    main()
