# Credential Taxonomy Remediation

This is a derived adjudication layer; source labels are unchanged. The `CREDENTIAL_REQUEST` vector is a multi-label threat-vector field in the schema, while the security label is a three-tier BENIGN/SUSPICIOUS_SPAM/MALICIOUS status. The schema does not define `CREDENTIAL_REQUEST` as a binary ground-truth credential-theft outcome, so the existing ≥80% gate is ambiguous.

Population: **2302**, original `MALICIOUS`: **2302**.

| Category | N | False negatives | Recall |
|---|---:|---:|---:|
| VERIFIED_MALICIOUS | 15 | 0 | 100.00% |
| PROBABLE_MALICIOUS | 17 | 9 | 47.06% |
| SUSPICIOUS | 624 | 42 | 93.27% |
| AMBIGUOUS | 1629 | 425 | 73.91% |
| ANTI_PHISHING | 17 | 3 | 82.35% |
| VERIFIED_BENIGN | 0 | 0 | n/a |

## Interpretation

Original-label recall is **79.19%**. Verified-malicious recall is **100.00%**. Including probable records gives **71.88%**; including suspicious records as a conservative attack population gives **92.23%**. The 27-record verified-malicious historical stratum is explicitly represented in the JSON and has no misses in this reconstruction.

The 80% gate cannot be declared scientifically valid for all 2,302/derived records without an annotation policy stating that every record carrying the multi-label vector is an actual credential-theft attack. The defensible next step is independent dual annotation and a versioned gate population; do not silently relabel or delete records.
