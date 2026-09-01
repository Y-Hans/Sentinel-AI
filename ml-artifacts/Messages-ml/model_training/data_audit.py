import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
sys.path.insert(0, str(ROOT / "evaluation"))

from leakage_analysis import audit_splits_for_leakage, LeakageAuditReport
from feature_extraction import extract_message_features

def check_feature_leakage(records, split_name):
    """
    Ensures no feature matches target/label fields.
    """
    leaks = 0
    forbidden_keys = {"security_label", "primary_type", "threat_vectors", "source_id", "message_id", "template_cluster_id", "split"}
    
    for r in records:
        features = extract_message_features(r.get("raw_text", ""), r.get("sender_header"))
        for k in features.keys():
            if k in forbidden_keys:
                leaks += 1
                break
    return leaks

def run_audit():
    data_dir = ROOT / "data" / "processed"
    splits = {"TRAIN": "train.jsonl", "VALIDATION": "val.jsonl", "TEST": "test.jsonl", "OOD": "ood.jsonl"}
    
    records_by_split = {}
    total_leaks = 0
    
    for split, filename in splits.items():
        filepath = data_dir / filename
        if filepath.exists():
            records = []
            with open(filepath, "r", encoding="utf-8") as f:
                for line in f:
                    if line.strip():
                        records.append(json.loads(line.strip()))
            records_by_split[split] = records
            print(f"Loaded {len(records)} records from {split}")
            
            # Check feature leakage
            leaks = check_feature_leakage(records[:100], split)  # Check first 100 for efficiency
            total_leaks += leaks
        else:
            print(f"Warning: {filepath} not found.")

    report = audit_splits_for_leakage(records_by_split, check_ood_sender=True)
    
    # Save the audit report
    results_dir = Path(__file__).resolve().parent / "results"
    results_dir.mkdir(exist_ok=True)
    
    audit_md = f"""# DATA AUDIT REPORT

## Leakage Audit
- Total Records: {report.total_records}
- Is Leakage Free: {report.is_leakage_free}
- Exact Text Overlaps: {report.exact_text_cross_split_overlaps}
- Template Overlaps: {report.template_cluster_cross_split_overlaps}
- OOD Sender Overlaps: {report.ood_sender_cross_split_overlaps}

## Feature Leakage
- Forbidden Feature Keys Detected: {total_leaks > 0}
"""
    with open(ROOT / "model_training" / "DATA_AUDIT_REPORT.md", "w", encoding="utf-8") as f:
        f.write(audit_md)

    feature_leak_md = f"""# FEATURE LEAKAGE REPORT

- Target variables used as features: {'YES' if total_leaks > 0 else 'NO'}
"""
    with open(ROOT / "model_training" / "FEATURE_LEAKAGE_REPORT.md", "w", encoding="utf-8") as f:
        f.write(feature_leak_md)
        
    print(f"Audit completed. Leakage free: {report.is_leakage_free}. Total feature leaks: {total_leaks}")

if __name__ == "__main__":
    run_audit()
