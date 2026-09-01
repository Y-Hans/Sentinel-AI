import json
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# Stage E: Diverse Semantic Expansion & Stage F: Adversarial Pairs
# Format: Tuple of (LEGITIMATE_TEXT, MALICIOUS_TEXT_PAIR)
# This forces the model to learn the semantic boundaries instead of relying on keywords.
ADVERSARIAL_PAIRS = [
    # URGENT + KYC
    ("Bank of Baroda: Urgent notice. Your KYC is expiring in 5 days. Please visit your home branch with PAN and Aadhar to update.", 
     "Bank of Baroda: Urgent notice. Your KYC is expiring today. Update immediately via this link http://bob-kyc-update.xyz to avoid block."),
    
    # URGENT + ACCOUNT BLOCK
    ("HDFC Bank Alert: We have temporarily blocked your account ending in 1234 due to multiple failed logins. Call 1800-202-6161 for assistance.",
     "HDFC Bank Alert: Your account is blocked urgently due to suspicious activity. Verify your identity now at https://hdfc-unblock.net to restore access."),
     
    # OTP + SECURITY WARNING
    ("SBI Security Warning: Your OTP for fund transfer is 849201. Never share this OTP with anyone, even bank officials.",
     "SBI Security Warning: We detected a fraudulent transaction. Share the OTP 849201 sent to your phone to our support agent to cancel it."),
     
    # PAYMENT + LINK
    ("ICICI Credit Card: Your payment of Rs. 4500 is due on 15th Aug. Pay securely via the iMobile app or official website icicibank.com.",
     "ICICI Credit Card: Your payment of Rs. 4500 is overdue. Pay immediately via http://icici-quick-pay.com to avoid Rs.500 penalty."),
     
    # SUSPENSION + LINK
    ("Income Tax Dept: Your account may be suspended if ITR is not filed. Visit official portal incometax.gov.in to file before deadline.",
     "Income Tax Dept: Your account is suspended for tax evasion. Click here http://incometax-refund-update.com to pay penalty and restore."),
     
    # BANK + VERIFY
    ("Axis Bank: Please verify your contact details next time you visit the ATM. Ensure your phone number is updated for security alerts.",
     "Axis Bank: Verify your account immediately to prevent closure. Login at http://axis-verify-secure.info using your netbanking password."),
     
    # CARD + UNAUTHORIZED
    ("Kotak Bank: We blocked an unauthorized transaction on your debit card. A new card will be dispatched. Call support if you need help.",
     "Kotak Bank: Unauthorized transaction detected! Click https://kotak-fraud-alert.com and enter your card details to reverse the charge."),
     
    # TAX + PENALTY
    ("Income Tax Dept: A late fee penalty under section 234F applies if you file ITR after the due date. Check incometax.gov.in for details.",
     "Income Tax Dept: You have an unpaid penalty of Rs 1000. Your bank accounts will be frozen. Pay now at http://it-dept-penalty.com."),
     
    # ELECTRICITY + DISCONNECTION
    ("MSEDCL: Dear Consumer No 123456789, your bill is generated. Pay by 20th to avoid disconnection. Use Mahavitaran app.",
     "MSEDCL: Dear customer, your electricity power will be disconnected tonight at 9 PM. Pay previous month bill update via http://msedcl-quick.com."),
     
    # DELIVERY + OTP
    ("Amazon Delivery: Your package is out for delivery today. Share OTP 5928 only with the delivery agent at your doorstep.",
     "Amazon Delivery: Your package is stuck due to incomplete address. Send OTP 5928 to this number to confirm delivery address or package will be returned."),
     
    # LOGIN + SECURITY ALERT
    ("Google Security Alert: A new login was detected on a Windows device. If this was you, you can ignore this. If not, change your password.",
     "Google Security Alert: Your account was accessed by a hacker. Click http://google-security-recovery.xyz to secure your account immediately.")
]

def generate_record(text, label, source, idx, primary_type):
    msg_id = hashlib.sha256(text.encode("utf-8")).hexdigest()
    return {
        "message_id": msg_id,
        "raw_text": text,
        "security_label": label,
        "source_id": source,
        "source_type": "SYNTHETIC",
        "primary_type": primary_type,
        "split": "TRAIN",
        "language": "en",
        "synthetic_reason": "STAGE_E_F_DIVERSE_ADVERSARIAL",
        "generation_family": "ADVERSARIAL_PAIRS_V1"
    }

def main():
    train_file = ROOT / "data" / "processed" / "train_expanded.jsonl"
    v3_file = ROOT / "data" / "processed" / "train_expanded_v3.jsonl"
    
    records = []
    with open(train_file, "r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                records.append(json.loads(line.strip()))
                
    # Add 25 copies of each to ensure the model learns them well against the 15k records background
    for i, (legit_text, mal_text) in enumerate(ADVERSARIAL_PAIRS):
        for j in range(25):
            records.append(generate_record(legit_text, "BENIGN", "SRC_ADV_PAIR_LEGIT", f"{i}_{j}", "SERVICE_UPDATE"))
            records.append(generate_record(mal_text, "MALICIOUS", "SRC_ADV_PAIR_MALICIOUS", f"{i}_{j}", "PHISHING"))
            
    with open(v3_file, "w", encoding="utf-8") as f:
        for r in records:
            f.write(json.dumps(r) + "\n")
            
    print(f"Added {len(ADVERSARIAL_PAIRS) * 50} adversarial pair records to training data.")
    print("Saved as train_expanded_v3.jsonl")

if __name__ == "__main__":
    main()
