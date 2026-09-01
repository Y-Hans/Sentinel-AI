import json
import hashlib
from pathlib import Path
import random

ROOT = Path("C:/Users/user/Programing files/Yajat/Projects/sentinel-ml/Messages-ml")

NEW_CONTRASTIVE_PAIRS = [
    # 1. Tax Notice / IT Act
    {
        "family": "TAX_NOTICE",
        "legit": [
            "Income Tax Department: Notice under section 143(1) for your PAN XXXXX1234X has been sent to your registered email. Please check the demand details on incometax.gov.in.",
            "Dear Taxpayer, intimation u/s 143(1) of Income Tax Act for AY 2022-23 has been sent. Log in to the official e-filing portal to respond."
        ],
        "malicious": [
            "Income Tax Department: Urgent notice under section 143(1) for PAN XXXXX1234X. Pay your pending demand of Rs 5000 immediately at http://bit.ly/pay-itd-demand or face arrest.",
            "Dear Taxpayer, your ITR is flagged under section 143(1). Click here http://tax-refund-claim.info to update PAN and prevent bank account freeze."
        ]
    },
    # 2. Video KYC / Credit Card
    {
        "family": "VIDEO_KYC",
        "legit": [
            "URGENT: Complete your video KYC for your new Axis bank credit card application. Click on official site link axisbank.com/vkyc to complete process.",
            "SBI Card: Your credit card application is pending for Video KYC. Please complete it by visiting sbicard.com/vkyc between 10 AM and 6 PM."
        ],
        "malicious": [
            "URGENT: Your Axis bank credit card is blocked. Complete video KYC immediately at http://axis-vkyc-update.com to unblock.",
            "SBI Card Alert: Your reward points worth Rs 5000 are expiring today. Complete Video KYC at http://bit.ly/sbi-rewards to claim them."
        ]
    },
    # 3. Electricity / Disconnection
    {
        "family": "ELECTRICITY_DISCONNECTION",
        "legit": [
            "TNEB Alert: Your electricity bill for consumer no. 123456789 is Rs. 1500. Please pay before 20-Nov to avoid disconnection of power supply.",
            "BESCOM: Power shutdown scheduled in your area on Sunday from 10 AM to 5 PM for maintenance work. We regret the inconvenience caused."
        ],
        "malicious": [
            "TNEB Alert: Your electricity will be disconnected tonight at 9 PM due to unpaid bill. Update payment immediately at http://tneb-quickpay.in or call 9876543210.",
            "BESCOM: Power shutdown alert! Your previous month bill is pending. Pay Rs 15 instantly at http://bit.ly/bescom-pay to avoid power cut."
        ]
    },
    # 4. Bank Branch Visit for KYC
    {
        "family": "BANK_BRANCH_KYC",
        "legit": [
            "ICICI Bank: Your account XXXXX1234 has a pending KYC update. Please visit your nearest branch with PAN and Aadhar copy within 15 days to prevent account freeze.",
            "PNB Alert: As per RBI guidelines, periodic KYC update is due. Please submit your latest documents at your home branch."
        ],
        "malicious": [
            "ICICI Bank: Your account XXXXX1234 is frozen due to pending KYC. Click http://icici-kyc-update.com to upload PAN and Aadhar online immediately.",
            "PNB Alert: RBI has suspended your account. Update your KYC instantly via http://pnb-online-kyc.in to restore your banking services."
        ]
    },
    # 5. Aadhaar Auth Successful
    {
        "family": "AADHAAR_AUTH",
        "legit": [
            "UIDAI: Your Aadhaar authentication was successful for e-KYC. If you did not initiate this, please call 1947 or lock your biometrics on the mAadhaar app.",
            "UIDAI Notification: OTP for Aadhaar authentication is 123456. Do not share this with anyone."
        ],
        "malicious": [
            "UIDAI: Your Aadhaar authentication failed. Your Aadhaar will be deactivated. Click http://uidai-update-status.in to verify your identity.",
            "UIDAI Notification: Someone is trying to use your Aadhaar. Download this safety app http://bit.ly/uidai-safe to secure your biometrics."
        ]
    },
    # 6. Net Banking Locked / Password Reset
    {
        "family": "NET_BANKING_LOCKED",
        "legit": [
            "HDFC Bank: Your net banking access is temporarily locked due to 3 incorrect password attempts. Reset your password using ATM debit card online.",
            "Kotak Bank: Your account is locked for security reasons. Please visit the nearest branch or use the official app to reset your credentials."
        ],
        "malicious": [
            "HDFC Bank: Your net banking is locked due to suspicious activity. Unlock immediately by verifying your details at http://hdfc-netbanking-unlock.com.",
            "Kotak Bank: Your account is locked. Reply with your ATM PIN to this message to verify your identity and restore access."
        ]
    },
    # 7. Traffic Challan
    {
        "family": "TRAFFIC_CHALLAN",
        "legit": [
            "Traffic Police: A challan of Rs. 500 is issued for vehicle DL12AB1234. Pay fine via official portal echallan.parivahan.gov.in to avoid court notice.",
            "Bengaluru Traffic Police: Traffic violation recorded for your vehicle. Please check the details and pay the fine on the official state portal."
        ],
        "malicious": [
            "Traffic Police: A challan of Rs. 5000 is pending. Pay immediately at http://echallan-pay-online.in to avoid vehicle seizure and arrest.",
            "Traffic Police Alert: Your vehicle registration will be cancelled today due to unpaid challan. Pay Rs 500 fine instantly at http://bit.ly/traffic-fine-pay."
        ]
    },
    # 8. SIM Card KYC
    {
        "family": "SIM_KYC",
        "legit": [
            "Jio: Your SIM card KYC is expiring in 7 days. To continue uninterrupted services, please complete verification at your nearest Jio store.",
            "Vi Alert: Re-verification of your mobile number is required as per DoT guidelines. Please visit our retail outlet with valid ID proof."
        ],
        "malicious": [
            "Jio: Your SIM card will be blocked in 2 hours due to incomplete KYC. Call customer care at 9876543210 immediately or click http://jio-kyc-verify.com.",
            "Vi Alert: Your number is suspended. Complete your e-KYC online at http://vi-sim-update.in to restore your outgoing services."
        ]
    },
    # 9. GSTN GSTR-3B
    {
        "family": "GSTN_FILING",
        "legit": [
            "GSTN: Dear Taxpayer, GSTR-3B for the tax period Oct 2023 is not filed. Please file it immediately to avoid late fee and penalty.",
            "GST Network: Reminder to file your GSTR-1 by the 11th of this month. Ignore if already filed."
        ],
        "malicious": [
            "GSTN: Dear Taxpayer, your GST registration is cancelled due to non-filing of GSTR-3B. Pay penalty of Rs 10000 at http://gst-penalty-pay.com to restore.",
            "GST Network: Urgent! Your bank accounts will be attached for pending GST dues. Click http://bit.ly/gst-clearance to pay online and stop action."
        ]
    }
]

def generate_record(text, label, index, family):
    msg_id = hashlib.sha256(text.encode("utf-8")).hexdigest()
    return {
        "message_id": msg_id,
        "raw_text": text,
        "security_label": label,
        "source_id": "SRC_ERROR_DRIVEN_EXPANSION_V1",
        "source_type": "SYNTHETIC",
        "primary_type": family,
        "split": "TRAIN",
        "language": "en"
    }

def main():
    train_file = ROOT / "data" / "processed" / "train_contrastive.jsonl"
    expanded_file = ROOT / "data" / "processed" / "train_expanded_v2.jsonl"
    
    records = []
    with open(train_file, "r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                records.append(json.loads(line.strip()))
                
    added_count = 0
    # Add new pairs with a 20x replication factor to ensure model learns them well
    for family_data in NEW_CONTRASTIVE_PAIRS:
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
            
    print(f"Added {added_count} new contrastive synthetic records to training data.")
    print("Saved as train_expanded_v2.jsonl")

if __name__ == "__main__":
    main()
