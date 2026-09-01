import json
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

SYNTHETIC_HARD_NEGATIVES = [
    # Urgent/Blocked/Suspended
    "Dear customer, your request to temporarily block your SBI credit card ending in 4592 is processed. If this was not you, contact branch urgently.",
    "Your ICICI NetBanking account access has been suspended due to 3 incorrect password attempts. Please visit the nearest branch to unblock.",
    "HDFC Bank: Your account is temporarily restricted for online transactions per your request. To restore, please call customer care.",
    
    # KYC/PAN/OTP/Security Warning/Unauthorized
    "UIDAI: Never share your Aadhaar OTP with anyone. UIDAI never calls to ask for OTP. Keep your biometric locked for security.",
    "Axis Bank Security Alert: We detected an unauthorized login attempt from a new device. If this was not you, immediately call 1800-419-5959 to block your account.",
    "Dear Taxpayer, verify your PAN details on the official Income Tax portal before the deadline. Ignore emails asking for OTP.",
    "Bank of Baroda: Important KYC update alert. As per RBI guidelines, please submit your latest documents at your home branch. Do not click on links sent via SMS.",
    
    # Penalty/Payment/Refund/Expire/Deadline
    "MSEDCL: Your electricity bill of Rs.1250 is due. Pay before the deadline of 15th to avoid a late penalty of Rs.50. Pay via official Mahadiscom app.",
    "Income Tax Department: Your refund of Rs. 4500 for AY 2023-24 has been credited to your bank account. Check your account statement.",
    "Your term insurance policy with LIC is about to expire today. Pay the premium online securely to avoid policy lapse.",
    "Traffic Police: A challan of Rs. 500 has been issued for your vehicle. Pay via official eChallan portal parivahan.gov.in.",
    "PNB: Your UPI mandate of Rs. 5000 will expire on 30th Nov. Please renew via PNB One app if required.",
    
    # Extra variants
    "Security Warning: Do not click on unknown links offering part-time jobs or lottery prizes. SBI will never ask for such payments.",
    "Action Required: Your KYC is expiring in 10 days. Visit your branch to update. Do not click any links to avoid fraud.",
    "Dear User, your UPI PIN is strictly confidential. Never share it to receive payments. UPI PIN is only used for sending money.",
    "Urgent notification: Your FASTag balance is low. Please recharge via official bank app to avoid double toll penalty at plaza."
]

def generate_record(text, index):
    msg_id = hashlib.sha256(text.encode("utf-8")).hexdigest()
    return {
        "message_id": msg_id,
        "raw_text": text,
        "security_label": "BENIGN",
        "source_id": "SRC_SYNTHETIC_HARD_NEGATIVES_V2",
        "source_type": "SYNTHETIC",
        "primary_type": "SERVICE_UPDATE",
        "split": "TRAIN",
        "language": "en"
    }

def main():
    train_file = ROOT / "data" / "processed" / "train.jsonl"
    expanded_file = ROOT / "data" / "processed" / "train_expanded.jsonl"
    
    # Copy existing records
    records = []
    with open(train_file, "r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                records.append(json.loads(line.strip()))
                
    # Add synthetic records (multiply to give them weight, e.g. 20 copies each since our dataset has 15k records)
    for i, text in enumerate(SYNTHETIC_HARD_NEGATIVES):
        for j in range(20):
            records.append(generate_record(text, f"{i}_{j}"))
            
    with open(expanded_file, "w", encoding="utf-8") as f:
        for r in records:
            f.write(json.dumps(r) + "\n")
            
    print(f"Added {len(SYNTHETIC_HARD_NEGATIVES) * 20} synthetic hard negative records to training data.")
    print("Saved as train_expanded.jsonl")

if __name__ == "__main__":
    main()
