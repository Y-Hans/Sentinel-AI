import json
import hashlib
from pathlib import Path
import random

ROOT = Path("C:/Users/user/Programing files/Yajat/Projects/sentinel-ml/Messages-ml")

CONTRASTIVE_PAIRS = [
    # Family A: TAX_NOTICE
    {
        "family": "TAX_NOTICE",
        "legit": [
            "Income Tax Department: Non-filing of ITR for AY 2023-24 may attract late fee under Section 234F. File return on official portal incometax.gov.in.",
            "Dear Taxpayer, verify your PAN details on the official Income Tax portal before the deadline. Ignore emails asking for OTP.",
            "Income Tax Department: Your refund of Rs. 4500 for AY 2023-24 has been credited to your bank account. Check your account statement."
        ],
        "malicious": [
            "Income Tax Department: Non-filing of ITR for AY 2023-24 may attract late fee under Section 234F. Pay penalty immediately at http://bit.ly/tax-penalty to avoid police action.",
            "Dear Taxpayer, your PAN is blocked due to pending tax. Update KYC immediately on http://pan-update.com/kyc or pay Rs. 5000 penalty.",
            "Income Tax Department: Your refund of Rs. 4500 for AY 2023-24 is approved. Click http://refund-tax.in/claim to claim your refund and enter OTP."
        ]
    },
    # Family B: ELECTRICITY_WARNING
    {
        "family": "ELECTRICITY_WARNING",
        "legit": [
            "MSEDCL: Consumer No 12345678, bill amount Rs. 1250 is due on 15th Nov. Pay promptly through Mahavitaran official app.",
            "BESCOM Power Update: Scheduled power shutdown for feeder maintenance in your area on 10-Nov from 10AM to 4PM. Inconvenience regretted.",
            "MSEDCL: Your electricity bill of Rs.1250 is due. Pay before the deadline of 15th to avoid a late penalty of Rs.50. Pay via official Mahadiscom app."
        ],
        "malicious": [
            "MSEDCL: Consumer No 12345678, your power will be cut tonight at 9:30 PM because previous month bill is not updated. Call electricity officer immediately on 9876543210.",
            "Dear Customer, your electricity connection will be disconnected in 2 hours. Pay outstanding Rs. 12 on http://pay-ebill.in/quickpay to avoid power cut.",
            "URGENT: Electricity department will cut power at 9 PM. Update your bill payment at http://mse-update.com or call 9876543210 immediately."
        ]
    },
    # Family C: KYC_UPDATE / UNAUTHORIZED_LOGIN
    {
        "family": "KYC_UPDATE",
        "legit": [
            "UIDAI Notification: Your Aadhaar authentication was performed on 12-Nov via Biometric at Telecom Partner. If unauthorized, lock Aadhaar at resident.uidai.gov.in.",
            "Axis Bank Security Alert: We detected an unauthorized login attempt from a new device. If this was not you, immediately call 1800-419-5959 to block your account.",
            "Bank of Baroda: Important KYC update alert. As per RBI guidelines, please submit your latest documents at your home branch. Do not click on links sent via SMS."
        ],
        "malicious": [
            "UIDAI Notification: Your Aadhaar is suspended. Authentication failed. Verify KYC immediately at http://aadhaar-verify.in/kyc to prevent blocking.",
            "Axis Bank Security Alert: We detected an unauthorized login. Your account is locked. Unlock immediately at http://axis-secure.com/login and enter OTP.",
            "Bank of Baroda: Important KYC update pending. Your account will be frozen in 24 hrs. Click http://bob-kyc.in to update PAN card online."
        ]
    }
]

def generate_record(text, label, index, family):
    msg_id = hashlib.sha256(text.encode("utf-8")).hexdigest()
    return {
        "message_id": msg_id,
        "raw_text": text,
        "security_label": label,
        "source_id": "SRC_CONTRASTIVE_PAIRS_V1",
        "source_type": "SYNTHETIC",
        "primary_type": family,
        "split": "TRAIN",
        "language": "en"
    }

def main():
    train_file = ROOT / "data" / "processed" / "train.jsonl"
    expanded_file = ROOT / "data" / "processed" / "train_contrastive.jsonl"
    
    records = []
    with open(train_file, "r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                records.append(json.loads(line.strip()))
                
    # Add contrastive pairs
    added_count = 0
    # Add pairs with a 20x replication factor for training weight
    for family_data in CONTRASTIVE_PAIRS:
        family = family_data["family"]
        for i, text in enumerate(family_data["legit"]):
            for j in range(20):
                records.append(generate_record(text, "BENIGN", f"L_{family}_{i}_{j}", family))
                added_count += 1
        for i, text in enumerate(family_data["malicious"]):
            for j in range(20):
                records.append(generate_record(text, "MALICIOUS", f"M_{family}_{i}_{j}", family))
                added_count += 1
            
    with open(expanded_file, "w", encoding="utf-8") as f:
        for r in records:
            f.write(json.dumps(r) + "\n")
            
    print(f"Added {added_count} contrastive synthetic records to training data.")
    print("Saved as train_contrastive.jsonl")

if __name__ == "__main__":
    main()
